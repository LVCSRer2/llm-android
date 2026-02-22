# LLM Android

Android on-device LLM chat application supporting multiple models with two inference engines.

## Features

- **Dual Inference Engine**: MediaPipe (GPU) for Gemma models, llama.cpp (CPU) for GGUF models
- **8 Supported Models**: Gemma 3 1B, Gemma 3n E2B, Gemma 3 GGUF, Qwen3 1.7B, Llama 3.2 3B, TinyLlama 1.1B, Phi-3.5 Mini, SmolLM2 1.7B
- **In-app Model Download**: HuggingFace integration with authentication support for gated models
- **Configurable Parameters**: System prompt, temperature, top-k, top-p, max tokens, repeat penalty
- **Real-time Memory Overlay**: APP/NAT/FREE memory usage display during inference
- **Streaming Token Generation**: Real-time response display with stop generation support
- **Context Overflow Protection**: 4096 token context with batch processing (512 tokens)

## Architecture

```
┌─────────────────────────────────────────────┐
│                  Compose UI                  │
│  ChatScreen / SettingsScreen / ModelSelect   │
├─────────────────────────────────────────────┤
│              ChatViewModel                   │
│         ChatEngine Interface                 │
├──────────────────┬──────────────────────────┤
│  MediaPipeEngine │     LlamaCppEngine        │
│  (GPU - OpenCL)  │     (CPU - llama.cpp)     │
├──────────────────┼──────────────────────────┤
│  InferenceModel  │      LlamaModel           │
│  (tasks-genai)   │      (JNI bridge)         │
├──────────────────┼──────────────────────────┤
│  MediaPipe LLM   │  llama.cpp (C++)          │
│  Inference API   │  llama_jni.cpp            │
└──────────────────┴──────────────────────────┘
```

## Supported Models

| Model | Engine | Size | Auth Required |
|-------|--------|------|:---:|
| Gemma 3 1B (MediaPipe GPU) | MediaPipe | ~529MB | Yes |
| Gemma 3n E2B (MediaPipe GPU) | MediaPipe | ~3.7GB | Yes |
| Gemma 3 1B (llama.cpp CPU) | llama.cpp | ~806MB | No |
| Qwen3 1.7B | llama.cpp | ~1.1GB | No |
| Llama 3.2 3B | llama.cpp | ~2.0GB | No |
| TinyLlama 1.1B | llama.cpp | ~0.7GB | No |
| Phi-3.5 Mini 3.8B | llama.cpp | ~2.2GB | No |
| SmolLM2 1.7B | llama.cpp | ~1.0GB | No |

## Requirements

- Android 10+ (API 29)
- ARM64 (arm64-v8a) device
- 4GB+ RAM recommended (6GB+ for larger models)
- Android SDK, NDK 25.1, CMake 3.22.1

## Build

```bash
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk-17

./gradlew assembleDebug
```

## Project Structure

```
app/src/main/
├── java/com/example/gemma/
│   ├── MainActivity.kt          # Navigation & entry point
│   ├── ChatViewModel.kt         # Chat logic & engine management
│   ├── ChatScreen.kt            # Chat UI & memory overlay
│   ├── ChatSettings.kt          # Settings persistence (SharedPreferences)
│   ├── SettingsScreen.kt        # Settings UI (parameters, model select)
│   ├── ModelSelectScreen.kt     # Model selection UI
│   ├── ModelDownloader.kt       # ModelType enum & HuggingFace download
│   ├── DownloadScreen.kt        # Download progress UI
│   ├── InferenceModel.kt        # MediaPipe LLM wrapper
│   ├── LlamaModel.kt            # llama.cpp JNI wrapper
│   ├── ChatUiState.kt           # Chat message state
│   └── ChatMessage.kt           # Message data class
├── cpp/
│   ├── CMakeLists.txt            # Native build config
│   └── llama_jni.cpp             # JNI bridge (load, generate, sampler)
└── AndroidManifest.xml
llama.cpp/                        # llama.cpp submodule
```

## Tech Stack

- **UI**: Jetpack Compose + Material 3
- **Inference**: MediaPipe tasks-genai 0.10.27, llama.cpp (static build)
- **Language**: Kotlin, C++ (JNI)
- **Networking**: OkHttp 4.12
- **Build**: Gradle + CMake

## License

This project uses [llama.cpp](https://github.com/ggerganov/llama.cpp) (MIT License) and [MediaPipe](https://github.com/google-ai-edge/mediapipe) (Apache 2.0).
