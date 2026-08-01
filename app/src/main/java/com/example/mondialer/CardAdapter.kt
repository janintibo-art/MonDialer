package com.example.mondialer

import android.app.Activity
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

        // Pastille : photo si disponible, sinon initiales colorées
        v.findViewById<ImageView>(R.id.avatar)?.let { img ->
            @Suppress("UNCHECKED_CAST")
            val item = getItem(position) as? Map<String, String>
            val label = item?.get("title")?.trimStart('★', '●', '○', '✓', '🔎', ' ') ?: ""
            val accent = ThemeRes.color(activity, R.attr.cNeon)
            img.setImageDrawable(AvatarDrawable(label, accent))
        }

        // Apparition : montée légère en fondu, décalée selon la ligne
        if (position > lastAnimated) {
            lastAnimated = position
            v.alpha = 0f
            v.translationY = 26f * activity.resources.displayMetrics.density
            v.animate()
                .alpha(1f).translationY(0f)
                .setStartDelay((position % 8) * 28L)
                .setDuration(260)
                .start()
        }
        return v
    }
}
