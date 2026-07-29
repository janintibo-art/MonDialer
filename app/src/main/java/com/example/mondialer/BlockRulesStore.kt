package com.example.mondialer

import android.content.Context
import android.content.SharedPreferences

object BlockRulesStore {

    private const val PREFS = "block_rules"

    /** Une liste prédéfinie de préfixes, activable indépendamment. */
    data class PredefList(val id: String, val label: String, val prefixes: List<String>)

    val PREDEFINED_LISTS = listOf(
        PredefList(
            "arcep_metro",
            "Démarchage ARCEP — métropole",
            listOf("0162", "0163", "0270", "0271", "0377", "0378",
                   "0424", "0425", "0568", "0569", "0948", "0949")
        ),
        PredefList(
            "arcep_om",
            "Démarchage ARCEP — outre-mer",
            listOf("09475", "09476", "09477", "09478", "09479")
        ),
        PredefList(
            "special_08",
            "Numéros spéciaux 08 (surtaxés / services)",
            listOf("08")
        )
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

    // ---- Options simples ----
    var blockHidden: Boolean
        get() = prefs(appCtx).getBoolean("hidden", false)
        set(v) { prefs(appCtx).edit().putBoolean("hidden", v).apply() }

    var blockNeighbors: Boolean
        get() = prefs(appCtx).getBoolean("neighbors", false)
        set(v) { prefs(appCtx).edit().putBoolean("neighbors", v).apply() }

    /** Bloque tout appel dont le numéro n'est pas français (indicatif ≠ +33). */
    var blockInternational: Boolean
        get() = prefs(appCtx).getBoolean("international", false)
        set(v) { prefs(appCtx).edit().putBoolean("international", v).apply() }

    var myNumber: String
        get() = prefs(appCtx).getString("my_number", "") ?: ""
        set(v) { prefs(appCtx).edit().putString("my_number", v).apply() }

    var neighborPrefixLen: Int
        get() = prefs(appCtx).getInt("neighbor_len", 6)
        set(v) { prefs(appCtx).edit().putInt("neighbor_len", v).apply() }

    // ---- Listes prédéfinies activées ----
    fun enabledLists(): MutableSet<String> {
        val p = prefs(appCtx)
        val stored = p.getStringSet("enabled_lists", null)
        if (stored != null) return HashSet(stored)
        // Migration depuis l'ancienne option unique "predefined"
        return if (p.getBoolean("predefined", true))
            hashSetOf("arcep_metro") else hashSetOf()
    }

    fun isListEnabled(id: String): Boolean = id in enabledLists()

    fun setListEnabled(id: String, on: Boolean) {
        val s = enabledLists()
        if (on) s.add(id) else s.remove(id)
        prefs(appCtx).edit().putStringSet("enabled_lists", s).apply()
    }

    // ---- Listes personnelles ----
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

    // ---- Décision ----
    fun shouldBlock(rawNumber: String?): Boolean {
        val n = normalize(rawNumber)

        if (n.isEmpty()) return blockHidden
        if (n in numbers()) return true

        for (pre in prefixes()) {
            if (pre.isNotEmpty() && n.startsWith(pre)) return true
        }

        val enabled = enabledLists()
        for (list in PREDEFINED_LISTS) {
            if (list.id in enabled) {
                for (pre in list.prefixes) {
                    if (n.startsWith(pre)) return true
                }
            }
        }

        // Après normalisation, un numéro français commence par 0.
        // Un numéro étranger garde son indicatif pays (ex: 49..., 216...).
        if (blockInternational && !n.startsWith("0") && n.length >= 8) return true

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
