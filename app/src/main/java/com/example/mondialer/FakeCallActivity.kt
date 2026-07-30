package com.example.mondialer

import android.app.Activity
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.Button
import android.widget.TextView

/** Écran d'appel entrant fictif : sonnerie, vibration et interface crédible. */
class FakeCallActivity : Activity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.apply(this)
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContentView(R.layout.activity_incall)

        val name = intent.getStringExtra("name") ?: getString(R.string.fake_default_name)
        val number = intent.getStringExtra("number") ?: "06 12 34 56 78"

        findViewById<TextView>(R.id.txtName).apply {
            text = name
            visibility = View.VISIBLE
        }
        findViewById<TextView>(R.id.txtNumber).text = number
        findViewById<TextView>(R.id.txtState).text = getString(R.string.state_ringing)
        findViewById<View>(R.id.controlsRow).visibility = View.GONE

        startRinging()

        findViewById<Button>(R.id.btnAnswer).setOnClickListener {
            stopRinging()
            findViewById<TextView>(R.id.txtState).text = getString(R.string.state_active)
            findViewById<Button>(R.id.btnAnswer).visibility = View.GONE
            findViewById<View>(R.id.controlsRow).visibility = View.GONE
        }
        findViewById<Button>(R.id.btnHangup).setOnClickListener { finish() }
    }

    private fun startRinging() {
        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(
                this, RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .build()
                play()
            }
        } catch (_: Exception) {}
        try {
            vibrator = getSystemService(Vibrator::class.java)
            val pattern = longArrayOf(0, 800, 900)
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            }
        } catch (_: Exception) {}
    }

    private fun stopRinging() {
        try { ringtone?.stop() } catch (_: Exception) {}
        try { vibrator?.cancel() } catch (_: Exception) {}
    }

    override fun onDestroy() {
        stopRinging()
        ThemeUtil.forget(this)
        super.onDestroy()
    }
}
