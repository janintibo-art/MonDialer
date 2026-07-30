package com.example.mondialer

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Couche d'accès aux fournisseurs d'IA. Trois services sont pris en charge :
 * Gemini (format propre à Google), Groq et DeepSeek (format compatible OpenAI).
 * La clé appartient à l'utilisateur et reste sur l'appareil.
 */
object AiClient {

    class AiException(message: String) : Exception(message)

    const val DEFAULT_MODEL = "gemini-2.5-flash"
    const val KEY_URL = "aistudio.google.com/apikey"

    /**
     * Envoie une consigne et renvoie la réponse brute du modèle.
     * À appeler depuis un fil d'arrière-plan.
     */
    fun ask(system: String, user: String): String {
        val key = BlockRulesStore.aiKey
        val model = BlockRulesStore.aiModel.ifBlank { DEFAULT_MODEL }
        if (key.isBlank()) throw AiException("clé manquante")
        return askGemini(key, model, system, user)
    }

    private fun post(urlStr: String, headers: Map<String, String>, body: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        conn.disconnect()

        if (code !in 200..299) {
            val detail = try {
                JSONObject(text).optJSONObject("error")?.optString("message") ?: ""
            } catch (e: Exception) { "" }
            throw AiException(when (code) {
                401, 403 -> "clé refusée"
                429 -> "quota atteint, réessayez plus tard"
                else -> "erreur $code ${detail.take(80)}"
            })
        }
        return text
    }

    private fun askGemini(key: String, model: String, system: String, user: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
                  "$model:generateContent?key=$key"
        val body = JSONObject()
            .put("system_instruction", JSONObject()
                .put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", user)))))
            .toString()

        val res = JSONObject(post(url, emptyMap(), body))
        val candidates = res.optJSONArray("candidates")
            ?: throw AiException("réponse vide")
        if (candidates.length() == 0) throw AiException("réponse vide")
        val parts = candidates.getJSONObject(0)
            .optJSONObject("content")?.optJSONArray("parts")
            ?: throw AiException("réponse vide")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
        return sb.toString()
    }

    /**
     * Analyse un message suspect et renvoie un verdict lisible.
     * Le modèle est cadré pour répondre court et sans jargon.
     */
    fun analyzeScam(message: String): String {
        val system = buildString {
            append("Tu es un expert francophone en fraudes par SMS et email. ")
            append("Analyse le message fourni et réponds en français, en 4 lignes maximum, ")
            append("dans ce format exact :\n")
            append("VERDICT : sûr / douteux / arnaque\n")
            append("POURQUOI : les indices concrets relevés\n")
            append("PIÈGE : ce que l'expéditeur cherche à obtenir\n")
            append("À FAIRE : la conduite à tenir en une phrase\n")
            append("Sois direct. Signale notamment les liens raccourcis, l'urgence artificielle, ")
            append("les fautes, les demandes de données bancaires ou de codes, ")
            append("les usurpations d'organismes connus (banque, impôts, colis, CPF).")
        }
        return ask(system, message).trim()
    }

    /**
     * Demande trois réponses possibles à un message, et les sépare.
     * Le modèle est prié de numéroter, ce qui rend l'analyse simple et fiable.
     */
    fun suggestReplies(conversation: String, kind: String): List<String> {
        val tone = BlockRulesStore.aiTone
        val system = buildString {
            append("Tu aides un utilisateur francophone à répondre à ")
            append(if (kind == "mail") "un email." else "un SMS.")
            append(" Propose exactement 3 réponses possibles, différentes entre elles ")
            append("(une brève, une neutre, une plus développée). ")
            append("Ton souhaité : $tone. ")
            append("Écris uniquement les réponses, chacune sur une ligne commençant par ")
            append("1) puis 2) puis 3). Pas de commentaire, pas de titre, pas de guillemets. ")
            append("Reste naturel, comme un vrai message écrit par la personne.")
        }
        val raw = ask(system, conversation)
        val out = mutableListOf<String>()
        val regex = Regex("""^\s*[123][)\.\-]\s*""")
        for (line in raw.lines()) {
            val t = line.trim()
            if (t.isEmpty()) continue
            if (regex.containsMatchIn(t)) out.add(t.replace(regex, "").trim())
            else if (out.isNotEmpty()) out[out.size - 1] = out.last() + " " + t
        }
        if (out.isEmpty() && raw.isNotBlank()) out.add(raw.trim())
        return out.filter { it.isNotBlank() }.take(3)
    }
}
