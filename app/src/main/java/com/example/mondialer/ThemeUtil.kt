package com.example.mondialer

import android.app.Activity

object ThemeUtil {

    /** Thème réellement appliqué à chaque activité vivante. */
    private val applied = HashMap<Int, String>()

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
            else -> R.style.AppTheme
        }
        a.setTheme(style)
        applied[System.identityHashCode(a)] = name
    }

    /**
     * À appeler dans onResume : si le thème a changé pendant que cette activité
     * était en arrière-plan (elle est reprise depuis la pile sans repasser par
     * onCreate), on la recrée pour qu'elle adopte la nouvelle palette.
     */
    fun refreshIfNeeded(a: Activity) {
        val key = System.identityHashCode(a)
        val was = applied[key] ?: return
        if (was != BlockRulesStore.theme) {
            applied.remove(key)
            a.recreate()
        }
    }

    fun forget(a: Activity) {
        applied.remove(System.identityHashCode(a))
    }
}
