package com.qawse.openpage.drivers

import android.graphics.Bitmap
import com.qawse.openpage.data.PrintSettings
import com.qawse.openpage.data.PrinterDatabase.DriverFamily
import com.qawse.openpage.usb.UsbPrinterConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * ESC/POS thermal driver — receipt and label printers.
 *
 *   ESC @              initialize
 *   ESC a n            justification
 *   GS v 0 m xh xl yh yl + data   raster bit image (1 = ink)
 *   ESC d n            feed n lines
 *   GS V 66 0          partial cut
 *
 * Widths follow the classic print heads: 576 dots for 80 mm paper,
 * 384 dots for 58 mm paper.
 */
class EscPosDriver : PrinterDriver {

    override val family: DriverFamily = DriverFamily.ESCPOS

    override fun renderDpi(settings: PrintSettings): Int = settings.quality.escposDpi()

    private fun targetWidthDots(settings: PrintSettings): Int = when {
        settings.paper.widthMm <= 60f -> 384
        else -> 576
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
            if (out.write(data) < 0) throw PrintTransferException("Thermal printer stopped accepting data")
        }

        write(byteArrayOf(Cmd.ESC, '@'.code.toByte()))             // init
        write(byteArrayOf(Cmd.ESC, 'a'.code.toByte(), 1.toByte())) // center

        for (index in 0 until pageCount) {
            coroutineContext.ensureActive()
            onPageStarted(index, pageCount)
            val page = renderPage(index)

            // Scale the paper-sized render onto the fixed head width.
            val dots = targetWidthDots(settings)
            val scale = dots.toFloat() / page.width
            val hDots = (page.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(page, dots, hDots, true)
            page.recycle()

            // GS v 0 transfers in height chunks to keep printer buffers happy.
            val chunkRows = 960
            var y = 0
            while (y < hDots) {
                coroutineContext.ensureActive()
                val rows = minOf(chunkRows, hDots - y)
                val slice = Bitmap.createBitmap(scaled, 0, y, dots, rows)
                val rowBytes = (dots + 7) / 8

                write(byteArrayOf(0x1D.toByte(), 'v'.code.toByte(), 0.toByte(),
                    '0'.code.toByte(), Cmd.u16le(rowBytes)[0], Cmd.u16le(rowBytes)[1],
                    Cmd.u16le(rows)[0], Cmd.u16le(rows)[1]))

                Raster.streamMonoRows(slice) { row, n ->
                    coroutineContext.ensureActive()
                    write(if (n == rowBytes) row else row.copyOf(rowBytes))
                }
                slice.recycle()
                y += rows
            }
            scaled.recycle()

            write(byteArrayOf(Cmd.ESC, 'd'.code.toByte(), 4.toByte())) // feed before cut
        }

        write(byteArrayOf(0x1D.toByte(), 'V'.code.toByte(), 66.toByte(), 0.toByte())) // cut
        write(byteArrayOf(Cmd.ESC, '@'.code.toByte()))
    }
}
