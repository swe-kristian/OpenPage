package com.qawse.openpage

import com.qawse.openpage.drivers.Cmd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PCL command framing. v1.0.0 emitted "&l1S", "*r1A" etc. WITHOUT the ESC
 * prefix — the printer printed them as literal text and duplex/raster
 * begin/end/page-eject silently failed. These tests pin the regression.
 */
class PclCommandTest {

    private val ESC = 0x1B

    private fun ByteArray.text() = String(this, Charsets.US_ASCII)

    @Test fun everyPclCommandStartsWithEsc() {
        val commands = listOf(
            Cmd.pclPageSize(26), Cmd.pclOrientation(true), Cmd.pclOrientation(false),
            Cmd.pclDuplex(1), Cmd.pclMediaPlain(), Cmd.pclJobCopies(1),
            Cmd.pclXRes(300), Cmd.pclRasterRes(600), Cmd.pclColorMode(4),
            Cmd.pclRasterStart(), Cmd.pclRasterEnd(), Cmd.pclEject(), Cmd.pclReset(),
            Cmd.pclRasterPresentationPortrait(), Cmd.pclRowHeader(90),
        )
        commands.forEach { cmd ->
            assertEquals("missing ESC prefix in ${cmd.text()}", ESC.toByte(), cmd[0])
        }
    }

    @Test fun resetIsExactlyEscE() {
        assertEquals(2, Cmd.pclReset().size)
        assertEquals(ESC.toByte(), Cmd.pclReset()[0])
        assertEquals('E'.code.toByte(), Cmd.pclReset()[1])
    }

    @Test fun duplexLongEdgeEncoding() {
        assertEquals("\u001B&l1S", Cmd.pclDuplex(1).text())
        assertEquals("\u001B&l2S", Cmd.pclDuplex(2).text())
        assertEquals("\u001B&l0S", Cmd.pclDuplex(0).text())
    }

    @Test fun pageSizeA4Encoding() {
        assertEquals("\u001B&l26A", Cmd.pclPageSize(26).text())
    }

    @Test fun rasterStartAndEnd() {
        assertEquals("\u001B*r1A", Cmd.pclRasterStart().text())
        assertEquals("\u001B*rB", Cmd.pclRasterEnd().text())
    }

    @Test fun pageEject() {
        assertEquals("\u001B&l0H", Cmd.pclEject().text())
    }

    @Test fun rowHeaderCarriesByteCount() {
        assertEquals("\u001B*b90W", Cmd.pclRowHeader(90).text())
    }

    @Test fun resolutionCommands() {
        assertEquals("\u001B&u300D", Cmd.pclXRes(300).text())
        assertEquals("\u001B*t600R", Cmd.pclRasterRes(600).text())
    }

    @Test fun colorModeKcmy() {
        assertEquals("\u001B*r4U", Cmd.pclColorMode(4).text())
    }

    @Test fun u16Helpers() {
        assertTrue(java.util.Arrays.equals(byteArrayOf(0x00, 0x01), Cmd.u16le(256)))
        assertTrue(java.util.Arrays.equals(byteArrayOf(0x01, 0x00), Cmd.u16be(256)))
        assertTrue(java.util.Arrays.equals(byteArrayOf(0x38, 0x02), Cmd.u16le(568)))
    }
}
