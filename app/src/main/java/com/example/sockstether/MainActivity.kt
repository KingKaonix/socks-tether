package com.example.sockstether

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.LinearLayout

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var portInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "SocksTether Server"
            textSize = 20f
        }
        portInput = EditText(this).apply {
            hint = "Listen port"
            setText("1080")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val ipText = TextView(this).apply {
            text = "Your IP: ${getLocalIp()}"
        }
        val startBtn = Button(this).apply { text = "Start Server" }
        val stopBtn = Button(this).apply { text = "Stop Server" }
        statusText = TextView(this).apply { text = "Status: Stopped" }

        layout.addView(title)
        layout.addView(portInput)
        layout.addView(ipText)
        layout.addView(startBtn)
        layout.addView(stopBtn)
        layout.addView(statusText)
        setContentView(layout)

        startBtn.setOnClickListener {
            val port = portInput.text.toString().toIntOrNull() ?: 1080
            val intent = Intent(this, SocksServerService::class.java).apply {
                putExtra("port", port)
            }
            startForegroundService(intent)
            statusText.text = "Status: Running on port $port"
        }

        stopBtn.setOnClickListener {
            stopService(Intent(this, SocksServerService::class.java))
            statusText.text = "Status: Stopped"
        }
    }

    private fun getLocalIp(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: "unknown"
                    }
                }
            }
        } catch (_: Exception) {}
        return "unknown"
    }
}
