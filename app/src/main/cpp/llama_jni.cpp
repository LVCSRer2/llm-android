#include <jni.h>
#include <string>
#include <vector>
#include <atomic>
#include <android/log.h>
#include <chrono>
#include <unistd.h>
#include "llama.h"

#ifdef GGML_USE_OPENMP
#include <omp.h>
#endif

#define TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model *model = nullptr;
static llama_context *ctx = nullptr;
static llama_sampler *smpl = nullptr;
static std::atomic<bool> stop_generation{false};

static int get_optimal_threads() {
    int cores = sysconf(_SC_NPROCESSORS_ONLN);
    return (cores <= 4) ? cores : 6; 
}

static std::vector<llama_token> tokenize(const llama_vocab *vocab, const std::string &text, bool add_special, bool parse_special) {
    int n_tokens = text.length() + 2;
    std::vector<llama_token> tokens(n_tokens);
    n_tokens = llama_tokenize(vocab, text.c_str(), text.length(), tokens.data(), tokens.size(), add_special, parse_special);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        llama_tokenize(vocab, text.c_str(), text.length(), tokens.data(), tokens.size(), add_special, parse_special);
        n_tokens = -n_tokens;
    }
    tokens.resize(n_tokens);
    return tokens;
}

static jstring bytesToJavaString(JNIEnv *env, const char *data, int len) {
    jbyteArray bytes = env->NewByteArray(len);
    env->SetByteArrayRegion(bytes, 0, len, reinterpret_cast<const jbyte *>(data));
    jclass strClass = env->FindClass("java/lang/String");
    jmethodID ctor = env->GetMethodID(strClass, "<init>", "([BLjava/lang/String;)V");
    jstring charset = env->NewStringUTF("UTF-8");
    jstring result = (jstring) env->NewObject(strClass, ctor, bytes, charset);
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(strClass);
    env->DeleteLocalRef(charset);
    return result;
}

static int validUtf8Len(const std::string &buf) {
    int len = (int)buf.size();
    if (len == 0) return 0;
    for (int i = 1; i <= 3 && i <= len; i++) {
        unsigned char c = (unsigned char)buf[len - i];
        if ((c & 0x80) == 0) return len;
        if ((c & 0xC0) == 0xC0) {
            int expected;
            if ((c & 0xE0) == 0xC0) expected = 2;
            else if ((c & 0xF0) == 0xE0) expected = 3;
            else if ((c & 0xF8) == 0xF0) expected = 4;
            else return len;
            if (i < expected) return len - i;
            return len;
        }
    }
    return len;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_gemma_LlamaModel_loadModel(JNIEnv *env, jobject, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Loading model: %s", path);
    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;
    mparams.use_mmap = true;
    model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(model_path, path);
    if (!model) return JNI_FALSE;

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 2048;
    cparams.n_threads = get_optimal_threads();
    cparams.n_threads_batch = cparams.n_threads;
    ctx = llama_init_from_model(model, cparams);
    if (!ctx) { llama_model_free(model); model = nullptr; return JNI_FALSE; }

    smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.8f));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_com_example_gemma_LlamaModel_countTokensNative(JNIEnv *env, jobject, jstring text) {
    if (!model) return 0;
    const char *cstr = env->GetStringUTFChars(text, nullptr);
    std::string str(cstr);
    env->ReleaseStringUTFChars(text, cstr);
    
    const llama_vocab *vocab = llama_model_get_vocab(model);
    std::vector<llama_token> tokens = tokenize(vocab, str, true, true);
    return (jint)tokens.size();
}

JNIEXPORT void JNICALL
Java_com_example_gemma_LlamaModel_updateSampler(JNIEnv *, jobject,
    jfloat temperature, jint topK, jfloat topP, jfloat repeatPenalty) {
    if (smpl) llama_sampler_free(smpl);
    smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (repeatPenalty > 1.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_penalties(256, repeatPenalty, 0.0f, 0.0f));
    }
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
}

JNIEXPORT jstring JNICALL
Java_com_example_gemma_LlamaModel_formatPromptNative(JNIEnv *env, jobject, jstring jsystem, jstring juser) {
    if (!model) return env->NewStringUTF("");
    const char *sys_cstr = env->GetStringUTFChars(jsystem, nullptr);
    const char *usr_cstr = env->GetStringUTFChars(juser, nullptr);
    
    std::vector<llama_chat_message> messages;
    if (strlen(sys_cstr) > 0) messages.push_back({"system", sys_cstr});
    messages.push_back({"user", usr_cstr});

    const char * tmpl = llama_model_chat_template(model, nullptr);
    if (tmpl == nullptr) {
        std::string fallback = std::string(sys_cstr) + "\n\nUser: " + std::string(usr_cstr) + "\nAssistant: ";
        env->ReleaseStringUTFChars(jsystem, sys_cstr);
        env->ReleaseStringUTFChars(juser, usr_cstr);
        return env->NewStringUTF(fallback.c_str());
    }

    int n = llama_chat_apply_template(tmpl, messages.data(), messages.size(), true, nullptr, 0);
    if (n < 0) {
        env->ReleaseStringUTFChars(jsystem, sys_cstr);
        env->ReleaseStringUTFChars(juser, usr_cstr);
        return env->NewStringUTF(usr_cstr);
    }

    std::vector<char> buf(n + 1);
    llama_chat_apply_template(tmpl, messages.data(), messages.size(), true, buf.data(), buf.size());
    buf[n] = '\0';

    env->ReleaseStringUTFChars(jsystem, sys_cstr);
    env->ReleaseStringUTFChars(juser, usr_cstr);
    return env->NewStringUTF(buf.data());
}

JNIEXPORT jstring JNICALL
Java_com_example_gemma_LlamaModel_generate(JNIEnv *env, jobject thiz, jstring jprompt, jint max_tokens) {
    if (!model || !ctx) return env->NewStringUTF("Error: model not loaded");
    const char *prompt_cstr = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(prompt_cstr);
    env->ReleaseStringUTFChars(jprompt, prompt_cstr);
    
    const llama_vocab *vocab = llama_model_get_vocab(model);
    // CRITICAL FIX: add_special=false to avoid duplicate BOS/formatting tokens
    std::vector<llama_token> tokens = tokenize(vocab, prompt, false, true);
    if (tokens.empty()) return env->NewStringUTF("Error: tokenization failed");
    
    llama_memory_seq_rm(llama_get_memory(ctx), -1, -1, -1);

    auto t_start_enc = std::chrono::high_resolution_clock::now();
    int n_batch = 512;
    for (int i = 0; i < (int)tokens.size(); i += n_batch) {
        int n_eval = (int)tokens.size() - i;
        if (n_eval > n_batch) n_eval = n_batch;
        llama_batch batch = llama_batch_get_one(tokens.data() + i, n_eval);
        if (llama_decode(ctx, batch) != 0) return env->NewStringUTF("Error: decode failed");
    }
    auto t_end_enc = std::chrono::high_resolution_clock::now();
    double t_enc_ms = std::chrono::duration<double, std::milli>(t_end_enc - t_start_enc).count();

    auto t_start_dec = std::chrono::high_resolution_clock::now();
    jclass cls = env->GetObjectClass(thiz);
    jmethodID mid = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)V");
    std::string result;
    std::string pending;
    int n_gen = 0;
    int n_cur = (int)tokens.size();
    stop_generation.store(false);

    while (n_gen < max_tokens && n_cur + 1 < llama_n_ctx(ctx) && !stop_generation.load()) {
        llama_token new_token = llama_sampler_sample(smpl, ctx, -1);
        if (llama_vocab_is_eog(vocab, new_token)) break;

        char buf[256];
        int n = llama_token_to_piece(vocab, new_token, buf, sizeof(buf), 0, false);
        if (n > 0) {
            result.append(buf, n);
            pending.append(buf, n);
            int safe = validUtf8Len(pending);
            if (safe > 0 && mid) {
                jstring token_str = bytesToJavaString(env, pending.c_str(), safe);
                env->CallVoidMethod(thiz, mid, token_str);
                env->DeleteLocalRef(token_str);
                pending = pending.substr(safe);
            }
        }
        llama_batch single = llama_batch_get_one(&new_token, 1);
        if (llama_decode(ctx, single) != 0) break;
        n_gen++;
        n_cur++;
    }
    auto t_end_dec = std::chrono::high_resolution_clock::now();
    double t_dec_ms = std::chrono::duration<double, std::milli>(t_end_dec - t_start_dec).count();

    if (!pending.empty() && mid) {
        jstring token_str = bytesToJavaString(env, pending.c_str(), pending.size());
        env->CallVoidMethod(thiz, mid, token_str);
        env->DeleteLocalRef(token_str);
    }
    env->DeleteLocalRef(cls);

    double enc_tps = (tokens.size() / (t_enc_ms / 1000.0));
    double dec_tps = (n_gen / (t_dec_ms / 1000.0));
    char perf_buf[512];
    snprintf(perf_buf, sizeof(perf_buf), 
        "\n\n[성능 통계]\n"
        "- 입력: %zu 토큰 (%.2f초, %.2f t/s)\n"
        "- 출력: %d 토큰 (%.2f초, %.2f t/s)",
        tokens.size(), t_enc_ms / 1000.0, enc_tps,
        n_gen, t_dec_ms / 1000.0, dec_tps);
    result.append(perf_buf);

    return bytesToJavaString(env, result.c_str(), result.size());
}

JNIEXPORT void JNICALL
Java_com_example_gemma_LlamaModel_stopGeneration(JNIEnv *, jobject) {
    stop_generation.store(true);
}

JNIEXPORT void JNICALL
Java_com_example_gemma_LlamaModel_freeModelNative(JNIEnv *, jobject) {
    if (smpl) { llama_sampler_free(smpl); smpl = nullptr; }
    if (ctx) { llama_free(ctx); ctx = nullptr; }
    if (model) { llama_model_free(model); model = nullptr; }
}

} // extern "C"
