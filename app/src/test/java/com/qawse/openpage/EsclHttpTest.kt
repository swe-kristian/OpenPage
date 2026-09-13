package com.qawse.openpage

import com.qawse.openpage.scan.EsclScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * eSCL HTTP response framing — the completeness heuristic and chunked
 * transfer decoding, as used over the IPP-USB byte stream.
 */
class EsclHttpTest {

    private val scanner = EsclScanner(null)

    private fun response(headers: String, body: String): ByteArray {
        val head = "HTTP/1.1 200 OK\r\n$headers\r\n\r\n"
        return (head + body).toByteArray(Charsets.US_ASCII)
    }

    @Test fun incompleteHeaderIsNotComplete() {
        val buf = "HTTP/1.1 200 OK\r\nContent-Len".toByteArray(Charsets.US_ASCII)
        assertFalse(scanner.hasCompleteResponse(buf, buf.size))
    }

    @Test fun contentLengthSatisfied() {
        val buf = response("Content-Length: 4", "abcd")
        assertTrue(scanner.hasCompleteResponse(buf, buf.size))
    }

    @Test fun contentLengthShortIsIncomplete() {
        val buf = response("Content-Length: 10", "abcd")
        assertFalse(scanner.hasCompleteResponse(buf, buf.size))
    }

    @Test fun noLengthReadsUntilBodyArrives() {
        // Read-till-dry contract: with no framing headers, completeness
        // requires at least one body byte (or the idle timeout in the reader).
        val empty = response("Connection: keep-alive", "")
        assertFalse(scanner.hasCompleteResponse(empty, empty.size))
        val some = response("Connection: keep-alive", "x")
        assertTrue(scanner.hasCompleteResponse(some, some.size))
    }

    @Test fun interim100ContinueIsNotComplete() {
        val buf = "HTTP/1.1 100 Continue\r\n\r\n".toByteArray(Charsets.US_ASCII)
        assertFalse(scanner.hasCompleteResponse(buf, buf.size))
    }

    @Test fun chunkedNeedsTerminalChunk() {
        val body = "4\r\nWiki\r\n5\r\npedia\r\n0\r\n\r\n"
        val buf = response("Transfer-Encoding: chunked", body)
        assertTrue(scanner.hasCompleteResponse(buf, buf.size))
        val truncated = response("Transfer-Encoding: chunked", "4\r\nWiki\r\n5\r\nped")
        assertFalse(scanner.hasCompleteResponse(truncated, truncated.size))
    }

    @Test fun headerCaseInsensitivity() {
        val buf = response("CONTENT-LENGTH: 2", "ab")
        assertTrue(scanner.hasCompleteResponse(buf, buf.size))
    }

    @Test fun bufferLongerThanLenUsesPrefix() {
        // Grow-buffer contract: bytes beyond len are garbage, must be ignored.
        val buf = response("Content-Length: 4", "abcd")
        val padded = buf + ByteArray(4096) { 0x7F }
        assertTrue(scanner.hasCompleteResponse(padded, buf.size))
        assertFalse(scanner.hasCompleteResponse(padded, buf.size - 1))
    }

    // ---------------------------------------------------------- chunked body

    @Test fun decodeSimpleChunks() {
        val raw = "IGNORED-HEADER\r\n\r\n4\r\nWiki\r\n5\r\npedia\r\n0\r\n\r\n"
            .toByteArray(Charsets.US_ASCII)
        // header end is after "IGNORED-HEADER\r\n\r\n" = 17 bytes
        val out = scanner.decodeChunked(raw, 17)
        assertEquals("Wikipedia", String(out, Charsets.US_ASCII))
    }

    @Test fun decodeSingleChunk() {
        val raw = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n0\r\n\r\n"
            .toByteArray(Charsets.US_ASCII)
        val headerEnd = String(raw, Charsets.US_ASCII).indexOf("\r\n\r\n") + 4
        val out = scanner.decodeChunked(raw, headerEnd)
        assertEquals("hello", String(out, Charsets.US_ASCII))
    }

    @Test fun decodeChunkWithExtension() {
        val raw = "H\r\n\r\n3;name=x\r\nabc\r\n0\r\n\r\n".toByteArray(Charsets.US_ASCII)
        val out = scanner.decodeChunked(raw, 5)
        assertEquals("abc", String(out, Charsets.US_ASCII))
    }

    @Test fun decodeTruncatedInputStopsCleanly() {
        val raw = "H\r\n\r\n3\r\nab".toByteArray(Charsets.US_ASCII)
        val out = scanner.decodeChunked(raw, 5)
        assertEquals(0, out.size)
    }
}
