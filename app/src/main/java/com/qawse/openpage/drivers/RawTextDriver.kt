package com.qawse.openpage.drivers

import android.graphics.Bitmap
import com.qawse.openpage.data.PrintSettings
import com.qawse.openpage.data.PrinterDatabase.DriverFamily
import com.qawse.openpage.usb.UsbPrinterConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Plain-text line-mode driver for impact printers and the most stubborn
 * legacy hardware: sends ASCII with carriage returns and a final form feed.
 */
class RawTextDriver : PrinterDriver {

    override val family: DriverFamily = DriverFamily.RAW

    override fun renderDpi(settings: PrintSettings): Int = 150

    override suspend fun print(
        connection: UsbPrinterConnection,
        pageCount: Int,
        settings: PrintSettings,
        renderPage: suspend (index: Int) -> Bitmap,
        onPageStarted: (index: Int, total: Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        // Raw text ignores the bitmap pipeline; the engine routes text jobs
        // straight to [sendText] instead of calling print().
        onPageStarted(0, pageCount.coerceAtLeast(1))
    }

    suspend fun sendText(connection: UsbPrinterConnection, text: String) {
        withContext(Dispatchers.IO) {
            val sb = StringBuilder(text.length + 16)
            sb.append(0x1B.toChar()).append('@')   // reset
            for (line in text.split('\n')) {
                sb.append(line.trimEnd('\r')).append("\r\n")
            }
            sb.append('\r')
            sb.append(0x0C.toChar())               // form feed
            val bytes = sb.toString().toByteArray(Charsets.US_ASCII)
            if (connection.write(bytes) < 0) throw PrintTransferException("Device rejected raw text")
        }
    }
}

/** The generic driver is a conservative PCL raster with safe settings. */
class GenericDriver : PclDriver() {
    override val family: DriverFamily = DriverFamily.GENERIC
    override val supportsColor: Boolean get() = false

    override fun renderDpi(settings: PrintSettings): Int =
        settings.quality.laserDpi().coerceAtMost(300)
}

object DriverFactory {
    fun create(family: DriverFamily): PrinterDriver = when (family) {
        DriverFamily.PCL -> PclDriver()
        DriverFamily.ESCPOS -> EscPosDriver()
        DriverFamily.ESCPR -> EscPRDriver()
        DriverFamily.POSTSCRIPT -> PostScriptDriver()
        DriverFamily.GENERIC -> GenericDriver()
        DriverFamily.RAW -> RawTextDriver()
    }
}
