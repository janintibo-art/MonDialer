package com.example.mondialer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/** Contenu d'une liste nommée : ajout manuel ou depuis les contacts. */
class ListDetailActivity : Activity() {

    private var listId: String = ""
    private lateinit var adapter: ArrayAdapter<String>
    private var target: BlockRulesStore.NamedList? = null
    private var all = mutableListOf<BlockRulesStore.NamedList>()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list_detail)

        listId = intent.getStringExtra("id") ?: ""
        load()
        val t = target
        if (t == null) { finish(); return }

        findViewById<TextView>(R.id.txtTitle).text = t.name
        findViewById<TextView>(R.id.txtKind).text =
            if (t.type == "allow") getString(R.string.list_allow_desc)
            else getString(R.string.list_block_desc)

        adapter = ArrayAdapter(this, R.layout.item_one_line, t.numbers.sorted().toMutableList())
        val lv = findViewById<ListView>(R.id.list)
        lv.adapter = adapter
        lv.setOnItemLongClickListener { _, _, pos, _ ->
            val n = adapter.getItem(pos) ?: return@setOnItemLongClickListener true
            target?.numbers?.remove(n)
            save()
            Toast.makeText(this, R.string.removed, Toast.LENGTH_SHORT).show()
            true
        }

        findViewById<Button>(R.id.btnAdd).setOnClickListener {
            val e = findViewById<EditText>(R.id.editNumber)
            val n = e.text.toString().trim()
            if (n.isNotEmpty()) {
                target?.numbers?.add(BlockRulesStore.normalize(n))
                e.setText("")
                save()
            }
        }

        findViewById<Button>(R.id.btnFromContacts).setOnClickListener {
            startActivityForResult(
                Intent(this, ContactsActivity::class.java).putExtra("pick", true), 95)
        }
    }

    private fun load() {
        all = BlockRulesStore.namedLists()
        target = all.firstOrNull { it.id == listId }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 95 && resultCode == RESULT_OK) {
            val num = data?.getStringExtra("number") ?: return
            target?.numbers?.add(BlockRulesStore.normalize(num))
            save()
        }
    }

    private fun save() {
        BlockRulesStore.saveNamedLists(all)
        adapter.clear()
        adapter.addAll(target?.numbers?.sorted() ?: emptyList())
        adapter.notifyDataSetChanged()
    }

    override fun onResume() {
        super.onResume()
        ThemeUtil.refreshIfNeeded(this)
    }

    override fun onDestroy() {
        ThemeUtil.forget(this)
        super.onDestroy()
    }
}
