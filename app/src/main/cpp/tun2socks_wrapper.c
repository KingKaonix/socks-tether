// Wrapper to include tun2socks with renamed main()
#define main tun2socks_main
#include "badvpn/tun2socks/tun2socks.c"
#undef main

#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>

#define LOG_TAG "SocksTether"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// JNI function to start tun2socks
JNIEXPORT jint JNICALL
Java_com_example_sockstether_Tun2socks_startTun2socks(JNIEnv *env, jobject thiz,
    jstring tunDev, jstring socksServer, jstring udpgwServer) {
    
    const char *tun = (*env)->GetStringUTFChars(env, tunDev, NULL);
    const char *socks = (*env)->GetStringUTFChars(env, socksServer, NULL);
    const char *udpgw = (*env)->GetStringUTFChars(env, udpgwServer, NULL);
    
    // Build argc/argv
    char *argv[] = {
        "tun2socks",
        "--tundev", (char*)tun,
        "--netif-ipaddr", "10.0.0.2",
        "--netif-netmask", "255.255.255.0",
        "--socks-server-addr", (char*)socks,
        "--udpgw-remote-server-addr", (char*)udpgw,
        NULL
    };
    int argc = 11;
    
    LOGI("Starting tun2socks: tun=%s, socks=%s, udpgw=%s", tun, socks, udpgw);
    
    // Call tun2socks main (renamed)
    int result = tun2socks_main(argc, argv);
    
    (*env)->ReleaseStringUTFChars(env, tunDev, tun);
    (*env)->ReleaseStringUTFChars(env, socksServer, socks);
    (*env)->ReleaseStringUTFChars(env, udpgwServer, udpgw);
    
    return result;
}
