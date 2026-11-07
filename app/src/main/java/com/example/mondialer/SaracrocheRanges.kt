package com.example.mondialer

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** Listes de plages Saracroche : source communautaire, jamais preuve d'identite. */
object SaracrocheRanges {
    private const val URL_LIST = "https://saracroche.org/api/v1/lists/french-list-arcep-operators"
    private const val STORAGE = "saracroche_patterns_v921.txt"
    private const val PREF = "saracroche_ranges_v921"
    @Volatile private var cache: Set<String>? = null
    private val lock = Any()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    fun enabled(ctx: Context): Boolean = prefs(ctx).getBoolean("enabled", false)
    fun setEnabled(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean("enabled", value).apply()
    fun lastUpdate(ctx: Context): Long = prefs(ctx).getLong("updated", 0L)
    fun count(ctx: Context): Int = read(ctx).size

    private fun read(ctx: Context): Set<String> {
        cache?.let { return it }
        synchronized(lock) {
            cache?.let { return it }
            val out = try {
                ctx.openFileInput(STORAGE).bufferedReader().useLines { seq ->
                    seq.filter { it.matches(Regex("0[0-9]{2,9}")) }.toSet()
                }
            } catch (_: Exception) { emptySet() }
            cache = out
            return out
        }
    }

    /** L'appelant correspond à une plage importée ; jamais de requête au moment de l'appel. */
    fun matches(ctx: Context, number: String?): Boolean {
        if (!enabled(ctx)) return false
        val n = BlockRulesStore.normalize(number)
        if (n.length != 10) return false
        val ranges = read(ctx)
        for (len in 3..10) if (n.take(len) in ranges) return true
        return false
    }

    /** Telechargement volontaire; si la source change de format, ancienne liste conservee. */
    fun update(ctx: Context): Int {
        val conn = URL(URL_LIST).openConnection() as HttpURLConnection
        conn.connectTimeout = 9000
        conn.readTimeout = 12000
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "AnarchiePhone/9.21")
        try {
            require(conn.responseCode == 200) { "Source indisponible (HTTP ${conn.responseCode})" }
            val output = java.io.ByteArrayOutputStream()
            conn.inputStream.use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    require(output.size() + n <= 3_000_000) { "Reponse trop volumineuse" }
                    output.write(buffer, 0, n)
                }
            }
            val arr = JSONObject(output.toString("UTF-8")).optJSONArray("patterns")
                ?: error("Format JSON non reconnu")
            val found = HashSet<String>()
            for (i in 0 until arr.length()) {
                val entry = arr.optJSONObject(i) ?: continue
                val p = entry.optString("pattern", "").trim()
                // Seulement les jokers de FIN : pas de regex, ni de numero incomplet libre.
                if (!p.matches(Regex("[0-9]+#+"))) continue
                val converted = if (p.startsWith("33")) "0" + p.drop(2) else p
                if (converted.length != 10) continue
                val prefix = converted.takeWhile { it.isDigit() }
                if (prefix.length in 3..9) found.add(prefix)
                require(found.size <= 50000) { "Trop de plages" }
            }
            require(found.size >= 5) { "Aucune plage compatible : ancienne version conservee" }
            val file = ctx.getFileStreamPath(STORAGE)
            val tmp = ctx.getFileStreamPath("$STORAGE.tmp")
            tmp.writeText(found.sorted().joinToString("\n") + "\n", Charsets.UTF_8)
            require(tmp.renameTo(file) || run {
                tmp.copyTo(file, overwrite = true); tmp.delete(); true
            }) { "Sauvegarde impossible" }
            synchronized(lock) { cache = found.toSet() }
            prefs(ctx).edit().putLong("updated", System.currentTimeMillis()).apply()
            return found.size
        } finally { conn.disconnect() }
    }
}
