#include <jni.h>
#include <string>
#include <vector>
#include <atomic>
#include <android/log.h>
#include "llama.h"

#define TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model *model = nullptr;
static llama_context *ctx = nullptr;
static llama_sampler *smpl = nullptr;
static std::atomic<bool> stop_generation{false};

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

// Safe conversion: raw bytes -> Java String via byte[] + UTF-8 charset
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

// Returns the number of valid UTF-8 bytes from the end of a buffer.
// If the buffer ends with an incomplete multi-byte sequence, returns
// the position up to which bytes are safe to emit.
static int validUtf8Len(const std::string &buf) {
    int len = (int)buf.size();
    if (len == 0) return 0;

    // Check last 1-3 bytes for an incomplete multi-byte start
    for (int i = 1; i <= 3 && i <= len; i++) {
        unsigned char c = (unsigned char)buf[len - i];
        if ((c & 0x80) == 0) {
            // ASCII — everything is valid
            return len;
        }
        if ((c & 0xC0) == 0xC0) {
            // This is a leading byte. How many bytes does it expect?
            int expected;
            if ((c & 0xE0) == 0xC0) expected = 2;
            else if ((c & 0xF0) == 0xE0) expected = 3;
            else if ((c & 0xF8) == 0xF0) expected = 4;
            else return len; // invalid leading byte, flush anyway

            int available = i; // bytes from this leading byte to end
            if (available < expected) {
                // Incomplete sequence — safe up to before this leading byte
                return len - i;
            }
            // Complete sequence
            return len;
        }
        // 0x80..0xBF = continuation byte, keep scanning backwards
    }
    // All trailing bytes are continuation bytes with no leader found in last 3
    // Probably corrupt, flush it
    return len;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_gemma_LlamaModel_loadModel(JNIEnv *env, jobject, jstring model_path, jint n_gpu_layers) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Loading model: %s (gpu_layers=%d)", path, n_gpu_layers);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = n_gpu_layers;
    model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(model_path, path);

    if (!model) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 4096;
    cparams.n_batch = 512;
    cparams.n_threads = 4;
    ctx = llama_init_from_model(model, cparams);

    if (!ctx) {
        LOGE("Failed to create context");
        llama_model_free(model);
        model = nullptr;
        return JNI_FALSE;
    }

    smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.8f));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_example_gemma_LlamaModel_updateSampler(JNIEnv *, jobject,
    jfloat temperature, jint topK, jfloat topP, jfloat repeatPenalty) {
    if (smpl) {
        llama_sampler_free(smpl);
    }
    smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (repeatPenalty > 1.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_penalties(256, repeatPenalty, 0.0f, 0.0f));
    }
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    LOGI("Sampler updated: temp=%.2f top_k=%d top_p=%.2f rep_pen=%.2f",
         temperature, topK, topP, repeatPenalty);
}

JNIEXPORT jstring JNICALL
Java_com_example_gemma_LlamaModel_generate(JNIEnv *env, jobject thiz, jstring jprompt, jint max_tokens) {
    if (!model || !ctx) {
        return env->NewStringUTF("Error: model not loaded");
    }

    const char *prompt_cstr = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(prompt_cstr);
    env->ReleaseStringUTFChars(jprompt, prompt_cstr);

    const llama_vocab *vocab = llama_model_get_vocab(model);
    std::vector<llama_token> tokens = tokenize(vocab, prompt, true, true);

    if (tokens.empty()) {
        return env->NewStringUTF("Error: tokenization failed");
    }

    // Reject if prompt exceeds context size
    int n_ctx = llama_n_ctx(ctx);
    if ((int)tokens.size() >= n_ctx) {
        LOGE("Prompt tokens (%d) exceed context size (%d)", (int)tokens.size(), n_ctx);
        return env->NewStringUTF("Error: input too long");
    }

    // Clear memory
    llama_memory_clear(llama_get_memory(ctx), true);

    // Eval prompt in chunks of n_batch
    int n_batch = 512;
    for (int i = 0; i < (int)tokens.size(); i += n_batch) {
        int n_eval = (int)tokens.size() - i;
        if (n_eval > n_batch) n_eval = n_batch;
        llama_batch batch = llama_batch_get_one(tokens.data() + i, n_eval);
        if (llama_decode(ctx, batch) != 0) {
            LOGE("Decode failed at prompt chunk %d", i);
            return env->NewStringUTF("Error: decode failed");
        }
    }

    // Cache class/method lookup outside loop
    jclass cls = env->GetObjectClass(thiz);
    jmethodID mid = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)V");

    // Generate tokens with UTF-8 buffering
    std::string result;
    std::string pending; // buffer for incomplete UTF-8 sequences
    int n_gen = 0;
    int n_cur = (int)tokens.size(); // current position in context
    stop_generation.store(false);

    while (n_gen < max_tokens && n_cur + 1 < n_ctx && !stop_generation.load()) {
        llama_token new_token = llama_sampler_sample(smpl, ctx, -1);

        if (llama_vocab_is_eog(vocab, new_token)) {
            break;
        }

        char buf[256];
        int n = llama_token_to_piece(vocab, new_token, buf, sizeof(buf), 0, true);
        if (n > 0) {
            result.append(buf, n);
            pending.append(buf, n);

            // Only emit valid UTF-8 portion
            int safe = validUtf8Len(pending);
            if (safe > 0 && mid) {
                jstring token_str = bytesToJavaString(env, pending.c_str(), safe);
                env->CallVoidMethod(thiz, mid, token_str);
                env->DeleteLocalRef(token_str);
                pending = pending.substr(safe);
            }
        }

        llama_batch single = llama_batch_get_one(&new_token, 1);
        if (llama_decode(ctx, single) != 0) {
            break;
        }
        n_gen++;
        n_cur++;
    }

    // Flush any remaining pending bytes
    if (!pending.empty() && mid) {
        jstring token_str = bytesToJavaString(env, pending.c_str(), pending.size());
        env->CallVoidMethod(thiz, mid, token_str);
        env->DeleteLocalRef(token_str);
    }

    env->DeleteLocalRef(cls);
    return bytesToJavaString(env, result.c_str(), result.size());
}

JNIEXPORT void JNICALL
Java_com_example_gemma_LlamaModel_stopGeneration(JNIEnv *, jobject) {
    stop_generation.store(true);
    LOGI("Generation stop requested");
}

JNIEXPORT void JNICALL
Java_com_example_gemma_LlamaModel_freeModel(JNIEnv *, jobject) {
    if (smpl) {
        llama_sampler_free(smpl);
        smpl = nullptr;
    }
    if (ctx) {
        llama_free(ctx);
        ctx = nullptr;
    }
    if (model) {
        llama_model_free(model);
        model = nullptr;
    }
    LOGI("Model freed");
}

} // extern "C"
