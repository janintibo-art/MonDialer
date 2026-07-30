package com.example.mondialer

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Bundle as OsBundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var display: EditText
    private var contacts = listOf<Pair<String, String>>() // nom, numéro
    private var contactsLoaded = false
    private var pendingCall = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        display = findViewById(R.id.editNumber)

        intent?.data?.let { uri: Uri ->
            if (uri.scheme == "tel") display.setText(uri.schemeSpecificPart)
        }

        val digits = mapOf(
            R.id.btn1 to "1", R.id.btn2 to "2", R.id.btn3 to "3",
            R.id.btn4 to "4", R.id.btn5 to "5", R.id.btn6 to "6",
            R.id.btn7 to "7", R.id.btn8 to "8", R.id.btn9 to "9",
            R.id.btnStar to "*", R.id.btn0 to "0", R.id.btnHash to "#"
        )
        digits.forEach { (id, d) ->
            findViewById<Button>(id).setOnClickListener { v ->
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                display.append(d)
            }
        }

        refreshDialpadTheme(digits.keys)

        findViewById<Button>(R.id.btnDelete).setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val t = display.text.toString()
            if (t.isNotEmpty()) display.setText(t.dropLast(1))
            display.setSelection(display.text.length)
        }
        findViewById<Button>(R.id.btnDelete).setOnLongClickListener {
            display.setText(""); true
        }

        findViewById<Button>(R.id.btnCall).setOnClickListener { placeCall() }

        findViewById<Button>(R.id.btnFilters).setOnClickListener { showMenu() }
        findViewById<Button>(R.id.btnContacts).setOnClickListener {
            startActivityForResult(Intent(this, ContactsActivity::class.java), 20)
        }
        findViewById<Button>(R.id.btnLog).setOnClickListener {
            startActivityForResult(Intent(this, CallLogActivity::class.java), 30)
        }
        findViewById<Button>(R.id.btnSms).setOnClickListener {
            startActivity(Intent(this, ConversationsActivity::class.java))
        }
        findViewById<Button>(R.id.btnVoicemail).setOnClickListener {
            startActivity(Intent(this, VoicemailActivity::class.java))
        }
        findViewById<Button>(R.id.btnVideo).setOnClickListener { placeVideoCall() }

        // Codes secrets : certaines séquences déclenchent des surprises
        display.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = checkSecretCode(s?.toString() ?: "")
        })

        // T9 : suggestions de contacts pendant la frappe
        display.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = updateSuggestions(s?.toString() ?: "")
        })

        // Autorisation contacts (T9, favoris, nom en appel)
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) {
            loadContacts(); loadFavorites()
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 2)
        }
    }

    /** Séquences cachées, à composer comme un numéro. */
    private fun checkSecretCode(typed: String) {
        when (typed) {
            "*#1871#" -> {                      // Commune de Paris
                display.setText("")
                startActivity(Intent(this, EasterEggActivity::class.java))
            }
            "*#666#" -> {                       // Bascule express en rouge
                display.setText("")
                BlockRulesStore.theme = "rouge"
                Toast.makeText(this, R.string.egg_red, Toast.LENGTH_SHORT).show()
                recreate()
            }
            "*#1936#" -> {                       // Révolution espagnole
                display.setText("")
                BlockRulesStore.theme = "cyan"
                Toast.makeText(this, R.string.egg_reset, Toast.LENGTH_SHORT).show()
                recreate()
            }
        }
    }

    // ---- Contacts en mémoire pour le T9 ----
    private fun loadContacts() {
        if (contactsLoaded) return
        val out = mutableListOf<Pair<String, String>>()
        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    out.add(Pair(c.getString(0) ?: "", c.getString(1) ?: ""))
                }
            }
        } catch (_: Exception) {}
        contacts = out
        contactsLoaded = true
    }

    private fun t9(s: String): String = s.lowercase().map { c ->
        when (c) {
            in 'a'..'c', 'à', 'â', 'ç' -> '2'
            in 'd'..'f', 'é', 'è', 'ê', 'ë' -> '3'
            in 'g'..'i', 'î', 'ï' -> '4'
            in 'j'..'l' -> '5'
            in 'm'..'o', 'ô', 'ö' -> '6'
            in 'p'..'s' -> '7'
            in 't'..'v', 'ù', 'û', 'ü' -> '8'
            in 'w'..'z' -> '9'
            in '0'..'9' -> c
            else -> ' '
        }
    }.joinToString("").replace(" ", "")

    private fun updateSuggestions(typed: String) {
        val row = findViewById<LinearLayout>(R.id.suggestRow)
        val q = typed.filter { it.isDigit() }
        if (q.length < 2 || contacts.isEmpty()) { row.visibility = View.GONE; return }

        val matches = contacts.filter {
            t9(it.first).contains(q) || it.second.filter { ch -> ch.isDigit() }.contains(q)
        }.distinctBy { it.first }.take(3)

        row.removeAllViews()
        if (matches.isEmpty()) { row.visibility = View.GONE; return }
        row.visibility = View.VISIBLE
        for ((name, number) in matches) {
            val tv = TextView(this)
            tv.text = name
            tv.setTextColor(resolveNeon())
            tv.textSize = 13f
            tv.setPadding(28, 16, 28, 16)
            tv.setBackgroundResource(ThemeRes.res(this, R.attr.actionBg))
            tv.setOnClickListener {
                display.setText(number.filter { ch -> ch.isDigit() || ch == '+' })
                display.setSelection(display.text.length)
                row.visibility = View.GONE
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(6, 0, 6, 0)
            row.addView(tv, lp)
        }
    }

    // ---- Favoris (contacts étoilés) ----
    private fun loadFavorites() {
        val row = findViewById<LinearLayout>(R.id.favRow)
        row.removeAllViews()
        val favs = mutableListOf<Pair<String, String>>()
        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                ContactsContract.CommonDataKinds.Phone.STARRED + "=1",
                null, null
            )?.use { c ->
                while (c.moveToNext() && favs.size < 6) {
                    val name = c.getString(0) ?: continue
                    if (favs.none { it.first == name })
                        favs.add(Pair(name, c.getString(1) ?: ""))
                }
            }
        } catch (_: Exception) {}

        if (favs.isEmpty()) { row.visibility = View.GONE; return }
        row.visibility = View.VISIBLE
        for ((name, number) in favs) {
            val tv = TextView(this)
            tv.text = "★ " + name.split(" ").first()
            tv.setTextColor(resolveNeon())
            tv.textSize = 13f
            tv.setPadding(28, 16, 28, 16)
            tv.setBackgroundResource(ThemeRes.res(this, R.attr.actionBg))
            tv.setOnClickListener {
                display.setText(number.filter { ch -> ch.isDigit() || ch == '+' })
                placeCall()
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(6, 0, 6, 0)
            row.addView(tv, lp)
        }
    }


    /**
     * Habille le clavier : fond résolu avec le thème courant, car le cache de
     * drawables d'Android peut garder les couleurs de l'ancienne palette.
     */
    private fun refreshDialpadTheme(ids: Collection<Int>) {
        val neon = resolveNeon()
        val bgRes = ThemeRes.res(this, R.attr.dialBg)
        for (id in ids) {
            val b = findViewById<Button>(id) ?: continue
            // Les touches 3D se dessinent elles-mêmes : pas de fond XML
            if (b !is Neon3DButton) {
                b.background = resources.getDrawable(bgRes, theme)
            }
            b.setShadowLayer(14f, 0f, 0f, neon)

        }
    }

    private fun resolveNeon(): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(R.attr.cNeon, tv, true)
        return if (tv.resourceId != 0) getColor(tv.resourceId) else tv.data
    }

    // ---- Appel vidéo (nécessite le support ViLTE de l'opérateur) ----
    private fun placeVideoCall() {
        val number = display.text.toString().trim()
        if (number.isEmpty()) {
            Toast.makeText(this, R.string.enter_number_first, Toast.LENGTH_SHORT).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 1)
            return
        }
        try {
            val tm = getSystemService(TelecomManager::class.java)
            val extras = OsBundle()
            extras.putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                VideoProfile.STATE_BIDIRECTIONAL)
            tm.placeCall(Uri.parse("tel:" + number), extras)
        } catch (e: Exception) {
            // Repli : appel audio classique
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number)))
        }
    }

    // ---- Appel ----
    private fun placeCall() {
        val number = display.text.toString().trim()
        if (number.isEmpty()) return
        if (checkSelfPermission(Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {
            pendingCall = true
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 1)
            return
        }
        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        when (requestCode) {
            1 -> if (grantResults.isNotEmpty()
                && grantResults[0] == PackageManager.PERMISSION_GRANTED && pendingCall) {
                pendingCall = false
                placeCall()
            }
            2 -> if (grantResults.isNotEmpty()
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadContacts(); loadFavorites()
            }
        }
    }

    /** Regroupe les réglages ponctuels, pour ne pas encombrer l'écran. */
    private fun showMenu() {
        val labels = arrayOf(
            getString(R.string.menu_filters),
            getString(R.string.menu_blocked),
            getString(R.string.menu_theme),
            getString(R.string.menu_default_dialer),
            getString(R.string.menu_default_sms),
            getString(R.string.menu_ai),
            getString(R.string.menu_fake_call),
            getString(R.string.menu_stats)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_title)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, FiltersActivity::class.java))
                    1 -> startActivity(Intent(this, BlockedLogActivity::class.java))
                    2 -> startActivity(Intent(this, CustomThemeActivity::class.java))
                    3 -> requestDefaultDialer()
                    4 -> requestDefaultSms()
                    5 -> startActivity(Intent(this, AiSettingsActivity::class.java))
                    6 -> scheduleFakeCall()
                    7 -> startActivity(Intent(this, StatsActivity::class.java))
                }
            }
            .show()
    }

    /** Programme un appel fictif, pour s'extraire d'une situation pénible. */
    private fun scheduleFakeCall() {
        val name = EditText(this)
        name.hint = getString(R.string.fake_name_hint)
        name.setText(BlockRulesStore.fakeCallName)
        val number = EditText(this)
        number.hint = getString(R.string.fake_number_hint)
        number.inputType = android.text.InputType.TYPE_CLASS_PHONE
        number.setText(BlockRulesStore.fakeCallNumber)

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val pad = (16 * resources.displayMetrics.density).toInt()
        box.setPadding(pad, pad, pad, 0)
        box.addView(name)
        box.addView(number)

        val delays = intArrayOf(10, 30, 60, 300)
        AlertDialog.Builder(this)
            .setTitle(R.string.fake_title)
            .setView(box)
            .setItems(resources.getStringArray(R.array.fake_delays)) { _, which ->
                BlockRulesStore.fakeCallName = name.text.toString().trim()
                BlockRulesStore.fakeCallNumber = number.text.toString().trim()
                armFakeCall(delays[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun armFakeCall(seconds: Int) {
        val intent = Intent(this, FakeCallReceiver::class.java)
            .putExtra("name", BlockRulesStore.fakeCallName
                .ifBlank { getString(R.string.fake_default_name) })
            .putExtra("number", BlockRulesStore.fakeCallNumber
                .ifBlank { "06 12 34 56 78" })
        val pi = PendingIntent.getBroadcast(this, 77, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val at = System.currentTimeMillis() + seconds * 1000L
        val am = getSystemService(AlarmManager::class.java)
        try {
            // Une alarme exacte demande une autorisation sur les versions récentes
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.set(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: Exception) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi)
        }
        Toast.makeText(this,
            getString(R.string.fake_armed, seconds), Toast.LENGTH_LONG).show()
    }

    /** Propose l'application comme application SMS par défaut. */
    private fun requestDefaultSms() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_SMS) && !rm.isRoleHeld(RoleManager.ROLE_SMS)) {
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_SMS), 11)
        } else if (rm.isRoleHeld(RoleManager.ROLE_SMS)) {
            Toast.makeText(this, R.string.already_sms_default, Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestDefaultDialer() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_DIALER) && !rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER), 10)
        } else if (rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
            Toast.makeText(this, R.string.already_default, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            10 -> if (resultCode == RESULT_OK)
                Toast.makeText(this, R.string.now_default, Toast.LENGTH_SHORT).show()
            11 -> if (resultCode == RESULT_OK)
                Toast.makeText(this, R.string.now_sms_default, Toast.LENGTH_SHORT).show()
            20, 30 -> if (resultCode == RESULT_OK) {
                val n = data?.getStringExtra("number") ?: return
                display.setText(n)
                display.setSelection(display.text.length)
                if (data.getBooleanExtra("call", false)) placeCall()
            }
        }
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
