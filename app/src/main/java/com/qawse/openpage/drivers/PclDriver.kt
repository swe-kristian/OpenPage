package com.qawse.openpage.drivers

import android.graphics.Bitmap
import com.qawse.openpage.data.ColorMode
import com.qawse.openpage.data.DuplexMode
import com.qawse.openpage.data.PrintSettings
import com.qawse.openpage.data.PrinterDatabase.DriverFamily
import com.qawse.openpage.usb.UsbPrinterConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * HP PCL raster driver — the workhorse for lasers and PCL-speaking inkjets.
 *
 * Speaks the classic PCL raster graphics model (all commands carry the ESC
 * prefix — see Cmd and PclCommandTest):
 *
 *   ESC E              printer reset
 *   ESC & l # S        duplex mode (0 simplex, 1 long edge, 2 short edge)
 *   ESC & l # A        page size
 *   ESC & l # O        orientation
 *   ESC & l 0 M        plain media
 *   ESC & u # D        x resolution
 *   ESC * t # R        raster resolution
 *   ESC * r # U        color mode (4 = KCMY planar)
 *   ESC * r 1 A … B    raster begin/end
 *   ESC * b # W        row transfer
 *   ESC & l 0 H        eject page
 */
open class PclDriver : PrinterDriver {

    override val family: DriverFamily = DriverFamily.PCL
    override val supportsColor: Boolean = true

    override fun renderDpi(settings: PrintSettings): Int {
        val requested = settings.quality.laserDpi()
        val color = settings.colorMode == ColorMode.COLOR
        val cap = if (color) 300 else requested
        return capOf(cap, settings)
    }

    /** Keeps the rendered pixel budget inside heap-safe territory. */
    private fun capOf(dpi: Int, settings: PrintSettings): Int {
        val maxPixels = 42_000_000L
        val wPx = (settings.paper.widthMm / 25.4f * dpi).toLong()
        val hPx = (settings.paper.heightMm / 25.4f * dpi).toLong()
        return if (wPx * hPx > maxPixels) {
            val scale = kotlin.math.sqrt(maxPixels.toDouble() / (wPx * hPx))
            (dpi * scale).toInt().coerceAtLeast(150)
        } else dpi
    }

    override suspend fun print(
        connection: UsbPrinterConnection,
        pageCount: Int,
        settings: PrintSettings,
        renderPage: suspend (index: Int) -> Bitmap,
        onPageStarted: (index: Int, total: Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val out = connection

        fun write(data: ByteArray) {
            val n = out.write(data)
            if (n < 0) throw PrintTransferException("printer stopped accepting data (PCL)")
        }

        // Soft reset, then duplex + media directives for the whole job.
        write(Cmd.pclReset())
        write(Cmd.pclDuplex(when (settings.duplex) {
            DuplexMode.LONG -> 1
            DuplexMode.SHORT -> 2
            DuplexMode.OFF -> 0
        }))

        for (index in 0 until pageCount) {
            coroutineContext.ensureActive()
            onPageStarted(index, pageCount)
            val page = renderPage(index)

            val landscape = page.width > page.height
            val paperWmm = if (landscape) settings.paper.heightMm else settings.paper.widthMm
            val dpi = (page.width / (paperWmm / 25.4f)).toInt().coerceAtLeast(150)

            write(Cmd.pclPageSize(settings.paper.pclCode))
            write(Cmd.pclOrientation(landscape))
            write(Cmd.pclMediaPlain())
            write(Cmd.pclJobCopies(1))          // one copy per sheet at this level
            write(Cmd.pclXRes(dpi))
            write(Cmd.pclRasterRes(dpi))
            write(Cmd.pclRasterPresentationPortrait())

            val color = settings.colorMode == ColorMode.COLOR && supportsColor
            if (color) write(Cmd.pclColorMode(4)) // KCMY planar

            write(Cmd.pclRasterStart())

            if (color) {
                Raster.streamKcmyRows(page) { k, c, m, y, rowBytes ->
                    coroutineContext.ensureActive()
                    write(Cmd.pclRowHeader(rowBytes)); write(k)
                    write(Cmd.pclRowHeader(rowBytes)); write(c)
                    write(Cmd.pclRowHeader(rowBytes)); write(m)
                    write(Cmd.pclRowHeader(rowBytes)); write(y)
                }
            } else {
                Raster.streamMonoRows(page) { row, rowBytes ->
                    coroutineContext.ensureActive()
                    write(Cmd.pclRowHeader(rowBytes))
                    write(row)
                }
            }

            write(Cmd.pclRasterEnd())
            write(Cmd.pclEject())
            page.recycle()
        }

        write(Cmd.pclReset())
    }
}
