package com.example.mondialer

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
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
            val sched = if (it.schedStart >= 0)
                "  •  🕐 " + fmt(it.schedStart) + "→" + fmt(it.schedEnd) else ""
            val live = if (BlockRulesStore.isListActiveNow(it)) "● " else "○ "
            mapOf(
                "title" to live + it.name,
                "sub" to "$kind  •  ${it.numbers.size} " +
                         getString(R.string.list_numbers) + sched
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
            getString(R.string.list_schedule),
            getString(R.string.list_delete)
        )
        AlertDialog.Builder(this)
            .setTitle(l.name)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> { l.enabled = !l.enabled; save() }
                    1 -> rename(l)
                    2 -> { l.type = if (l.type == "allow") "block" else "allow"; save() }
                    3 -> editSchedule(l)
                    4 -> confirmDelete(l)
                }
            }
            .show()
    }

    /** Définit la plage horaire pendant laquelle la liste agit. */
    private fun editSchedule(l: BlockRulesStore.NamedList) {
        if (l.schedStart >= 0) {
            // Un horaire existe déjà : proposer de le retirer ou de le refaire
            AlertDialog.Builder(this)
                .setTitle(R.string.list_schedule)
                .setMessage(getString(R.string.sched_current,
                    fmt(l.schedStart), fmt(l.schedEnd)))
                .setPositiveButton(R.string.sched_change) { _, _ -> pickStart(l) }
                .setNeutralButton(R.string.sched_remove) { _, _ ->
                    l.schedStart = -1; l.schedEnd = -1; l.schedDays.clear(); save()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else pickStart(l)
    }

    private fun pickStart(l: BlockRulesStore.NamedList) {
        val now = java.util.Calendar.getInstance()
        TimePickerDialog(this, { _, h, m ->
            l.schedStart = h * 60 + m
            pickEnd(l)
        }, now.get(java.util.Calendar.HOUR_OF_DAY), 0, true).apply {
            setTitle(getString(R.string.sched_start))
            show()
        }
    }

    private fun pickEnd(l: BlockRulesStore.NamedList) {
        TimePickerDialog(this, { _, h, m ->
            l.schedEnd = h * 60 + m
            pickDays(l)
        }, 8, 0, true).apply {
            setTitle(getString(R.string.sched_end))
            show()
        }
    }

    private fun pickDays(l: BlockRulesStore.NamedList) {
        // Ordre affiché : lundi → dimanche, valeurs Calendar : dimanche = 1
        val labels = resources.getStringArray(R.array.week_days)
        val values = intArrayOf(2, 3, 4, 5, 6, 7, 1)
        val checked = BooleanArray(7) { values[it] in l.schedDays }
        AlertDialog.Builder(this)
            .setTitle(R.string.sched_days)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                l.schedDays.clear()
                for (i in 0..6) if (checked[i]) l.schedDays.add(values[i])
                save()
                Toast.makeText(this, getString(R.string.sched_saved,
                    fmt(l.schedStart), fmt(l.schedEnd)), Toast.LENGTH_LONG).show()
            }
            .setNeutralButton(R.string.sched_everyday) { _, _ ->
                l.schedDays.clear()
                save()
                Toast.makeText(this, getString(R.string.sched_saved,
                    fmt(l.schedStart), fmt(l.schedEnd)), Toast.LENGTH_LONG).show()
            }
            .show()
    }

    private fun fmt(minutes: Int): String =
        String.format(java.util.Locale.FRANCE, "%02d:%02d", minutes / 60, minutes % 60)

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
