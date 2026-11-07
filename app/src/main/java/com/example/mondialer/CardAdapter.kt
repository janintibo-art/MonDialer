package com.example.mondialer

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SimpleAdapter

/**
 * Adaptateur de liste habillé : chaque ligne devient une carte, avec la
 * pastille d'initiales du correspondant et une apparition en fondu décalé.
 */
class CardAdapter(
    private val activity: Activity,
    data: List<Map<String, String>>,
    layout: Int = R.layout.item_two_lines
) : SimpleAdapter(activity, data, layout,
    arrayOf("title", "sub"), intArrayOf(R.id.text1, R.id.text2)) {

    /** Position la plus haute déjà animée : on n'anime qu'à la découverte. */
    private var lastAnimated = -1

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val v = super.getView(position, convertView, parent)

        @Suppress("UNCHECKED_CAST")
        val status = (getItem(position) as? Map<String, String>)?.get("status")
        // Couleurs lisibles, distinctes du theme sans modifier le filtrage.
        val stateColor = when (status) {
            "incoming" -> Color.rgb(59, 220, 166)
            "outgoing" -> Color.rgb(84, 192, 255)
            "missed" -> Color.rgb(255, 172, 75)
            "rejected", "blocked" -> Color.rgb(255, 92, 117)
            else -> ThemeRes.color(activity, R.attr.cNeon)
        }
        // Une vraie pastille unie : le tint sur le dégradé du thème était trompeur.
        v.findViewById<View>(R.id.statusStrip)?.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 4f * activity.resources.displayMetrics.density
            setColor(stateColor)
        }
        v.findViewById<TextView>(R.id.text2)?.let { sub ->
            sub.setTextColor(if (status in listOf("blocked", "rejected", "missed"))
                stateColor else Color.rgb(207, 221, 232))
        }
        v.findViewById<TextView>(R.id.text1)?.setTextColor(Color.WHITE)

        // Pastille : photo si disponible, sinon initiales colorées
        v.findViewById<ImageView>(R.id.avatar)?.let { img ->
            @Suppress("UNCHECKED_CAST")
            val item = getItem(position) as? Map<String, String>
            // Les préfixes décoratifs sont retirés avant de calculer les initiales.
            // (Un emoji ne tient pas dans un littéral de caractère : on filtre par chaîne.)
            var label = item?.get("title") ?: ""
            for (mark in listOf("★", "●", "○", "✓", "🔎", "✎", "📷")) {
                label = label.removePrefix(mark).trimStart()
            }
            label = label.trim()
            val number = item?.get("num") ?: item?.get("number").orEmpty()
            val avatarLabel = when {
                label.equals("Numéro masqué", ignoreCase = true) || number.isBlank() -> "?"
                label.startsWith("+") || label.firstOrNull()?.isDigit() == true -> "☎"
                else -> label
            }
            img.setImageDrawable(AvatarDrawable(avatarLabel, stateColor))
        }

        // Apparition : montée légère en fondu, décalée selon la ligne.
        // La vue est systématiquement remise à l'état visible : une animation
        // interrompue laisserait sinon la ligne transparente.
        v.animate().cancel()
        if (position > lastAnimated) {
            lastAnimated = position
            v.alpha = 0f
            v.translationY = 26f * activity.resources.displayMetrics.density
            v.animate()
                .alpha(1f).translationY(0f)
                .setStartDelay((position % 8) * 28L)
                .setDuration(260)
                .withEndAction { v.alpha = 1f; v.translationY = 0f }
                .start()
        } else {
            v.alpha = 1f
            v.translationY = 0f
        }
        return v
    }
}
