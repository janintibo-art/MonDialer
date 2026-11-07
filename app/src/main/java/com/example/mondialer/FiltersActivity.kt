package com.example.mondialer

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.app.AlertDialog
import android.net.Uri
import android.view.View
import java.text.DateFormat
import java.util.Date

class FiltersActivity : Activity() {

    private lateinit var numbersAdapter: ArrayAdapter<String>
    private lateinit var prefixesAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_filters)

        // Boutons hauts : journal bloqués, export, import
        findViewById<Button>(R.id.btnBlockedLog).setOnClickListener {
            startActivity(Intent(this, BlockedLogActivity::class.java))
        }
        findViewById<Button>(R.id.btnExport).setOnClickListener { exportRules() }
        findViewById<Button>(R.id.btnImport).setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "*/*"
            startActivityForResult(i, 40)
        }

        // V9.18 : listes communautaires facultatives, choix global warn/block.
        setupCommunityLists()

        // Palettes complètes avec aperçu multicolore et thème actif mis en avant.
        val themeOptions = listOf(
            Triple(R.id.themeCyan, "cyan", R.string.theme_cyber_ocean),
            Triple(R.id.themeViolet, "violet", R.string.theme_synthwave),
            Triple(R.id.themeGreen, "green", R.string.theme_toxic_punk),
            Triple(R.id.themeOrange, "orange", R.string.theme_solar_flare),
            Triple(R.id.themeRose, "rose", R.string.theme_candy_pulse),
            Triple(R.id.themeRouge, "rouge", R.string.theme_crimson_ice),
            Triple(R.id.themeOr, "or", R.string.theme_royal_gold),
            Triple(R.id.themeGraphite, "graphite", R.string.theme_obsidian_pro),
            Triple(R.id.themeArdoise, "ardoise", R.string.theme_ardoise_nuit)
        )
        // Glyphe indiquant la famille de formes du thème
        val shapeGlyph = mapOf(
            "cyan" to "●", "green" to "●", "rose" to "●",          // touches rondes
            "violet" to "▢", "orange" to "▢", "or" to "▢",          // tuiles arrondies
            "rouge" to "◤", "graphite" to "◤", "ardoise" to "◤"     // plaques anguleuses
        )
        val density = resources.displayMetrics.density
        themeOptions.forEach { (id, name, labelRes) ->
            val card = findViewById<TextView>(id)
            val active = BlockRulesStore.theme == name
            val g = shapeGlyph[name] ?: ""
            card.text = if (active) "✓ $g ${getString(labelRes)}" else "$g ${getString(labelRes)}"
            card.alpha = if (active) 1f else 0.72f
            card.scaleX = if (active) 1.02f else 0.97f
            card.scaleY = if (active) 1.02f else 0.97f
            card.elevation = (if (active) 14f else 5f) * density
            card.setOnClickListener {
                BlockRulesStore.theme = name
                recreate()
                overridePendingTransition(
                    android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }

        // Carte « Sur mesure » : ouvre l'atelier de personnalisation
        findViewById<TextView>(R.id.themeCustom).let { card ->
            val active = BlockRulesStore.theme == "custom"
            card.text = if (active) "✓ ✎ ${getString(R.string.theme_custom)}"
                        else "✎ ${getString(R.string.theme_custom)}"
            card.alpha = if (active) 1f else 0.72f
            card.scaleX = if (active) 1.02f else 0.97f
            card.scaleY = if (active) 1.02f else 0.97f
            card.elevation = (if (active) 14f else 5f) * density
            card.setOnClickListener {
                startActivity(Intent(this, CustomThemeActivity::class.java))
            }
        }

        // Listes nommées
        findViewById<Button>(R.id.btnLists).setOnClickListener {
            startActivity(Intent(this, ListsActivity::class.java))
        }

        // Filtrage par carnet d'adresses
        val swUnknown = findViewById<Switch>(R.id.swUnknown)
        val swAllowOnly = findViewById<Switch>(R.id.swAllowOnly)
        val swAllowContacts = findViewById<Switch>(R.id.swAllowContacts)
        swUnknown.isChecked = BlockRulesStore.blockUnknown
        swAllowOnly.isChecked = BlockRulesStore.allowOnlyMode
        swAllowContacts.isChecked = BlockRulesStore.allowContacts
        swAllowContacts.isEnabled = BlockRulesStore.allowOnlyMode

        swUnknown.setOnCheckedChangeListener { _, v ->
            BlockRulesStore.blockUnknown = v
            if (v) askContactsPermission()
        }
        swAllowOnly.setOnCheckedChangeListener { _, v ->
            BlockRulesStore.allowOnlyMode = v
            swAllowContacts.isEnabled = v
            if (v) askContactsPermission()
        }
        swAllowContacts.setOnCheckedChangeListener { _, v -> BlockRulesStore.allowContacts = v }

        // Options simples
        val swHidden = findViewById<Switch>(R.id.swHidden)
        val swNeighbors = findViewById<Switch>(R.id.swNeighbors)
        val swInternational = findViewById<Switch>(R.id.swInternational)
        val swSilent = findViewById<Switch>(R.id.swSilent)

        swHidden.isChecked = BlockRulesStore.blockHidden
        swNeighbors.isChecked = BlockRulesStore.blockNeighbors
        swInternational.isChecked = BlockRulesStore.blockInternational
        swSilent.isChecked = BlockRulesStore.silentMode

        swHidden.setOnCheckedChangeListener { _, v -> BlockRulesStore.blockHidden = v }
        swNeighbors.setOnCheckedChangeListener { _, v -> BlockRulesStore.blockNeighbors = v }
        swInternational.setOnCheckedChangeListener { _, v -> BlockRulesStore.blockInternational = v }
        swSilent.setOnCheckedChangeListener { _, v -> BlockRulesStore.silentMode = v }

        val swNotify = findViewById<Switch>(R.id.swNotifyBlocked)
        swNotify.isChecked = BlockRulesStore.notifyBlocked
        swNotify.setOnCheckedChangeListener { _, v -> BlockRulesStore.notifyBlocked = v }

        // Listes prédéfinies
        val container = findViewById<LinearLayout>(R.id.listsContainer)
        for (list in BlockRulesStore.PREDEFINED_LISTS) {
            val sw = Switch(this)
            sw.text = list.label
            sw.setPadding(16, 20, 16, 20)
            sw.isChecked = BlockRulesStore.isListEnabled(list.id)
            sw.setOnCheckedChangeListener { _, v ->
                BlockRulesStore.setListEnabled(list.id, v)
            }
            container.addView(sw)
        }

        // Mon numéro
        val editMy = findViewById<EditText>(R.id.editMyNumber)
        editMy.setText(BlockRulesStore.myNumber)
        findViewById<Button>(R.id.btnSaveMy).setOnClickListener {
            BlockRulesStore.myNumber = editMy.text.toString().trim()
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        }

        // Numéros bloqués
        val listNumbers = findViewById<ListView>(R.id.listNumbers)
        numbersAdapter = ArrayAdapter(this, R.layout.item_one_line,
            BlockRulesStore.numbers().sorted().toMutableList())
        listNumbers.adapter = numbersAdapter
        listNumbers.setOnItemLongClickListener { _, _, pos, _ ->
            val n = numbersAdapter.getItem(pos) ?: return@setOnItemLongClickListener true
            BlockRulesStore.removeNumber(n)
            refreshNumbers()
            Toast.makeText(this, R.string.removed, Toast.LENGTH_SHORT).show()
            true
        }
        val editNum = findViewById<EditText>(R.id.editAddNumber)
        findViewById<Button>(R.id.btnAddNumber).setOnClickListener {
            val n = editNum.text.toString().trim()
            if (n.isNotEmpty()) {
                BlockRulesStore.addNumber(n)
                editNum.setText("")
                refreshNumbers()
            }
        }

        // Préfixes bloqués
        val listPrefixes = findViewById<ListView>(R.id.listPrefixes)
        prefixesAdapter = ArrayAdapter(this, R.layout.item_one_line,
            BlockRulesStore.prefixes().sorted().toMutableList())
        listPrefixes.adapter = prefixesAdapter
        listPrefixes.setOnItemLongClickListener { _, _, pos, _ ->
            val p = prefixesAdapter.getItem(pos) ?: return@setOnItemLongClickListener true
            BlockRulesStore.removePrefix(p)
            refreshPrefixes()
            Toast.makeText(this, R.string.removed, Toast.LENGTH_SHORT).show()
            true
        }
        val editPre = findViewById<EditText>(R.id.editAddPrefix)
        findViewById<Button>(R.id.btnAddPrefix).setOnClickListener {
            val p = editPre.text.toString().trim()
            if (p.isNotEmpty()) {
                BlockRulesStore.addPrefix(p)
                editPre.setText("")
                refreshPrefixes()
            }
        }
    }

    private lateinit var communityInfo: TextView

    private fun setupCommunityLists() {
        // Le layout du filtre est le premier ScrollView de la vue d'activité.
        val scroll = findViewById<View>(R.id.btnBlockedLog).parent.parent as? android.widget.LinearLayout
            ?: return
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 18, 12, 18)
            setBackgroundResource(R.drawable.card_row)
        }
        val heading = TextView(this).apply {
            text = "LISTES COMMUNAUTAIRES · V9.19"
            textSize = 17f
            setTextColor(ThemeRes.color(this@FiltersActivity, R.attr.cNeon))
        }
        box.addView(heading)
        communityInfo = TextView(this).apply {
            textSize = 13f
            setTextColor(ThemeRes.color(this@FiltersActivity, R.attr.cText))
            setPadding(4, 9, 4, 9)
        }
        box.addView(communityInfo)
        val modeBtn = Button(this).apply { text = "Mode : avertissement / blocage" }
        modeBtn.setOnClickListener {
            val items = arrayOf("Avertissement seulement (recommandé)",
                "Bloquer tous les numéros des listes actives")
            AlertDialog.Builder(this).setTitle("Politique des listes communautaires")
                .setSingleChoiceItems(items, if (CommunityLists.mode(this) == "block") 1 else 0) { dialog, which ->
                    CommunityLists.setMode(this, if (which == 1) "block" else "warn")
                    refreshCommunityInfo()
                    dialog.dismiss()
                }.setNegativeButton(android.R.string.cancel, null).show()
        }
        box.addView(modeBtn)
        for ((id, label, request) in listOf(
            Triple("saracroche", "Saracroche", 91), Triple("nocall", "NoCall", 92))) {
            val sw = Switch(this).apply {
                text = "Activer $label (après import)"
                isChecked = CommunityLists.enabled(this@FiltersActivity, id)
                setOnCheckedChangeListener { _, value ->
                    CommunityLists.setEnabled(this@FiltersActivity, id, value)
                    refreshCommunityInfo()
                }
            }
            box.addView(sw)
            val btn = Button(this).apply { text = "Importer CSV $label" }
            btn.setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(intent, request)
            }
            box.addView(btn)
            val download = Button(this).apply {
                text = "Télécharger / actualiser $label (CSV HTTPS)"
                contentDescription = "Télécharger la liste $label depuis une URL CSV"
            }
            download.setOnClickListener { askDownloadCommunity(id, label) }
            box.addView(download)
            val website = Button(this).apply { text = "Voir la source $label" }
            website.setOnClickListener {
                val link = if (id == "nocall")
                    "https://nocall.io/fr/numeros-indesirables"
                else "https://saracroche.org/"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            }
            box.addView(website)
        }
        // Insérer après le bandeau et les actions hautes, avant les thèmes.
        scroll.addView(box, 2)
        refreshCommunityInfo()
    }

    private fun refreshCommunityInfo() {
        if (!::communityInfo.isInitialized) return
        val mode = if (CommunityLists.mode(this) == "block") "BLOCAGE" else "AVERTISSEMENT"
        val lines = listOf("saracroche", "nocall").map { id ->
            val count = CommunityLists.count(this, id)
            val enabled = if (CommunityLists.enabled(this, id)) "active" else "inactive"
            val updated = CommunityLists.lastImport(this, id)
            val date = if (updated > 0L) DateFormat.getDateTimeInstance(
                DateFormat.SHORT, DateFormat.SHORT).format(Date(updated)) else "jamais"
            "$id : $count numéros · $enabled · MAJ : $date"
        }
        communityInfo.text = "Mode : $mode\n" + lines.joinToString("\n") +
            "\nImport CSV manuel ou téléchargement HTTPS à la demande. " +
            "Les signalements ne prouvent pas une fraude."
    }

    private fun askDownloadCommunity(id: String, label: String) {
        val entry = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            hint = "https://.../liste.csv"
            setText(CommunityLists.sourceUrl(this@FiltersActivity, id))
            selectAll()
        }
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 12, 32, 4)
            addView(TextView(this@FiltersActivity).apply {
                text = "Collez l'URL directe du fichier CSV publiée par $label. " +
                    "Aucun téléchargement automatique sans votre accord. " +
                    "Une liste vide ou invalide ne remplacera pas l'ancienne."
                setPadding(0, 0, 0, 12)
            })
            addView(entry)
        }
        AlertDialog.Builder(this)
            .setTitle("Actualiser $label")
            .setView(wrap)
            .setPositiveButton("Télécharger") { _, _ ->
                val url = entry.text.toString().trim()
                if (!url.startsWith("https://")) {
                    Toast.makeText(this, "URL HTTPS requise", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Téléchargement en cours…", Toast.LENGTH_SHORT).show()
                    Thread {
                        val result = try {
                            val count = CommunityLists.downloadCsv(this, id, url)
                            "Liste $label mise à jour : $count numéros. " +
                                "Mode actuel : ${CommunityLists.mode(this)}."
                        } catch (e: Exception) {
                            "Mise à jour impossible : ${e.message ?: "Erreur réseau / CSV"}. " +
                                "Ancienne liste conservée."
                        }
                        runOnUiThread {
                            refreshCommunityInfo()
                            AlertDialog.Builder(this).setMessage(result)
                                .setPositiveButton(android.R.string.ok, null).show()
                        }
                    }.start()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun importCommunity(requestCode: Int, data: Intent?) {
        val id = if (requestCode == 91) "saracroche" else "nocall"
        val uri = data?.data ?: return
        Toast.makeText(this, "Import en cours...", Toast.LENGTH_SHORT).show()
        Thread {
            val result = try {
                val stream = contentResolver.openInputStream(uri) ?: error("Fichier inaccessible")
                val n = CommunityLists.importCsv(this, id, stream)
                "Import $id : $n numéros enregistrés"
            } catch (e: Exception) { "Import impossible : ${e.message ?: "CSV invalide"}" }
            runOnUiThread {
                refreshCommunityInfo()
                AlertDialog.Builder(this).setMessage(result)
                    .setPositiveButton(android.R.string.ok, null).show()
            }
        }.start()
    }

    private fun exportRules() {
        try {
            val values = ContentValues().apply {
                val stamp = java.text.SimpleDateFormat("yyyy-MM-dd-HHmm",
                    java.util.Locale.FRANCE).format(java.util.Date())
                put(MediaStore.Downloads.DISPLAY_NAME,
                    "anarchie-phone-sauvegarde-$stamp.json")
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use {
                    it.write(BlockRulesStore.exportJson().toByteArray())
                }
                Toast.makeText(this, R.string.export_ok, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.export_fail, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode in 91..92 && resultCode == RESULT_OK) {
            importCommunity(requestCode, data)
            return
        }
        if (requestCode == 40 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val json = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
                if (BlockRulesStore.importJson(json)) {
                    Toast.makeText(this, R.string.import_ok, Toast.LENGTH_SHORT).show()
                    recreate()
                } else {
                    Toast.makeText(this, R.string.import_fail, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, R.string.import_fail, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Sans accès aux contacts, ces filtres ne peuvent pas savoir qui est connu. */
    private fun askContactsPermission() {
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_CONTACTS), 5)
        }
    }

    private fun refreshNumbers() {
        numbersAdapter.clear()
        numbersAdapter.addAll(BlockRulesStore.numbers().sorted())
        numbersAdapter.notifyDataSetChanged()
    }

    private fun refreshPrefixes() {
        prefixesAdapter.clear()
        prefixesAdapter.addAll(BlockRulesStore.prefixes().sorted())
        prefixesAdapter.notifyDataSetChanged()
    }

    override fun onResume() {
        super.onResume()
        ThemeUtil.refreshIfNeeded(this)
        refreshCommunityInfo()
    }

    override fun onDestroy() {
        ThemeUtil.forget(this)
        super.onDestroy()
    }
}
