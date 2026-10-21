package com.example.sockstether

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var hostInput: EditText
    private lateinit var portInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        hostInput = EditText(this).apply {
            hint = "Proxy host (e.g. 192.168.49.1)"
            setText("192.168.49.1")
        }
        portInput = EditText(this).apply {
            hint = "Proxy port (e.g. 8000)"
            setText("8000")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val startBtn = Button(this).apply { text = "Start VPN" }
        val stopBtn = Button(this).apply { text = "Stop VPN" }
        statusText = TextView(this).apply { text = "Status: Stopped" }

        layout.addView(hostInput)
        layout.addView(portInput)
        layout.addView(startBtn)
        layout.addView(stopBtn)
        layout.addView(statusText)
        setContentView(layout)

        startBtn.setOnClickListener {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                startActivityForResult(intent, 0)
            } else {
                startVpn()
            }
        }

        stopBtn.setOnClickListener {
            stopService(Intent(this, SocksVpnService::class.java))
            statusText.text = "Status: Stopped"
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            startVpn()
        }
    }

    private fun startVpn() {
        val host = hostInput.text.toString()
        val port = portInput.text.toString().toIntOrNull() ?: 8000
        val intent = Intent(this, SocksVpnService::class.java).apply {
            putExtra("host", host)
            putExtra("port", port)
        }
        startForegroundService(intent)
        statusText.text = "Status: Running (VPN active)"
    }
}
