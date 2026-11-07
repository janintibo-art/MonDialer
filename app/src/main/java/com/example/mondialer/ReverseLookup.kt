package com.example.mondialer

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Annuaire inversé facultatif. Pas de fournisseur présélectionné, pas de requête silencieuse. */
object ReverseLookup {
    private const val PREFS = "reverse_lookup_v1"
    private val executor = Executors.newSingleThreadExecutor()
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(ctx: Context) = prefs(ctx).getBoolean("enabled", false)
    fun endpoint(ctx: Context) = prefs(ctx).getString("endpoint", "").orEmpty()

    fun configure(ctx: Context, enabled: Boolean, endpoint: String) {
        val url = endpoint.trim()
        // HTTPS + variable obligatoire : chaque demande cible un numéro précis.
        require(!enabled || (url.startsWith("https://") && url.contains("{number}"))) {
            "Adresse HTTPS avec {number} requise"
        }
        prefs(ctx).edit().putBoolean("enabled", enabled).putString("endpoint", url).apply()
    }

    fun cached(ctx: Context, number: String?): String? {
        val n = BlockRulesStore.normalize(number)
        if (n.length < 8) return null
        val p = prefs(ctx)
        val checked = p.getLong("ts_" + n, 0L)
        val expiry = if (p.getString("name_" + n, "").isNullOrBlank()) 24L * 3600_000 else 7L * 24 * 3600_000
        if (checked == 0L || System.currentTimeMillis() - checked > expiry) return null
        return p.getString("name_" + n, "")?.takeIf { it.isNotBlank() }
    }

    fun findAsync(ctx: Context, number: String, onResult: (String?) -> Unit) {
        val app = ctx.applicationContext
        val n = BlockRulesStore.normalize(number)
        val template = endpoint(app)
        if (!enabled(app) || n.length < 8 || !template.startsWith("https://") || !template.contains("{number}")) return
        val p = prefs(app)
        val last = p.getLong("ts_" + n, 0L)
        val old = p.getString("name_" + n, "").orEmpty()
        val ttl = if (old.isBlank()) 24L * 3600_000 else 7L * 24 * 3600_000
        if (last > 0L && System.currentTimeMillis() - last < ttl) return

        executor.execute {
            var result: String? = null
            var complete = false
            try {
                val full = template.replace("{number}", Uri.encode(n))
                val address = URL(full)
                if (address.protocol != "https") return@execute
                val conn = address.openConnection() as HttpURLConnection
                try {
                    conn.instanceFollowRedirects = false
                    conn.connectTimeout = 2200
                    conn.readTimeout = 2200
                    conn.setRequestProperty("Accept", "application/json")
                    conn.setRequestProperty("User-Agent", "AnarchiePhone/9.15")
                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().use { it.readText().take(16384) }
                        val obj = JSONObject(body)
                        result = obj.optString("name").trim().take(100).takeIf { it.isNotBlank() }
                        complete = true
                    } else if (conn.responseCode == 404) complete = true
                } finally { conn.disconnect() }
            } catch (_: Exception) { /* Réseau indisponible : pas de cache négatif */ }
            if (complete) {
                p.edit().putLong("ts_" + n, System.currentTimeMillis())
                    .putString("name_" + n, result.orEmpty()).apply()
                android.os.Handler(android.os.Looper.getMainLooper()).post { onResult(result) }
            }
        }
    }
}
