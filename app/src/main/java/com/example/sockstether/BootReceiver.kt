package com.example.sockstether

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("sockstether", Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean("auto_start", false)
            val port = prefs.getInt("port", 1080)
            if (autoStart) {
                val serviceIntent = Intent(context, SocksServerService::class.java).apply {
                    putExtra("port", port)
                }
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
