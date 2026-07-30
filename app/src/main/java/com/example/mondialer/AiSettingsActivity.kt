package com.example.mondialer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

/** Configuration de l'assistant : clé Gemini personnelle et ton des réponses. */
class AiSettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_settings)

        findViewById<EditText>(R.id.editKey).setText(BlockRulesStore.aiKey)
        findViewById<EditText>(R.id.editModel).setText(BlockRulesStore.aiModel)
        findViewById<EditText>(R.id.editModel).hint = AiClient.DEFAULT_MODEL
        findViewById<EditText>(R.id.editTone).setText(BlockRulesStore.aiTone)
        findViewById<TextView>(R.id.txtHint).text =
            getString(R.string.ai_key_hint, AiClient.KEY_URL, AiClient.DEFAULT_MODEL)

        findViewById<Button>(R.id.btnGetKey).setOnClickListener {
            val url = "https://" + AiClient.KEY_URL
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                Toast.makeText(this, url, Toast.LENGTH_LONG).show()
            }
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        findViewById<Button>(R.id.btnTest).setOnClickListener { save(); test() }
    }

    private fun save() {
        BlockRulesStore.aiKey = findViewById<EditText>(R.id.editKey).text.toString().trim()
        BlockRulesStore.aiModel = findViewById<EditText>(R.id.editModel).text.toString().trim()
        BlockRulesStore.aiTone = findViewById<EditText>(R.id.editTone).text.toString().trim()
            .ifEmpty { "amical et naturel" }
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
    }

    private fun test() {
        val status = findViewById<TextView>(R.id.txtStatus)
        status.text = getString(R.string.ai_testing)
        Thread {
            val msg = try {
                val r = AiClient.ask(
                    "Réponds en français, très brièvement.",
                    "Dis bonjour en une phrase.")
                getString(R.string.ai_test_ok, r.trim().take(60))
            } catch (e: Exception) {
                getString(R.string.ai_test_fail, e.message ?: "")
            }
            runOnUiThread { status.text = msg }
        }.start()
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
