package com.qawse.openpage

import com.qawse.openpage.data.PageRange
import com.qawse.openpage.data.PageSelection
import com.qawse.openpage.data.RangeValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Page-range parsing: the syntax users actually type, including the
 * typographic dashes the hint itself displays.
 */
class PageRangeTest {

    @Test fun emptySelectsAllPages() {
        val sel = PageRange.parse("", 10)!!
        assertEquals((1..10).toList(), sel.pages)
    }

    @Test fun whitespaceOnlySelectsAllPages() {
        val sel = PageRange.parse("   ", 10)!!
        assertEquals(10, sel.pages.size)
    }

    @Test fun singlePages() {
        assertEquals(listOf(3), PageRange.parse("3", 10)!!.pages)
    }

    @Test fun commaSeparatedList() {
        assertEquals(listOf(1, 3, 5), PageRange.parse("1,3,5", 10)!!.pages)
    }

    @Test fun listWithSpaces() {
        assertEquals(listOf(1, 3, 5), PageRange.parse(" 1, 3 ,5 ", 10)!!.pages)
    }

    @Test fun closedRange() {
        assertEquals(listOf(2, 3, 4), PageRange.parse("2-4", 10)!!.pages)
    }

    @Test fun enDashRange_matchesTheHint() {
        // The UI hint shows “1–3”; the parser must honor it.
        assertEquals(listOf(1, 2, 3), PageRange.parse("1–3", 10)!!.pages)
    }

    @Test fun emDashRange() {
        assertEquals(listOf(1, 2, 3), PageRange.parse("1—3", 10)!!.pages)
    }

    @Test fun openEndedRange() {
        val sel = PageRange.parse("8-", 10)!!
        assertEquals(listOf(8, 9, 10), sel.pages)
        assertTrue(sel.isOpenEnded)
    }

    @Test fun mixedExpression() {
        assertEquals(listOf(1, 2, 3, 5, 8, 9, 10), PageRange.parse("1-3,5,8-", 10)!!.pages)
    }

    @Test fun duplicatesCollapse() {
        assertEquals(listOf(2), PageRange.parse("2,2,2", 10)!!.pages)
    }

    @Test fun overlappingRangesCollapse() {
        assertEquals(listOf(1, 2, 3, 4), PageRange.parse("1-3,2-4", 10)!!.pages)
    }

    @Test fun zeroIsRejected() {
        assertNull(PageRange.parse("0", 10))
    }

    @Test fun reversedRangeIsRejected() {
        assertNull(PageRange.parse("5-2", 10))
    }

    @Test fun garbageIsRejected() {
        assertNull(PageRange.parse("abc", 10))
        assertNull(PageRange.parse("1..3", 10))
        assertNull(PageRange.parse("1,,3", 10))
        assertNull(PageRange.parse("1-2-3", 10))
        assertNull(PageRange.parse("-", 10))
    }

    @Test fun beyondTotalIsDroppedNotFatal() {
        // Usable pages print; pages beyond the end are flagged, not fatal.
        val subset = PageRange.parse("9,15", 10)
        assertNotNull(subset)
        assertEquals(listOf(9), subset!!.pages)
        val mixed = PageRange.parse("2,15", 10)
        assertNotNull(mixed)
        assertEquals(listOf(2), mixed!!.pages)
        val v = PageRange.validate("2,15", 10)
        assertTrue(v is RangeValidation.OutOfRange)
        assertEquals(listOf(15), (v as RangeValidation.OutOfRange).ignored)
        // Entirely out of range: nothing printable.
        assertNull(PageRange.parse("11,15", 10))
    }

    @Test fun closedRangeClampsToTotal() {
        // Like every print dialog: 8-15 on a 10-page document = 8-10.
        assertEquals(listOf(8, 9, 10), PageRange.parse("8-15", 10)!!.pages)
    }

    // ------------------------------------------------------ live validation

    @Test fun validateEmptyIsAllPages() {
        assertTrue(PageRange.validate("", 5) is RangeValidation.AllPages)
    }

    @Test fun validateOkReportsCount() {
        val v = PageRange.validate("1-3", 5)
        assertTrue(v is RangeValidation.Ok)
        assertEquals(3, (v as RangeValidation.Ok).selection.pages.size)
    }

    @Test fun validateSyntaxError() {
        assertTrue(PageRange.validate("1..2", 5) is RangeValidation.SyntaxError)
        assertTrue(PageRange.validate("0", 5) is RangeValidation.SyntaxError)
        assertTrue(PageRange.validate("2-1", 5) is RangeValidation.SyntaxError)
    }

    @Test fun validateOutOfRangeFlagsIgnored() {
        val v = PageRange.validate("2,15", 5)
        assertTrue(v is RangeValidation.OutOfRange)
        assertEquals(listOf(15), (v as RangeValidation.OutOfRange).ignored)
    }

    @Test fun selectionObjectCarriesTotal() {
        val sel: PageSelection = PageRange.parse("1", 7)!!
        assertEquals(7, sel.total)
    }
}
