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
 * PostScript Level 2 driver — office lasers with a PS interpreter.
 *
 * Pages are emitted as ASCII-hex `imagemask` rasters, the most portable
 * representation PostScript accepts:
 *
 *   /rstr W 8 div cvi string def
 *   W H true [W 0 0 -H 0 H] {currentfile rstr readhexstring pop} imagemask
 *   <hex…>
 *   showpage
 */
class PostScriptDriver : PrinterDriver {

    override val family: DriverFamily = DriverFamily.POSTSCRIPT

    override fun renderDpi(settings: PrintSettings): Int {
        val dpi = settings.quality.laserDpi().coerceAtMost(400)
        val maxPixels = 30_000_000L
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
            if (out.write(data) < 0) throw PrintTransferException("PostScript printer stopped accepting data")
        }

        val ptW = settings.paper.widthMm / 25.4f * 72f
        val ptH = settings.paper.heightMm / 25.4f * 72f

        write(Cmd.ascii("%!PS-Adobe-3.0\n"))
        write(Cmd.ascii("%%Creator: OpenPage (The QAWSE Institute)\n"))
        write(Cmd.ascii("%%Pages: $pageCount\n"))
        write(Cmd.ascii("%%LanguageLevel: 2\n"))
        write(Cmd.ascii("%%EndComments\n"))

        for (index in 0 until pageCount) {
            coroutineContext.ensureActive()
            onPageStarted(index, pageCount)
            val page = renderPage(index)

            val w = page.width
            val h = page.height
            val landscape = w > h
            val pw = if (landscape) ptH else ptW
            val ph = if (landscape) ptW else ptH
            val rowBytes = (w + 7) / 8

            write(Cmd.ascii("%%Page: ${index + 1} ${index + 1}\n"))
            write(Cmd.ascii("save\n"))
            write(Cmd.ascii("<< /PageSize [$pw $ph] /ImagingBBox null >> setpagedevice\n"))
            write(Cmd.ascii("/rstr $rowBytes string def\n"))
            write(Cmd.ascii("$w $h true [$w 0 0 -$h 0 $h] {currentfile rstr readhexstring pop} imagemask\n"))

            // hex payload, 72 bytes per line → tidy ASCII
            val hex = HexEncoder()
            Raster.streamMonoRows(page) { row, n ->
                coroutineContext.ensureActive()
                hex.add(row, n)
                while (hex.hasLine()) write(hex.takeLine())
            }
            while (hex.hasLine()) write(hex.takeLine())
            write(Cmd.ascii("\nrestore showpage\n"))
            page.recycle()
        }

        write(Cmd.ascii("%%EOF\n"))
    }
}

/** Incremental hex encoder with fixed-width ASCII lines. */
internal class HexEncoder {
    private val sb = StringBuilder(96)
    private val digits = "0123456789ABCDEF".toCharArray()

    fun add(bytes: ByteArray, n: Int) {
        for (i in 0 until n) {
            val v = bytes[i].toInt() and 0xFF
            sb.append(digits[v shr 4])
            sb.append(digits[v and 0xF])
        }
    }

    fun hasLine(): Boolean = sb.length >= 72

    fun takeLine(): ByteArray {
        val line = sb.substring(0, 72) + "\n"
        sb.delete(0, 72)
        return line.toByteArray(Charsets.US_ASCII)
    }
}
