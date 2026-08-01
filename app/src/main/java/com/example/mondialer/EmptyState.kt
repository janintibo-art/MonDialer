package com.example.mondialer

import android.app.Activity
import android.view.View
import android.widget.TextView

/** Affiche un symbole et un message lorsqu'une liste n'a rien à montrer. */
object EmptyState {

    fun show(a: Activity, empty: Boolean, icon: String, message: String) {
        val box = a.findViewById<View>(R.id.emptyBox) ?: return
        box.visibility = if (empty) View.VISIBLE else View.GONE
        if (!empty) return
        a.findViewById<TextView>(R.id.emptyIcon)?.text = icon
        a.findViewById<TextView>(R.id.emptyText)?.text = message
        // Apparition en douceur
        box.alpha = 0f
        box.animate().alpha(1f).setDuration(320).start()
    }
}
