#define LOG_TAG "OpenFluxJNI"

#include "jni.h"
#include <android/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>

#include <sys/un.h>
#include <sys/socket.h>
#include <ancillary.h>

#define LOGI(...) do { __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__); } while(0)
#define LOGW(...) do { __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__); } while(0)
#define LOGE(...) do { __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__); } while(0)

extern "C" void
Java_io_github_p1neapplexpress_openflux_NativeBridge_jniclose(
        JNIEnv *env, jobject thiz, jint fd) {
    close(fd);
}

extern "C" jint
Java_io_github_p1neapplexpress_openflux_NativeBridge_sendfd(
        JNIEnv *env, jobject thiz, jint tun_fd, jstring sock) {
    int fd;
    struct sockaddr_un addr;
    const char *sockpath;

    if ((fd = socket(AF_UNIX, SOCK_STREAM, 0)) == -1) {
        LOGE("socket() failed: %s (socket fd = %d)", strerror(errno), fd);
        return (jint)-1;
    }

    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    sockpath = env->GetStringUTFChars(sock, 0);
    if (sockpath == NULL) {
        LOGE("GetStringUTFChars failed");
        close(fd);
        return (jint)-1;
    }
    strncpy(addr.sun_path, sockpath, sizeof(addr.sun_path) - 1);
    env->ReleaseStringUTFChars(sock, sockpath);

    if (connect(fd, (struct sockaddr*)&addr, sizeof(addr)) == -1) {
        LOGE("connect() failed: %s (fd = %d)", strerror(errno), fd);
        close(fd);
        return (jint)-1;
    }

    if (ancil_send_fd(fd, tun_fd)) {
        LOGE("ancil_send_fd: %s", strerror(errno));
        close(fd);
        return (jint)-1;
    }

    close(fd);
    return 0;
}

static const char *classPathName =
        "io/github/p1neapplexpress/openflux/NativeBridge";

static JNINativeMethod method_table[] = {
        { "jniclose", "(I)V",
                (void*) Java_io_github_p1neapplexpress_openflux_NativeBridge_jniclose },
        { "sendfd", "(ILjava/lang/String;)I",
                (void*) Java_io_github_p1neapplexpress_openflux_NativeBridge_sendfd }
};

static int registerNativeMethods(JNIEnv* env, const char* className,
                                 JNINativeMethod* gMethods, int numMethods)
{
    jclass clazz = env->FindClass(className);
    if (clazz == NULL) {
        LOGE("Native registration unable to find class '%s'", className);
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        LOGE("RegisterNatives failed for '%s'", className);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

static int registerNatives(JNIEnv* env) {
    return registerNativeMethods(env, classPathName, method_table,
                                 sizeof(method_table) / sizeof(method_table[0]));
}

typedef union {
    JNIEnv* env;
    void* venv;
} UnionJNIEnvToVoid;

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    UnionJNIEnvToVoid uenv;
    uenv.venv = NULL;
    jint result = -1;
    JNIEnv* env = NULL;

    LOGI("JNI_OnLoad");

    if (vm->GetEnv(&uenv.venv, JNI_VERSION_1_4) != JNI_OK) {
        LOGE("ERROR: GetEnv failed");
        goto bail;
    }
    env = uenv.env;

    if (registerNatives(env) != JNI_TRUE) {
        LOGE("ERROR: registerNatives failed");
        goto bail;
    }

    result = JNI_VERSION_1_4;

    bail:
    return result;
}