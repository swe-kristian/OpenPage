package com.qawse.openpage

import com.qawse.openpage.data.Margins
import com.qawse.openpage.data.PaperSize
import com.qawse.openpage.data.PrintSettings
import com.qawse.openpage.data.ScalingMode
import com.qawse.openpage.print.PageGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Pure geometry: paper dimensions, margins, scaling, dpi capping. */
class PageGeometryTest {

    private val settings = PrintSettings() // A4, 12.7 mm margins, FIT

    @Test fun a4PortraitAt300dpi() {
        // floor(): never exceed the paper's physical extent
        assertEquals(2480, PageGeometry.paperWidthPx(PaperSize.A4, 300, false))
        assertEquals(3507, PageGeometry.paperHeightPx(PaperSize.A4, 300, false))
    }

    @Test fun a4LandscapeSwapsDimensions() {
        assertEquals(3507, PageGeometry.paperWidthPx(PaperSize.A4, 300, true))
        assertEquals(2480, PageGeometry.paperHeightPx(PaperSize.A4, 300, true))
    }

    @Test fun letterAt150dpi() {
        val w = PageGeometry.paperWidthPx(PaperSize.LETTER, 150, false)
        val h = PageGeometry.paperHeightPx(PaperSize.LETTER, 150, false)
        assertEquals(1275, w)
        assertEquals(1650, h)
    }

    @Test fun contentBoxAppliesUniformMargins() {
        val dpi = 300
        val w = PageGeometry.paperWidthPx(PaperSize.A4, dpi, false)
        val h = PageGeometry.paperHeightPx(PaperSize.A4, dpi, false)
        val box = PageGeometry.contentBox(settings, dpi, w, h)
        val expected = 12.7f / 25.4f * dpi // half inch
        assertTrue(abs(box.left - expected) < 1.5f)
        assertTrue(abs(box.top - expected) < 1.5f)
        assertTrue(abs(box.right - (w - expected)) < 1.5f)
        assertTrue(abs(box.bottom - (h - expected)) < 1.5f)
    }

    @Test fun contentBoxRespectsPerSideCustomMargins() {
        val s = settings.copy(margins = Margins(10f, 20f, 30f, 40f))
        val dpi = 100
        val w = PageGeometry.paperWidthPx(PaperSize.A4, dpi, false)
        val h = PageGeometry.paperHeightPx(PaperSize.A4, dpi, false)
        val box = PageGeometry.contentBox(s, dpi, w, h)
        val mmToPx = { mm: Float -> mm / 25.4f * dpi }
        assertEquals(mmToPx(10f), box.left, 0.01f)
        assertEquals(mmToPx(20f), w - box.right, 0.01f)
        assertEquals(mmToPx(30f), box.top, 0.01f)
        assertEquals(mmToPx(40f), h - box.bottom, 0.01f)
    }

    @Test fun thermalRollGetsSafetyGutterNotMargins() {
        val s = settings.copy(paper = PaperSize.ROLL_80)
        val dpi = 203
        val w = PageGeometry.paperWidthPx(PaperSize.ROLL_80, dpi, false)
        val h = PageGeometry.paperHeightPx(PaperSize.ROLL_80, dpi, false)
        val box = PageGeometry.contentBox(s, dpi, w, h)
        val gutter = 2f / 25.4f * dpi
        assertEquals(gutter, box.left, 0.01f)
        assertEquals(gutter, box.top, 0.01f)
        assertEquals(w - gutter, box.right, 0.01f)
    }

    @Test fun zeroMarginsGiveFullPage() {
        val s = settings.copy(margins = Margins(0f, 0f, 0f, 0f))
        val w = 1000
        val h = 1000
        val box = PageGeometry.contentBox(s, 300, w, h)
        assertEquals(0f, box.left, 0.001f)
        assertEquals(1000f, box.right, 0.001f)
    }

    @Test fun fitScalesToSmallerAxis() {
        val box = PageGeometry.Box(0f, 0f, 100f, 200f)
        // 50x100 content fits exactly
        assertEquals(2f, PageGeometry.fitScale(ScalingMode.FIT, box, 50f, 100f), 0.001f)
        // 100x100 content constrained by width
        assertEquals(1f, PageGeometry.fitScale(ScalingMode.FIT, box, 100f, 100f), 0.001f)
        // larger content shrinks
        assertEquals(0.5f, PageGeometry.fitScale(ScalingMode.FIT, box, 200f, 300f), 0.001f)
    }

    @Test fun fillScalesToLargerAxis() {
        val box = PageGeometry.Box(0f, 0f, 100f, 200f)
        assertEquals(2f, PageGeometry.fitScale(ScalingMode.FILL, box, 100f, 100f), 0.001f)
    }

    @Test fun actualSizeNeverScales() {
        val box = PageGeometry.Box(0f, 0f, 100f, 100f)
        assertEquals(1f, PageGeometry.fitScale(ScalingMode.ACTUAL, box, 1000f, 1000f), 0.001f)
    }

    @Test fun capDpiLimitsHugePaper() {
        // Letter at 600 dpi is ~2 MP — far under the 42 MP cap, unchanged
        assertEquals(600, PageGeometry.capDpi(PaperSize.LETTER, 600, 42_000_000L, 150))
        // A3 at 600 dpi is ~70 MP — over budget, must be reduced (466 dpi fits)
        val capped = PageGeometry.capDpi(PaperSize.A3, 600, 42_000_000L, 150)
        assertTrue("A3@600dpi should be capped", capped < 600)
        val w = (PaperSize.A3.widthMm / 25.4f * capped).toLong()
        val h = (PaperSize.A3.heightMm / 25.4f * capped).toLong()
        assertTrue(w * h <= 42_000_000L)
    }

    @Test fun capDpiReducesWhenOverBudget() {
        // A2-sized paper at 600 dpi would exceed the cap; dpi must drop.
        val bigPaper = PaperSize.A3
        val capped = PageGeometry.capDpi(bigPaper, 1200, 42_000_000L, 150)
        val w = (bigPaper.widthMm / 25.4f * capped).toLong()
        val h = (bigPaper.heightMm / 25.4f * capped).toLong()
        assertTrue("capped raster exceeds budget", w * h <= 42_000_000L)
        assertTrue(capped >= 150)
        assertTrue(capped < 1200)
    }

    @Test fun boxWidthHeightDerived() {
        val box = PageGeometry.Box(10f, 20f, 110f, 270f)
        assertEquals(100f, box.width, 0.001f)
        assertEquals(250f, box.height, 0.001f)
    }

    @Test fun receiptRollsAreFlaggedThermal() {
        assertTrue(PaperSize.ROLL_80.thermalRoll)
        assertTrue(PaperSize.ROLL_58.thermalRoll)
        assertTrue(!PaperSize.A4.thermalRoll)
    }

    @Test fun everyPaperHasPositiveDimensions() {
        PaperSize.entries.forEach { paper ->
            assertTrue("${paper.name} width", paper.widthMm > 0f)
            assertTrue("${paper.name} height", paper.heightMm > 0f)
        }
    }
}
