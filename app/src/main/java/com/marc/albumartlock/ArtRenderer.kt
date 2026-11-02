package com.marc.albumartlock

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Turns a (usually square) album cover into a phone-sized lock screen image. */
object ArtRenderer {

    /** All sizes are fractions of the screen: coverTop of the height, coverSize and corner of the width. */
    data class Look(
        val layout: Int,
        val background: Int,
        val coverTop: Float,
        val coverSize: Float,
        val corner: Float,
        val title: String,
        val artist: String,
        val album: String,
        val textScale: Float,
        val dimPercent: Int,
        val textPosition: Int = Prefs.TEXT_BELOW,
        val textGap: Float = 0.03f,
        val textAlign: Int = Prefs.ALIGN_CENTER,
    )

    fun render(art: Bitmap, width: Int, height: Int, look: Look): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        if (look.layout == Prefs.LAYOUT_FILL) {
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            canvas.drawBitmap(art, centerCropSource(art, width, height), Rect(0, 0, width, height), paint)
            dim(canvas, look.dimPercent)
        } else {
            drawBackground(canvas, art, width, height, look.background)
            dim(canvas, look.dimPercent)
            drawCard(canvas, art, width, height, look)
        }
        return out
    }

    // Defaults were measured from a Galaxy S23 screenshot of Samsung's expanded lock screen player:
    // cover 89% of the width, top edge at 29% of the height, corner ~7% of the width, title
    // centred ~3% of the height below the cover and the artist ~3.6% below that.

    private fun drawCard(canvas: Canvas, art: Bitmap, w: Int, h: Int, look: Look) {
        val size = w * look.coverSize.coerceIn(0.3f, 1f)
        val left = (w - size) / 2f
        val top = h * look.coverTop.coerceIn(0f, 0.9f)
        val dst = RectF(left, top, left + size, top + size)
        drawRoundedArt(canvas, art, dst, w * look.corner.coerceIn(0f, 0.25f))

        val lines = listOf(
            look.title to Line(0.039f, true, 255),
            look.artist to Line(0.030f, false, 225),
            look.album to Line(0.027f, false, 170),
        ).filter { it.first.isNotBlank() }
        if (lines.isEmpty()) return

        // Positions below are the vertical centres of each text line.
        val step = h * 0.036f * look.textScale
        val gap = h * look.textGap.coerceIn(0f, 0.3f)
        val blockSpan = step * (lines.size - 1)
        val onCover = look.textPosition == Prefs.TEXT_ON_COVER
        val firstCentre = when (look.textPosition) {
            Prefs.TEXT_ABOVE -> dst.top - gap - blockSpan
            Prefs.TEXT_ON_COVER -> dst.bottom - max(gap, h * 0.025f) - blockSpan
            else -> dst.bottom + gap
        }

        if (onCover) {
            // Darken the bottom of the cover so white text stays readable on bright art.
            val shadeTop = firstCentre - h * 0.05f
            val save = canvas.save()
            val radius = w * look.corner.coerceIn(0f, 0.25f)
            canvas.clipPath(Path().apply { addRoundRect(dst, radius, radius, Path.Direction.CW) })
            canvas.drawRect(dst.left, shadeTop, dst.right, dst.bottom, Paint().apply {
                shader = LinearGradient(0f, shadeTop, 0f, dst.bottom,
                    Color.TRANSPARENT, Color.argb(170, 0, 0, 0), Shader.TileMode.CLAMP)
            })
            canvas.restoreToCount(save)
        }

        val leftAligned = look.textAlign == Prefs.ALIGN_LEFT
        val inset = if (onCover || leftAligned) w * 0.045f else 0f
        val x = if (leftAligned) dst.left + inset else w / 2f
        val maxWidth = size - 2 * inset
        for ((i, pair) in lines.withIndex()) {
            val (text, style) = pair
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(style.alpha, 255, 255, 255)
                textSize = w * style.size * look.textScale
                if (style.bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = if (leftAligned) Paint.Align.LEFT else Paint.Align.CENTER
                setShadowLayer(6f, 0f, 2f, Color.argb(90, 0, 0, 0))
            }
            val shown = TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()
            canvas.drawText(shown, x, firstCentre + i * step + paint.textSize * 0.35f, paint)
        }
    }

    private data class Line(val size: Float, val bold: Boolean, val alpha: Int)

    private fun drawBackground(canvas: Canvas, art: Bitmap, w: Int, h: Int, mode: Int) {
        when (mode) {
            Prefs.BG_BLACK -> canvas.drawColor(Color.BLACK)
            Prefs.BG_BLUR -> {
                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                val bg = blur(art)
                canvas.drawBitmap(bg, centerCropSource(bg, w, h), Rect(0, 0, w, h), paint)
                canvas.drawColor(Color.argb(90, 0, 0, 0))
            }
            else -> {
                // Samsung-style: dark grey at the top fading into a dark shade of the cover's colour.
                val tint = dominantColor(art)
                val top = Color.rgb(32, 32, 34)
                val bottom = mix(Color.rgb(18, 16, 16), tint, 0.38f)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
                    shader = LinearGradient(0f, h * 0.30f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
                })
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
                    shader = RadialGradient(w / 2f, h * 0.55f, w * 0.9f,
                        Color.argb(70, Color.red(tint), Color.green(tint), Color.blue(tint)),
                        Color.TRANSPARENT, Shader.TileMode.CLAMP)
                })
            }
        }
    }

    /** The most vivid of the cover's common colours, so a red cover gives a red-tinted background. */
    private fun dominantColor(art: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(art, 24, 24, true)
        val buckets = HashMap<Int, IntArray>() // key -> [count, r, g, b]
        val hsv = FloatArray(3)
        for (y in 0 until 24) for (x in 0 until 24) {
            val c = small.getPixel(x, y)
            Color.colorToHSV(c, hsv)
            if (hsv[2] < 0.12f) continue // ignore near-black
            val key = (hsv[0] / 20f).toInt() * 4 + (hsv[1] * 3.99f).toInt()
            val b = buckets.getOrPut(key) { IntArray(4) }
            b[0]++; b[1] += Color.red(c); b[2] += Color.green(c); b[3] += Color.blue(c)
        }
        var best = Color.rgb(90, 90, 96)
        var bestScore = -1f
        for (b in buckets.values) {
            val c = Color.rgb(b[1] / b[0], b[2] / b[0], b[3] / b[0])
            Color.colorToHSV(c, hsv)
            val score = b[0] * (0.25f + hsv[1])
            if (score > bestScore) { bestScore = score; best = c }
        }
        return best
    }

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).roundToInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).roundToInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).roundToInt(),
    )

    private fun drawRoundedArt(canvas: Canvas, art: Bitmap, dst: RectF, radius: Float) {
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 0, 0, 0)
            setShadowLayer(max(radius, 24f), 0f, max(radius, 24f) / 3f, Color.argb(130, 0, 0, 0))
        }
        canvas.drawRoundRect(dst, radius, radius, shadow)
        val save = canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(dst, radius, radius, Path.Direction.CW) })
        // Crop non-square covers (e.g. video thumbnails) to a square instead of stretching them.
        val side = min(art.width, art.height)
        val src = Rect((art.width - side) / 2, (art.height - side) / 2, (art.width + side) / 2, (art.height + side) / 2)
        canvas.drawBitmap(art, src, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        canvas.restoreToCount(save)
    }

    private fun dim(canvas: Canvas, dimPercent: Int) {
        val d = dimPercent.coerceIn(0, 80)
        if (d > 0) canvas.drawColor(Color.argb((d * 255 / 100f).roundToInt(), 0, 0, 0))
    }

    private fun centerCropSource(src: Bitmap, width: Int, height: Int): Rect {
        val targetRatio = width.toFloat() / height
        val srcRatio = src.width.toFloat() / src.height
        return if (srcRatio > targetRatio) {
            val cw = (src.height * targetRatio).roundToInt()
            val x = (src.width - cw) / 2
            Rect(x, 0, x + cw, src.height)
        } else {
            val ch = (src.width / targetRatio).roundToInt()
            val y = (src.height - ch) / 2
            Rect(0, y, src.width, y + ch)
        }
    }

    /** Cheap, strong blur: shrink a lot, box-blur the tiny image, and let scaling up smooth it. */
    private fun blur(src: Bitmap): Bitmap {
        val small = max(8, min(src.width, src.height) / 16)
        var bmp = Bitmap.createScaledBitmap(src, small, small, true)
        if (bmp.config != Bitmap.Config.ARGB_8888) bmp = bmp.copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(small * small)
        bmp.getPixels(px, 0, small, 0, 0, small, small)
        repeat(3) { boxBlur(px, small, small, 2) }
        val result = Bitmap.createBitmap(small, small, Bitmap.Config.ARGB_8888)
        result.setPixels(px, 0, small, 0, 0, small, small)
        return result
    }

    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        for (pass in 0..1) {
            val from = if (pass == 0) px else tmp
            val to = if (pass == 0) tmp else px
            for (y in 0 until h) for (x in 0 until w) {
                var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
                for (k in -r..r) {
                    val xx = if (pass == 0) (x + k).coerceIn(0, w - 1) else x
                    val yy = if (pass == 1) (y + k).coerceIn(0, h - 1) else y
                    val c = from[yy * w + xx]
                    a += c ushr 24; rr += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
                }
                to[y * w + x] = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
    }
}
