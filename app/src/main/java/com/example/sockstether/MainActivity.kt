package com.example.sockstether

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.ScrollView

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var statsText: TextView
    private lateinit var logText: TextView
    private lateinit var portInput: EditText
    private lateinit var ipText: TextView
    private lateinit var autoStartCheck: CheckBox
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateStats()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
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
        ipText = TextView(this).apply {
            text = "Your IP: ${getLocalIp()}"
        }
        autoStartCheck = CheckBox(this).apply {
            text = "Auto-start on boot"
            isChecked = getSharedPreferences("sockstether", Context.MODE_PRIVATE)
                .getBoolean("auto_start", false)
        }
        val startBtn = Button(this).apply { text = "Start Server" }
        val stopBtn = Button(this).apply { text = "Stop Server" }
        statusText = TextView(this).apply { text = "Status: Stopped" }
        statsText = TextView(this).apply { text = "Stats: --" }
        logText = TextView(this).apply {
            text = "Connection Log:\n"
            textSize = 10f
        }

        layout.addView(title)
        layout.addView(portInput)
        layout.addView(ipText)
        layout.addView(autoStartCheck)
        layout.addView(startBtn)
        layout.addView(stopBtn)
        layout.addView(statusText)
        layout.addView(statsText)
        layout.addView(logText)
        scroll.addView(layout)
        setContentView(scroll)

        autoStartCheck.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences("sockstether", Context.MODE_PRIVATE)
                .edit().putBoolean("auto_start", isChecked).apply()
        }

        startBtn.setOnClickListener {
            val port = portInput.text.toString().toIntOrNull() ?: 1080
            val intent = Intent(this, SocksServerService::class.java).apply {
                putExtra("port", port)
            }
            startForegroundService(intent)
            statusText.text = "Status: Running on port $port"
            handler.post(updateRunnable)
        }

        stopBtn.setOnClickListener {
            stopService(Intent(this, SocksServerService::class.java))
            statusText.text = "Status: Stopped"
            handler.removeCallbacks(updateRunnable)
        }
    }

    private fun updateStats() {
        // Stats would need to be retrieved from the service
        // For now, just update the log display
        val prefs = getSharedPreferences("sockstether", Context.MODE_PRIVATE)
        val port = prefs.getInt("port", 1080)
        statusText.text = "Status: Running on port $port"
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

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }
}
