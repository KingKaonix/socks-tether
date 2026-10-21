package com.example.sockstether

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import java.io.*
import java.net.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class SocksServerService : Service() {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private var port = 1080

    // Traffic stats
    val totalBytesReceived = AtomicLong(0)
    val totalBytesSent = AtomicLong(0)
    val totalConnections = AtomicLong(0)
    val activeConnections = AtomicLong(0)

    // Connection log
    val connectionLog = mutableListOf<String>()
    private val logLock = Any()

    companion object {
        const val TAG = "SocksServer"
        const val CHANNEL_ID = "sockstether_server"
        const val NOTIF_ID = 1
        val instance: SocksServerService? = null

        fun formatBytes(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
                else -> "${bytes / (1024 * 1024 * 1024)} GB"
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        port = intent?.getIntExtra("port", 1080) ?: 1080

        // Save prefs for auto-start
        val prefs = getSharedPreferences("sockstether", Context.MODE_PRIVATE)
        prefs.edit().putInt("port", port).apply()

        startForeground(NOTIF_ID, createNotification())
        startServer()
        return START_STICKY
    }

    private fun startServer() {
        running.set(true)
        Thread {
            try {
                serverSocket = ServerSocket(port)
                Log.i(TAG, "SOCKS5 server listening on port $port")
                while (running.get()) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        executor.submit { handleClient(client) }
                    } catch (e: Exception) {
                        if (running.get()) Log.e(TAG, "Accept error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error: ${e.message}")
            }
        }.start()
    }

    private fun handleClient(client: Socket) {
        val connId = totalConnections.incrementAndGet()
        activeConnections.incrementAndGet()
        val clientAddr = client.inetAddress.hostAddress ?: "unknown"
        val startTime = System.currentTimeMillis()

        log("[$connId] New connection from $clientAddr")

        try {
            client.soTimeout = 30000
            val input = DataInputStream(client.getInputStream())
            val output = DataOutputStream(client.getOutputStream())

            // SOCKS5 greeting
            val version = input.readByte().toInt() and 0xFF
            if (version != 5) {
                client.close()
                log("[$connId] Unsupported SOCKS version: $version")
                return
            }
            val nMethods = input.readByte().toInt() and 0xFF
            val methods = ByteArray(nMethods)
            input.readFully(methods)

            // Respond: no auth required
            output.write(byteArrayOf(0x05, 0x00))
            output.flush()

            // SOCKS5 request
            val reqVer = input.readByte().toInt() and 0xFF
            val cmd = input.readByte().toInt() and 0xFF
            val rsv = input.readByte().toInt() and 0xFF
            val addrType = input.readByte().toInt() and 0xFF

            val targetHost: String
            val targetPort: Int

            when (addrType) {
                0x01 -> { // IPv4
                    val ip = ByteArray(4)
                    input.readFully(ip)
                    targetHost = "${ip[0].toInt() and 0xFF}.${ip[1].toInt() and 0xFF}.${ip[2].toInt() and 0xFF}.${ip[3].toInt() and 0xFF}"
                }
                0x03 -> { // Domain name
                    val len = input.readByte().toInt() and 0xFF
                    val domain = ByteArray(len)
                    input.readFully(domain)
                    targetHost = String(domain)
                }
                0x04 -> { // IPv6
                    val ip = ByteArray(16)
                    input.readFully(ip)
                    targetHost = InetAddress.getByAddress(ip).hostAddress ?: ""
                }
                else -> {
                    sendReply(output, 0x08)
                    client.close()
                    log("[$connId] Unsupported address type: $addrType")
                    return
                }
            }
            targetPort = (input.readByte().toInt() and 0xFF) shl 8 or (input.readByte().toInt() and 0xFF)

            log("[$connId] $clientAddr -> $targetHost:$targetPort (cmd=$cmd)")

            when (cmd) {
                0x01 -> handleConnect(client, input, output, targetHost, targetPort, connId)
                0x02 -> handleBind(output, connId)
                0x03 -> handleUdpAssociate(client, output, connId)
                else -> {
                    sendReply(output, 0x07)
                    client.close()
                    log("[$connId] Unsupported command: $cmd")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client handler error: ${e.message}")
            log("[$connId] Error: ${e.message}")
        } finally {
            activeConnections.decrementAndGet()
            val duration = System.currentTimeMillis() - startTime
            log("[$connId] Connection closed (${duration}ms)")
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun handleConnect(client: Socket, input: DataInputStream, output: DataOutputStream, host: String, port: Int, connId: Long) {
        try {
            val remote = Socket(host, port)
            remote.soTimeout = 0

            sendReply(output, 0x00, client.localAddress.hostAddress ?: "0.0.0.0", client.localPort)

            val t1 = Thread { forward(client.getInputStream(), remote.getOutputStream(), true, connId) }
            val t2 = Thread { forward(remote.getInputStream(), client.getOutputStream(), false, connId) }
            t1.start()
            t2.start()
            t1.join()
            t2.join()

            remote.close()
        } catch (e: Exception) {
            Log.e(TAG, "CONNECT failed: ${e.message}")
            sendReply(output, 0x05)
            log("[$connId] CONNECT failed: ${e.message}")
        }
    }

    private fun handleUdpAssociate(client: Socket, output: DataOutputStream, connId: Long) {
        try {
            val udpSocket = DatagramSocket()
            val relayPort = udpSocket.localPort
            val relayAddr = client.localAddress.hostAddress ?: "0.0.0.0"

            sendReply(output, 0x00, relayAddr, relayPort)
            log("[$connId] UDP ASSOCIATE on port $relayPort")

            // Forward UDP packets
            Thread {
                val buf = ByteArray(65535)
                val packet = DatagramPacket(buf, buf.size)
                while (running.get()) {
                    try {
                        udpSocket.receive(packet)
                        forwardUdpPacket(udpSocket, packet)
                    } catch (e: Exception) {
                        if (running.get()) break
                    }
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "UDP ASSOCIATE failed: ${e.message}")
            sendReply(output, 0x05)
            log("[$connId] UDP ASSOCIATE failed: ${e.message}")
        }
    }

    private fun forwardUdpPacket(relaySocket: DatagramSocket, packet: DatagramPacket) {
        val data = packet.data
        if (packet.length < 10) return

        var offset = 3 // Skip RSV(2) + FRAG(1)
        val atyp = data[offset].toInt() and 0xFF
        offset++

        val targetHost: String
        when (atyp) {
            0x01 -> {
                targetHost = "${data[offset].toInt() and 0xFF}.${data[offset+1].toInt() and 0xFF}.${data[offset+2].toInt() and 0xFF}.${data[offset+3].toInt() and 0xFF}"
                offset += 4
            }
            0x03 -> {
                val len = data[offset].toInt() and 0xFF
                offset++
                targetHost = String(data, offset, len)
                offset += len
            }
            0x04 -> {
                val ip = ByteArray(16)
                System.arraycopy(data, offset, ip, 0, 16)
                targetHost = InetAddress.getByAddress(ip).hostAddress ?: ""
                offset += 16
            }
            else -> return
        }
        val targetPort = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
        offset += 2

        val payload = data.copyOfRange(offset, packet.length)

        try {
            val addr = InetAddress.getByName(targetHost)
            val outPacket = DatagramPacket(payload, payload.size, addr, targetPort)
            totalBytesSent.addAndGet(payload.size.toLong())
            relaySocket.send(outPacket)
        } catch (e: Exception) {
            Log.e(TAG, "UDP forward error: ${e.message}")
        }
    }

    private fun handleBind(output: DataOutputStream, connId: Long) {
        sendReply(output, 0x07) // Not supported
        log("[$connId] BIND not supported")
    }

    private fun sendReply(output: DataOutputStream, status: Int, bindAddr: String = "0.0.0.0", bindPort: Int = 0) {
        val addr = try { InetAddress.getByName(bindAddr).address } catch (_: Exception) { ByteArray(4) }
        output.write(byteArrayOf(0x05, status.toByte(), 0x00, if (addr.size == 16) 0x04 else 0x01))
        output.write(addr)
        output.writeByte((bindPort shr 8) and 0xFF)
        output.writeByte(bindPort and 0xFF)
        output.flush()
    }

    private fun forward(input: InputStream, output: OutputStream, isUpload: Boolean, connId: Long) {
        val buffer = ByteArray(8192)
        try {
            while (running.get()) {
                val n = input.read(buffer)
                if (n <= 0) break
                output.write(buffer, 0, n)
                output.flush()
                if (isUpload) totalBytesReceived.addAndGet(n.toLong())
                else totalBytesSent.addAndGet(n.toLong())
            }
        } catch (_: Exception) {}
    }

    private fun log(message: String) {
        synchronized(logLock) {
            connectionLog.add("${System.currentTimeMillis()}: $message")
            if (connectionLog.size > 100) {
                connectionLog.removeAt(0)
            }
        }
        Log.i(TAG, message)
    }

    fun getConnectionLog(): List<String> {
        synchronized(logLock) {
            return connectionLog.toList()
        }
    }

    fun getStats(): String {
        return "Connections: ${totalConnections.get()} (active: ${activeConnections.get()})\n" +
               "Received: ${formatBytes(totalBytesReceived.get())}\n" +
               "Sent: ${formatBytes(totalBytesSent.get())}"
    }

    private fun createNotification(): Notification {
        val channel = NotificationChannel(CHANNEL_ID, "SocksTether Server", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("SocksTether Server")
            .setContentText("SOCKS5 proxy running on port $port")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
        executor.shutdown()
        super.onDestroy()
    }
}
