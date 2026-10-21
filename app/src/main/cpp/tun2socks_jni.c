#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>

#define LOG_TAG "SocksTether"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global state
static int running = 0;
static int tun_fd = -1;

JNIEXPORT jint JNICALL
Java_com_example_sockstether_Tun2socks_start(JNIEnv *env, jobject thiz,
                                             jint fd, jstring proxy_host, jint proxy_port) {
    const char *host = (*env)->GetStringUTFChars(env, proxy_host, NULL);
    LOGI("Starting tun2socks: fd=%d, proxy=%s:%d", fd, host, proxy_port);

    tun_fd = fd;
    running = 1;

    // TODO: Initialize tun2socks library here
    // For now, just log that we started
    LOGI("tun2socks started (native bridge placeholder)");

    (*env)->ReleaseStringUTFChars(env, proxy_host, host);
    return 0;
}

JNIEXPORT void JNICALL
Java_com_example_sockstether_Tun2socks_stop(JNIEnv *env, jobject thiz) {
    LOGI("Stopping tun2socks");
    running = 0;
    tun_fd = -1;
}

JNIEXPORT jboolean JNICALL
Java_com_example_sockstether_Tun2socks_isRunning(JNIEnv *env, jobject thiz) {
    return running ? JNI_TRUE : JNI_FALSE;
}
