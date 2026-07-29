package com.example.mondialer

import android.content.Context
import android.content.SharedPreferences

object BlockRulesStore {

    private const val PREFS = "block_rules"

    /** Préfixes ARCEP réservés au démarchage commercial (France métropolitaine). */
    val PREDEFINED_SPAM_PREFIXES = listOf(
        "0162", "0163", "0270", "0271", "0377", "0378",
        "0424", "0425", "0568", "0569", "0948", "0949"
    )

    lateinit var appCtx: Context

    private fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun normalize(raw: String?): String {
        if (raw == null) return ""
        var n = raw.filter { it.isDigit() || it == '+' }
        if (n.startsWith("0033")) n = "0" + n.substring(4)
        if (n.startsWith("+33")) n = "0" + n.substring(3)
        n = n.filter { it.isDigit() }
        return n
    }

    var blockHidden: Boolean
        get() = prefs(appCtx).getBoolean("hidden", false)
        set(v) { prefs(appCtx).edit().putBoolean("hidden", v).apply() }

    var usePredefined: Boolean
        get() = prefs(appCtx).getBoolean("predefined", true)
        set(v) { prefs(appCtx).edit().putBoolean("predefined", v).apply() }

    var blockNeighbors: Boolean
        get() = prefs(appCtx).getBoolean("neighbors", false)
        set(v) { prefs(appCtx).edit().putBoolean("neighbors", v).apply() }

    var myNumber: String
        get() = prefs(appCtx).getString("my_number", "") ?: ""
        set(v) { prefs(appCtx).edit().putString("my_number", v).apply() }

    var neighborPrefixLen: Int
        get() = prefs(appCtx).getInt("neighbor_len", 6)
        set(v) { prefs(appCtx).edit().putInt("neighbor_len", v).apply() }

    fun numbers(): MutableSet<String> =
        HashSet(prefs(appCtx).getStringSet("numbers", emptySet()) ?: emptySet())

    fun addNumber(n: String) {
        val s = numbers(); s.add(normalize(n))
        prefs(appCtx).edit().putStringSet("numbers", s).apply()
    }

    fun removeNumber(n: String) {
        val s = numbers(); s.remove(n)
        prefs(appCtx).edit().putStringSet("numbers", s).apply()
    }

    fun prefixes(): MutableSet<String> =
        HashSet(prefs(appCtx).getStringSet("prefixes", emptySet()) ?: emptySet())

    fun addPrefix(p: String) {
        val s = prefixes(); s.add(normalize(p))
        prefs(appCtx).edit().putStringSet("prefixes", s).apply()
    }

    fun removePrefix(p: String) {
        val s = prefixes(); s.remove(p)
        prefs(appCtx).edit().putStringSet("prefixes", s).apply()
    }

    fun shouldBlock(rawNumber: String?): Boolean {
        val n = normalize(rawNumber)

        if (n.isEmpty()) return blockHidden
        if (n in numbers()) return true

        for (pre in prefixes()) {
            if (pre.isNotEmpty() && n.startsWith(pre)) return true
        }

        if (usePredefined) {
            for (pre in PREDEFINED_SPAM_PREFIXES) {
                if (n.startsWith(pre)) return true
            }
        }

        if (blockNeighbors) {
            val mine = normalize(myNumber)
            val k = neighborPrefixLen
            if (mine.length >= k && n != mine &&
                n.length == mine.length &&
                n.take(k) == mine.take(k)
            ) return true
        }

        return false
    }
}
