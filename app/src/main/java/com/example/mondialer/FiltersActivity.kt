package com.example.mondialer

import android.app.Activity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Switch
import android.widget.Toast

class FiltersActivity : Activity() {

    private lateinit var numbersAdapter: ArrayAdapter<String>
    private lateinit var prefixesAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BlockRulesStore.appCtx = applicationContext
        setContentView(R.layout.activity_filters)

        // Options simples
        val swHidden = findViewById<Switch>(R.id.swHidden)
        val swNeighbors = findViewById<Switch>(R.id.swNeighbors)
        val swInternational = findViewById<Switch>(R.id.swInternational)

        swHidden.isChecked = BlockRulesStore.blockHidden
        swNeighbors.isChecked = BlockRulesStore.blockNeighbors
        swInternational.isChecked = BlockRulesStore.blockInternational

        swHidden.setOnCheckedChangeListener { _, v -> BlockRulesStore.blockHidden = v }
        swNeighbors.setOnCheckedChangeListener { _, v -> BlockRulesStore.blockNeighbors = v }
        swInternational.setOnCheckedChangeListener { _, v -> BlockRulesStore.blockInternational = v }

        // Listes prédéfinies : un interrupteur par liste, généré dynamiquement
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
