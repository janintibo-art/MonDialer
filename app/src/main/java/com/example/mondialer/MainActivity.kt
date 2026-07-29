package com.example.mondialer

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var display: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BlockRulesStore.appCtx = applicationContext
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
            findViewById<Button>(id).setOnClickListener { display.append(d) }
        }

        findViewById<Button>(R.id.btnDelete).setOnClickListener {
            val t = display.text.toString()
            if (t.isNotEmpty()) display.setText(t.dropLast(1))
            display.setSelection(display.text.length)
        }

        findViewById<Button>(R.id.btnCall).setOnClickListener { placeCall() }

        findViewById<Button>(R.id.btnFilters).setOnClickListener {
            startActivity(Intent(this, FiltersActivity::class.java))
        }
        findViewById<Button>(R.id.btnContacts).setOnClickListener {
            startActivityForResult(Intent(this, ContactsActivity::class.java), 20)
        }
        findViewById<Button>(R.id.btnLog).setOnClickListener {
            startActivityForResult(Intent(this, CallLogActivity::class.java), 30)
        }
        findViewById<Button>(R.id.btnDefault).setOnClickListener { requestDefaultDialer() }
    }

    private fun placeCall() {
        val number = display.text.toString().trim()
        if (number.isEmpty()) return
        if (checkSelfPermission(android.Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CALL_PHONE), 1)
            return
        }
        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == 1 && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) placeCall()
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
            20, 30 -> if (resultCode == RESULT_OK) {
                val n = data?.getStringExtra("number") ?: return
                display.setText(n)
                display.setSelection(display.text.length)
            }
        }
    }
}
