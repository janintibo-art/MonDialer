package com.example.mondialer

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Écran de composition d'un thème sur mesure : couleur, forme, image de fond. */
class CustomThemeActivity : Activity() {

    private var color = 0xFF45E9FF.toInt()
    private var shape = "orb"
    private var imageUri = ""
    private var dim = 55

    private val palette = listOf(
        0xFF45E9FF.toInt(), 0xFF00FFC2.toInt(), 0xFF7CFF3C.toInt(), 0xFFFFE24A.toInt(),
        0xFFFF9E2C.toInt(), 0xFFFF4D4D.toInt(), 0xFFFF4FD8.toInt(), 0xFFC77DFF.toInt(),
        0xFF6C8CFF.toInt(), 0xFF9C7B5A.toInt(), 0xFFB0BEC5.toInt(), 0xFFFFFFFF.toInt()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_theme)

        color = BlockRulesStore.customAccent
        shape = BlockRulesStore.customShape
        imageUri = BlockRulesStore.customImage
        dim = BlockRulesStore.customDim

        buildSwatches(R.id.swatchRow1, palette.take(6))
        buildSwatches(R.id.swatchRow2, palette.drop(6))

        val r = findViewById<SeekBar>(R.id.seekR)
        val g = findViewById<SeekBar>(R.id.seekG)
        val b = findViewById<SeekBar>(R.id.seekB)
        r.progress = Color.red(color); g.progress = Color.green(color); b.progress = Color.blue(color)

        val watcher = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                color = Color.rgb(r.progress, g.progress, b.progress)
                updatePreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }
        r.setOnSeekBarChangeListener(watcher)
        g.setOnSeekBarChangeListener(watcher)
        b.setOnSeekBarChangeListener(watcher)

        mapOf(R.id.shapeOrb to "orb", R.id.shapeTuile to "tuile", R.id.shapeHud to "hud")
            .forEach { (id, name) ->
                findViewById<Button>(id).setOnClickListener {
                    shape = name
                    updatePreview()
                }
            }

        findViewById<Button>(R.id.btnPickImage).setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "image/*"
            i.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            startActivityForResult(i, 90)
        }
        findViewById<Button>(R.id.btnClearImage).setOnClickListener {
            imageUri = ""
            findViewById<ImageView>(R.id.imgPreview).setImageDrawable(null)
            updatePreview()
        }

        val sd = findViewById<SeekBar>(R.id.seekDim)
        sd.progress = dim
        sd.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                dim = p; updatePreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        setupKeypadSection()

        findViewById<Button>(R.id.btnApply).setOnClickListener { save() }

        loadImagePreview()
        updatePreview()
    }

    /**
     * Réglages propres au clavier téléphonique. Ils s'appliquent quel que soit
     * le thème choisi, et sont enregistrés dès la modification.
     */
    private fun setupKeypadSection() {
        val shapes = mapOf(
            R.id.kpAuto to "auto", R.id.kpOrb to "orb",
            R.id.kpTuile to "tuile", R.id.kpHud to "hud")

        fun highlight() {
            val current = BlockRulesStore.keypadShape
            shapes.forEach { (id, name) ->
                findViewById<Button>(id).alpha = if (name == current) 1f else 0.45f
            }
        }
        shapes.forEach { (id, name) ->
            findViewById<Button>(id).setOnClickListener {
                BlockRulesStore.keypadShape = name
                highlight()
                Toast.makeText(this, R.string.keypad_saved, Toast.LENGTH_SHORT).show()
            }
        }
        highlight()

        val glow = findViewById<SeekBar>(R.id.seekGlow)
        val lbl = findViewById<TextView>(R.id.lblGlow)
        glow.progress = BlockRulesStore.keypadGlow
        lbl.text = getString(R.string.keypad_glow, BlockRulesStore.keypadGlow)
        glow.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                lbl.text = getString(R.string.keypad_glow, p)
                if (fromUser) BlockRulesStore.keypadGlow = p
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        val sw = findViewById<Switch>(R.id.swDigitAccent)
        sw.isChecked = BlockRulesStore.keypadDigitAccent
        sw.setOnCheckedChangeListener { _, v -> BlockRulesStore.keypadDigitAccent = v }
    }

    private fun buildSwatches(rowId: Int, colors: List<Int>) {
        val row = findViewById<LinearLayout>(rowId)
        row.removeAllViews()
        for (c in colors) {
            val v = View(this)
            val d = GradientDrawable()
            d.shape = GradientDrawable.OVAL
            d.setColor(c)
            d.setStroke((2 * resources.displayMetrics.density).toInt(), Color.WHITE)
            v.background = d
            v.setOnClickListener {
                color = c
                findViewById<SeekBar>(R.id.seekR).progress = Color.red(c)
                findViewById<SeekBar>(R.id.seekG).progress = Color.green(c)
                findViewById<SeekBar>(R.id.seekB).progress = Color.blue(c)
                updatePreview()
            }
            val size = (42 * resources.displayMetrics.density).toInt()
            val m = (4 * resources.displayMetrics.density).toInt()
            val lp = LinearLayout.LayoutParams(0, size, 1f)
            lp.setMargins(m, m, m, m)
            row.addView(v, lp)
        }
    }

    private fun updatePreview() {
        val p = findViewById<TextView>(R.id.preview)
        val d = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(darken(color, 0.75f), color, darken(color, 0.75f)))
        d.cornerRadius = when (shape) {
            "tuile" -> 16 * resources.displayMetrics.density
            "hud" -> 5 * resources.displayMetrics.density
            else -> 34 * resources.displayMetrics.density
        }
        d.setStroke((2 * resources.displayMetrics.density).toInt(), Color.WHITE)
        p.background = d
        p.text = String.format("#%06X", 0xFFFFFF and color)

        findViewById<TextView>(R.id.lblR).text = getString(R.string.lbl_red, Color.red(color))
        findViewById<TextView>(R.id.lblG).text = getString(R.string.lbl_green, Color.green(color))
        findViewById<TextView>(R.id.lblB).text = getString(R.string.lbl_blue, Color.blue(color))
        findViewById<TextView>(R.id.lblDim).text = getString(R.string.lbl_dim, dim)

        // Le bouton de forme actif est mis en avant
        mapOf(R.id.shapeOrb to "orb", R.id.shapeTuile to "tuile", R.id.shapeHud to "hud")
            .forEach { (id, name) ->
                findViewById<Button>(id).alpha = if (shape == name) 1f else 0.45f
            }
        findViewById<ImageView>(R.id.imgPreview).imageAlpha =
            255 - (dim.coerceIn(0, 100) * 255 / 100)
    }

    private fun darken(c: Int, f: Float) = Color.rgb(
        (Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())

    private fun loadImagePreview() {
        if (imageUri.isBlank()) return
        Thread {
            try {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                val bmp = contentResolver.openInputStream(Uri.parse(imageUri))?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                runOnUiThread {
                    if (bmp != null) findViewById<ImageView>(R.id.imgPreview).setImageBitmap(bmp)
                }
            } catch (_: Exception) {}
        }.start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 90 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            imageUri = uri.toString()
            loadImagePreview()
            updatePreview()
        }
    }

    private fun save() {
        BlockRulesStore.customAccent = color
        BlockRulesStore.customShape = shape
        BlockRulesStore.customImage = imageUri
        BlockRulesStore.customDim = dim
        BlockRulesStore.theme = "custom"
        Toast.makeText(this, R.string.custom_applied, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onResume() {
        super.onResume()
        // Pas de recoloration ici : les couleurs affichées doivent rester fidèles.
    }

    override fun onDestroy() {
        ThemeUtil.forget(this)
        super.onDestroy()
    }
}
