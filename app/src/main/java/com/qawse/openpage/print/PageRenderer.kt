package com.qawse.openpage.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.exifinterface.media.ExifInterface
import com.qawse.openpage.R
import com.qawse.openpage.core.AppLog
import com.qawse.openpage.core.Tags
import com.qawse.openpage.data.ColorMode
import com.qawse.openpage.data.OrientationMode
import com.qawse.openpage.data.PrintSettings
import com.qawse.openpage.data.ScalingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileDescriptor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Renders a document into paper-sized bitmaps with the full page setup
 * applied: paper size, orientation, margins, scaling, color mode, mirroring.
 *
 * Pages are produced one at a time and recycled by the driver, keeping the
 * memory footprint flat even at 600 dpi. All geometry math lives in
 * [PageGeometry] and is unit-tested.
 */
class PageRenderer(private val context: Context) {

    sealed class Source {
        data class Pdf(val fd: FileDescriptor) : Source()
        data class Image(val uri: Uri) : Source()
        data class Images(val uris: List<Uri>) : Source()
        data class Text(val text: String) : Source()
        data object TestPage : Source()
    }

    companion object {
        fun open(context: Context, uri: Uri): Source? {
            val name = (uri.lastPathSegment ?: "").lowercase()
            val type = context.contentResolver.getType(uri)?.lowercase() ?: ""
            return when {
                type.contains("pdf") || name.endsWith(".pdf") -> openPdf(context, uri)
                type.startsWith("text/") || name.endsWith(".txt") || name.endsWith(".md") ||
                    name.endsWith(".log") || name.endsWith(".csv") -> openText(context, uri)
                else -> Source.Image(uri)
            }
        }

        private fun openPdf(context: Context, uri: Uri): Source? = try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            Source.Pdf(pfd.fileDescriptor)
        } catch (e: Exception) {
            AppLog.w(Tags.RENDER, "PDF open failed", e)
            null
        }

        private fun openText(context: Context, uri: Uri): Source? = try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                ?.let { Source.Text(it) } ?: null
        } catch (e: Exception) {
            AppLog.w(Tags.RENDER, "Text open failed", e)
            null
        }
    }

    // ------------------------------------------------------------ page count

    suspend fun pageCount(source: Source): Int = withContext(Dispatchers.IO) {
        when (source) {
            is Source.Pdf -> try {
                val pfd = ParcelFileDescriptor.dup(source.fd)
                try {
                    PdfRenderer(pfd).use { it.pageCount }
                } catch (e: Exception) {
                    runCatching { pfd.close() }
                    throw e
                }
            } catch (e: Exception) {
                AppLog.w(Tags.RENDER, "PDF page count failed", e)
                0
            }
            is Source.Image -> 1
            is Source.Images -> source.uris.size
            is Source.Text -> 1 // computed lazily during render; UI shows estimate
            Source.TestPage -> 1
        }
    }

    /** Text pagination depends on the settings; compute the real count. */
    suspend fun textPageCount(text: String, settings: PrintSettings, dpi: Int): Int =
        withContext(Dispatchers.IO) {
            val layout = buildTextLayout(text, settings, dpi)
            val lineH = layout.getLineBottom(0).coerceAtLeast(1)
            val box = contentBox(settings, dpi, wPx = paperW(settings, dpi), hPx = paperH(settings, dpi))
            max(1, (layout.height + lineH - 1) / max(1, box.height().toInt()))
        }

    // -------------------------------------------------------------- rendering

    /**
     * Renders page [pageIndex] (0-based) onto a paper-sized bitmap at [dpi].
     */
    suspend fun render(
        source: Source,
        pageIndex: Int,
        settings: PrintSettings,
        dpi: Int,
        driverLabel: String = "",
    ): Bitmap = withContext(Dispatchers.IO) {
        when (source) {
            is Source.Pdf -> renderPdf(source, pageIndex, settings, dpi)
            is Source.Image -> renderImage(source.uri, settings, dpi)
            is Source.Images -> renderImage(
                source.uris[pageIndex.coerceIn(0, source.uris.lastIndex)], settings, dpi)
            is Source.Text -> renderTextPage(source.text, pageIndex, settings, dpi)
            Source.TestPage -> renderTestPage(settings, dpi, driverLabel)
        }
    }

    // -------------------------------------------------------------------- PDF

    private fun renderPdf(source: Source.Pdf, pageIndex: Int, settings: PrintSettings, dpi: Int): Bitmap {
        val pfd = ParcelFileDescriptor.dup(source.fd)
        val renderer = try {
            PdfRenderer(pfd)
        } catch (e: Exception) {
            runCatching { pfd.close() }
            throw e
        }
        try {
            val page = renderer.openPage(pageIndex.coerceIn(0, renderer.pageCount - 1))
            try {
                val landscape = when (settings.orientation) {
                    OrientationMode.PORTRAIT -> false
                    OrientationMode.LANDSCAPE -> true
                    OrientationMode.AUTO -> page.width > page.height
                }
                val paper = createPaperBitmap(settings, dpi, landscape)
                val box = contentBox(settings, dpi, paper.width, paper.height)
                val canvas = Canvas(paper)
                canvas.drawColor(android.graphics.Color.WHITE)
                applyMirror(canvas, paper, settings)

                val srcW = page.width.toFloat()
                val srcH = page.height.toFloat()
                val zoom = PageGeometry.pdfZoom(settings.scaling, box.toGeo(), srcW, srcH, dpi)
                val bmpW = max(1, (srcW * dpi / 72f * zoom).toInt())
                val bmpH = max(1, (srcH * dpi / 72f * zoom).toInt())

                val content = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                content.eraseColor(android.graphics.Color.WHITE)
                page.render(content, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                val paint = contentPaint(settings.colorMode)
                val fitScale = PageGeometry.fitScale(
                    settings.scaling, box.toGeo(), content.width.toFloat(), content.height.toFloat())
                val dw = content.width * fitScale
                val dh = content.height * fitScale
                val dx = box.left + (box.width() - dw) / 2f
                val dy = box.top + (box.height() - dh) / 2f
                canvas.save()
                canvas.clipRect(box)
                canvas.translate(dx, dy)
                canvas.scale(fitScale, fitScale)
                canvas.drawBitmap(content, 0f, 0f, paint)
                canvas.restore()
                content.recycle()
                return paper
            } finally {
                page.close()
            }
        } finally {
            renderer.close()
        }
    }

    // ------------------------------------------------------------------ image

    private fun renderImage(uri: Uri, settings: PrintSettings, dpi: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val exifRotation = readExifRotation(uri)
        val swapped = exifRotation == 90 || exifRotation == 270

        val naturalW = (if (swapped) bounds.outHeight else bounds.outWidth).toFloat()
        val naturalH = (if (swapped) bounds.outWidth else bounds.outHeight).toFloat()
        val landscape = when (settings.orientation) {
            OrientationMode.PORTRAIT -> false
            OrientationMode.LANDSCAPE -> true
            OrientationMode.AUTO -> naturalW > naturalH
        }

        val paper = createPaperBitmap(settings, dpi, landscape)
        val box = contentBox(settings, dpi, paper.width, paper.height)
        val canvas = Canvas(paper)
        canvas.drawColor(android.graphics.Color.WHITE)
        applyMirror(canvas, paper, settings)

        // Decode at about the needed pixel budget
        val needed = max(box.width(), box.height()).toInt().coerceAtLeast(256)
        val sample = max(1, min(naturalW, naturalH).toInt() / needed)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val content = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: run {
            paper.eraseColor(android.graphics.Color.WHITE)
            return paper
        }

        val upright = rotateByExif(content, exifRotation)

        val paint = contentPaint(settings.colorMode)
        val fitScale = PageGeometry.fitScale(
            settings.scaling, box.toGeo(), upright.width.toFloat(), upright.height.toFloat())
        val dw = upright.width * fitScale
        val dh = upright.height * fitScale
        val dx = box.left + (box.width() - dw) / 2f
        val dy = box.top + (box.height() - dh) / 2f
        canvas.save()
        canvas.clipRect(box)
        canvas.translate(dx, dy)
        canvas.scale(fitScale, fitScale)
        canvas.drawBitmap(upright, 0f, 0f, paint)
        canvas.restore()

        if (upright !== content) content.recycle()
        upright.recycle()
        return paper
    }

    private fun readExifRotation(uri: Uri): Int = try {
        context.contentResolver.openInputStream(uri)?.use { ins ->
            when (ExifInterface(ins).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (e: Exception) { 0 }

    private fun rotateByExif(bmp: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bmp
        val m = Matrix()
        m.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    // ------------------------------------------------------------------- text

    private fun paperW(settings: PrintSettings, dpi: Int): Int =
        PageGeometry.paperWidthPx(settings.paper, dpi, landscape = false)

    private fun paperH(settings: PrintSettings, dpi: Int): Int =
        PageGeometry.paperHeightPx(settings.paper, dpi, landscape = false)

    private fun buildTextLayout(text: String, settings: PrintSettings, dpi: Int): StaticLayout {
        val box = contentBox(settings, dpi, paperW(settings, dpi), paperH(settings, dpi))
        val textSize = 10f / 72f * dpi   // 10 pt body
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            this.textSize = textSize
            typeface = ResourcesCompat.getFont(context, R.font.inter_regular) ?: Typeface.MONOSPACE
        }
        val body = text.replace("\r\n", "\n").replace('\r', '\n').ifBlank { " " }
        return StaticLayout.Builder.obtain(body, 0, body.length, tp, box.width().toInt().coerceAtLeast(8))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(textSize * 0.35f, 1f)
            .build()
    }

    private fun renderTextPage(text: String, pageIndex: Int, settings: PrintSettings, dpi: Int): Bitmap {
        val paper = createPaperBitmap(settings, dpi, landscape = false)
        val box = contentBox(settings, dpi, paper.width, paper.height)
        val canvas = Canvas(paper)
        canvas.drawColor(android.graphics.Color.WHITE)

        val layout = buildTextLayout(text, settings, dpi)
        val lineH = (layout.getLineBottom(0).coerceAtLeast(1))
        val linesPerPage = max(1, box.height().toInt() / lineH)
        val first = pageIndex * linesPerPage

        canvas.save()
        canvas.clipRect(box)
        canvas.translate(box.left, box.top - layout.getLineTop(first).toFloat())
        layout.draw(canvas)
        canvas.restore()
        return paper
    }

    // -------------------------------------------------------------- test page

    /**
     * Deterministic printer test page. Identifies OpenPage, the paper size,
     * resolution, color mode and the selected driver — and states plainly
     * that it is a test output, not a compatibility certificate.
     */
    fun renderTestPage(settings: PrintSettings, dpi: Int, driverLabel: String): Bitmap {
        val paper = createPaperBitmap(settings, dpi, landscape = false)
        val canvas = Canvas(paper)
        canvas.drawColor(android.graphics.Color.WHITE)
        val box = contentBox(settings, dpi, paper.width, paper.height)

        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 22f / 72f * dpi
            typeface = ResourcesCompat.getFont(context, R.font.inter_semibold) ?: Typeface.DEFAULT_BOLD
        }
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 10.5f / 72f * dpi
            typeface = ResourcesCompat.getFont(context, R.font.inter_regular) ?: Typeface.DEFAULT
        }

        val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        canvas.drawText("OpenPage test page", box.left, box.top + title.textSize, title)
        canvas.drawText("The QAWSE Institute · Developed by Kristian Espedido", box.left, box.top + title.textSize * 2.1f, body)
        canvas.drawText("${settings.paper.label} · $dpi dpi · ${date}", box.left, box.top + title.textSize * 3.2f, body)
        canvas.drawText("Driver: $driverLabel · ${if (settings.colorMode == ColorMode.COLOR) "Color" else "Monochrome"}", box.left, box.top + title.textSize * 4.2f, body)

        // Ruler + halftone ramp to check geometry and dithering
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = dpi / 96f }
        var y = box.top + title.textSize * 6.4f
        for (i in 0..100 step 5) {
            val x = box.left + box.width() * i / 100f
            val len = if (i % 10 == 0) dpi * 0.25f else dpi * 0.13f
            p.color = android.graphics.Color.BLACK
            canvas.drawLine(x, y, x, y + len, p)
        }
        y += dpi * 0.5f
        val sw = box.width() / 8f
        for (g in 0 until 8) {
            p.color = android.graphics.Color.rgb(g * 32, g * 32, g * 32)
            canvas.drawRect(RectF(box.left + g * sw, y, box.left + (g + 1) * sw, y + dpi * 0.45f), p)
        }
        y += dpi * 0.75f
        // Parallel hairlines at increasing dot spacing
        for (k in 0 until 3) {
            val gap = when (k) { 0 -> 1; 1 -> 2; else -> 4 }
            p.strokeWidth = 1f
            for (l in 0 until 24) {
                val x = box.left + l * gap * dpi / 150f
                canvas.drawLine(x, y, x, y + dpi * 0.3f, p)
            }
            y += dpi * 0.45f
        }
        return paper
    }

    // --------------------------------------------------------------- helpers

    private fun RectF.toGeo() = PageGeometry.Box(left, top, right, bottom)

    private fun createPaperBitmap(settings: PrintSettings, dpi: Int, landscape: Boolean): Bitmap {
        val w = PageGeometry.paperWidthPx(settings.paper, dpi, landscape)
        val h = PageGeometry.paperHeightPx(settings.paper, dpi, landscape)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    }

    private fun contentBox(settings: PrintSettings, dpi: Int, wPx: Int, hPx: Int): RectF {
        val b = PageGeometry.contentBox(settings, dpi, wPx, hPx)
        return RectF(b.left, b.top, b.right, b.bottom)
    }

    private fun contentPaint(mode: ColorMode): Paint {
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        if (mode != ColorMode.COLOR) {
            val m = ColorMatrix().apply { setSaturation(0f) }
            if (mode == ColorMode.MONO) {
                // Slight contrast lift so thresholded text stays razor sharp
                val c = 1.15f
                val shift = (-0.5f * 255f * (c - 1f))
                val scale = ColorMatrix(floatArrayOf(
                    c, 0f, 0f, 0f, shift,
                    0f, c, 0f, 0f, shift,
                    0f, 0f, c, 0f, shift,
                    0f, 0f, 0f, 1f, 0f))
                m.postConcat(scale)
            }
            paint.colorFilter = ColorMatrixColorFilter(m)
        }
        return paint
    }

    private fun applyMirror(canvas: Canvas, paper: Bitmap, settings: PrintSettings) {
        if (settings.mirror) {
            canvas.scale(-1f, 1f, paper.width / 2f, paper.height / 2f)
        }
    }
}
