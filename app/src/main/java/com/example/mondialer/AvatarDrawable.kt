package com.example.mondialer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import java.text.Normalizer

/**
 * Pastille ronde portant les initiales d'un contact. La teinte est dérivée
 * du nom, elle reste donc identique d'un affichage à l'autre.
 */
class AvatarDrawable(
    private val name: String,
    private val accent: Int
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val box = RectF()

    /** Deux initiales au maximum, accents retirés. */
    private val initials: String = run {
        val clean = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
        val words = clean.split(" ", "-", "_").filter { it.isNotBlank() }
        when {
            words.isEmpty() -> "?"
            words.size == 1 -> words[0].take(2).uppercase()
            else -> (words[0].take(1) + words[1].take(1)).uppercase()
        }
    }

    /** Teinte stable : même nom, même couleur, à chaque ouverture. */
    private val hue: Float = run {
        var h = 0
        for (c in name) h = h * 31 + c.code
        ((h % 360) + 360) % 360f
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        box.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        val r = minOf(box.width(), box.height()) / 2f

        val top = Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.42f))
        val bottom = Color.HSVToColor(floatArrayOf(hue, 0.70f, 0.20f))

        // Corps dégradé
        paint.shader = LinearGradient(0f, box.top, 0f, box.bottom,
            top, bottom, Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(box.centerX(), box.centerY(), r, paint)

        // Reflet supérieur, pour le volume
        paint.shader = LinearGradient(0f, box.top, 0f, box.centerY(),
            Color.argb(70, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawCircle(box.centerX(), box.centerY(), r, paint)

        // Contour à la couleur du thème
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.07f
        paint.color = Color.argb(150, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawCircle(box.centerX(), box.centerY(), r - paint.strokeWidth / 2f, paint)

        // Initiales
        textPaint.textSize = r * 0.85f
        val fm = textPaint.fontMetrics
        canvas.drawText(initials, box.centerX(),
            box.centerY() - (fm.ascent + fm.descent) / 2f, textPaint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
