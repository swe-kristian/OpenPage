package com.qawse.openpage.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Post-capture document processing: quad crop with perspective flattening,
 * rotation and the three enhancement looks (original / document / grayscale).
 */
object ScanProcessor {

    data class Corner(val x: Float, val y: Float)

    /** Four page corners in clockwise order starting at top-left. */
    data class Quad(
        val topLeft: Corner,
        val topRight: Corner,
        val bottomRight: Corner,
        val bottomLeft: Corner,
    )

    fun defaultQuad(w: Float, h: Float, inset: Float = 0.06f): Quad {
        val dx = w * inset
        val dy = h * inset
        return Quad(
            Corner(dx, dy),
            Corner(w - dx, dy),
            Corner(w - dx, h - dy),
            Corner(dx, h - dy),
        )
    }

    /** Flattens the quad marked on the photo into a rectangular page. */
    fun flatten(bitmap: Bitmap, quad: Quad, outWidth: Int): Bitmap {
        val topW = hypot(quad.topRight.x - quad.topLeft.x, quad.topRight.y - quad.topLeft.y)
        val botW = hypot(quad.bottomRight.x - quad.bottomLeft.x, quad.bottomRight.y - quad.bottomLeft.y)
        val leftH = hypot(quad.bottomLeft.x - quad.topLeft.x, quad.bottomLeft.y - quad.topLeft.y)
        val rightH = hypot(quad.bottomRight.x - quad.topRight.x, quad.bottomRight.y - quad.topRight.y)
        val avgW = max(1f, (topW + botW) / 2f)
        val avgH = max(1f, (leftH + rightH) / 2f)
        val aspect = avgW / avgH

        val outW = outWidth.coerceIn(256, 4000)
        val outH = (outW / aspect.coerceIn(0.2f, 5f)).toInt().coerceIn(256, 6000)

        // Matrix.setPolyToPoly with 4 points maps the quad to the rectangle.
        // It is affine rather than fully projective, which is visually
        // indistinguishable for the slight tilts of handheld page shots and
        // runs on every device with zero extra dependencies.
        val srcPts = floatArrayOf(
            quad.topLeft.x, quad.topLeft.y,
            quad.topRight.x, quad.topRight.y,
            quad.bottomRight.x, quad.bottomRight.y,
            quad.bottomLeft.x, quad.bottomLeft.y,
        )
        val dstPts = floatArrayOf(
            0f, 0f,
            outW.toFloat(), 0f,
            outW.toFloat(), outH.toFloat(),
            0f, outH.toFloat(),
        )
        val m = Matrix()
        if (!m.setPolyToPoly(srcPts, 0, dstPts, 0, 4)) {
            m.setScale(outW / bitmap.width.toFloat(), outH / bitmap.height.toFloat())
        }

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        out.eraseColor(android.graphics.Color.WHITE)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(bitmap, m, paint)
        return out
    }

    enum class Enhance(val label: String) {
        ORIGINAL("Original"),
        DOCUMENT("Document"),
        GRAYSCALE("Grayscale");
    }

    fun enhance(bitmap: Bitmap, mode: Enhance): Bitmap {
        if (mode == Enhance.ORIGINAL) return bitmap
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        when (mode) {
            Enhance.DOCUMENT -> {
                // Desaturate, then stretch contrast for crisp white paper
                val m = ColorMatrix().apply { setSaturation(0f) }
                val c = 1.45f
                val shift = -0.5f * 255f * (c - 1f) + 12f
                m.postConcat(ColorMatrix(floatArrayOf(
                    c, 0f, 0f, 0f, shift,
                    0f, c, 0f, 0f, shift,
                    0f, 0f, c, 0f, shift,
                    0f, 0f, 0f, 1f, 0f)))
                paint.colorFilter = ColorMatrixColorFilter(m)
            }
            Enhance.GRAYSCALE -> {
                paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            }
            Enhance.ORIGINAL -> {}
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return out
    }

    fun rotate90(bitmap: Bitmap): Bitmap {
        val m = Matrix().apply { postRotate(90f) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    /** Downscale for preview use without losing too much detail. */
    fun fit(bitmap: Bitmap, maxDim: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDim) return bitmap
        val s = maxDim.toFloat() / largest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * s).toInt(), (bitmap.height * s).toInt(), true)
    }

    /** Bounds helper used by the crop UI to validate drag results. */
    fun quadWithin(quad: Quad, w: Float, h: Float): Boolean {
        val pts = listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft)
        val minX = pts.minOf { it.x }; val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }; val maxY = pts.maxOf { it.y }
        return minX >= 0 && minY >= 0 && maxX <= w && maxY <= h &&
            abs(maxX - minX) > w * 0.1f && abs(maxY - minY) > h * 0.1f
    }
}
