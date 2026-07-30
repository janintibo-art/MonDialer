package com.example.mondialer

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Le mur de la honte : ce que les indésirables ont tenté, et ce qu'il en reste. */
class StatsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        val log = BlockRulesStore.blockedLog()
        val root = findViewById<LinearLayout>(R.id.statsRoot)

        findViewById<TextView>(R.id.txtTotal).text = log.size.toString()
        findViewById<TextView>(R.id.txtTotalLabel).text =
            resources.getQuantityString(R.plurals.stats_total, log.size)

        if (log.isEmpty()) {
            addLine(root, getString(R.string.stats_empty))
            return
        }

        // --- Sur les sept derniers jours ---
        val cal = Calendar.getInstance()
        val dayFmt = SimpleDateFormat("EEE", Locale.FRANCE)
        val days = mutableListOf<Pair<String, Int>>()
        for (back in 6 downTo 0) {
            cal.timeInMillis = System.currentTimeMillis()
            cal.add(Calendar.DAY_OF_YEAR, -back)
            val start = startOfDay(cal.timeInMillis)
            val end = start + 86_400_000L
            val n = log.count { it.time in start until end }
            days.add(Pair(dayFmt.format(Date(start)).uppercase(), n))
        }
        addTitle(root, getString(R.string.stats_week))
        val maxDay = days.maxOf { it.second }.coerceAtLeast(1)
        for ((label, n) in days) addBar(root, label, n, maxDay)

        // --- Palmarès des préfixes ---
        val byPrefix = log.groupingBy { it.number.take(4) }.eachCount()
            .entries.sortedByDescending { it.value }.take(6)
        if (byPrefix.isNotEmpty()) {
            addTitle(root, getString(R.string.stats_prefixes))
            val maxP = byPrefix.first().value.coerceAtLeast(1)
            for (e in byPrefix) addBar(root, e.key.ifBlank { "?" }, e.value, maxP)
        }

        // --- Motifs de blocage ---
        val byReason = log.groupingBy { it.reason }.eachCount()
            .entries.sortedByDescending { it.value }.take(6)
        addTitle(root, getString(R.string.stats_reasons))
        val maxR = byReason.first().value.coerceAtLeast(1)
        for (e in byReason) addBar(root, e.key, e.value, maxR)

        // --- Le plus insistant ---
        val worst = log.groupingBy { it.number }.eachCount()
            .entries.maxByOrNull { it.value }
        if (worst != null && worst.value > 1) {
            addTitle(root, getString(R.string.stats_worst))
            addLine(root, getString(R.string.stats_worst_line, worst.key, worst.value))
        }
    }

    private fun startOfDay(t: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = t
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun addTitle(root: LinearLayout, text: String) {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(ThemeRes.color(this, R.attr.cNeon))
        tv.textSize = 14f
        tv.setTypeface(null, android.graphics.Typeface.BOLD)
        tv.setPadding(0, dp(20), 0, dp(6))
        root.addView(tv)
    }

    private fun addLine(root: LinearLayout, text: String) {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(ThemeRes.color(this, R.attr.cText))
        tv.textSize = 14f
        tv.setPadding(0, dp(4), 0, dp(4))
        root.addView(tv)
    }

    /** Une ligne du graphique : libellé, barre proportionnelle, valeur. */
    private fun addBar(root: LinearLayout, label: String, value: Int, max: Int) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(3), 0, dp(3))

        val lbl = TextView(this)
        lbl.text = label
        lbl.setTextColor(ThemeRes.color(this, R.attr.cTextDim))
        lbl.textSize = 12f
        lbl.width = dp(96)
        lbl.maxLines = 1
        row.addView(lbl)

        val neon = ThemeRes.color(this, R.attr.cNeon)
        val bar = TextView(this)
        val d = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(darken(neon), neon))
        d.cornerRadius = dp(8).toFloat()
        bar.background = d
        bar.height = dp(18)
        val frac = value.toFloat() / max
        val lp = LinearLayout.LayoutParams(0, dp(18), frac.coerceAtLeast(0.04f))
        row.addView(bar, lp)

        val spacer = LinearLayout.LayoutParams(0, dp(18), (1f - frac).coerceAtLeast(0.001f))
        row.addView(TextView(this), spacer)

        val num = TextView(this)
        num.text = value.toString()
        num.setTextColor(ThemeRes.color(this, R.attr.cText))
        num.textSize = 13f
        num.setPadding(dp(8), 0, 0, 0)
        row.addView(num)

        root.addView(row)
    }

    private fun darken(c: Int) = Color.rgb(
        (Color.red(c) * 0.45f).toInt(),
        (Color.green(c) * 0.45f).toInt(),
        (Color.blue(c) * 0.45f).toInt())

    override fun onResume() {
        super.onResume()
        ThemeUtil.refreshIfNeeded(this)
    }

    override fun onDestroy() {
        ThemeUtil.forget(this)
        super.onDestroy()
    }
}
