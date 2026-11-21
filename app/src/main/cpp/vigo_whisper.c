// Cầu nối JNI giữa Kotlin và whisper.cpp
#include <jni.h>
#include <stdbool.h>
#include <stdlib.h>
#include <android/log.h>
#include "whisper.h"

#define TAG "VigoWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

static volatile int g_abort = 0;

typedef struct {
    JNIEnv *env;
    jobject listener;
    jmethodID onProgress;
} cb_ctx;

static void progress_cb(struct whisper_context *ctx, struct whisper_state *state, int progress, void *user_data) {
    (void) ctx; (void) state;
    cb_ctx *c = (cb_ctx *) user_data;
    if (c && c->listener && c->onProgress) {
        (*c->env)->CallVoidMethod(c->env, c->listener, c->onProgress, (jint) progress);
    }
}

static bool abort_cb(void *data) {
    (void) data;
    return g_abort != 0;
}

JNIEXPORT jlong JNICALL
Java_vn_vigo_music_ai_WhisperNative_init(JNIEnv *env, jclass clazz, jstring modelPath) {
    (void) clazz;
    const char *path = (*env)->GetStringUTFChars(env, modelPath, NULL);
    struct whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cp);
    (*env)->ReleaseStringUTFChars(env, modelPath, path);
    return (jlong) ctx;
}

JNIEXPORT void JNICALL
Java_vn_vigo_music_ai_WhisperNative_free(JNIEnv *env, jclass clazz, jlong ctx) {
    (void) env; (void) clazz;
    if (ctx) whisper_free((struct whisper_context *) ctx);
}

JNIEXPORT void JNICALL
Java_vn_vigo_music_ai_WhisperNative_abort(JNIEnv *env, jclass clazz) {
    (void) env; (void) clazz;
    g_abort = 1;
}

JNIEXPORT jint JNICALL
Java_vn_vigo_music_ai_WhisperNative_transcribe(
        JNIEnv *env, jclass clazz, jlong ctxPtr, jfloatArray audio, jint threads,
        jstring language, jstring prompt, jint maxLen, jobject listener) {
    (void) clazz;
    struct whisper_context *ctx = (struct whisper_context *) ctxPtr;
    if (!ctx) return -100;
    g_abort = 0;

    const char *lang = language ? (*env)->GetStringUTFChars(env, language, NULL) : NULL;
    const char *pr = prompt ? (*env)->GetStringUTFChars(env, prompt, NULL) : NULL;

    struct whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.n_threads = threads;
    p.translate = false;
    p.print_realtime = false;
    p.print_progress = false;
    p.print_timestamps = false;
    p.print_special = false;
    p.no_context = false;
    p.suppress_blank = true;
    p.suppress_nst = true;          // bỏ các token không phải lời như [Music]
    p.language = (lang && lang[0]) ? lang : "auto";
    p.detect_language = false;
    p.initial_prompt = (pr && pr[0]) ? pr : NULL;
    if (maxLen > 0) {               // chia dòng ngắn để chạy theo nhạc
        p.token_timestamps = true;
        p.max_len = maxLen;
        p.split_on_word = true;
    }

    cb_ctx cb = { env, listener, NULL };
    if (listener) {
        jclass cls = (*env)->GetObjectClass(env, listener);
        cb.onProgress = (*env)->GetMethodID(env, cls, "onProgress", "(I)V");
    }
    p.progress_callback = progress_cb;
    p.progress_callback_user_data = &cb;
    p.abort_callback = abort_cb;
    p.abort_callback_user_data = NULL;

    jfloat *data = (*env)->GetFloatArrayElements(env, audio, NULL);
    jsize n = (*env)->GetArrayLength(env, audio);
    LOGI("whisper_full: %d samples, %d threads, lang=%s", (int) n, (int) threads, p.language);
    int r = whisper_full(ctx, p, data, n);
    (*env)->ReleaseFloatArrayElements(env, audio, data, JNI_ABORT);

    if (lang) (*env)->ReleaseStringUTFChars(env, language, lang);
    if (pr) (*env)->ReleaseStringUTFChars(env, prompt, pr);
    if (g_abort) return -1;
    return r;
}

JNIEXPORT jint JNICALL
Java_vn_vigo_music_ai_WhisperNative_segmentCount(JNIEnv *env, jclass clazz, jlong ctx) {
    (void) env; (void) clazz;
    return whisper_full_n_segments((struct whisper_context *) ctx);
}

// Trả về byte UTF-8 thô: lời tiếng Việt có thể bị cắt giữa ký tự nhiều byte,
// NewStringUTF sẽ làm sập app với chuỗi không hợp lệ nên để Kotlin tự giải mã.
JNIEXPORT jbyteArray JNICALL
Java_vn_vigo_music_ai_WhisperNative_segmentBytes(JNIEnv *env, jclass clazz, jlong ctx, jint i) {
    (void) clazz;
    const char *t = whisper_full_get_segment_text((struct whisper_context *) ctx, i);
    if (!t) t = "";
    jsize len = 0;
    while (t[len]) len++;
    jbyteArray arr = (*env)->NewByteArray(env, len);
    (*env)->SetByteArrayRegion(env, arr, 0, len, (const jbyte *) t);
    return arr;
}

JNIEXPORT jlong JNICALL
Java_vn_vigo_music_ai_WhisperNative_segmentStart(JNIEnv *env, jclass clazz, jlong ctx, jint i) {
    (void) env; (void) clazz;
    return whisper_full_get_segment_t0((struct whisper_context *) ctx, i) * 10; // đơn vị 10ms → ms
}

JNIEXPORT jlong JNICALL
Java_vn_vigo_music_ai_WhisperNative_segmentEnd(JNIEnv *env, jclass clazz, jlong ctx, jint i) {
    (void) env; (void) clazz;
    return whisper_full_get_segment_t1((struct whisper_context *) ctx, i) * 10;
}
