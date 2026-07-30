package com.example.mondialer

import android.content.Context
import android.util.TypedValue

/** Résolution des attributs de thème (couleurs et drawables de forme). */
object ThemeRes {

    /** Identifiant de ressource pointé par un attribut (?attr/dialBg, etc.). */
    fun res(ctx: Context, attr: Int): Int {
        val tv = TypedValue()
        ctx.theme.resolveAttribute(attr, tv, true)
        return tv.resourceId
    }

    /** Couleur d'un attribut, que ce soit une valeur littérale ou une ressource. */
    fun color(ctx: Context, attr: Int): Int {
        val tv = TypedValue()
        ctx.theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) ctx.getColor(tv.resourceId) else tv.data
    }
}
