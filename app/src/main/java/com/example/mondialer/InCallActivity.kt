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

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            updateUi(state)
            if (state == Call.STATE_DISCONNECTED) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incall)

        call = OngoingCall.call
        val c = call
        if (c == null) { finish(); return }

        val number = c.details?.handle?.schemeSpecificPart
        findViewById<TextView>(R.id.txtNumber).text =
            number ?: getString(R.string.hidden_number)

        // Nom + photo depuis le carnet d'adresses système
        if (number != null &&
            checkSelfPermission(Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) {
            lookupContact(number)
        }

        findViewById<Button>(R.id.btnAnswer).setOnClickListener {
            c.answer(VideoProfile.STATE_AUDIO_ONLY)
        }
        findViewById<Button>(R.id.btnHangup).setOnClickListener {
            if (c.state == Call.STATE_RINGING) c.reject(false, null) else c.disconnect()
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
                b.background = resources.getDrawable(R.drawable.btn_dial, theme)
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
                txt.text = getString(R.string.state_active)
                answer.visibility = View.GONE
                controls.visibility = View.VISIBLE
            }
            Call.STATE_DISCONNECTED -> txt.text = getString(R.string.state_ended)
            else -> txt.text = ""
        }
    }

    override fun onDestroy() {
        ThemeUtil.forget(this)
        call?.unregisterCallback(callback)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        ThemeUtil.refreshIfNeeded(this)
    }
}
