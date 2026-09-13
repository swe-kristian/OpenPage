package com.qawse.openpage

import com.qawse.openpage.scan.ScanProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Scan-side pure math: default quad, bounds validation, aspect behavior. */
class ScanMathTest {

    @Test fun defaultQuadInsetsSixPercent() {
        val quad = ScanProcessor.defaultQuad(1000f, 2000f)
        assertEquals(60f, quad.topLeft.x, 0.01f)    // 6% of width
        assertEquals(120f, quad.topLeft.y, 0.01f)   // 6% of height
        assertEquals(940f, quad.topRight.x, 0.01f)
        assertEquals(1880f, quad.bottomLeft.y, 0.01f)
        assertEquals(1880f, quad.bottomRight.y, 0.01f)
    }

    @Test fun quadWithinAcceptsDefault() {
        val quad = ScanProcessor.defaultQuad(1000f, 2000f)
        assertTrue(ScanProcessor.quadWithin(quad, 1000f, 2000f))
    }

    @Test fun quadWithinRejectsDegenerate() {
        // All corners collapsed to the same point — not a page.
        val c = ScanProcessor.Corner(100f, 100f)
        val quad = ScanProcessor.Quad(c, c, c, c)
        assertFalse(ScanProcessor.quadWithin(quad, 1000f, 2000f))
    }

    @Test fun quadWithinRejectsOutsideBounds() {
        val quad = ScanProcessor.defaultQuad(1000f, 2000f)
        val shifted = quad.copy(
            topLeft = quad.topLeft.copy(x = -50f),
            bottomLeft = quad.bottomLeft.copy(x = -50f),
        )
        assertFalse(ScanProcessor.quadWithin(shifted, 1000f, 2000f))
    }

    @Test fun quadWithinRejectsTooSmall() {
        // A tiny 30x40 selection inside a 1000x2000 frame.
        val quad = ScanProcessor.Quad(
            ScanProcessor.Corner(100f, 100f),
            ScanProcessor.Corner(130f, 100f),
            ScanProcessor.Corner(130f, 140f),
            ScanProcessor.Corner(100f, 140f),
        )
        assertFalse(ScanProcessor.quadWithin(quad, 1000f, 2000f))
    }

    @Test fun cornerOrderingIsClockwiseFromTopLeft() {
        val quad = ScanProcessor.defaultQuad(100f, 100f)
        // Sanity of the contract used by flatten: TL, TR, BR, BL.
        assertTrue(quad.topLeft.x < quad.topRight.x)
        assertTrue(quad.topLeft.y < quad.bottomLeft.y)
        assertTrue(quad.bottomRight.y > quad.topRight.y)
    }

    @Test fun paperLikeAspectIsPreservedByFlattenMath() {
        // The aspect computation inside flatten: avg width / avg height.
        val quad = ScanProcessor.defaultQuad(1000f, 1414f) // A4-ish
        val topW = kotlin.math.hypot(quad.topRight.x - quad.topLeft.x, quad.topRight.y - quad.topLeft.y)
        val leftH = kotlin.math.hypot(quad.bottomLeft.x - quad.topLeft.x, quad.bottomLeft.y - quad.topLeft.y)
        val aspect = topW / leftH
        assertTrue(abs(aspect - 1000f / 1414f) < 0.01f)
    }
}
