package com.qawse.openpage

import com.qawse.openpage.data.PrinterDatabase
import com.qawse.openpage.data.PrinterDatabase.DriverFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Brand database + driver routing — the app’s printer knowledge base. */
class PrinterDatabaseTest {

    @Test fun allPromisedBrandsArePresent() {
        val names = PrinterDatabase.brands.map { it.name }
        listOf(
            "Epson", "HP", "Canon", "Brother", "Samsung", "Xerox", "Dell",
            "Konica Minolta", "Kyocera", "Lexmark", "Ricoh", "Sharp",
            "Toshiba", "OKI",
        ).forEach { brand ->
            assertTrue("missing brand $brand", brand in names)
        }
    }

    @Test fun thermalBrandsSpeakEscPos() {
        listOf("Zebra", "DYMO", "Bixolon", "Star Micronics").forEach { name ->
            val brand = PrinterDatabase.brands.first { it.name == name }
            assertEquals("$name should route to ESC/POS", DriverFamily.ESCPOS, brand.preferredDriver)
        }
    }

    @Test fun officeLaserBrandsPreferPcl() {
        listOf("HP", "Brother", "Samsung", "Xerox", "Kyocera", "Lexmark", "Ricoh").forEach { name ->
            val brand = PrinterDatabase.brands.first { it.name == name }
            assertEquals("$name should route to PCL", DriverFamily.PCL, brand.preferredDriver)
        }
    }

    @Test fun epsonAndCanonPreferEscpr() {
        assertEquals(DriverFamily.ESCPR, PrinterDatabase.brandFor(0x04B8)?.preferredDriver)
        assertEquals(DriverFamily.ESCPR, PrinterDatabase.brandFor(0x04A9)?.preferredDriver)
    }

    @Test fun brandVidsAreUnique() {
        val vids = PrinterDatabase.brands.map { it.vid }
        // Citizen appears twice in v1 (same VID, different names) — dedup check
        assertEquals("duplicate VIDs across brands", vids.distinct().size, vids.size)
    }

    @Test fun knownVidRoutesToBrandPreferredDriver() {
        assertEquals(DriverFamily.PCL, PrinterDatabase.recommendedDriver(0x03F0, 7, 1, false))
        assertEquals(DriverFamily.ESCPR, PrinterDatabase.recommendedDriver(0x04B8, 7, 1, false))
    }

    @Test fun thermalLikeUnknownRoutesToEscPos() {
        assertEquals(DriverFamily.ESCPOS, PrinterDatabase.recommendedDriver(0xFFFF, 0xFF, 0, true))
    }

    @Test fun unknownPrinterClassRoutesToGeneric() {
        assertEquals(DriverFamily.GENERIC, PrinterDatabase.recommendedDriver(0xFFFF, 7, 1, false))
        assertEquals(DriverFamily.GENERIC, PrinterDatabase.recommendedDriver(0xABCD, 0, null, false))
    }

    @Test fun printerClassDetection() {
        assertTrue(PrinterDatabase.isPrinterClass(7, 1))
        assertTrue(PrinterDatabase.isPrinterClass(7, null))
        assertFalse(PrinterDatabase.isPrinterClass(0xFF, 1))
    }

    @Test fun displayNamePrefersProductString() {
        assertEquals("Epson ET-2850",
            PrinterDatabase.displayName(0x04B8, 0x1234, "Epson ET-2850", 7))
    }

    @Test fun displayNamePrefixesBrandWhenProductOmitsIt() {
        assertEquals("HP LaserJet",
            PrinterDatabase.displayName(0x03F0, 0x01, "LaserJet", 7))
    }

    @Test fun displayNameWithoutProductString() {
        val name = PrinterDatabase.displayName(0x04B8, 0x1234, null, 7)
        assertTrue(name.startsWith("Epson"))
    }

    @Test fun displayNameForUnknownDevice() {
        val name = PrinterDatabase.displayName(0x9999, 0x7777, null, 7)
        assertTrue(name.contains("USB"))
    }

    @Test fun brandLookupForUnknownVidIsNull() {
        assertNull(PrinterDatabase.brandFor(0x1234))
        assertNotNull(PrinterDatabase.brandFor(0x04B8))
    }

    @Test fun everyDriverFamilyHasALabel() {
        DriverFamily.entries.forEach { family ->
            assertTrue(PrinterDatabase.driverLabel(family).isNotBlank())
            assertTrue(PrinterDatabase.driverDescription(family).length > 20)
        }
    }
}
