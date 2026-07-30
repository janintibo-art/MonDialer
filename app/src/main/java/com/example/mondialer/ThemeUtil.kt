package com.example.mondialer

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.view.View

object ThemeUtil {

    /** Couleur d'accent des drawables de base, servant de référence à la recoloration. */
    private const val BASE_ACCENT = 0xFF45E9FF.toInt()

    private val applied = HashMap<Int, String>()

    /** Bitmap de fond décodé une fois, partagé entre les écrans. */
    private var cachedBitmap: android.graphics.Bitmap? = null
    private var cachedBitmapUri: String = ""

    fun apply(a: Activity) {
        BlockRulesStore.appCtx = a.applicationContext
        val name = BlockRulesStore.theme
        val style = when (name) {
            "violet" -> R.style.AppTheme_Violet
            "green" -> R.style.AppTheme_Green
            "orange" -> R.style.AppTheme_Orange
            "rose" -> R.style.AppTheme_Rose
            "rouge" -> R.style.AppTheme_Rouge
            "or" -> R.style.AppTheme_Or
            "graphite" -> R.style.AppTheme_Graphite
            "ardoise" -> R.style.AppTheme_Ardoise
            "custom" -> when (BlockRulesStore.customShape) {
                "tuile" -> R.style.AppTheme_CustomTuile
                "hud" -> R.style.AppTheme_CustomHud
                else -> R.style.AppTheme_Custom
            }
            else -> R.style.AppTheme
        }
        a.setTheme(style)
        applied[System.identityHashCode(a)] = signature()
    }

    /** Signature de l'apparence : change dès qu'un réglage visuel est modifié. */
    private fun signature(): String {
        val t = BlockRulesStore.theme
        return if (t != "custom") t
        else "custom|${BlockRulesStore.customAccent}|${BlockRulesStore.customShape}" +
             "|${BlockRulesStore.customImage}|${BlockRulesStore.customDim}"
    }

    /**
     * À appeler dans onResume : recrée l'écran si l'apparence a changé pendant
     * qu'il était en arrière-plan, puis applique la personnalisation.
     */
    fun refreshIfNeeded(a: Activity) {
        val key = System.identityHashCode(a)
        val was = applied[key]
        if (was != null && was != signature()) {
            applied.remove(key)
            a.recreate()
            a.overridePendingTransition(
                android.R.anim.fade_in, android.R.anim.fade_out)
            return
        }
        decorate(a)
    }

    /** Famille de formes du thème actif : orb, tuile ou hud. */
    fun currentShape(ctx: android.content.Context): String {
        BlockRulesStore.appCtx = ctx.applicationContext
        // Le clavier peut imposer sa propre forme, indépendamment du thème
        val forced = BlockRulesStore.keypadShape
        if (forced != "auto") return forced
        return when (BlockRulesStore.theme) {
            "violet", "orange", "or" -> "tuile"
            "rouge", "graphite", "ardoise" -> "hud"
            "custom" -> BlockRulesStore.customShape
            else -> "orb"
        }
    }

    fun forget(a: Activity) {
        applied.remove(System.identityHashCode(a))
    }

    /** Applique la couleur choisie et l'image de fond du thème personnalisé. */
    fun decorate(a: Activity) {
        val content = a.findViewById<View>(android.R.id.content) ?: return

        if (BlockRulesStore.theme != "custom") {
            content.setLayerType(View.LAYER_TYPE_NONE, null)
            return
        }

        // --- Image de fond : posée derrière le contenu, donc non recolorée ---
        val uri = BlockRulesStore.customImage
        if (uri.isNotBlank()) {
            if (cachedBitmapUri != uri || cachedBitmap == null) {
                cachedBitmap = decodeBitmap(a, uri)
                cachedBitmapUri = uri
            }
            val bmp = cachedBitmap
            if (bmp != null) {
                // Un drawable neuf par écran : un même objet partagé entre
                // plusieurs fenêtres ne se dessine pas correctement.
                val image = BitmapDrawable(a.resources, bmp)
                image.gravity = android.view.Gravity.FILL
                val dim = BlockRulesStore.customDim.coerceIn(0, 100)
                val bg: Drawable = if (dim == 0) image
                else LayerDrawable(arrayOf(
                    image,
                    ColorDrawable(Color.argb(dim * 255 / 100, 0, 0, 0))))
                a.window.setBackgroundDrawable(bg)
            }
        }

        // --- Recoloration : l'écran est teinté vers la couleur choisie ---
        val accent = BlockRulesStore.customAccent
        if (accent == BASE_ACCENT) {
            content.setLayerType(View.LAYER_TYPE_NONE, null)
            return
        }
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(recolorMatrix(accent))
        content.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
    }

    /** Décode l'image à la résolution de l'écran, sans la charger en entier. */
    private fun decodeBitmap(a: Activity, uriStr: String): android.graphics.Bitmap? {
        return try {
            val uri = Uri.parse(uriStr)
            val dm = a.resources.displayMetrics
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            a.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (bounds.outWidth / sample > dm.widthPixels * 1.5 ||
                   bounds.outHeight / sample > dm.heightPixels * 1.5) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            a.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Matrice qui déplace la teinte de l'interface depuis la couleur de base
     * vers celle choisie, en ajustant aussi la saturation.
     */
    private fun recolorMatrix(target: Int): ColorMatrix {
        val hsvBase = FloatArray(3)
        val hsvTarget = FloatArray(3)
        Color.colorToHSV(BASE_ACCENT, hsvBase)
        Color.colorToHSV(target, hsvTarget)

        val deg = hsvTarget[0] - hsvBase[0]
        val rad = Math.toRadians(deg.toDouble())
        val cos = Math.cos(rad).toFloat()
        val sin = Math.sin(rad).toFloat()
        val lr = 0.213f; val lg = 0.715f; val lb = 0.072f

        val hue = ColorMatrix(floatArrayOf(
            lr + cos * (1 - lr) + sin * (-lr),
            lg + cos * (-lg) + sin * (-lg),
            lb + cos * (-lb) + sin * (1 - lb), 0f, 0f,

            lr + cos * (-lr) + sin * 0.143f,
            lg + cos * (1 - lg) + sin * 0.140f,
            lb + cos * (-lb) + sin * (-0.283f), 0f, 0f,

            lr + cos * (-lr) + sin * (-(1 - lr)),
            lg + cos * (-lg) + sin * lg,
            lb + cos * (1 - lb) + sin * lb, 0f, 0f,

            0f, 0f, 0f, 1f, 0f
        ))

        // Ajustement de saturation : une couleur pâle donne un rendu plus doux
        val ratio = if (hsvBase[1] > 0.01f) hsvTarget[1] / hsvBase[1] else 1f
        val sat = ColorMatrix()
        sat.setSaturation(ratio.coerceIn(0.35f, 1.6f))
        hue.postConcat(sat)
        return hue
    }
}
