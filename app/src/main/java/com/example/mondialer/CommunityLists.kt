package com.example.mondialer

import android.content.Context
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/** Listes locales importees volontairement, jamais de telechargement implicite. */
object CommunityLists {
    private const val PREF = "community_lists_v918"
    private val ids = listOf("saracroche", "nocall")
    data class Hit(val source: String, val reports: Int)
    private val data = ConcurrentHashMap<String, Map<String, Int>>()
    private val lock = Any()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    fun mode(ctx: Context): String = prefs(ctx).getString("mode", "warn") ?: "warn"
    fun setMode(ctx: Context, value: String) {
        require(value == "warn" || value == "block")
        prefs(ctx).edit().putString("mode", value).apply()
    }
    fun enabled(ctx: Context, id: String): Boolean =
        id in ids && prefs(ctx).getBoolean("enabled_" + id, false)
    fun setEnabled(ctx: Context, id: String, value: Boolean) {
        require(id in ids)
        prefs(ctx).edit().putBoolean("enabled_" + id, value).apply()
    }
    fun count(ctx: Context, id: String): Int = read(ctx, id).size
    fun lastImport(ctx: Context, id: String): Long = prefs(ctx).getLong("updated_" + id, 0L)

    private fun read(ctx: Context, id: String): Map<String, Int> {
        if (id !in ids) return emptyMap()
        data[id]?.let { return it }
        synchronized(lock) {
            data[id]?.let { return it }
            val rows = HashMap<String, Int>()
            try {
                ctx.openFileInput("community_$id.tsv").bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val fields = line.split('\t')
                        if (fields.size == 2) fields[1].toIntOrNull()?.let { rows[fields[0]] = it }
                    }
                }
            } catch (_: Exception) { }
            data[id] = rows
            return rows
        }
    }

    fun match(ctx: Context, raw: String?): Hit? {
        val n = BlockRulesStore.normalize(raw)
        if (n.length < 9) return null
        var best: Hit? = null
        for (id in ids) {
            if (!enabled(ctx, id)) continue
            val count = read(ctx, id)[n] ?: continue
            if (best == null || count > best.reports) best = Hit(id, count)
        }
        return best
    }

    /** CSV avec colonne numero et eventuellement signalements; taille bornee. */
    fun importCsv(ctx: Context, id: String, input: InputStream): Int {
        require(id in ids)
        val rows = HashMap<String, Int>()
        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
            var headerChecked = false
            var numIndex = 0
            var countIndex = -1
            var delimiter = ';'
            var bytes = 0L
            var lines = 0
            while (true) {
                val line = reader.readLine() ?: break
                bytes += line.length * 2L
                lines++
                require(bytes <= 10_000_000L && lines <= 180_000) { "Fichier trop volumineux" }
                if (line.isBlank() || line.startsWith('#')) continue
                if (!headerChecked) {
                    delimiter = listOf(';', ',', '\t').maxByOrNull { sep -> line.count { it == sep } } ?: ';'
                    val first = line.split(delimiter).map { it.trim().trim('"').lowercase() }
                    val indexes = listOf("number", "numero", "numéro", "phone", "telephone", "téléphone", "e164")
                    val column = first.indexOfFirst { it in indexes }
                    val reports = listOf("reports", "report_count", "signalements", "count", "votes")
                    countIndex = first.indexOfFirst { it in reports }
                    if (column >= 0) { numIndex = column; headerChecked = true; continue }
                    headerChecked = true
                }
                val cols = line.split(delimiter)
                if (numIndex >= cols.size) continue
                val raw = cols[numIndex].trim().trim('"', '\'', ' ')
                val n = BlockRulesStore.normalize(raw)
                if (n.length !in 9..12) continue
                // Pas de plages, regex ou prefixes: uniquement des numeros exacts.
                if (raw.any { it.isLetter() || it == '*' }) continue
                val reports = if (countIndex >= 0 && countIndex < cols.size)
                    cols[countIndex].trim().trim('"').toIntOrNull()?.coerceIn(0, 1000000) ?: 0
                    else 0
                rows[n] = maxOf(rows[n] ?: 0, reports)
                require(rows.size <= 120_000) { "Trop de numeros" }
            }
        }
        require(rows.isNotEmpty()) { "Aucun numero valide trouve dans le CSV" }
        val output = rows.entries.joinToString("\n") { "${it.key}\t${it.value}" }
        synchronized(lock) {
            ctx.openFileOutput("community_$id.tsv", Context.MODE_PRIVATE).bufferedWriter().use {
                it.write(output)
                it.newLine()
            }
            data[id] = rows.toMap()
            prefs(ctx).edit().putLong("updated_" + id, System.currentTimeMillis()).apply()
        }
        return rows.size
    }
}
