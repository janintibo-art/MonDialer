package com.example.mondialer

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object BlockRulesStore {

    private const val PREFS = "block_rules"

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

    // ---- Options ----
    var blockHidden: Boolean
        get() = prefs(appCtx).getBoolean("hidden", false)
        set(v) { prefs(appCtx).edit().putBoolean("hidden", v).apply() }

    var blockNeighbors: Boolean
        get() = prefs(appCtx).getBoolean("neighbors", false)
        set(v) { prefs(appCtx).edit().putBoolean("neighbors", v).apply() }

    var blockInternational: Boolean
        get() = prefs(appCtx).getBoolean("international", false)
        set(v) { prefs(appCtx).edit().putBoolean("international", v).apply() }

    /** Mode discret : sonnerie coupée au lieu de rejeter l'appel. */
    var silentMode: Boolean
        get() = prefs(appCtx).getBoolean("silent", false)
        set(v) { prefs(appCtx).edit().putBoolean("silent", v).apply() }

    // ---- Thème personnalisé ----
    /** Couleur d'accent choisie par l'utilisateur. */
    var customAccent: Int
        get() = prefs(appCtx).getInt("custom_accent", 0xFF45E9FF.toInt())
        set(v) { prefs(appCtx).edit().putInt("custom_accent", v).apply() }

    /** Famille de formes du thème personnalisé : orb, tuile ou hud. */
    var customShape: String
        get() = prefs(appCtx).getString("custom_shape", "orb") ?: "orb"
        set(v) { prefs(appCtx).edit().putString("custom_shape", v).apply() }

    /** Image de fond choisie dans la galerie (URI persistée), ou vide. */
    var customImage: String
        get() = prefs(appCtx).getString("custom_image", "") ?: ""
        set(v) { prefs(appCtx).edit().putString("custom_image", v).apply() }

    /** Assombrissement de l'image de fond, de 0 à 100. */
    var customDim: Int
        get() = prefs(appCtx).getInt("custom_dim", 55)
        set(v) { prefs(appCtx).edit().putInt("custom_dim", v).apply() }

    var theme: String
        get() = prefs(appCtx).getString("theme", "cyan") ?: "cyan"
        set(v) { prefs(appCtx).edit().putString("theme", v).apply() }

    var myNumber: String
        get() = prefs(appCtx).getString("my_number", "") ?: ""
        set(v) { prefs(appCtx).edit().putString("my_number", v).apply() }

    var neighborPrefixLen: Int
        get() = prefs(appCtx).getInt("neighbor_len", 6)
        set(v) { prefs(appCtx).edit().putInt("neighbor_len", v).apply() }

    // ---- Listes prédéfinies ----
    fun enabledLists(): MutableSet<String> {
        val p = prefs(appCtx)
        val stored = p.getStringSet("enabled_lists", null)
        if (stored != null) return HashSet(stored)
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

    // ---- Décision, avec raison ----
    /** Renvoie la raison du blocage, ou null si l'appel est autorisé. */
    fun blockReason(rawNumber: String?): String? {
        val n = normalize(rawNumber)

        if (n.isEmpty()) return if (blockHidden) "Numéro masqué" else null
        if (n in numbers()) return "Numéro bloqué"

        for (pre in prefixes()) {
            if (pre.isNotEmpty() && n.startsWith(pre)) return "Préfixe $pre"
        }

        val enabled = enabledLists()
        for (list in PREDEFINED_LISTS) {
            if (list.id in enabled) {
                for (pre in list.prefixes) {
                    if (n.startsWith(pre)) return list.label
                }
            }
        }

        if (blockInternational && !n.startsWith("0") && n.length >= 8)
            return "Appel international"

        if (blockNeighbors) {
            val mine = normalize(myNumber)
            val k = neighborPrefixLen
            if (mine.length >= k && n != mine &&
                n.length == mine.length &&
                n.take(k) == mine.take(k)
            ) return "Numéro imitant le mien"
        }

        return null
    }

    fun shouldBlock(rawNumber: String?): Boolean = blockReason(rawNumber) != null

    // ---- Journal des appels bloqués ----
    fun logBlocked(number: String, reason: String) {
        val p = prefs(appCtx)
        val arr = try { JSONArray(p.getString("blocked_log", "[]")) } catch (e: Exception) { JSONArray() }
        val entry = JSONObject()
            .put("n", number)
            .put("r", reason)
            .put("t", System.currentTimeMillis())
        val out = JSONArray()
        out.put(entry)
        var i = 0
        while (i < arr.length() && i < 199) { out.put(arr.get(i)); i++ }
        p.edit().putString("blocked_log", out.toString()).apply()
    }

    data class BlockedEntry(val number: String, val reason: String, val time: Long)

    fun blockedLog(): List<BlockedEntry> {
        val out = mutableListOf<BlockedEntry>()
        try {
            val arr = JSONArray(prefs(appCtx).getString("blocked_log", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(BlockedEntry(o.optString("n"), o.optString("r"), o.optLong("t")))
            }
        } catch (_: Exception) {}
        return out
    }

    fun clearBlockedLog() {
        prefs(appCtx).edit().putString("blocked_log", "[]").apply()
    }

    // ---- Export / import des règles ----
    fun exportJson(): String {
        val o = JSONObject()
        o.put("numbers", JSONArray(numbers().toList()))
        o.put("prefixes", JSONArray(prefixes().toList()))
        o.put("enabled_lists", JSONArray(enabledLists().toList()))
        o.put("hidden", blockHidden)
        o.put("international", blockInternational)
        o.put("neighbors", blockNeighbors)
        o.put("silent", silentMode)
        o.put("my_number", myNumber)
        o.put("theme", theme)
        return o.toString(2)
    }

    fun importJson(json: String): Boolean {
        return try {
            val o = JSONObject(json)
            val e = prefs(appCtx).edit()
            val nums = HashSet<String>()
            val na = o.optJSONArray("numbers") ?: JSONArray()
            for (i in 0 until na.length()) nums.add(na.getString(i))
            e.putStringSet("numbers", nums)
            val pres = HashSet<String>()
            val pa = o.optJSONArray("prefixes") ?: JSONArray()
            for (i in 0 until pa.length()) pres.add(pa.getString(i))
            e.putStringSet("prefixes", pres)
            val lists = HashSet<String>()
            val la = o.optJSONArray("enabled_lists") ?: JSONArray()
            for (i in 0 until la.length()) lists.add(la.getString(i))
            e.putStringSet("enabled_lists", lists)
            e.putBoolean("hidden", o.optBoolean("hidden", false))
            e.putBoolean("international", o.optBoolean("international", false))
            e.putBoolean("neighbors", o.optBoolean("neighbors", false))
            e.putBoolean("silent", o.optBoolean("silent", false))
            e.putString("my_number", o.optString("my_number", ""))
            e.putString("theme", o.optString("theme", "cyan"))
            e.apply()
            true
        } catch (ex: Exception) { false }
    }
}
