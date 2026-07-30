package com.example.mondialer

import java.io.ByteArrayOutputStream

/**
 * Encodeur/décodeur minimal des PDU MMS (WSP/MMS Encapsulation).
 * Suffisant pour : notifications entrantes, récupération (retrieve-conf),
 * et composition d'envois (send-req) multipart.
 */
object MmsCodec {

    class Part(val mime: String, val name: String?, val data: ByteArray)
    class Retrieved(val from: String?, val parts: List<Part>)

    // ---- uintvar ----
    private fun readUintvar(b: ByteArray, start: Int): Pair<Int, Int> {
        var i = start
        var v = 0
        while (i < b.size) {
            val c = b[i].toInt() and 0xFF
            v = (v shl 7) or (c and 0x7F)
            i++
            if (c and 0x80 == 0) break
        }
        return Pair(v, i)
    }

    private fun writeUintvar(o: ByteArrayOutputStream, value: Int) {
        var v = value
        val tmp = ArrayList<Int>()
        tmp.add(v and 0x7F)
        v = v ushr 7
        while (v > 0) {
            tmp.add((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        for (i in tmp.indices.reversed()) o.write(tmp[i])
    }

    private fun readText(b: ByteArray, start: Int): Pair<String, Int> {
        var i = start
        // Quote éventuel des textes commençant par un octet >= 0x80
        if (i < b.size && (b[i].toInt() and 0xFF) == 0x7F) i++
        val sb = StringBuilder()
        while (i < b.size && b[i].toInt() != 0) {
            sb.append((b[i].toInt() and 0xFF).toChar())
            i++
        }
        return Pair(sb.toString(), i + 1)
    }

    /** Saute une valeur d'en-tête WSP quelle que soit sa forme. */
    private fun skipValue(b: ByteArray, start: Int): Int {
        if (start >= b.size) return b.size
        val v = b[start].toInt() and 0xFF
        return when {
            v >= 0x80 -> start + 1                       // short integer / token
            v == 0x1F -> {                                // value-length uintvar
                val (len, ni) = readUintvar(b, start + 1)
                ni + len
            }
            v < 0x1F -> start + 1 + v                     // value-length court
            else -> readText(b, start).second             // texte
        }
    }

    private fun wellKnownMime(code: Int): String = when (code) {
        0x02 -> "text/html"
        0x03 -> "text/plain"
        0x1D -> "image/gif"
        0x1E -> "image/jpeg"
        0x20 -> "image/png"
        0x23 -> "application/vnd.wap.multipart.mixed"
        0x33 -> "application/vnd.wap.multipart.related"
        else -> "application/octet-stream"
    }

    /** Lit un Content-Type de partie ; renvoie (mime, nomFichier?, indexFin). */
    private fun readContentType(b: ByteArray, start: Int, end: Int): Triple<String, String?, Int> {
        if (start >= end) return Triple("application/octet-stream", null, end)
        val v = b[start].toInt() and 0xFF
        return when {
            v >= 0x80 -> Triple(wellKnownMime(v and 0x7F), null, start + 1)
            v in 0x20..0x7E -> {
                val (t, ni) = readText(b, start)
                Triple(t, null, ni)
            }
            else -> {
                // Forme générale : value-length puis media + paramètres
                val (len, ni) = if (v == 0x1F) readUintvar(b, start + 1)
                                else Pair(v, start + 1)
                val innerEnd = (ni + len).coerceAtMost(end)
                var mime = "application/octet-stream"
                var name: String? = null
                var i = ni
                if (i < innerEnd) {
                    val m = b[i].toInt() and 0xFF
                    if (m >= 0x80) { mime = wellKnownMime(m and 0x7F); i++ }
                    else { val (t, x) = readText(b, i); mime = t; i = x }
                }
                // Paramètres : chercher Name (0x85) ou Filename (0x98)
                while (i < innerEnd) {
                    val p = b[i].toInt() and 0xFF
                    if (p == 0x85 || p == 0x98) {
                        val (t, x) = readText(b, i + 1)
                        name = t; i = x
                    } else {
                        i = skipValue(b, i + 1)
                    }
                }
                Triple(mime, name, innerEnd)
            }
        }
    }

    /** Extrait l'URL de téléchargement d'une notification MMS entrante. */
    fun parseNotificationLocation(b: ByteArray): String? {
        var i = 0
        try {
            while (i < b.size - 1) {
                val h = b[i].toInt() and 0xFF
                if (h == 0x83) { // X-Mms-Content-Location
                    return readText(b, i + 1).first
                }
                if (h >= 0x80) i = skipValue(b, i + 1) else i++
            }
        } catch (_: Exception) {}
        return null
    }

    /** Analyse un retrieve-conf téléchargé : expéditeur + parties. */
    fun parseRetrieve(b: ByteArray): Retrieved? {
        try {
            var i = 0
            var from: String? = null
            var bodyStart = -1
            while (i < b.size) {
                val h = b[i].toInt() and 0xFF
                if (h < 0x80) return null
                i++
                when (h) {
                    0x84 -> { // Content-Type du message : le corps suit
                        val (_, _, ni) = readContentType(b, i, b.size)
                        bodyStart = ni
                        i = ni
                    }
                    0x89 -> { // From
                        val v = b[i].toInt() and 0xFF
                        val (len, ni) = if (v == 0x1F) readUintvar(b, i + 1)
                                        else Pair(v, i + 1)
                        val end = ni + len
                        var j = ni
                        if (j < end && (b[j].toInt() and 0xFF) == 0x80) {
                            j++
                            // Encoded-string : éventuellement préfixée charset
                            val c = b[j].toInt() and 0xFF
                            if (c < 0x20) {
                                val (l2, n2) = if (c == 0x1F) readUintvar(b, j + 1)
                                               else Pair(c, j + 1)
                                var k = n2
                                if (k < n2 + l2 && (b[k].toInt() and 0xFF) >= 0x80) k++
                                from = readText(b, k).first
                            } else {
                                from = readText(b, j).first
                            }
                        }
                        i = end
                    }
                    else -> i = skipValue(b, i)
                }
                if (bodyStart >= 0) break
            }
            if (bodyStart < 0 || bodyStart >= b.size) return Retrieved(from, emptyList())

            // Multipart
            val parts = mutableListOf<Part>()
            var p = bodyStart
            val (n, p1) = readUintvar(b, p)
            p = p1
            repeat(n.coerceAtMost(20)) {
                if (p >= b.size) return@repeat
                val (hLen, p2) = readUintvar(b, p)
                val (dLen, p3) = readUintvar(b, p2)
                val hEnd = (p3 + hLen).coerceAtMost(b.size)
                val (mime, ctName, afterCt) = readContentType(b, p3, hEnd)
                // Chercher Content-Location (0x8E) dans le reste des en-têtes
                var name = ctName
                var q = afterCt
                while (q < hEnd) {
                    val hb = b[q].toInt() and 0xFF
                    if (hb == 0x8E) {
                        val (t, x) = readText(b, q + 1)
                        name = t; q = x
                    } else if (hb >= 0x80) {
                        q = skipValue(b, q + 1)
                    } else q++
                }
                val dStart = hEnd
                val dEnd = (dStart + dLen).coerceAtMost(b.size)
                parts.add(Part(mime, name, b.copyOfRange(dStart, dEnd)))
                p = dEnd
            }
            // Nettoyer le nom d'expéditeur ("+336.../TYPE=PLMN")
            val cleanFrom = from?.substringBefore("/")
            return Retrieved(cleanFrom, parts)
        } catch (_: Exception) {
            return null
        }
    }

    /** Compose un send-req multipart prêt à être envoyé par SmsManager. */
    fun composeSendReq(to: String, parts: List<Part>): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(0x8C); o.write(0x80)                                  // Type : send-req
        o.write(0x98)                                                  // Transaction-ID
        o.write("T${System.currentTimeMillis()}".toByteArray()); o.write(0)
        o.write(0x8D); o.write(0x92)                                   // Version MMS 1.2
        o.write(0x89); o.write(1); o.write(0x81)                       // From : inséré par l'opérateur
        o.write(0x97)                                                  // To
        o.write("$to/TYPE=PLMN".toByteArray()); o.write(0)
        o.write(0x84); o.write(0xA3)                                   // Content-Type : multipart.mixed
        writeUintvar(o, parts.size)
        for (part in parts) {
            val hdr = ByteArrayOutputStream()
            hdr.write(part.mime.toByteArray()); hdr.write(0)
            if (part.name != null) {
                hdr.write(0x8E)                                        // Content-Location = nom
                hdr.write(part.name.toByteArray()); hdr.write(0)
            }
            val h = hdr.toByteArray()
            writeUintvar(o, h.size)
            writeUintvar(o, part.data.size)
            o.write(h)
            o.write(part.data)
        }
        return o.toByteArray()
    }
}
