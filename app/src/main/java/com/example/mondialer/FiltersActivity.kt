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
        val density = resources.displayMetrics.density
        themeOptions.forEach { (id, name, labelRes) ->
            val card = findViewById<TextView>(id)
            val active = BlockRulesStore.theme == name
            card.text = if (active) "✓ ${getString(labelRes)}" else getString(labelRes)
            card.alpha = if (active) 1f else 0.72f
            card.scaleX = if (active) 1.02f else 0.97f
            card.scaleY = if (active) 1.02f else 0.97f
            card.elevation = (if (active) 14f else 5f) * density
            card.setOnClickListener {
                BlockRulesStore.theme = name
                recreate()
            }
        }

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

    private fun exportRules() {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "mondialer-regles.json")
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
}
