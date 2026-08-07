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

    /** Plage horaire du mode strict (-1 = permanent). */
    var strictStart: Int
        get() = prefs(appCtx).getInt("strict_start", -1)
        set(v) { prefs(appCtx).edit().putInt("strict_start", v).apply() }

    var strictEnd: Int
        get() = prefs(appCtx).getInt("strict_end", -1)
        set(v) { prefs(appCtx).edit().putInt("strict_end", v).apply() }

    /**
     * L'instant présent tombe-t-il dans la plage ? Une plage qui franchit
     * minuit (22h → 7h) est gérée correctement.
     */
    fun withinSchedule(start: Int, end: Int, days: Set<Int>): Boolean {
        if (start < 0 || end < 0) return true          // aucun horaire : toujours actif
        val c = java.util.Calendar.getInstance()
        val minute = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
                     c.get(java.util.Calendar.MINUTE)
        val day = c.get(java.util.Calendar.DAY_OF_WEEK)
        val inRange = if (start <= end) minute in start until end
                      else minute >= start || minute < end   // franchit minuit
        if (!inRange) return false
        if (days.isEmpty()) return true
        // Pour une plage nocturne, le jour de référence est celui du début
        val refDay = if (start > end && minute < end)
            (if (day == 1) 7 else day - 1) else day
        return refDay in days
    }

    /** Une liste agit-elle en ce moment ? */
    fun isListActiveNow(l: NamedList): Boolean =
        l.enabled && withinSchedule(l.schedStart, l.schedEnd, l.schedDays)

    fun strictActiveNow(): Boolean =
        allowOnlyMode && withinSchedule(strictStart, strictEnd, emptySet())

    /** Bloque tout numéro absent du carnet d'adresses. */
    var blockUnknown: Boolean
        get() = prefs(appCtx).getBoolean("block_unknown", false)
        set(v) { prefs(appCtx).edit().putBoolean("block_unknown", v).apply() }

    /** Mode strict : tout est bloqué sauf ce qui est explicitement autorisé. */
    var allowOnlyMode: Boolean
        get() = prefs(appCtx).getBoolean("allow_only", false)
        set(v) { prefs(appCtx).edit().putBoolean("allow_only", v).apply() }

    /** En mode strict, les contacts enregistrés restent autorisés. */
    var allowContacts: Boolean
        get() = prefs(appCtx).getBoolean("allow_contacts", true)
        set(v) { prefs(appCtx).edit().putBoolean("allow_contacts", v).apply() }

    // ---- Listes nommées ----
    /** Liste personnalisée : soit d'autorisation, soit de blocage. */
    data class NamedList(
        val id: String,
        var name: String,
        var type: String,               // "allow" ou "block"
        var enabled: Boolean,
        val numbers: MutableSet<String>,
        var schedStart: Int = -1,       // minute de début (-1 = sans horaire)
        var schedEnd: Int = -1,         // minute de fin
        var schedDays: MutableSet<Int> = HashSet()  // 1=dimanche … 7=samedi
    )

    fun namedLists(): MutableList<NamedList> {
        val out = mutableListOf<NamedList>()
        try {
            val arr = JSONArray(prefs(appCtx).getString("named_lists", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val nums = HashSet<String>()
                val na = o.optJSONArray("numbers") ?: JSONArray()
                for (j in 0 until na.length()) nums.add(na.getString(j))
                val days = HashSet<Int>()
                val da = o.optJSONArray("days") ?: JSONArray()
                for (j in 0 until da.length()) days.add(da.getInt(j))
                out.add(NamedList(
                    o.optString("id"), o.optString("name"),
                    o.optString("type", "block"), o.optBoolean("enabled", true), nums,
                    o.optInt("start", -1), o.optInt("end", -1), days))
            }
        } catch (_: Exception) {}
        return out
    }

    fun saveNamedLists(lists: List<NamedList>) {
        val arr = JSONArray()
        for (l in lists) {
            arr.put(JSONObject()
                .put("id", l.id)
                .put("name", l.name)
                .put("type", l.type)
                .put("enabled", l.enabled)
                .put("numbers", JSONArray(l.numbers.toList()))
                .put("start", l.schedStart)
                .put("end", l.schedEnd)
                .put("days", JSONArray(l.schedDays.toList())))
        }
        prefs(appCtx).edit().putString("named_lists", arr.toString()).apply()
    }

    fun newListId(): String = "L" + System.currentTimeMillis()

    /** Le numéro figure-t-il dans le carnet d'adresses du téléphone ? */
    fun isInContacts(rawNumber: String?): Boolean {
        val n = rawNumber ?: return false
        if (n.isBlank()) return false
        return try {
            if (appCtx.checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return false
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(n))
            appCtx.contentResolver.query(uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup._ID),
                null, null, null)?.use { it.count > 0 } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /** Prévenir discrètement lorsqu'un appel est bloqué. */
    var notifyBlocked: Boolean
        get() = prefs(appCtx).getBoolean("notify_blocked", true)
        set(v) { prefs(appCtx).edit().putBoolean("notify_blocked", v).apply() }

    // ---- Brouillons ----
    /** Texte en cours de rédaction, conservé par conversation. */
    fun draft(address: String): String =
        prefs(appCtx).getString("draft_" + normalize(address), "") ?: ""

    fun setDraft(address: String, text: String) {
        val key = "draft_" + normalize(address)
        val e = prefs(appCtx).edit()
        if (text.isBlank()) e.remove(key) else e.putString(key, text)
        e.apply()
    }

    // ---- Comptes email ----
    /** Un compte d'envoi : nom affiché, serveur, identifiants. */
    data class MailAccount(
        val id: String,
        var label: String,
        var host: String,
        var port: String,
        var user: String,
        var pass: String,
        var fromName: String = ""
    )

    fun mailAccounts(): MutableList<MailAccount> {
        val out = mutableListOf<MailAccount>()
        try {
            val arr = JSONArray(prefs(appCtx).getString("mail_accounts", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(MailAccount(
                    o.optString("id"), o.optString("label"), o.optString("host"),
                    o.optString("port", "465"), o.optString("user"),
                    o.optString("pass"), o.optString("from_name")))
            }
        } catch (_: Exception) {}

        // Reprise de l'ancien réglage unique, pour ne rien perdre
        if (out.isEmpty()) {
            val old = appCtx.getSharedPreferences("email_cfg", Context.MODE_PRIVATE)
            val h = old.getString("host", "") ?: ""
            val u = old.getString("user", "") ?: ""
            if (h.isNotBlank() && u.isNotBlank()) {
                out.add(MailAccount(newAccountId(), u.substringAfter("@"), h,
                    old.getString("port", "465") ?: "465", u,
                    old.getString("pass", "") ?: ""))
                saveMailAccounts(out)
            }
        }
        return out
    }

    fun saveMailAccounts(list: List<MailAccount>) {
        val arr = JSONArray()
        for (a in list) {
            arr.put(JSONObject()
                .put("id", a.id).put("label", a.label).put("host", a.host)
                .put("port", a.port).put("user", a.user).put("pass", a.pass)
                .put("from_name", a.fromName))
        }
        prefs(appCtx).edit().putString("mail_accounts", arr.toString()).apply()
    }

    fun newAccountId(): String = "M" + System.currentTimeMillis()

    /** Compte utilisé par défaut à l'ouverture du composeur. */
    var defaultMailAccount: String
        get() = prefs(appCtx).getString("mail_default", "") ?: ""
        set(v) { prefs(appCtx).edit().putString("mail_default", v).apply() }

    // ---- Style du clavier ----
    /** Forme des touches : auto (suit le thème), orb, tuile ou hud. */
    var keypadShape: String
        get() = prefs(appCtx).getString("keypad_shape", "auto") ?: "auto"
        set(v) { prefs(appCtx).edit().putString("keypad_shape", v).apply() }

    /** Intensité du halo des touches, de 0 à 200 (100 = normal). */
    var keypadGlow: Int
        get() = prefs(appCtx).getInt("keypad_glow", 100)
        set(v) { prefs(appCtx).edit().putInt("keypad_glow", v).apply() }

    /** Couleur des chiffres : auto (clair) ou accent du thème. */
    var keypadDigitAccent: Boolean
        get() = prefs(appCtx).getBoolean("keypad_digit_accent", false)
        set(v) { prefs(appCtx).edit().putBoolean("keypad_digit_accent", v).apply() }

    // ---- Faux appel ----
    var fakeCallName: String
        get() = prefs(appCtx).getString("fake_name", "") ?: ""
        set(v) { prefs(appCtx).edit().putString("fake_name", v).apply() }

    var fakeCallNumber: String
        get() = prefs(appCtx).getString("fake_number", "") ?: ""
        set(v) { prefs(appCtx).edit().putString("fake_number", v).apply() }

    // ---- Assistant IA ----
    var aiKey: String
        get() = prefs(appCtx).getString("ai_key", "") ?: ""
        set(v) { prefs(appCtx).edit().putString("ai_key", v).apply() }

    var aiModel: String
        get() {
            val m = prefs(appCtx).getString("ai_model", "") ?: ""
            // Les identifiants figés finissent par être retirés par Google :
            // on repart de l'alias courant plutôt que d'échouer.
            val obsolete = listOf("gemini-2.5-flash", "gemini-2.0-flash",
                "gemini-1.5-flash", "gemini-pro")
            return if (m in obsolete) "" else m
        }
        set(v) { prefs(appCtx).edit().putString("ai_model", v).apply() }

    /** Ton demandé aux suggestions : amical, neutre, professionnel... */
    var aiTone: String
        get() = prefs(appCtx).getString("ai_tone", "amical et naturel")
            ?: "amical et naturel"
        set(v) { prefs(appCtx).edit().putString("ai_tone", v).apply() }

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
        get() = prefs(appCtx).getInt("custom_dim", 25)
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

        val lists = namedLists().filter { isListActiveNow(it) }

        // 1. Une liste d'autorisation active laisse toujours passer l'appel
        for (l in lists) {
            if (l.type == "allow" && n in l.numbers) return null
        }

        val known = isInContacts(rawNumber)

        // 2. Mode strict : tout est refusé sauf autorisations explicites
        if (strictActiveNow()) {
            if (allowContacts && known) return null
            return "Hors liste autorisée"
        }

        // 3. Blocage des numéros absents du carnet d'adresses
        if (blockUnknown && !known) return "Numéro inconnu"

        // 4. Listes de blocage nommées
        for (l in lists) {
            if (l.type == "block" && n in l.numbers) return "Liste " + l.name
        }

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

    // ---- Sauvegarde complète ----
    /**
     * Exporte l'intégralité de la configuration : filtres, listes nommées,
     * comptes email, apparence et assistant. Le fichier obtenu suffit à
     * retrouver son installation à l'identique.
     */
    fun exportJson(): String {
        val o = JSONObject()
        o.put("version", 2)
        o.put("exported_at", System.currentTimeMillis())

        // Filtres
        o.put("numbers", JSONArray(numbers().toList()))
        o.put("prefixes", JSONArray(prefixes().toList()))
        o.put("enabled_lists", JSONArray(enabledLists().toList()))
        o.put("hidden", blockHidden)
        o.put("international", blockInternational)
        o.put("neighbors", blockNeighbors)
        o.put("silent", silentMode)
        o.put("my_number", myNumber)
        o.put("block_unknown", blockUnknown)
        o.put("allow_only", allowOnlyMode)
        o.put("allow_contacts", allowContacts)
        o.put("strict_start", strictStart)
        o.put("strict_end", strictEnd)

        // Listes nommées, horaires compris
        val lists = JSONArray()
        for (l in namedLists()) {
            lists.put(JSONObject()
                .put("id", l.id).put("name", l.name).put("type", l.type)
                .put("enabled", l.enabled)
                .put("numbers", JSONArray(l.numbers.toList()))
                .put("start", l.schedStart).put("end", l.schedEnd)
                .put("days", JSONArray(l.schedDays.toList())))
        }
        o.put("named_lists", lists)

        // Comptes email
        val mails = JSONArray()
        for (a in mailAccounts()) {
            mails.put(JSONObject()
                .put("id", a.id).put("label", a.label).put("host", a.host)
                .put("port", a.port).put("user", a.user).put("pass", a.pass)
                .put("from_name", a.fromName))
        }
        o.put("mail_accounts", mails)
        o.put("mail_default", defaultMailAccount)

        // Apparence
        o.put("theme", theme)
        o.put("custom_accent", customAccent)
        o.put("custom_shape", customShape)
        o.put("custom_image", customImage)
        o.put("custom_dim", customDim)
        o.put("keypad_shape", keypadShape)
        o.put("keypad_glow", keypadGlow)
        o.put("keypad_digit_accent", keypadDigitAccent)

        // Assistant et divers
        o.put("ai_key", aiKey)
        o.put("ai_model", aiModel)
        o.put("ai_tone", aiTone)
        o.put("fake_name", fakeCallName)
        o.put("fake_number", fakeCallNumber)

        return o.toString(2)
    }

    /** Restaure une sauvegarde. Les champs absents gardent leur valeur actuelle. */
    fun importJson(json: String): Boolean {
        return try {
            val o = JSONObject(json)
            val e = prefs(appCtx).edit()

            fun strSet(key: String, pref: String) {
                val a = o.optJSONArray(key) ?: return
                val set = HashSet<String>()
                for (i in 0 until a.length()) set.add(a.getString(i))
                e.putStringSet(pref, set)
            }
            strSet("numbers", "numbers")
            strSet("prefixes", "prefixes")
            strSet("enabled_lists", "enabled_lists")

            e.putBoolean("hidden", o.optBoolean("hidden", blockHidden))
            e.putBoolean("international", o.optBoolean("international", blockInternational))
            e.putBoolean("neighbors", o.optBoolean("neighbors", blockNeighbors))
            e.putBoolean("silent", o.optBoolean("silent", silentMode))
            e.putString("my_number", o.optString("my_number", myNumber))
            e.putBoolean("block_unknown", o.optBoolean("block_unknown", blockUnknown))
            e.putBoolean("allow_only", o.optBoolean("allow_only", allowOnlyMode))
            e.putBoolean("allow_contacts", o.optBoolean("allow_contacts", allowContacts))
            e.putInt("strict_start", o.optInt("strict_start", strictStart))
            e.putInt("strict_end", o.optInt("strict_end", strictEnd))

            if (o.has("named_lists")) e.putString("named_lists",
                o.getJSONArray("named_lists").toString())
            if (o.has("mail_accounts")) e.putString("mail_accounts",
                o.getJSONArray("mail_accounts").toString())
            e.putString("mail_default", o.optString("mail_default", defaultMailAccount))

            e.putString("theme", o.optString("theme", theme))
            e.putInt("custom_accent", o.optInt("custom_accent", customAccent))
            e.putString("custom_shape", o.optString("custom_shape", customShape))
            e.putString("custom_image", o.optString("custom_image", customImage))
            e.putInt("custom_dim", o.optInt("custom_dim", customDim))
            e.putString("keypad_shape", o.optString("keypad_shape", keypadShape))
            e.putInt("keypad_glow", o.optInt("keypad_glow", keypadGlow))
            e.putBoolean("keypad_digit_accent",
                o.optBoolean("keypad_digit_accent", keypadDigitAccent))

            e.putString("ai_key", o.optString("ai_key", aiKey))
            e.putString("ai_model", o.optString("ai_model", aiModel))
            e.putString("ai_tone", o.optString("ai_tone", aiTone))
            e.putString("fake_name", o.optString("fake_name", fakeCallName))
            e.putString("fake_number", o.optString("fake_number", fakeCallNumber))

            e.apply()
            true
        } catch (ex: Exception) { false }
    }
}
