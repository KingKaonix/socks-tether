#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <errno.h>

#define LOG_TAG "SocksTether"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global state
static int running = 0;
static int tun_fd = -1;
static int proxy_sock = -1;
static char proxy_host[256];
static int proxy_port = 0;

// SOCKS5 handshake
static int socks5_connect(const char* host, int port, const char* target_host, int target_port) {
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
    inet_pton(AF_INET, host, &addr.sin_addr);

    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) return -1;

    if (connect(sock, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        close(sock);
        return -1;
    }

    // SOCKS5 greeting: version 5, 1 auth method (no auth)
    unsigned char greeting[] = {0x05, 0x01, 0x00};
    send(sock, greeting, 3, 0);

    // Read response
    unsigned char resp[2];
    recv(sock, resp, 2, 0);
    if (resp[0] != 0x05 || resp[1] != 0x00) {
        close(sock);
        return -1;
    }

    // SOCKS5 CONNECT request
    // version, cmd (CONNECT=1), rsv, addr type (IPv4=1), IP, port
    struct in_addr target_addr;
    inet_pton(AF_INET, target_host, &target_addr);

    unsigned char request[10];
    request[0] = 0x05; // version
    request[1] = 0x01; // CONNECT
    request[2] = 0x00; // reserved
    request[3] = 0x01; // IPv4
    memcpy(&request[4], &target_addr.s_addr, 4);
    request[8] = (target_port >> 8) & 0xFF;
    request[9] = target_port & 0xFF;

    send(sock, request, 10, 0);

    // Read response
    unsigned char connect_resp[10];
    recv(sock, connect_resp, 10, 0);
    if (connect_resp[1] != 0x00) {
        close(sock);
        return -1;
    }

    return sock;
}

JNIEXPORT jint JNICALL
Java_com_example_sockstether_Tun2socks_start(JNIEnv *env, jobject thiz,
                                             jint fd, jstring proxy_host_str, jint proxy_port_val) {
    const char *host = (*env)->GetStringUTFChars(env, proxy_host_str, NULL);
    LOGI("Starting SocksTether: fd=%d, proxy=%s:%d", fd, host, proxy_port_val);

    strncpy(proxy_host, host, sizeof(proxy_host) - 1);
    proxy_port = proxy_port_val;
    tun_fd = fd;
    running = 1;

    // Test SOCKS5 connection to proxy
    int test_sock = socks5_connect(proxy_host, proxy_port, "8.8.8.8", 53);
    if (test_sock >= 0) {
        LOGI("SOCKS5 proxy connection successful");
        close(test_sock);
    } else {
        LOGE("SOCKS5 proxy connection failed");
    }

    (*env)->ReleaseStringUTFChars(env, proxy_host_str, host);
    return 0;
}

JNIEXPORT void JNICALL
Java_com_example_sockstether_Tun2socks_stop(JNIEnv *env, jobject thiz) {
    LOGI("Stopping SocksTether");
    running = 0;
    if (proxy_sock >= 0) {
        close(proxy_sock);
        proxy_sock = -1;
    }
    tun_fd = -1;
}

JNIEXPORT jboolean JNICALL
Java_com_example_sockstether_Tun2socks_isRunning(JNIEnv *env, jobject thiz) {
    return running ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_example_sockstether_Tun2socks_testSocks5(JNIEnv *env, jobject thiz,
                                                   jstring host_str, jint port) {
    const char *host = (*env)->GetStringUTFChars(env, host_str, NULL);
    int sock = socks5_connect(host, port, "8.8.8.8", 53);
    (*env)->ReleaseStringUTFChars(env, host_str, host);
    if (sock >= 0) {
        close(sock);
        return 1;
    }
    return 0;
}
