package com.qawse.openpage.drivers

import android.graphics.Bitmap
import com.qawse.openpage.data.ColorMode
import com.qawse.openpage.data.PrintQuality
import com.qawse.openpage.data.PrintSettings
import com.qawse.openpage.data.PrinterDatabase.DriverFamily
import com.qawse.openpage.usb.UsbPrinterConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Epson ESC/P-R driver — the native raster language of modern Epson inkjets
 * (Stylus, EcoTank, WorkForce, Expression) and the closest cousin for Canon.
 *
 * Job skeleton follows the publicly documented ESC/P-R structure:
 *
 *   ESC ( G 01 01 02        enter graphics mode
 *   ESC ( U 01 01 05        unit: 1440 dpi
 *   ESC ( S 08 …            quality/density block
 *   ESC ( C 02 00 {len}     page length (big-endian, 360ths)
 *   ESC ( V 02 00 {len}     top margin (big-endian, 360ths)
 *   ESC . 00 {len LE} 04 00 00 00 1c 1c + data    one byte per dot:
 *                           0x00 = full ink, 0xFF = blank — the printer's own
 *                           halftoner takes it from there
 *   ESC ( E 01 01 00        end graphics
 *   0x0c                    form feed
 *   ESC @                   reset
 */
class EscPRDriver : PrinterDriver {

    override val family: DriverFamily = DriverFamily.ESCPR

    override fun renderDpi(settings: PrintSettings): Int {
        val dpi = settings.quality.escprDpi()
        val maxPixels = 42_000_000L
        val wPx = (settings.paper.widthMm / 25.4f * dpi).toLong()
        val hPx = (settings.paper.heightMm / 25.4f * dpi).toLong()
        return if (wPx * hPx > maxPixels) {
            val scale = kotlin.math.sqrt(maxPixels.toDouble() / (wPx * hPx))
            (dpi * scale).toInt().coerceAtLeast(180)
        } else dpi
    }

    private fun qualityBlock(quality: PrintQuality): ByteArray {
        val q = when (quality) {
            PrintQuality.DRAFT -> 0x08
            PrintQuality.NORMAL -> 0x10
            PrintQuality.HIGH -> 0x28
        }
        return byteArrayOf(Cmd.ESC, '('.code.toByte(), 'S'.code.toByte(), 0x08, 0x00, 0x00, q.toByte(), 0x00,
            0x01, 0x28, 0xFF.toByte(), 0xFF.toByte(), 0x07, 0x00)
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
            if (out.write(data) < 0) throw PrintTransferException("Epson printer stopped accepting data")
        }

        fun escCmd(name: Char, len16be: Int) =
            byteArrayOf(Cmd.ESC, '('.code.toByte(), name.code.toByte(), 0x02, 0x00,
                Cmd.u16be(len16be)[0], Cmd.u16be(len16be)[1])

        for (index in 0 until pageCount) {
            coroutineContext.ensureActive()
            onPageStarted(index, pageCount)
            val page = renderPage(index)

            // Header per sheet
            write(byteArrayOf(Cmd.ESC, '('.code.toByte(), 'G'.code.toByte(), 0x01, 0x01, 0x02)) // graphics mode
            write(byteArrayOf(Cmd.ESC, '('.code.toByte(), 'U'.code.toByte(), 0x01, 0x01, 0x05)) // 1440dpi unit
            write(qualityBlock(settings.quality))

            // Page length and top margin in 360 dpi dots (big-endian)
            val pageLenDots = (settings.paper.heightMm / 25.4f * 360).toInt()
            write(escCmd('C', pageLenDots))
            write(escCmd('V', 0))

            val mono = settings.colorMode == ColorMode.MONO

            Raster.streamDensityRows(page) { raw ->
                coroutineContext.ensureActive()
                val row = if (mono) ByteArray(raw.size) { i ->
                    if (raw[i] < 128) 0x00 else 0xFF.toByte()
                } else raw
                val len = row.size
                write(byteArrayOf(Cmd.ESC, '.'.code.toByte(), 0x00,
                    Cmd.u16le(len)[0], Cmd.u16le(len)[1],
                    0x04, 0x00, 0x00, 0x00, 0x1C, 0x1C))
                write(row)
            }

            // End graphics, eject, restore
            write(byteArrayOf(Cmd.ESC, '('.code.toByte(), 'E'.code.toByte(), 0x01, 0x01, 0x00))
            write(byteArrayOf(0x0C))
            page.recycle()
        }

        write(byteArrayOf(Cmd.ESC, '@'.code.toByte()))
    }
}
