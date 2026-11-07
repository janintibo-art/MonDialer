package com.example.mondialer

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView

class InCallActivity : Activity() {

    private var call: Call? = null
    private var speakerOn = false
    private var muted = false
    private var onHold = false
    private var connectedAt = 0L
    private val ticker = android.os.Handler(android.os.Looper.getMainLooper())

    /** Rafraîchit la durée de communication chaque seconde. */
    private val tick = object : Runnable {
        override fun run() {
            if (connectedAt > 0L) {
                val sec = ((System.currentTimeMillis() - connectedAt) / 1000).toInt()
                findViewById<TextView>(R.id.txtState).text = String.format(
                    java.util.Locale.FRANCE, "%02d:%02d", sec / 60, sec % 60)
            }
            ticker.postDelayed(this, 1000)
        }
    }

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            updateUi(state)
            if (state == Call.STATE_DISCONNECTED) {
                ticker.removeCallbacks(tick)
                // La prochaine communication peut encore être active.
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (!isFinishing && OngoingCall.call != null) bindCurrentCall()
                    else finish()
                }, 250)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incall)

        val c = OngoingCall.call
        if (c == null) { finish(); return }
        call = c

        val number = c.details?.handle?.schemeSpecificPart
        // Le nom réseau s'affiche automatiquement s'il est fourni.
        BlockRulesStore.rememberNetworkCallerName(number, c.details?.callerDisplayName)
        val identified = BlockRulesStore.callerAlias(number)
            ?: BlockRulesStore.networkCallerName(number)
        if (!identified.isNullOrBlank()) {
            findViewById<TextView>(R.id.txtName).apply {
                text = identified + " (indicatif, non vérifié)"
                visibility = View.VISIBLE
            }
        }
        findViewById<TextView>(R.id.txtNumber).text =
            number ?: getString(R.string.hidden_number)

        // Nom + photo depuis le carnet d'adresses système
        if (number != null &&
            checkSelfPermission(Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) {
            ContactLookup.name(this, number)?.let {
                findViewById<TextView>(R.id.txtName).apply {
                    text = it
                    visibility = View.VISIBLE
                }
            }
            lookupContact(number)
        }

        findViewById<Button>(R.id.btnAnswer).setOnClickListener {
            c.answer(VideoProfile.STATE_AUDIO_ONLY)
        }
        findViewById<Button>(R.id.btnHangup).setOnClickListener {
            val current = call ?: OngoingCall.call
            if (current == null) {
                android.widget.Toast.makeText(this, "Appel introuvable", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                try {
                    if (current.state == Call.STATE_RINGING) current.reject(false, null)
                    else current.disconnect()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this, "Raccrochage impossible : vérifier l'application téléphone par défaut", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }

        // Haut-parleur
        val btnSpeaker = findViewById<Button>(R.id.btnSpeaker)
        btnSpeaker.setOnClickListener {
            speakerOn = !speakerOn
            MyInCallService.instance?.setAudioRoute(
                if (speakerOn) CallAudioState.ROUTE_SPEAKER
                else CallAudioState.ROUTE_EARPIECE
            )
            btnSpeaker.alpha = if (speakerOn) 1f else 0.5f
        }
        btnSpeaker.alpha = 0.5f

        // Micro coupé
        val btnMute = findViewById<Button>(R.id.btnMute)
        btnMute.setOnClickListener {
            muted = !muted
            MyInCallService.instance?.setMuted(muted)
            btnMute.alpha = if (muted) 1f else 0.5f
        }
        btnMute.alpha = 0.5f

        // Mise en attente
        val btnHold = findViewById<Button>(R.id.btnHold)
        btnHold.setOnClickListener {
            val c2 = call ?: return@setOnClickListener
            onHold = !onHold
            if (onHold) c2.hold() else c2.unhold()
            btnHold.alpha = if (onHold) 1f else 0.5f
        }
        btnHold.alpha = 0.5f

        // Clavier DTMF (serveurs vocaux « tapez 1 »)
        val dtmfGrid = findViewById<GridLayout>(R.id.dtmfGrid)
        val btnKeypad = findViewById<Button>(R.id.btnKeypad)
        btnKeypad.setOnClickListener {
            dtmfGrid.visibility =
                if (dtmfGrid.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        val keys = mapOf(
            R.id.dt1 to '1', R.id.dt2 to '2', R.id.dt3 to '3',
            R.id.dt4 to '4', R.id.dt5 to '5', R.id.dt6 to '6',
            R.id.dt7 to '7', R.id.dt8 to '8', R.id.dt9 to '9',
            R.id.dtStar to '*', R.id.dt0 to '0', R.id.dtHash to '#'
        )
        // Réinflater le fond avec le thème courant (contourne le cache de drawables)
        keys.keys.forEach { id ->
            findViewById<Button>(id)?.let { b ->
                if (b !is Neon3DButton) {
                    b.background = resources.getDrawable(
                        ThemeRes.res(this, R.attr.dialBg), theme)
                }
            }
        }

        keys.forEach { (id, ch) ->
            findViewById<Button>(id).setOnClickListener {
                call?.playDtmfTone(ch)
                call?.stopDtmfTone()
            }
        }

        c.registerCallback(callback)
        updateUi(c.state)
    }

    private fun lookupContact(number: String) {
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            contentResolver.query(uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME,
                        ContactsContract.PhoneLookup.PHOTO_URI),
                null, null, null)?.use { cur ->
                if (cur.moveToFirst()) {
                    val name = cur.getString(0)
                    val photo = cur.getString(1)
                    if (!name.isNullOrBlank()) {
                        findViewById<TextView>(R.id.txtName).apply {
                            text = name
                            visibility = View.VISIBLE
                        }
                    }
                    if (!photo.isNullOrBlank()) {
                        findViewById<ImageView>(R.id.imgPhoto).apply {
                            setImageURI(Uri.parse(photo))
                            visibility = View.VISIBLE
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun updateUi(state: Int) {
        val txt = findViewById<TextView>(R.id.txtState)
        val answer = findViewById<Button>(R.id.btnAnswer)
        val controls = findViewById<View>(R.id.controlsRow)
        when (state) {
            Call.STATE_RINGING -> {
                txt.text = getString(R.string.state_ringing)
                answer.visibility = View.VISIBLE
                controls.visibility = View.GONE
            }
            Call.STATE_DIALING -> {
                txt.text = getString(R.string.state_dialing)
                answer.visibility = View.GONE
                controls.visibility = View.VISIBLE
            }
            Call.STATE_ACTIVE -> {
                if (connectedAt == 0L) {
                    connectedAt = System.currentTimeMillis()
                    ticker.post(tick)
                }
                answer.visibility = View.GONE
                controls.visibility = View.VISIBLE
            }
            Call.STATE_HOLDING -> {
                txt.text = getString(R.string.state_hold)
                answer.visibility = View.GONE
                controls.visibility = View.VISIBLE
            }
            Call.STATE_DISCONNECTED -> {
                ticker.removeCallbacks(tick)
                txt.text = getString(R.string.state_ended)
            }
            else -> txt.text = ""
        }
    }

    override fun onDestroy() {
        ThemeUtil.forget(this)
        call?.unregisterCallback(callback)
        super.onDestroy()
    }

    private fun bindCurrentCall() {
        val next = OngoingCall.call ?: return
        if (next !== call) {
            call?.unregisterCallback(callback)
            ticker.removeCallbacks(tick)
            connectedAt = 0L
            call = next
            next.registerCallback(callback)
            findViewById<TextView>(R.id.txtNumber).text =
                next.details?.handle?.schemeSpecificPart ?: getString(R.string.hidden_number)
            findViewById<TextView>(R.id.txtName).visibility = View.GONE
        }
        updateUi(next.state)
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        bindCurrentCall()
    }

    override fun onResume() {
        super.onResume()
        bindCurrentCall()
        ThemeUtil.refreshIfNeeded(this)
    }
}
