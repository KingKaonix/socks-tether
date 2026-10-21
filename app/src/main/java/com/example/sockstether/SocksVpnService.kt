package com.example.sockstether

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log

class SocksVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var running = false
    private var proxyHost = "192.168.49.1"
    private var proxyPort = 1080

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        proxyHost = intent?.getStringExtra("host") ?: "192.168.49.1"
        proxyPort = intent?.getIntExtra("port", 1080) ?: 1080

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

        val fd = vpnInterface?.fd ?: -1
        if (fd >= 0) {
            Thread {
                val socksServer = "$proxyHost:$proxyPort"
                val udpgwServer = "$proxyHost:7300"
                val result = Tun2socks.startTun2socks("fd://$fd", socksServer, udpgwServer)
                Log.i("SocksVpn", "tun2socks exited with code: $result")
            }.start()
            Log.i("SocksVpn", "Started tun2socks with fd=$fd, proxy=$proxyHost:$proxyPort")
        } else {
            Log.e("SocksVpn", "Failed to establish VPN interface")
            stopSelf()
        }
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
            .setContentText("VPN is active - $proxyHost:$proxyPort")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        running = false
        Tun2socks.stop()
        vpnInterface?.close()
        super.onDestroy()
    }
}
