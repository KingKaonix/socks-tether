package com.example.sockstether

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var hostInput: EditText
    private lateinit var portInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        hostInput = EditText(this).apply {
            hint = "SOCKS proxy host"
            setText("192.168.49.1")
        }
        portInput = EditText(this).apply {
            hint = "SOCKS proxy port"
            setText("1080")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val testBtn = Button(this).apply { text = "Test SOCKS5" }
        val startBtn = Button(this).apply { text = "Start VPN" }
        val stopBtn = Button(this).apply { text = "Stop VPN" }
        statusText = TextView(this).apply { text = "Status: Stopped" }

        layout.addView(hostInput)
        layout.addView(portInput)
        layout.addView(testBtn)
        layout.addView(startBtn)
        layout.addView(stopBtn)
        layout.addView(statusText)
        setContentView(layout)

        testBtn.setOnClickListener {
            val host = hostInput.text.toString()
            val port = portInput.text.toString().toIntOrNull() ?: 1080
            Thread {
                val result = Tun2socks.testSocks5(host, port)
                runOnUiThread {
                    if (result == 1) {
                        Toast.makeText(this, "SOCKS5 OK!", Toast.LENGTH_SHORT).show()
                        statusText.text = "SOCKS5: Connected"
                    } else {
                        Toast.makeText(this, "SOCKS5 Failed", Toast.LENGTH_SHORT).show()
                        statusText.text = "SOCKS5: Failed"
                    }
                }
            }.start()
        }

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
        val port = portInput.text.toString().toIntOrNull() ?: 1080
        val intent = Intent(this, SocksVpnService::class.java).apply {
            putExtra("host", host)
            putExtra("port", port)
        }
        startForegroundService(intent)
        statusText.text = "Status: Running (VPN active)"
    }
}
