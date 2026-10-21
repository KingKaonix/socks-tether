package com.example.sockstether

object Tun2socks {
    init {
        System.loadLibrary("sockstether")
    }

    external fun start(fd: Int, proxyHost: String, proxyPort: Int): Int
    external fun stop()
    external fun isRunning(): Boolean
    external fun testSocks5(host: String, port: Int): Int
    external fun startTun2socks(tunDev: String, socksServer: String, udpgwServer: String): Int
}
