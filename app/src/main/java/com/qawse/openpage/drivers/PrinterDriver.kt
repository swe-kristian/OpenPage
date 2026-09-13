package com.qawse.openpage.drivers

import android.graphics.Bitmap
import com.qawse.openpage.data.PrintQuality
import com.qawse.openpage.data.PrintSettings
import com.qawse.openpage.data.PrinterDatabase.DriverFamily
import com.qawse.openpage.usb.UsbPrinterConnection

/**
 * A printer-language driver. Implementations pull paper-sized rendered
 * bitmaps lazily from [renderPage] — one page lives in memory at a time —
 * and push the converted byte stream over a bulk USB endpoint.
 */
interface PrinterDriver {
    val family: DriverFamily

    /** True when the language can express halftoned color output. */
    val supportsColor: Boolean get() = false

    /** Effective raster dpi the renderer should target for this driver. */
    fun renderDpi(settings: PrintSettings): Int

    /**
     * Sends [pageCount] pages to the printer. The driver receives each page
     * via [renderPage] and MUST recycle the bitmap when done with it.
     * Throws [PrintTransferException] when the device stops accepting data.
     */
    suspend fun print(
        connection: UsbPrinterConnection,
        pageCount: Int,
        settings: PrintSettings,
        renderPage: suspend (index: Int) -> Bitmap,
        onPageStarted: (index: Int, total: Int) -> Unit,
    )
}

/** Thrown when the device stops accepting bulk transfers. */
class PrintTransferException(message: String) : Exception(message)

/** Resolution mapping per language family (heads and engines differ). */
internal fun PrintQuality.laserDpi(): Int = when (this) {
    PrintQuality.DRAFT -> 150
    PrintQuality.NORMAL -> 300
    PrintQuality.HIGH -> 600
}

internal fun PrintQuality.escposDpi(): Int = when (this) {
    PrintQuality.DRAFT -> 150
    PrintQuality.NORMAL -> 200
    PrintQuality.HIGH -> 203 // thermal heads are fixed ~203 dpi
}

internal fun PrintQuality.escprDpi(): Int = when (this) {
    PrintQuality.DRAFT -> 180
    PrintQuality.NORMAL -> 360
    PrintQuality.HIGH -> 720
}

/**
 * Command helpers shared by drivers. EVERY PCL command here carries the
 * ESC (0x1B) prefix — bare "&l1S" would be printed as literal text.
 * (Regression-tested in PclCommandTest.)
 */
internal object Cmd {
    val ESC = 0x1B.toByte()

    // ---- "&" parameter group: ESC & {a} {payload} {b}
    fun pclPageSize(n: Int) = escParam('l', 'A', n.toString())
    fun pclOrientation(landscape: Boolean) = escParam('l', 'O', if (landscape) "1" else "0")
    fun pclDuplex(mode: Int) = escParam('l', 'S', mode.toString())        // 0/1/2
    fun pclMediaPlain() = escParam('l', 'M', "0")                          // plain paper
    fun pclJobCopies(n: Int) = escParam('l', 'X', n.toString())
    fun pclXRes(dpi: Int) = escParam('u', 'D', dpi.toString())
    fun pclEject() = escParam('l', 'H', "0")

    // ---- "*" raster group: ESC * {a} {payload} {b}  (no ampersand!)
    fun pclRasterRes(dpi: Int) = escStar('t', 'R', dpi.toString())
    fun pclColorMode(n: Int) = escStar('r', 'U', n.toString())
    fun pclRowHeader(n: Int) = escStar('b', 'W', n.toString())
    fun pclRasterStart() = escStar('r', 'A', "1")
    fun pclRasterEnd() = byteArrayOf(ESC, '*'.code.toByte(), 'r'.code.toByte(), 'B'.code.toByte())
    fun pclRasterPresentationPortrait() = escStar('r', 'F', "0")

    fun pclReset() = byteArrayOf(ESC, 'E'.code.toByte())

    /** ESC & {a} {payload} {b} */
    private fun escParam(a: Char, b: Char, payload: String): ByteArray =
        byteArrayOf(ESC, '&'.code.toByte(), a.code.toByte()) +
            payload.toByteArray(Charsets.US_ASCII) +
            byteArrayOf(b.code.toByte())

    /** ESC * {a} {payload} {b} */
    private fun escStar(a: Char, b: Char, payload: String): ByteArray =
        byteArrayOf(ESC, '*'.code.toByte(), a.code.toByte()) +
            payload.toByteArray(Charsets.US_ASCII) +
            byteArrayOf(b.code.toByte())

    fun ascii(s: String) = s.toByteArray(Charsets.US_ASCII)

    fun u16le(n: Int) = byteArrayOf((n and 0xFF).toByte(), ((n shr 8) and 0xFF).toByte())
    fun u16be(n: Int) = byteArrayOf(((n shr 8) and 0xFF).toByte(), (n and 0xFF).toByte())
}
