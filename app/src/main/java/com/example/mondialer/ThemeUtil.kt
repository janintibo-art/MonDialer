package com.example.mondialer

import android.app.Activity

object ThemeUtil {
    fun apply(a: Activity) {
        BlockRulesStore.appCtx = a.applicationContext
        val style = when (BlockRulesStore.theme) {
            "violet" -> R.style.AppTheme_Violet
            "green" -> R.style.AppTheme_Green
            "orange" -> R.style.AppTheme_Orange
            else -> R.style.AppTheme
        }
        a.setTheme(style)
    }
}
