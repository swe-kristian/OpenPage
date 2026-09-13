package com.qawse.openpage

import com.qawse.openpage.data.PrintSettings
import com.qawse.openpage.print.buildPageOrder
import org.junit.Assert.assertEquals
import org.junit.Test

/** Copy/collation/reverse ordering — what the printer actually receives. */
class PageOrderTest {

    private val pages = listOf(0, 1, 2) // 0-based page indices

    @Test fun singleCopyStraightOrder() {
        val order = buildPageOrder(pages, PrintSettings(copies = 1))
        assertEquals(listOf(0 to 1, 1 to 1, 2 to 1), order)
    }

    @Test fun collatedCopies() {
        val order = buildPageOrder(pages, PrintSettings(copies = 2, collate = true))
        assertEquals(
            listOf(0 to 1, 1 to 1, 2 to 1, 0 to 2, 1 to 2, 2 to 2),
            order,
        )
    }

    @Test fun uncollatedCopies() {
        val order = buildPageOrder(pages, PrintSettings(copies = 2, collate = false))
        assertEquals(
            listOf(0 to 1, 0 to 2, 1 to 1, 1 to 2, 2 to 1, 2 to 2),
            order,
        )
    }

    @Test fun reverseOrderFlipsEverything() {
        val order = buildPageOrder(pages, PrintSettings(copies = 2, collate = true, reverseOrder = true))
        assertEquals(
            listOf(2 to 2, 1 to 2, 0 to 2, 2 to 1, 1 to 1, 0 to 1),
            order,
        )
    }

    @Test fun singlePageManyCopies() {
        val order = buildPageOrder(listOf(0), PrintSettings(copies = 3, collate = true))
        assertEquals(listOf(0 to 1, 0 to 2, 0 to 3), order)
    }

    @Test fun emptySelectionIsEmpty() {
        assertEquals(emptyList<Pair<Int, Int>>(), buildPageOrder(emptyList(), PrintSettings()))
    }
}
