package com.example.mondialer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.TextView
import android.widget.Toast

/** Gestion des listes nommées : création, activation, renommage, suppression. */
class ListsActivity : Activity() {

    private lateinit var listView: ListView
    private var lists = mutableListOf<BlockRulesStore.NamedList>()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lists)

        findViewById<TextView>(R.id.txtTitle).text = getString(R.string.lists_title)
        listView = findViewById(R.id.list)

        findViewById<Button>(R.id.btnNewList).setOnClickListener { createList() }

        listView.setOnItemClickListener { _, _, pos, _ ->
            // Un appui ouvre la liste pour en gérer les numéros
            startActivity(Intent(this, ListDetailActivity::class.java)
                .putExtra("id", lists[pos].id))
        }
        listView.setOnItemLongClickListener { _, _, pos, _ ->
            showActions(pos)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeUtil.refreshIfNeeded(this)
        reload()
    }

    private fun reload() {
        lists = BlockRulesStore.namedLists()
        val items = lists.map {
            val kind = if (it.type == "allow") getString(R.string.list_allow)
                       else getString(R.string.list_block)
            mapOf(
                "title" to (if (it.enabled) "● " else "○ ") + it.name,
                "sub" to "$kind  •  ${it.numbers.size} " + getString(R.string.list_numbers)
            )
        }
        listView.adapter = SimpleAdapter(
            this, items, R.layout.item_two_lines,
            arrayOf("title", "sub"), intArrayOf(R.id.text1, R.id.text2))
        findViewById<TextView>(R.id.txtHint).text =
            if (items.isEmpty()) getString(R.string.lists_empty)
            else getString(R.string.lists_hint)
    }

    private fun createList() {
        val input = EditText(this)
        input.hint = getString(R.string.list_name_hint)
        AlertDialog.Builder(this)
            .setTitle(R.string.list_new)
            .setView(input)
            .setPositiveButton(R.string.list_type_allow) { _, _ ->
                addList(input.text.toString(), "allow")
            }
            .setNeutralButton(R.string.list_type_block) { _, _ ->
                addList(input.text.toString(), "block")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun addList(rawName: String, type: String) {
        val name = rawName.trim().ifEmpty { getString(R.string.list_untitled) }
        lists.add(BlockRulesStore.NamedList(
            BlockRulesStore.newListId(), name, type, true, HashSet()))
        BlockRulesStore.saveNamedLists(lists)
        reload()
    }

    private fun showActions(pos: Int) {
        val l = lists[pos]
        val actions = arrayOf(
            if (l.enabled) getString(R.string.list_disable) else getString(R.string.list_enable),
            getString(R.string.list_rename),
            if (l.type == "allow") getString(R.string.list_to_block)
            else getString(R.string.list_to_allow),
            getString(R.string.list_delete)
        )
        AlertDialog.Builder(this)
            .setTitle(l.name)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> { l.enabled = !l.enabled; save() }
                    1 -> rename(l)
                    2 -> { l.type = if (l.type == "allow") "block" else "allow"; save() }
                    3 -> confirmDelete(l)
                }
            }
            .show()
    }

    private fun rename(l: BlockRulesStore.NamedList) {
        val input = EditText(this)
        input.setText(l.name)
        AlertDialog.Builder(this)
            .setTitle(R.string.list_rename)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) { l.name = n; save() }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(l: BlockRulesStore.NamedList) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.list_delete_confirm, l.name))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lists.remove(l); save()
                Toast.makeText(this, R.string.removed, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun save() {
        BlockRulesStore.saveNamedLists(lists)
        reload()
    }

    override fun onDestroy() {
        ThemeUtil.forget(this)
        super.onDestroy()
    }
}
