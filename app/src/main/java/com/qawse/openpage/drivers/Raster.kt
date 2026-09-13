package com.qawse.openpage.drivers

import android.graphics.Bitmap
import kotlin.math.min

/**
 * Bitmap → printer-language raster conversion.
 *
 * All drivers receive fully rendered paper-sized bitmaps and use these
 * utilities to turn them into 1-bit rows, ink-density rows or KCMY planes.
 */
object Raster {

    /** Ordered 8×8 Bayer matrix scaled to 0..255. Deterministic, no error diffusion carry. */
    private val bayer = intArrayOf(
        0, 32, 8, 40, 2, 34, 10, 42,
        48, 16, 56, 24, 50, 18, 58, 26,
        12, 44, 4, 36, 14, 46, 6, 38,
        60, 28, 52, 20, 62, 30, 54, 22,
        3, 35, 11, 43, 1, 33, 9, 41,
        51, 19, 59, 27, 49, 17, 57, 25,
        15, 47, 7, 39, 13, 45, 5, 37,
        63, 31, 55, 23, 61, 29, 53, 21,
    ).map { it * 4 + 2 }.toIntArray() // 0..254

    private fun bayerAt(x: Int, y: Int): Int = bayer[(y and 7) * 8 + (x and 7)]

    /**
     * Streams 1-bits-per-pixel packed rows (MSB = leftmost dot, 1 = ink)
     * with ordered-dither halftoning.
     */
    fun streamMonoRows(bitmap: Bitmap, onRow: (ByteArray, Int) -> Unit) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w)
        val rowBytes = (w + 7) / 8
        val row = ByteArray(rowBytes)
        for (y in 0 until h) {
            bitmap.getPixels(pixels, 0, w, 0, y, w, 1)
            java.util.Arrays.fill(row, 0)
            for (x in 0 until w) {
                val p = pixels[x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val a = (p ushr 24)
                // composite over paper white
                val lum = if (a == 255) (r * 299 + g * 587 + b * 114) / 1000
                else if (a == 0) 255
                else {
                    val rr = (r * a + 255 * (255 - a)) / 255
                    val gg = (g * a + 255 * (255 - a)) / 255
                    val bb = (b * a + 255 * (255 - a)) / 255
                    (rr * 299 + gg * 587 + bb * 114) / 1000
                }
                if (lum < bayerAt(x, y)) row[x shr 3] =
                    (row[x shr 3].toInt() or (0x80 shr (x and 7))).toByte()
            }
            onRow(row, rowBytes)
        }
    }

    /**
     * Streams ink-density rows (0x00 = full ink, 0xFF = blank) letting the
     * printer's own halftoning engine do the beautiful work.
     */
    fun streamDensityRows(bitmap: Bitmap, onRow: (ByteArray) -> Unit) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w)
        val row = ByteArray(w)
        for (y in 0 until h) {
            bitmap.getPixels(pixels, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val p = pixels[x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val a = (p ushr 24)
                val lum = if (a == 255) (r * 299 + g * 587 + b * 114) / 1000
                else if (a == 0) 255
                else {
                    val rr = (r * a + 255 * (255 - a)) / 255
                    val gg = (g * a + 255 * (255 - a)) / 255
                    val bb = (b * a + 255 * (255 - a)) / 255
                    (rr * 299 + gg * 587 + bb * 114) / 1000
                }
                row[x] = (255 - lum).toByte()
            }
            onRow(row)
        }
    }

    /**
     * Streams KCMY 1-bit plane quartets per raster row (PCL planar color).
     * Black generation: full GCR — K takes the common minimum, subtracted
     * from the chroma planes; each plane dithered with a phase offset so the
     * screens interleave instead of stacking.
     */
    fun streamKcmyRows(
        bitmap: Bitmap,
        onRowPlanes: (k: ByteArray, c: ByteArray, m: ByteArray, y: ByteArray, rowBytes: Int) -> Unit,
    ) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w)
        val rowBytes = (w + 7) / 8
        val kRow = ByteArray(rowBytes)
        val cRow = ByteArray(rowBytes)
        val mRow = ByteArray(rowBytes)
        val yRow = ByteArray(rowBytes)

        for (y in 0 until h) {
            bitmap.getPixels(pixels, 0, w, 0, y, w, 1)
            java.util.Arrays.fill(kRow, 0)
            java.util.Arrays.fill(cRow, 0)
            java.util.Arrays.fill(mRow, 0)
            java.util.Arrays.fill(yRow, 0)
            for (x in 0 until w) {
                val p = pixels[x]
                val a = (p ushr 24)
                var r = (p shr 16) and 0xFF
                var g = (p shr 8) and 0xFF
                var b = p and 0xFF
                if (a != 255) {
                    r = (r * a + 255 * (255 - a)) / 255
                    g = (g * a + 255 * (255 - a)) / 255
                    b = (b * a + 255 * (255 - a)) / 255
                }
                var c = 255 - r
                var m = 255 - g
                var yy = 255 - b
                val k = min(c, min(m, yy))
                if (k > 0) { c -= k; m -= k; yy -= k }
                val kb = bayerAt(x, y)
                val cb = bayerAt(x + 21, y + 13)
                val mb = bayerAt(x + 43, y + 29)
                val yb = bayerAt(x + 57, y + 41)
                if (k > kb) kRow[x shr 3] = (kRow[x shr 3].toInt() or (0x80 shr (x and 7))).toByte()
                if (c > cb) cRow[x shr 3] = (cRow[x shr 3].toInt() or (0x80 shr (x and 7))).toByte()
                if (m > mb) mRow[x shr 3] = (mRow[x shr 3].toInt() or (0x80 shr (x and 7))).toByte()
                if (yy > yb) yRow[x shr 3] = (yRow[x shr 3].toInt() or (0x80 shr (x and 7))).toByte()
            }
            onRowPlanes(kRow, cRow, mRow, yRow, rowBytes)
        }
    }
}
