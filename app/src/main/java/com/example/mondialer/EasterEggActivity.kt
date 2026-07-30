package com.example.mondialer

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.os.Bundle
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView

/** Récompense cachée : le A tourne, une citation s'affiche. */
class EasterEggActivity : Activity() {

    private val quotes = listOf(
        "« La liberté des autres étend la mienne à l’infini. » — Mikhaïl Bakounine",
        "« Le pouvoir appartient à ceux qui osent le prendre. » — Louise Michel",
        "« L’anarchie, c’est l’ordre sans le pouvoir. » — Pierre-Joseph Proudhon",
        "« Ni Dieu ni maître. » — Auguste Blanqui",
        "« Il est interdit d’interdire. » — Mai 68",
        "« Sous les pavés, la plage. » — Mai 68",
        "« La propriété, c’est le vol. » — Pierre-Joseph Proudhon",
        "« Les hommes naissent libres et égaux ; après, ils se débrouillent. » — Coluche",
        "« Il vaut mieux mourir debout que vivre à genoux. » — Buenaventura Durruti",
        "« Ce n’est pas parce qu’ils sont nombreux à avoir tort qu’ils ont raison. » — Coluche"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_egg)

        findViewById<TextView>(R.id.txtQuote).text = quotes.random()

        val logo = findViewById<ImageView>(R.id.imgEgg)
        // Rotation lente et respiration : le symbole prend vie
        ObjectAnimator.ofFloat(logo, "rotation", 0f, 360f).apply {
            duration = 12000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(logo, "scaleX", 0.86f, 1.06f).apply {
            duration = 2200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
        ObjectAnimator.ofFloat(logo, "scaleY", 0.86f, 1.06f).apply {
            duration = 2200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }

        findViewById<TextView>(R.id.txtQuote).setOnClickListener {
            findViewById<TextView>(R.id.txtQuote).text = quotes.random()
        }
    }

    override fun onDestroy() {
        ThemeUtil.forget(this)
        super.onDestroy()
    }
}
