package com.qawse.openpage.print

import com.qawse.openpage.data.Margins
import com.qawse.openpage.data.PaperSize
import com.qawse.openpage.data.PrintSettings
import com.qawse.openpage.data.ScalingMode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure page geometry — no Android dependencies, fully unit-testable.
 *
 * Owns every calculation about paper size, margins, and content scaling so
 * the renderer stays a thin adapter and the math has tests.
 */
object PageGeometry {

    /** Simple float rectangle (avoids android.graphics for JVM tests). */
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    const val MM_PER_INCH = 25.4f

    fun paperWidthPx(paper: PaperSize, dpi: Int, landscape: Boolean): Int =
        (((if (landscape) paper.heightMm else paper.widthMm) / MM_PER_INCH * dpi).toInt()).coerceAtLeast(1)

    fun paperHeightPx(paper: PaperSize, dpi: Int, landscape: Boolean): Int =
        (((if (landscape) paper.widthMm else paper.heightMm) / MM_PER_INCH * dpi).toInt()).coerceAtLeast(1)

    /**
     * Printable content box for a paper bitmap of [wPx] × [hPx].
     * Thermal rolls print edge to edge with a 2 mm safety gutter.
     */
    fun contentBox(settings: PrintSettings, dpi: Int, wPx: Int, hPx: Int): Box {
        if (settings.paper.thermalRoll) {
            val g = 2f / MM_PER_INCH * dpi
            return Box(g, g, wPx - g, hPx - g)
        }
        val m: Margins = settings.margins
        val left = m.leftMm / MM_PER_INCH * dpi
        val right = m.rightMm / MM_PER_INCH * dpi
        val top = m.topMm / MM_PER_INCH * dpi
        val bottom = m.bottomMm / MM_PER_INCH * dpi
        return Box(left, top, wPx - right, hPx - bottom)
    }

    /** Scale factor applied to a [w] × [h] source inside [box] per [mode]. */
    fun fitScale(mode: ScalingMode, box: Box, w: Float, h: Float): Float = when (mode) {
        ScalingMode.FIT -> min(box.width / w, box.height / h)
        ScalingMode.FILL -> max(box.width / w, box.height / h)
        ScalingMode.ACTUAL -> 1f
    }

    /**
     * PDF zoom: PdfRenderer pages are 72 dpi points; the box is in device
     * pixels at [dpi]. Result multiplies point dimensions into pixels.
     */
    fun pdfZoom(mode: ScalingMode, box: Box, srcWpt: Float, srcHpt: Float, dpi: Int): Float =
        fitScale(mode, box, srcWpt * dpi / 72f, srcHpt * dpi / 72f) * 72f / dpi

    /**
     * Caps [dpi] so the paper bitmap stays within [maxPixels] — keeps the
     * heap flat on 600 dpi A3 jobs. Returns the effective dpi (>= [min]).
     */
    fun capDpi(paper: PaperSize, dpi: Int, maxPixels: Long, min: Int): Int {
        val w = (paper.widthMm / MM_PER_INCH * dpi).toLong()
        val h = (paper.heightMm / MM_PER_INCH * dpi).toLong()
        return if (w * h > maxPixels) {
            val scale = sqrt(maxPixels.toDouble() / (w * h))
            (dpi * scale).toInt().coerceAtLeast(min)
        } else dpi
    }
}
