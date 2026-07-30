package com.example.mondialer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import android.widget.Button

/**
 * Touche de clavier au rendu 3D peint sur Canvas : éclairage venant du haut,
 * dôme lumineux, reflet spéculaire, ombre interne, ombre portée et halo néon.
 * À l'appui, l'éclairage s'inverse et le contenu s'enfonce, comme un bouton
 * physique. La forme suit la famille du thème actif (rond, tuile ou HUD).
 */
class Neon3DButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : Button(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val body = RectF()
    private val tmp = RectF()

    /** Progression de l'appui, animée de 0 (relâché) à 1 (enfoncé). */
    private var press = 0f
    private var animator: ValueAnimator? = null

    init {
        background = null
        stateListAnimator = null   // l'enfoncement est géré ici, en interne
    }

    override fun dispatchSetPressed(pressed: Boolean) {
        super.dispatchSetPressed(pressed)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(press, if (pressed) 1f else 0f).apply {
            duration = if (pressed) 60 else 190
            interpolator = DecelerateInterpolator()
            addUpdateListener { press = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun a(c: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    private fun cornerRadius(): Float {
        val d = resources.displayMetrics.density
        return when (ThemeUtil.currentShape(context)) {
            "tuile" -> 20f * d
            "hud" -> 6f * d
            else -> minOf(body.width(), body.height()) / 2f
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) { super.onDraw(canvas); return }

        val d = resources.displayMetrics.density
        val neon = try { ThemeRes.color(context, R.attr.cNeon) } catch (e: Exception) { 0xFF45E9FF.toInt() }
        val pad = 6f * d
        body.set(pad, pad, w - pad, h - pad)
        val r = cornerRadius()
        val sink = press * 2.5f * d          // enfoncement du corps
        val lift = (1f - press)              // hauteur apparente

        // ---- 1. Ombre portée : se réduit quand la touche s'enfonce ----
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = a(Color.BLACK, (140 * lift + 50).toInt())
        tmp.set(body); tmp.offset(0f, 3.5f * d * lift + 1f * d)
        canvas.drawRoundRect(tmp, r, r, paint)

        // ---- 2. Halo néon concentrique (s'intensifie à l'appui) ----
        paint.style = Paint.Style.STROKE
        for (i in 4 downTo 1) {
            paint.strokeWidth = i * 2.6f * d
            paint.color = a(neon, (16 + (4 - i) * 14 + press * 70).toInt())
            tmp.set(body); tmp.offset(0f, sink)
            canvas.drawRoundRect(tmp, r, r, paint)
        }

        body.offset(0f, sink)

        // ---- 3. Corps : verre sombre, lumière inversée à l'appui ----
        paint.style = Paint.Style.FILL
        val litTop = 0xFF102A3D.toInt()
        val dark = 0xFF030810.toInt()
        val mid = 0xFF081826.toInt()
        val cTop = blend(litTop, dark, press)
        val cBot = blend(dark, litTop, press)
        paint.shader = LinearGradient(
            0f, body.top, 0f, body.bottom,
            intArrayOf(cTop, mid, cBot), floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP)
        canvas.drawRoundRect(body, r, r, paint)

        // ---- 4. Dôme : lumière du thème diffusée depuis le haut ----
        paint.shader = RadialGradient(
            w / 2f, body.top + body.height() * (0.16f + press * 0.55f),
            body.width() * 0.78f,
            intArrayOf(a(neon, (42 * lift + 20).toInt()), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(body, r, r, paint)

        // ---- 5. Reflet spéculaire (bande brillante du haut) ----
        val specH = body.height() * 0.44f
        tmp.set(
            body.left + body.width() * 0.13f, body.top + 2f * d,
            body.right - body.width() * 0.13f, body.top + specH)
        paint.shader = LinearGradient(
            0f, tmp.top, 0f, tmp.bottom,
            a(Color.WHITE, (85 * lift + 12).toInt()), Color.TRANSPARENT,
            Shader.TileMode.CLAMP)
        canvas.drawRoundRect(tmp, r * 0.85f, r * 0.85f, paint)

        // ---- 6. Ombre interne au bas (donne le creux) ----
        paint.shader = LinearGradient(
            0f, body.bottom - body.height() * 0.34f, 0f, body.bottom,
            Color.TRANSPARENT, a(Color.BLACK, (110 * lift + 40).toInt()),
            Shader.TileMode.CLAMP)
        canvas.drawRoundRect(body, r, r, paint)

        // ---- 7. Contour néon + liserés haut/bas (arête 3D) ----
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.9f * d
        paint.color = a(neon, 235)
        canvas.drawRoundRect(body, r, r, paint)

        paint.strokeWidth = 1f * d
        tmp.set(body); tmp.inset(3.4f * d, 3.4f * d)
        // arête claire en haut
        paint.color = a(Color.WHITE, (52 * lift + 8).toInt())
        canvas.drawArc(tmp, 180f, 180f, false, paint)
        // arête sombre en bas
        paint.color = a(Color.BLACK, 90)
        canvas.drawArc(tmp, 0f, 180f, false, paint)

        body.offset(0f, -sink)

        // ---- 8. Contenu (chiffre + lettres), qui s'enfonce avec la touche ----
        canvas.save()
        canvas.translate(0f, sink)
        super.onDraw(canvas)
        canvas.restore()
    }

    private fun blend(c1: Int, c2: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(c1) * (1 - k) + Color.red(c2) * k).toInt(),
            (Color.green(c1) * (1 - k) + Color.green(c2) * k).toInt(),
            (Color.blue(c1) * (1 - k) + Color.blue(c2) * k).toInt())
    }
}
