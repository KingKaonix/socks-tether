package com.example.sockstether

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket

class SocksVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var running = false
    private var proxyHost = "192.168.49.1"
    private var proxyPort = 8000

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        proxyHost = intent?.getStringExtra("host") ?: "192.168.49.1"
        proxyPort = intent?.getIntExtra("port", 8000) ?: 8000

        startForeground(1, createNotification())
        setupVpn()
        return START_STICKY
    }

    private fun setupVpn() {
        val builder = Builder()
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .setSession("SocksTether")
            .setMtu(1500)

        vpnInterface = builder.establish()
        running = true

        Thread {
            runVpnLoop()
        }.start()
    }

    private fun runVpnLoop() {
        val vpnFd = vpnInterface?.fileDescriptor ?: return
        val input = FileInputStream(vpnFd)
        val output = FileOutputStream(vpnFd)
        val buffer = ByteArray(32767)

        while (running) {
            try {
                val length = input.read(buffer)
                if (length > 0) {
                    // Parse IP packet and forward through SOCKS proxy
                    // This is a simplified version - full implementation
                    // would parse IP/TCP/UDP headers and handle connections
                    processPacket(buffer, length, output)
                }
            } catch (e: Exception) {
                Log.e("SocksVpn", "Error reading VPN: ${e.message}")
                break
            }
        }
    }

    private fun processPacket(buffer: ByteArray, length: Int, output: FileOutputStream) {
        // Simplified: just log the packet
        // Full implementation would:
        // 1. Parse IP header to get destination
        // 2. Parse TCP/UDP header to get port
        // 3. Create SOCKS connection to proxyHost:proxyPort
        // 4. Forward data bidirectionally
        Log.d("SocksVpn", "Received packet: $length bytes")
    }

    private fun createNotification(): Notification {
        val channelId = "sockstether"
        val channel = NotificationChannel(channelId, "SocksTether", NotificationManager.IMPORTANCE_LOW)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, channelId)
            .setContentTitle("SocksTether VPN")
            .setContentText("VPN is active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        running = false
        vpnInterface?.close()
        super.onDestroy()
    }
}
