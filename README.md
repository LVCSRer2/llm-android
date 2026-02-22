# LLM Android

Android 온디바이스 LLM 채팅 앱. 두 가지 추론 엔진으로 다양한 모델을 지원합니다.

## 주요 기능

- **듀얼 추론 엔진**: MediaPipe (OpenCL GPU)로 Gemma 모델, llama.cpp (CPU)로 GGUF 모델 실행
- **8개 모델 지원**: Gemma 3 1B, Gemma 3n E2B, Gemma 3 GGUF, Qwen3 1.7B, Llama 3.2 3B, TinyLlama 1.1B, Phi-3.5 Mini, SmolLM2 1.7B
- **앱 내 모델 다운로드**: HuggingFace 연동, 게이트 모델 인증 지원
- **추론 파라미터 설정**: 시스템 프롬프트, temperature, top-k, top-p, 최대 토큰, 반복 패널티
- **실시간 메모리 오버레이**: 추론 중 APP/NAT/FREE 메모리 사용량 표시
- **스트리밍 토큰 생성**: 실시간 응답 표시 및 생성 중단 기능
- **컨텍스트 오버플로 보호**: 4096 토큰 컨텍스트, 512 토큰 배치 처리

## 아키텍처

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

## 지원 모델

| 모델 | 엔진 | 크기 | 인증 필요 |
|------|------|------|:---------:|
| Gemma 3 1B (MediaPipe GPU) | MediaPipe | ~529MB | O |
| Gemma 3n E2B (MediaPipe GPU) | MediaPipe | ~3.7GB | O |
| Gemma 3 1B (llama.cpp CPU) | llama.cpp | ~806MB | X |
| Qwen3 1.7B | llama.cpp | ~1.1GB | X |
| Llama 3.2 3B | llama.cpp | ~2.0GB | X |
| TinyLlama 1.1B | llama.cpp | ~0.7GB | X |
| Phi-3.5 Mini 3.8B | llama.cpp | ~2.2GB | X |
| SmolLM2 1.7B | llama.cpp | ~1.0GB | X |

## 요구 사항

- Android 10 이상 (API 29)
- ARM64 (arm64-v8a) 기기
- RAM 4GB 이상 권장 (대형 모델은 6GB 이상)
- Android SDK, NDK 25.1, CMake 3.22.1

## 빌드

```bash
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk-17

./gradlew assembleDebug
```

## 프로젝트 구조

```
app/src/main/
├── java/com/example/gemma/
│   ├── MainActivity.kt          # 네비게이션 및 진입점
│   ├── ChatViewModel.kt         # 채팅 로직 및 엔진 관리
│   ├── ChatScreen.kt            # 채팅 UI 및 메모리 오버레이
│   ├── ChatSettings.kt          # 설정 저장 (SharedPreferences)
│   ├── SettingsScreen.kt        # 설정 UI (파라미터, 모델 선택)
│   ├── ModelSelectScreen.kt     # 모델 선택 화면
│   ├── ModelDownloader.kt       # ModelType 열거형 및 HuggingFace 다운로드
│   ├── DownloadScreen.kt        # 다운로드 진행률 UI
│   ├── InferenceModel.kt        # MediaPipe LLM 래퍼
│   ├── LlamaModel.kt            # llama.cpp JNI 래퍼
│   ├── ChatUiState.kt           # 채팅 메시지 상태 관리
│   └── ChatMessage.kt           # 메시지 데이터 클래스
├── cpp/
│   ├── CMakeLists.txt            # 네이티브 빌드 설정
│   └── llama_jni.cpp             # JNI 브릿지 (모델 로드, 생성, 샘플러)
└── AndroidManifest.xml
llama.cpp/                        # llama.cpp 서브모듈
```

## 기술 스택

- **UI**: Jetpack Compose + Material 3
- **추론**: MediaPipe tasks-genai 0.10.27, llama.cpp (정적 빌드)
- **언어**: Kotlin, C++ (JNI)
- **네트워크**: OkHttp 4.12
- **빌드**: Gradle + CMake

## 라이선스

이 프로젝트는 [llama.cpp](https://github.com/ggerganov/llama.cpp) (MIT License)와 [MediaPipe](https://github.com/google-ai-edge/mediapipe) (Apache 2.0)를 사용합니다.
