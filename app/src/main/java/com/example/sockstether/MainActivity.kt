package com.example.sockstether

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import android.widget.LinearLayout

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

    private val bgColor = Color.parseColor("#0F0F23")
    private val surfaceColor = Color.parseColor("#1A1A2E")
    private val primaryColor = Color.parseColor("#6C5CE7")
    private val accentColor = Color.parseColor("#00CEC9")
    private val textColor = Color.parseColor("#FFFFFF")
    private val textSecondary = Color.parseColor("#A0A0B8")
    private val successColor = Color.parseColor("#00B894")
    private val errorColor = Color.parseColor("#FF6B6B")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(bgColor)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        // Header
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 32)
        }
        val iconView = TextView(this).apply {
            text = "🛡️"
            textSize = 36f
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 0, 0, 0)
        }
        val title = TextView(this).apply {
            text = "SocksTether"
            textSize = 28f
            setTextColor(textColor)
            typeface = Typeface.DEFAULT_BOLD
        }
        val subtitle = TextView(this).apply {
            text = "SOCKS5 Tethering Server"
            textSize = 14f
            setTextColor(textSecondary)
        }
        titleCol.addView(title)
        titleCol.addView(subtitle)
        headerLayout.addView(iconView)
        headerLayout.addView(titleCol)
        layout.addView(headerLayout)

        // IP Card
        val ipCard = createCard()
        val ipLabel = TextView(this).apply {
            text = "YOUR IP ADDRESS"
            textSize = 11f
            setTextColor(textSecondary)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 8)
        }
        ipText = TextView(this).apply {
            text = getLocalIp()
            textSize = 24f
            setTextColor(accentColor)
            typeface = Typeface.DEFAULT_BOLD
        }
        ipCard.addView(ipLabel)
        ipCard.addView(ipText)
        layout.addView(ipCard)

        // Port Card
        val portCard = createCard()
        val portLabel = TextView(this).apply {
            text = "LISTEN PORT"
            textSize = 11f
            setTextColor(textSecondary)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 8)
        }
        portInput = EditText(this).apply {
            hint = "1080"
            setText("1080")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            textSize = 18f
            setTextColor(textColor)
            setHintTextColor(textSecondary)
            setBackgroundColor(surfaceColor)
            setPadding(24, 16, 24, 16)
        }
        portCard.addView(portLabel)
        portCard.addView(portInput)
        layout.addView(portCard)

        // Auto-start toggle
        val toggleCard = createCard()
        autoStartCheck = CheckBox(this).apply {
            text = "Auto-start on boot"
            setTextColor(textColor)
            textSize = 16f
            isChecked = getSharedPreferences("sockstether", Context.MODE_PRIVATE)
                .getBoolean("auto_start", false)
        }
        toggleCard.addView(autoStartCheck)
        layout.addView(toggleCard)

        // Buttons
        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
        }
        val startBtn = createButton("▶ Start Server", successColor)
        val stopBtn = createButton("■ Stop Server", errorColor)
        btnLayout.addView(startBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, 8, 0) })
        btnLayout.addView(stopBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(8, 0, 0, 0) })
        layout.addView(btnLayout)

        // Status Card
        val statusCard = createCard()
        val statusLabel = TextView(this).apply {
            text = "STATUS"
            textSize = 11f
            setTextColor(textSecondary)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 8)
        }
        statusText = TextView(this).apply {
            text = "Stopped"
            textSize = 18f
            setTextColor(errorColor)
            typeface = Typeface.DEFAULT_BOLD
        }
        statusCard.addView(statusLabel)
        statusCard.addView(statusText)
        layout.addView(statusCard)

        // Stats Card
        val statsCard = createCard()
        val statsLabel = TextView(this).apply {
            text = "TRAFFIC STATS"
            textSize = 11f
            setTextColor(textSecondary)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 8)
        }
        statsText = TextView(this).apply {
            text = "Connections: 0\nReceived: 0 B\nSent: 0 B"
            textSize = 14f
            setTextColor(textColor)
            typeface = Typeface.MONOSPACE
        }
        statsCard.addView(statsLabel)
        statsCard.addView(statsText)
        layout.addView(statsCard)

        // Log Card
        val logCard = createCard()
        val logLabel = TextView(this).apply {
            text = "CONNECTION LOG"
            textSize = 11f
            setTextColor(textSecondary)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 8)
        }
        logText = TextView(this).apply {
            text = "No connections yet"
            textSize = 11f
            setTextColor(textSecondary)
            typeface = Typeface.MONOSPACE
        }
        logCard.addView(logLabel)
        logCard.addView(logText)
        layout.addView(logCard)

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
            statusText.text = "Running"
            statusText.setTextColor(successColor)
            handler.post(updateRunnable)
        }

        stopBtn.setOnClickListener {
            stopService(Intent(this, SocksServerService::class.java))
            statusText.text = "Stopped"
            statusText.setTextColor(errorColor)
            handler.removeCallbacks(updateRunnable)
        }
    }

    private fun createCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surfaceColor)
            setPadding(32, 24, 32, 24)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 16)
            layoutParams = params
        }
    }

    private fun createButton(text: String, color: Int): Button {
        return Button(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(color)
            setPadding(16, 12, 16, 12)
        }
    }

    private fun updateStats() {
        val prefs = getSharedPreferences("sockstether", Context.MODE_PRIVATE)
        val port = prefs.getInt("port", 1080)
        statusText.text = "Running on port $port"
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
