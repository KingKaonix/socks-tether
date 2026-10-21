package com.example.sockstether

object Tun2socks {
    init {
        System.loadLibrary("sockstether")
    }

    external fun start(fd: Int, proxyHost: String, proxyPort: Int): Int
    external fun stop()
    external fun isRunning(): Boolean
}
