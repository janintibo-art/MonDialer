package com.example.mondialer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import android.widget.Button

/**
 * Touche de clavier au rendu 3D peint sur Canvas.
 *
 * Le volume repose sur un capot de brillance découpé à la forme exacte de la
 * touche (et non sur des liserés rapportés, qui donnaient un double contour
 * plat) : corps très contrasté, dôme lumineux, vernis dense en haut, ombre
 * interne au bas et lumière rebondie. À l'appui, l'éclairage bascule et le
 * contenu s'enfonce, comme un bouton physique.
 */
class Neon3DButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : Button(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val body = RectF()
    private val tmp = RectF()
    private val clip = Path()

    private var press = 0f
    private var animator: ValueAnimator? = null

    init {
        background = null
        stateListAnimator = null
    }

    override fun dispatchSetPressed(pressed: Boolean) {
        super.dispatchSetPressed(pressed)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(press, if (pressed) 1f else 0f).apply {
            duration = if (pressed) 55 else 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { press = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun a(c: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    private fun radiusFor(): Float {
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
        val neon = try { ThemeRes.color(context, R.attr.cNeon) }
                   catch (e: Exception) { 0xFF45E9FF.toInt() }

        val pad = 6f * d
        body.set(pad, pad, w - pad, h - pad)
        val sink = press * 3f * d
        val lift = 1f - press

        // ---------- 1. Ombre portée (se résorbe à l'appui) ----------
        paint.shader = null
        paint.style = Paint.Style.FILL
        tmp.set(body)
        tmp.offset(0f, 4f * d * lift + d)
        var r = radiusFor()
        paint.color = a(Color.BLACK, (150 * lift + 45).toInt())
        canvas.drawRoundRect(tmp, r, r, paint)

        body.offset(0f, sink)
        r = radiusFor()

        // ---------- 2. Halo néon, dégradé sur 6 anneaux ----------
        paint.style = Paint.Style.STROKE
        for (i in 6 downTo 1) {
            paint.strokeWidth = i * 2.2f * d
            paint.color = a(neon, (10 + (6 - i) * 9 + press * 60).toInt())
            canvas.drawRoundRect(body, r, r, paint)
        }

        // ---------- 3. Corps : fort contraste haut/bas, inversé à l'appui ----------
        paint.style = Paint.Style.FILL
        val lit = 0xFF2A6285.toInt()     // sommet éclairé
        val deep = 0xFF010306.toInt()    // base dans l'ombre
        val mid = 0xFF0A1D2C.toInt()
        paint.shader = LinearGradient(
            0f, body.top, 0f, body.bottom,
            intArrayOf(blend(lit, deep, press), mid, blend(deep, lit, press)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(body, r, r, paint)

        // ---------- Tout ce qui suit est découpé à la forme de la touche ----------
        clip.reset()
        clip.addRoundRect(body, r, r, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)

        // 4. Dôme : lumière du thème diffusée depuis la source
        paint.shader = RadialGradient(
            body.centerX(), body.top + body.height() * (0.10f + press * 0.72f),
            body.width() * 0.72f,
            intArrayOf(a(neon, (70 * lift + 26).toInt()), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(body, paint)

        // 5. Capot de brillance : dense en haut, il épouse le bord arrondi
        val capH = body.height() * (0.52f - press * 0.30f)
        tmp.set(body.left, body.top, body.right, body.top + capH)
        paint.shader = LinearGradient(
            0f, tmp.top, 0f, tmp.bottom,
            intArrayOf(
                a(Color.WHITE, (150 * lift + 14).toInt()),
                a(Color.WHITE, (58 * lift + 6).toInt()),
                Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(tmp, paint)

        // 6. Éclat de vernis : bande étroite très lumineuse au sommet
        tmp.set(
            body.left + body.width() * 0.14f, body.top + 1.5f * d,
            body.right - body.width() * 0.14f, body.top + body.height() * 0.26f)
        paint.shader = LinearGradient(
            0f, tmp.top, 0f, tmp.bottom,
            a(Color.WHITE, (200 * lift + 10).toInt()), Color.TRANSPARENT,
            Shader.TileMode.CLAMP)
        canvas.drawRoundRect(tmp, tmp.height(), tmp.height(), paint)

        // 7. Ombre interne au bas : creuse la matière
        paint.shader = LinearGradient(
            0f, body.bottom - body.height() * 0.45f, 0f, body.bottom,
            Color.TRANSPARENT, a(Color.BLACK, (165 * lift + 60).toInt()),
            Shader.TileMode.CLAMP)
        canvas.drawRect(body, paint)

        // 8. Lumière rebondie : liseré coloré près du bord inférieur
        paint.shader = LinearGradient(
            0f, body.bottom - body.height() * 0.22f, 0f, body.bottom,
            Color.TRANSPARENT, a(neon, (95 * lift + 18).toInt()),
            Shader.TileMode.CLAMP)
        canvas.drawRect(body, paint)

        // 9. Biseau : mince arête claire posée sur le bord haut, suit la forme
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.4f * d
        paint.color = a(Color.WHITE, (165 * lift + 12).toInt())
        canvas.drawRoundRect(body, r, r, paint)   // rogné par le clip : reste net

        canvas.restore()

        // ---------- 10. Contour néon, net et complet ----------
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * d
        paint.color = a(neon, 245)
        canvas.drawRoundRect(body, r, r, paint)

        body.offset(0f, -sink)

        // ---------- 11. Contenu, solidaire de l'enfoncement ----------
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
