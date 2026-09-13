package com.qawse.openpage.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Canonical paper catalogue with metric dimensions and the PCL / ESC-P
 * selector codes used by the driver engine.
 */
enum class PaperSize(
    val label: String,
    val widthMm: Float,
    val heightMm: Float,
    val pclCode: Int,      // ESC&l{code}A
    val thermalRoll: Boolean = false,
) {
    A4("A4 · 210 × 297 mm", 210f, 297f, 26),
    LETTER("Letter · 8.5 × 11 in", 215.9f, 279.4f, 2),
    LEGAL("Legal · 8.5 × 14 in", 215.9f, 355.6f, 3),
    A3("A3 · 297 × 420 mm", 297f, 420f, 27),
    A5("A5 · 148 × 210 mm", 148f, 210f, 25),
    A6("A6 · 105 × 148 mm", 105f, 148f, 24),
    B4("B4 · 250 × 353 mm", 250f, 353f, 28),
    B5("B5 · 176 × 250 mm", 176f, 250f, 46),
    EXECUTIVE("Executive · 7.25 × 10.5 in", 184.2f, 266.7f, 1),
    STATEMENT("Statement · 5.5 × 8.5 in", 139.7f, 215.9f, 34),
    TABLOID("Tabloid · 11 × 17 in", 279.4f, 431.8f, 6),
    FOLIO("Folio · 8.5 × 13 in", 215.9f, 330.2f, 22),
    OFICIO("Oficio · 8.5 × 13.4 in", 215.9f, 340.4f, 22),
    PHOTO_4X6("Photo · 4 × 6 in", 101.6f, 152.4f, 71),
    PHOTO_5X7("Photo · 5 × 7 in", 127f, 177.8f, 72),
    PHOTO_L("Photo L · 3.5 × 5 in", 88.9f, 127f, 74),
    CARD_2X3("Card · 2 × 3.5 in", 50.8f, 88.9f, 78),
    ROLL_80("Roll 80 mm · receipt", 80f, 297f, 0, true),
    ROLL_58("Roll 58 mm · receipt", 58f, 210f, 0, true);
}

enum class OrientationMode { AUTO, PORTRAIT, LANDSCAPE }
enum class ColorMode { COLOR, GRAYSCALE, MONO }
enum class PrintQuality(val dpi: Int) { DRAFT(150), NORMAL(300), HIGH(600) }
enum class ScalingMode { FIT, FILL, ACTUAL }
enum class DuplexMode { OFF, LONG, SHORT }

enum class MarginPreset(val mm: Float) {
    NONE(0f), NARROW(6.35f), NORMAL(12.7f), WIDE(25.4f), CUSTOM(0f);
}

@Parcelize
data class Margins(
    val leftMm: Float = 12.7f,
    val rightMm: Float = 12.7f,
    val topMm: Float = 12.7f,
    val bottomMm: Float = 12.7f,
) : Parcelable

/**
 * Full page-setup of a print job. Everything the renderer and drivers need.
 * Labels live in string resources; the UI maps enum -> resource id.
 */
@Parcelize
data class PrintSettings(
    val paper: PaperSize = PaperSize.A4,
    val pageRange: String = "",            // "" = all pages; else "1-3,5,8-"
    val copies: Int = 1,
    val orientation: OrientationMode = OrientationMode.AUTO,
    val colorMode: ColorMode = ColorMode.GRAYSCALE,
    val quality: PrintQuality = PrintQuality.NORMAL,
    val margins: Margins = Margins(),
    val marginPreset: MarginPreset = MarginPreset.NORMAL,
    val scaling: ScalingMode = ScalingMode.FIT,
    val duplex: DuplexMode = DuplexMode.OFF,
    val collate: Boolean = true,
    val reverseOrder: Boolean = false,
    val mirror: Boolean = false,
) : Parcelable

/** Parsed, validated page selection. Pages are 1-based, ascending. */
data class PageSelection(val pages: List<Int>, val total: Int, val isOpenEnded: Boolean = false)

/**
 * Live validation verdict for the page-range field — drives inline feedback
 * while the user types, instead of a late generic error at print time.
 */
sealed interface RangeValidation {
    /** Empty input: all pages will print. */
    data object AllPages : RangeValidation

    /** Valid expression. */
    data class Ok(val selection: PageSelection) : RangeValidation

    /** Syntax problem (empty chunk, bad number, reversed range…). */
    data object SyntaxError : RangeValidation

    /** Valid syntax but some pages exceed the document; [ignored] dropped,
     *  [selection] carries the usable remainder. */
    data class OutOfRange(val selection: PageSelection, val ignored: List<Int>) : RangeValidation
}

object PageRange {
    /**
     * Parses expressions like "1-3, 5, 8-" against a document page count.
     * Accepts ASCII hyphen, en dash and em dash interchangeably — the hint
     * shows typographic dashes, so the parser must honor what it shows.
     * Empty input selects every page. Closed ranges clamp to the document;
     * single pages beyond the end are dropped (see [validate]).
     */
    fun parse(expr: String, total: Int): PageSelection? = when (val v = validate(expr, total)) {
        is RangeValidation.Ok -> v.selection
        is RangeValidation.AllPages -> PageSelection((1..total).toList(), total)
        is RangeValidation.OutOfRange -> v.selection.takeIf { it.pages.isNotEmpty() }
        RangeValidation.SyntaxError -> null
    }

    fun validate(expr: String, total: Int): RangeValidation {
        val trimmed = expr.trim()
            .replace('–', '-')   // en dash
            .replace('—', '-')   // em dash
        if (trimmed.isEmpty()) return RangeValidation.AllPages

        val out = sortedSetOf<Int>()
        val ignored = mutableListOf<Int>()
        var openEnded = false

        for (chunk in trimmed.split(',')) {
            val c = chunk.trim()
            if (c.isEmpty()) return RangeValidation.SyntaxError
            if (c.contains('-')) {
                val parts = c.split('-')
                if (parts.size != 2) return RangeValidation.SyntaxError
                val start = parts[0].trim().toIntOrNull() ?: return RangeValidation.SyntaxError
                val endRaw = parts[1].trim()
                val end = if (endRaw.isEmpty()) { openEnded = true; total }
                else endRaw.toIntOrNull() ?: return RangeValidation.SyntaxError
                if (start < 1 || end < start) return RangeValidation.SyntaxError
                // Closed ranges clamp to the document, like every print dialog.
                for (p in start..end.coerceAtMost(total)) out.add(p)
                if (start > total) for (p in start..end) ignored.add(p)
            } else {
                val n = c.toIntOrNull() ?: return RangeValidation.SyntaxError
                if (n < 1) return RangeValidation.SyntaxError
                if (n <= total) out.add(n) else ignored.add(n)
            }
        }
        if (out.isEmpty()) {
            return if (ignored.isEmpty()) RangeValidation.SyntaxError
            else RangeValidation.OutOfRange(PageSelection(emptyList(), total), ignored.distinct())
        }
        // Something printable remains: return the selection and flag drops.
        val sel = PageSelection(out.toList(), total, openEnded)
        return if (ignored.isEmpty()) RangeValidation.Ok(sel)
        else RangeValidation.OutOfRange(sel, ignored.distinct())
    }
}

/** A completed print job entry for the Recent list. */
data class PrintJobRecord(
    val fileName: String,
    val printerName: String,
    val pages: Int,
    val copies: Int,
    val timestamp: Long,
    val ok: Boolean,
    /** Machine-readable problem name for failed jobs (for recovery copy). */
    val problem: String? = null,
    /** Content URI of the source document, when available, for "Print again". */
    val sourceUri: String? = null,
)
