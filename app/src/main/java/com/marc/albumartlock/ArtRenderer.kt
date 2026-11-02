package com.marc.albumartlock

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Turns a (usually square) album cover into a phone-sized lock screen image. */
object ArtRenderer {

    fun render(art: Bitmap, width: Int, height: Int, style: Int, dimPercent: Int): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        // Background: the cover cropped to fill the screen, blurred when the sharp copy goes on top.
        val background = if (style == Prefs.STYLE_FIT_BLUR) blur(art) else art
        canvas.drawBitmap(background, centerCropSource(background, width, height), Rect(0, 0, width, height), paint)

        if (style == Prefs.STYLE_FIT_BLUR) {
            // Darken the blurred part a bit more so the sharp cover stands out.
            canvas.drawColor(Color.argb(70, 0, 0, 0))
            val size = (width * 0.86f)
            val left = (width - size) / 2f
            val top = height * 0.40f - size / 2f
            val dst = RectF(left, top, left + size, top + size)
            val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(90, 0, 0, 0)
                setShadowLayer(48f, 0f, 16f, Color.argb(140, 0, 0, 0))
            }
            canvas.drawRoundRect(dst, 36f, 36f, shadow)
            val save = canvas.save()
            val clip = android.graphics.Path().apply {
                addRoundRect(dst, 36f, 36f, android.graphics.Path.Direction.CW)
            }
            canvas.clipPath(clip)
            canvas.drawBitmap(art, null, dst, paint)
            canvas.restoreToCount(save)
        }

        val dim = dimPercent.coerceIn(0, 80)
        if (dim > 0) canvas.drawColor(Color.argb((dim * 255 / 100f).roundToInt(), 0, 0, 0))
        return out
    }

    private fun centerCropSource(src: Bitmap, width: Int, height: Int): Rect {
        val targetRatio = width.toFloat() / height
        val srcRatio = src.width.toFloat() / src.height
        return if (srcRatio > targetRatio) {
            val w = (src.height * targetRatio).roundToInt()
            val x = (src.width - w) / 2
            Rect(x, 0, x + w, src.height)
        } else {
            val h = (src.width / targetRatio).roundToInt()
            val y = (src.height - h) / 2
            Rect(0, y, src.width, y + h)
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
