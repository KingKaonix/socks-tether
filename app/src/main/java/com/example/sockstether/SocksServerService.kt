package com.example.sockstether

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.io.*
import java.net.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SocksServerService : Service() {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private var port = 1080

    companion object {
        const val TAG = "SocksServer"
        const val CHANNEL_ID = "sockstether_server"
        const val NOTIF_ID = 1
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        port = intent?.getIntExtra("port", 1080) ?: 1080
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
        try {
            client.soTimeout = 30000
            val input = DataInputStream(client.getInputStream())
            val output = DataOutputStream(client.getOutputStream())

            // SOCKS5 greeting
            val version = input.readByte().toInt() and 0xFF
            if (version != 5) {
                client.close()
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
                    targetHost = "${ip[0] and 0xFF}.${ip[1] and 0xFF}.${ip[2] and 0xFF}.${ip[3] and 0xFF}"
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
                    sendReply(output, 0x08) // Address type not supported
                    client.close()
                    return
                }
            }
            targetPort = (input.readByte().toInt() and 0xFF) shl 8 or (input.readByte().toInt() and 0xFF)

            when (cmd) {
                0x01 -> handleConnect(client, input, output, targetHost, targetPort) // CONNECT
                0x02 -> handleBind(output) // BIND (not fully implemented)
                0x03 -> handleUdpAssociate(client, output) // UDP ASSOCIATE
                else -> {
                    sendReply(output, 0x07) // Command not supported
                    client.close()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client handler error: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun handleConnect(client: Socket, input: DataInputStream, output: DataOutputStream, host: String, port: Int) {
        try {
            val remote = Socket(host, port)
            remote.soTimeout = 0

            // Send success reply
            sendReply(output, 0x00, client.localAddress.hostAddress ?: "0.0.0.0", client.localPort)

            // Bidirectional forwarding
            val t1 = Thread { forward(client.getInputStream(), remote.getOutputStream()) }
            val t2 = Thread { forward(remote.getInputStream(), client.getOutputStream()) }
            t1.start()
            t2.start()
            t1.join()
            t2.join()

            remote.close()
        } catch (e: Exception) {
            Log.e(TAG, "CONNECT failed: ${e.message}")
            sendReply(output, 0x05) // Connection refused
        }
    }

    private fun handleUdpAssociate(client: Socket, output: DataOutputStream) {
        try {
            // Create UDP relay socket
            val udpSocket = DatagramSocket()
            val relayPort = udpSocket.localPort

            // Send reply with relay address/port
            sendReply(output, 0x00, client.localAddress.hostAddress ?: "0.0.0.0", relayPort)

            // Forward UDP packets
            Thread {
                val buf = ByteArray(65535)
                val packet = DatagramPacket(buf, buf.size)
                while (running.get()) {
                    try {
                        udpSocket.receive(packet)
                        // Parse SOCKS5 UDP header and forward
                        forwardUdpPacket(udpSocket, packet, client)
                    } catch (e: Exception) {
                        if (running.get()) Log.e(TAG, "UDP receive error: ${e.message}")
                        break
                    }
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "UDP ASSOCIATE failed: ${e.message}")
            sendReply(output, 0x05)
        }
    }

    private fun forwardUdpPacket(relaySocket: DatagramSocket, packet: DatagramPacket, client: Socket) {
        // SOCKS5 UDP header: RSV(2) + FRAG(1) + ATYP(1) + DST.ADDR + DST.PORT + DATA
        val data = packet.data
        if (packet.length < 10) return

        var offset = 3 // Skip RSV(2) + FRAG(1)
        val atyp = data[offset].toInt() and 0xFF
        offset++

        val targetHost: String
        when (atyp) {
            0x01 -> {
                targetHost = "${data[offset] and 0xFF}.${data[offset+1] and 0xFF}.${data[offset+2] and 0xFF}.${data[offset+3] and 0xFF}"
                offset += 4
            }
            0x03 -> {
                val len = data[offset].toInt() and 0xFF
                offset++
                targetHost = String(data, offset, len)
                offset += len
            }
            else -> return
        }
        val targetPort = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset+1].toInt() and 0xFF)
        offset += 2

        val payload = data.copyOfRange(offset, packet.length)

        try {
            val addr = InetAddress.getByName(targetHost)
            val outPacket = DatagramPacket(payload, payload.size, addr, targetPort)
            relaySocket.send(outPacket)
        } catch (e: Exception) {
            Log.e(TAG, "UDP forward error: ${e.message}")
        }
    }

    private fun handleBind(output: DataOutputStream) {
        // BIND is rarely used, send not supported
        sendReply(output, 0x07)
    }

    private fun sendReply(output: DataOutputStream, status: Int, bindAddr: String = "0.0.0.0", bindPort: Int = 0) {
        val addr = InetAddress.getByName(bindAddr).address
        output.write(byteArrayOf(0x05, status.toByte(), 0x00, 0x01))
        output.write(addr)
        output.writeByte((bindPort shr 8) and 0xFF)
        output.writeByte(bindPort and 0xFF)
        output.flush()
    }

    private fun forward(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        try {
            while (running.get()) {
                val n = input.read(buffer)
                if (n <= 0) break
                output.write(buffer, 0, n)
                output.flush()
            }
        } catch (_: Exception) {}
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
