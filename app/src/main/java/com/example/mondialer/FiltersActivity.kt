package com.example.mondialer

import android.app.Activity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
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

        // Interrupteurs
        val swHidden = findViewById<Switch>(R.id.swHidden)
        val swPredefined = findViewById<Switch>(R.id.swPredefined)
        val swNeighbors = findViewById<Switch>(R.id.swNeighbors)

        swHidden.isChecked = BlockRulesStore.blockHidden
        swPredefined.isChecked = BlockRulesStore.usePredefined
        swNeighbors.isChecked = BlockRulesStore.blockNeighbors

        swHidden.setOnCheckedChangeListener { _, v -> BlockRulesStore.blockHidden = v }
        swPredefined.setOnCheckedChangeListener { _, v -> BlockRulesStore.usePredefined = v }
        swNeighbors.setOnCheckedChangeListener { _, v -> BlockRulesStore.blockNeighbors = v }

        // Mon numéro (pour la détection des "voisins")
        val editMy = findViewById<EditText>(R.id.editMyNumber)
        editMy.setText(BlockRulesStore.myNumber)
        findViewById<Button>(R.id.btnSaveMy).setOnClickListener {
            BlockRulesStore.myNumber = editMy.text.toString().trim()
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        }

        // Numéros bloqués
        val listNumbers = findViewById<ListView>(R.id.listNumbers)
        numbersAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
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

        // Préfixes bloqués (ex: 01 pour Paris / Île-de-France)
        val listPrefixes = findViewById<ListView>(R.id.listPrefixes)
        prefixesAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
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
