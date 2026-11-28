package eu.kanade.tachiyomi.ui.reader.translate

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.max
import kotlin.math.min

data class RenderStyle(
    val fontScale: Float = 1f,
    val textColor: TextColorMode = TextColorMode.AUTO,
)

/**
 * Draws translated text over the original text regions of a page.
 * Draws in place when [render] receives a mutable ARGB_8888 bitmap (it never touches the original file).
 */
object PageRenderer {

    /** Part of the cache key, bump when the output of [render] changes. */
    const val VERSION = 1

    private const val PADDING_PX = 4
    private const val MIN_TEXT_PX = 9f

    fun render(source: Bitmap, blocks: List<TranslatedBlock>, style: RenderStyle): Bitmap {
        val out = if (source.isMutable && source.config == Bitmap.Config.ARGB_8888) {
            source
        } else {
            source.copy(Bitmap.Config.ARGB_8888, true)
                ?: throw PageTranslationException("Not enough memory to draw the translated page")
        }
        val canvas = Canvas(out)
        // First erase every region, then write: neighbouring regions must not erase freshly drawn text.
        val prepared = blocks.map { block ->
            val area = expand(block.box, out.width, out.height)
            Prepared(block, area, sampleBackground(out, area))
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        for (p in prepared) {
            fill.color = p.background
            val radius = min(p.area.width(), p.area.height()) * 0.12f
            canvas.drawRoundRect(RectF(p.area), radius, radius, fill)
        }
        for (p in prepared) {
            if (p.block.translated.isBlank()) continue
            drawFitted(canvas, p.block.translated, p.area, p.background, style)
        }
        return out
    }

    private class Prepared(val block: TranslatedBlock, val area: Rect, val background: Int)

    private fun expand(box: Rect, width: Int, height: Int): Rect = Rect(
        max(0, box.left - PADDING_PX),
        max(0, box.top - PADDING_PX),
        min(width, box.right + PADDING_PX),
        min(height, box.bottom + PADDING_PX),
    )

    /** Median colour of a thin ring just outside [area]: bubble interior is usually flat white or tinted. */
    internal fun sampleBackground(bitmap: Bitmap, area: Rect): Int {
        val reds = ArrayList<Int>()
        val greens = ArrayList<Int>()
        val blues = ArrayList<Int>()
        val step = max(1, max(area.width(), area.height()) / 60)
        fun take(x: Int, y: Int) {
            if (x < 0 || y < 0 || x >= bitmap.width || y >= bitmap.height) return
            val c = bitmap.getPixel(x, y)
            reds.add(Color.red(c))
            greens.add(Color.green(c))
            blues.add(Color.blue(c))
        }
        val ring = 3
        var x = area.left
        while (x < area.right) {
            take(x, area.top - ring)
            take(x, area.bottom + ring - 1)
            x += step
        }
        var y = area.top
        while (y < area.bottom) {
            take(area.left - ring, y)
            take(area.right + ring - 1, y)
            y += step
        }
        if (reds.isEmpty()) return Color.WHITE
        reds.sort()
        greens.sort()
        blues.sort()
        val m = reds.size / 2
        return Color.rgb(reds[m], greens[m], blues[m])
    }

    private fun luminance(color: Int): Double =
        0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)

    internal fun pickTextColor(background: Int, mode: TextColorMode): Int = when (mode) {
        TextColorMode.BLACK -> Color.BLACK
        TextColorMode.WHITE -> Color.WHITE
        TextColorMode.AUTO -> if (luminance(background) > 140) Color.BLACK else Color.WHITE
    }

    private fun drawFitted(canvas: Canvas, text: String, area: Rect, background: Int, style: RenderStyle) {
        val inner = Rect(area).apply { inset(PADDING_PX / 2, PADDING_PX / 2) }
        val width = max(1, inner.width())
        val height = max(1, inner.height())
        val color = pickTextColor(background, style.textColor)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            this.color = color
        }
        val size = fitTextSize(text, width, height, paint) * style.fontScale.coerceIn(0.5f, 1.5f)
        paint.textSize = max(size, MIN_TEXT_PX)
        val layout = buildLayout(text, paint, width)
        val top = inner.top + max(0, (height - layout.height) / 2)
        canvas.save()
        canvas.clipRect(area)
        canvas.translate(inner.left.toFloat(), top.toFloat())
        // Thin outline in the opposite colour keeps the text readable when the fill is imperfect.
        val outline = Paint(paint).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = max(1f, paint.textSize / 9f)
            this.color = if (color == Color.BLACK) Color.WHITE else Color.BLACK
        }
        val strokePaint = TextPaint(outline)
        buildLayout(text, strokePaint, width).draw(canvas)
        buildLayout(text, paint, width).draw(canvas)
        canvas.restore()
    }

    private fun buildLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(0f, 0.95f)
            .build()

    /** Largest text size (px) for which [text] wrapped to [width] fits inside [height]. */
    internal fun fitTextSize(text: String, width: Int, height: Int, template: TextPaint): Float {
        var low = MIN_TEXT_PX
        var high = max(MIN_TEXT_PX, min(height.toFloat(), width.toFloat()))
        val paint = TextPaint(template)
        repeat(12) {
            val mid = (low + high) / 2f
            paint.textSize = mid
            val layout = buildLayout(text, paint, width)
            if (layout.height <= height) low = mid else high = mid
        }
        return low
    }
}
