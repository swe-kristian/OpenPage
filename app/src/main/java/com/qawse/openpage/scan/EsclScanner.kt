package com.qawse.openpage.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.qawse.openpage.core.AppLog
import com.qawse.openpage.core.ScanProblem
import com.qawse.openpage.core.Tags
import com.qawse.openpage.usb.UsbPrinterConnection
import com.qawse.openpage.usb.UsbPrinterInfo
import com.qawse.openpage.usb.UsbPrinterManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * eSCL (AirPrint-style) scanning over the printer's IPP-USB bulk stream.
 *
 * eSCL is plain HTTP/1.1 spoken over the USB byte stream, so a compact
 * client is enough:
 *
 *   GET  /eSCL/ScannerCapabilities            → is a scanner there?
 *   POST /eSCL/ScanJobs      + job XML        → 201, Location header
 *   GET  {Location}/NextDocument              → image bytes (JPEG/PDF)
 *   DELETE {Location}                          → tidy up
 */
class EsclScanner(internal val usbManager: UsbPrinterManager?) {

    data class ScanOptions(
        val dpi: Int = 300,
        val color: Boolean = true,
        val adf: Boolean = false,
    )

    /** Non-content scan diagnostics — dimensions, sizes, timing. */
    data class ScanStats(
        val widthPx: Int,
        val heightPx: Int,
        val bytes: Int,
        val elapsedMs: Long,
        val dpi: Int,
        val colorMode: String,
    )

    sealed class ScanResult {
        data class Success(val bitmap: Bitmap, val stats: ScanStats) : ScanResult()
        data class Error(val problem: ScanProblem) : ScanResult()
    }

    fun isSupported(printer: UsbPrinterInfo): Boolean = printer.ippInterfaceIndex != null

    suspend fun scan(printer: UsbPrinterInfo, options: ScanOptions): ScanResult =
        withContext(Dispatchers.IO) {
            val started = android.os.SystemClock.elapsedRealtime()
            val conn = usbManager?.openIppConnection(printer)
                ?: return@withContext ScanResult.Error(ScanProblem.NoScanner)
            conn.use { c ->
                try {
                    // Probe
                    val caps = http(c, "GET", "/eSCL/ScannerCapabilities", null, null)
                        ?: return@withContext ScanResult.Error(
                            ScanProblem.Transport("capabilities request timed out"))
                    if (!caps.status.startsWith("2")) {
                        AppLog.d(Tags.SCAN, "caps status ${caps.status}")
                        return@withContext ScanResult.Error(ScanProblem.NoScanner)
                    }

                    // Create the job
                    val body = scanJobXml(options)
                    val created = http(c, "POST", "/eSCL/ScanJobs", body, "application/xml")
                        ?: return@withContext ScanResult.Error(
                            ScanProblem.Transport("job creation timed out"))
                    if (!created.status.startsWith("2")) {
                        return@withContext ScanResult.Error(
                            ScanProblem.Unsupported("scanner refused job: ${created.status}"))
                    }
                    val location = created.headers["location"]
                        ?: return@withContext ScanResult.Error(
                            ScanProblem.InvalidResponse("no job location header"))

                    // Pull the page
                    val image = http(c, "GET", "$location/NextDocument", null, null)
                        ?: return@withContext ScanResult.Error(
                            ScanProblem.Transport("document fetch timed out"))
                    if (!image.status.startsWith("2") || image.body.isEmpty()) {
                        runCatching { http(c, "DELETE", location, null, null) }
                        return@withContext ScanResult.Error(
                            ScanProblem.InvalidResponse("empty document, status ${image.status}"))
                    }

                    runCatching { http(c, "DELETE", location, null, null) }

                    val bmp = BitmapFactory.decodeByteArray(image.body, 0, image.body.size)
                        ?: return@withContext ScanResult.Error(ScanProblem.UndecodableImage)
                    val stats = ScanStats(
                        widthPx = bmp.width,
                        heightPx = bmp.height,
                        bytes = image.body.size,
                        elapsedMs = android.os.SystemClock.elapsedRealtime() - started,
                        dpi = options.dpi,
                        colorMode = if (options.color) "RGB8" else "Grayscale8",
                    )
                    AppLog.d(Tags.SCAN, "scan ok: ${stats.widthPx}x${stats.heightPx} in ${stats.elapsedMs} ms")
                    ScanResult.Success(bmp, stats)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    ScanResult.Error(ScanProblem.Cancelled)
                } catch (e: Exception) {
                    AppLog.w(Tags.SCAN, "scan failed", e)
                    ScanResult.Error(ScanProblem.Transport(e.message ?: "unexpected scanner error"))
                }
            }
        }

    // ------------------------------------------------------------ HTTP layer

    internal class HttpResponse(
        val status: String,
        val headers: Map<String, String>,
        val body: ByteArray,
    )

    private fun http(
        conn: UsbPrinterConnection,
        method: String,
        path: String,
        body: ByteArray?,
        contentType: String?,
    ): HttpResponse? {
        val req = StringBuilder("$method $path HTTP/1.1\r\n")
        req.append("Host: localhost\r\n")
        if (contentType != null) req.append("Content-Type: $contentType\r\n")
        if (body != null) req.append("Content-Length: ${body.size}\r\n")
        req.append("Connection: keep-alive\r\n")
        req.append("User-Agent: OpenPage/2.0 (The QAWSE Institute)\r\n\r\n")

        val head = req.toString().toByteArray(Charsets.US_ASCII)
        if (conn.write(head) < 0) return null
        if (body != null && conn.write(body) < 0) return null

        // Read the full response: status line + headers + body.
        val raw = readUntil(conn, done = { buf, len -> hasCompleteResponse(buf, len) }) ?: return null
        val text = raw.toString(Charsets.US_ASCII)
        val headerEnd = text.indexOf("\r\n\r\n")
        if (headerEnd < 0) return null
        val headerBlock = text.substring(0, headerEnd)
        val lines = headerBlock.split("\r\n")
        val status = lines.firstOrNull()?.substringAfter(" ", "")?.trim() ?: return null
        val headers = mutableMapOf<String, String>()
        for (i in 1 until lines.size) {
            val idx = lines[i].indexOf(':')
            if (idx > 0) headers[lines[i].substring(0, idx).trim().lowercase()] =
                lines[i].substring(idx + 1).trim()
        }
        val bodyStart = headerEnd + 4
        val transferEncoding = headers["transfer-encoding"]?.lowercase().orEmpty()
        val contentLength = headers["content-length"]?.toIntOrNull()
        val responseBody: ByteArray = when {
            transferEncoding.contains("chunked") -> decodeChunked(raw, bodyStart)
            contentLength != null -> raw.copyOfRange(
                bodyStart, minOf(raw.size, bodyStart + contentLength))
            else -> raw.copyOfRange(bodyStart, raw.size)
        }
        return HttpResponse(status, headers, responseBody)
    }

    /**
     * Heuristic completeness check used while accumulating the response.
     * Pure byte-level scan over [buf] .. [len] — no intermediate String
     * allocation. Unit-tested in EsclHttpTest.
     */
    internal fun hasCompleteResponse(buf: ByteArray, len: Int): Boolean {
        if (len < 16) return false
        val headerEnd = findHeaderEnd(buf, len) ?: return false
        // Status line
        val statusEnd = indexOfByte(buf, 0, headerEnd, '\n'.code) ?: return false
        val statusLine = ascii(buf, 0, statusEnd - 1) // strip trailing '\r'
        if (statusLine.contains(" 100 ")) return false // interim response
        // Headers
        var chunked = false
        var contentLength: Int? = null
        var lineStart = statusEnd + 1
        while (lineStart < headerEnd) {
            val nl = indexOfByte(buf, lineStart, headerEnd, '\n'.code)
            // Content ends before the CR (when a LF was found) or at the
            // final CRLFCRLF start (fallback) — never includes terminators.
            val contentEnd = if (nl != null) nl - 1 else headerEnd
            val colon = indexOfByte(buf, lineStart, contentEnd, ':'.code)
            if (colon != null && colon > lineStart) {
                val name = asciiLower(buf, lineStart, colon).trim()
                val value = ascii(buf, colon + 1, contentEnd).trim()
                if (name == "transfer-encoding" && value.lowercase().contains("chunked")) chunked = true
                if (name == "content-length") contentLength = value.toIntOrNull()
            }
            lineStart = (nl ?: headerEnd) + 1
        }
        return if (chunked) {
            // terminal zero-size chunk ("0\r\n\r\n" = 5 bytes)
            len >= headerEnd + 4 + 5 &&
                regionEquals(buf, len - 5, len, "0\r\n\r\n")
        } else {
            val expected = contentLength ?: return len > headerEnd + 4 // read till dry
            len >= headerEnd + 4 + expected
        }
    }

    private fun findHeaderEnd(buf: ByteArray, len: Int): Int? {
        for (i in 0..len - 4) {
            if (buf[i] == '\r'.code.toByte() && buf[i + 1] == '\n'.code.toByte() &&
                buf[i + 2] == '\r'.code.toByte() && buf[i + 3] == '\n'.code.toByte()) return i
        }
        return null
    }

    private fun indexOfByte(buf: ByteArray, from: Int, until: Int, b: Int): Int? {
        for (i in from until until) if (buf[i].toInt() and 0xFF == b) return i
        return null
    }

    private fun regionEquals(buf: ByteArray, from: Int, until: Int, s: String): Boolean {
        if (until - from != s.length) return false
        for (i in s.indices) if (buf[from + i].toInt() and 0xFF != s[i].code) return false
        return true
    }

    private fun ascii(buf: ByteArray, from: Int, until: Int): String {
        val sb = StringBuilder(maxOf(0, until - from))
        for (i in from until until) sb.append((buf[i].toInt() and 0xFF).toChar())
        return sb.toString()
    }

    private fun asciiLower(buf: ByteArray, from: Int, until: Int): String {
        val sb = StringBuilder(maxOf(0, until - from))
        for (i in from until until) {
            val c = (buf[i].toInt() and 0xFF).toChar()
            sb.append(if (c in 'A'..'Z') c.lowercaseChar() else c)
        }
        return sb.toString()
    }

    internal fun decodeChunked(raw: ByteArray, start: Int): ByteArray {
        val out = ByteArrayOutputStream()
        var i = start
        while (i + 2 <= raw.size) {
            val lineEnd = indexOf(raw, i, "\r\n".toByteArray()) ?: break
            val sizeStr = ascii(raw, i, lineEnd).trim()
            val size = sizeStr.substringBefore(';').toIntOrNull(16) ?: break
            if (size == 0) break
            val chunkStart = lineEnd + 2
            if (chunkStart + size > raw.size) break
            out.write(raw, chunkStart, size)
            i = chunkStart + size + 2
        }
        return out.toByteArray()
    }

    private fun indexOf(hay: ByteArray, from: Int, needle: ByteArray): Int? {
        outer@ for (i in from until hay.size - needle.size + 1) {
            for (j in needle.indices) {
                if (hay[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return null
    }

    /**
     * Accumulates bytes until [done] is satisfied or the stream runs dry.
     * Uses a single growable buffer with amortized doubling — no per-poll
     * whole-buffer copies (the v1 implementation was accidentally O(n²)).
     */
    private fun readUntil(
        conn: UsbPrinterConnection,
        done: (ByteArray, Int) -> Boolean,
        maxBytes: Int = 24 * 1024 * 1024,
    ): ByteArray? {
        var buf = ByteArray(64 * 1024)
        var len = 0
        var idle = 0
        while (len < maxBytes) {
            val chunk = conn.readBytes(64 * 1024, timeoutMs = 3000)
            if (chunk == null) {
                idle++
                // eSCL devices pause between pushes; keep polling while the
                // response is incomplete, stop after ~2 minutes of silence.
                if (done(buf, len)) break
                if (idle > 40) break
                continue
            }
            idle = 0
            if (len + chunk.size > buf.size) {
                var cap = buf.size
                while (cap < len + chunk.size) cap *= 2
                buf = buf.copyOf(cap)
            }
            System.arraycopy(chunk, 0, buf, len, chunk.size)
            len += chunk.size
            if (done(buf, len)) break
        }
        return if (len == 0) null else buf.copyOf(len)
    }

    // -------------------------------------------------------------- job XML

    private fun scanJobXml(o: ScanOptions): ByteArray {
        // Fixed A4-equivalent scan region at requested dpi — the crop UI
        // trims to the page afterwards.
        val w = (210f / 25.4f * o.dpi).toInt()
        val h = (297f / 25.4f * o.dpi).toInt()
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<scan:ScanJob xmlns:scan="http://schemas.hp.com/imaging/escl/2011/05/03">
 <scan:PurgeImages>true</scan:PurgeImages>
 <scan:InputSource>${if (o.adf) "Feeder" else "Platen"}</scan:InputSource>
 <scan:DocumentFormat>JPEG</scan:DocumentFormat>
 <scan:XResolution>${o.dpi}</scan:XResolution>
 <scan:YResolution>${o.dpi}</scan:YResolution>
 <scan:ColorMode>${if (o.color) "RGB8" else "Grayscale8"}</scan:ColorMode>
 <scan:ImageWidth>$w</scan:ImageWidth>
 <scan:ImageHeight>$h</scan:ImageHeight>
 <scan:ScanRegion>
  <scan:Height>$h</scan:Height>
  <scan:Width>$w</scan:Width>
  <scan:XOffset>0</scan:XOffset>
  <scan:YOffset>0</scan:YOffset>
 </scan:ScanRegion>
 <scan:ContentType>Photo</scan:ContentType>
</scan:ScanJob>"""
        return xml.toByteArray(Charsets.UTF_8)
    }
}
