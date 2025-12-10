<p align="center">
  <img src="logo.svg" alt="CopiloTTS Logo" width="200">
</p>

# CopiloTTS

A Kotlin Multiplatform Text-to-Speech SDK with support for native platform engines and HuggingFace open-source models.

## Features

- **Native TTS Engines**: Android `TextToSpeech` and iOS `AVSpeechSynthesizer` with full multilingual support
- **HuggingFace Models**: High-quality Supertonic English TTS model via ONNX Runtime (4 voice styles: F1, F2, M1, M2)
- **Full-Featured API**: SSML, voice selection, callbacks, progress tracking, priority queuing
- **Easy Integration**: Simple API with sensible defaults
- **Kotlin Multiplatform**: Shared code for Android and iOS (Compose Multiplatform)

<p>
  <img src="ic_drivista.svg" alt="Drivista" width="18" style="vertical-align: middle;">
  <strong>As used in <a href="https://drivista.app">Drivista</a></strong> - the ultimate route planning and navigation app for driving enthusiasts.
</p>

# Demo

https://github.com/user-attachments/assets/ab566f24-4248-484c-86a2-e9557a130f2f

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("app.drivista:copilotts:1.0.0")
}
```

## Quick Start

```kotlin
// Initialize
val tts = CopiloTTS()
tts.initialize()

// Simple speech
tts.speak("Hello, world!")

// With options
tts.speak(SpeakRequest(
    text = "Hello with options!",
    priority = TTSPriority.HIGH,
    speechRate = 1.2f,
    volume = 0.8f
))

// Cleanup
tts.shutdown()
```

## Configuration

```kotlin
val config = TTSConfig(
    preferredEngine = TTSEngineType.NATIVE,
    defaultSpeechRate = 1.0f,
    defaultVolume = 0.8f,
    audioDucking = true,
    queueConfig = QueueConfig(
        maxQueueSize = 100,
        enablePriorityQueue = true
    )
)

val tts = CopiloTTS(config)
```

## Voice Selection

```kotlin
// Get available voices
val voices = tts.availableVoices.value

// Set a specific voice
tts.setVoice(voices.first())
```

## SSML Support

```kotlin
val ssml = ssml {
    text("Hello, ")
    emphasis("world", EmphasisLevel.STRONG)
    pause(500)
    prosody(text = "This is slower", rate = "slow")
}

tts.speak(SpeakRequest(text = "Hello", ssml = ssml))
```

## Callbacks

```kotlin
tts.setCallback(ttsCallback {
    onStart { id -> println("Started: $id") }
    onDone { id -> println("Done: $id") }
    onProgress { id, progress, word ->
        println("Progress: ${(progress * 100).toInt()}%")
    }
    onError { id, error ->
        println("Error: ${error.message}")
    }
})
```

## Priority Queue

```kotlin
// Normal priority (queued)
tts.speak(SpeakRequest(text = "First", priority = TTSPriority.NORMAL))
tts.speak(SpeakRequest(text = "Second", priority = TTSPriority.NORMAL))

// Urgent priority (interrupts current speech)
tts.speak(SpeakRequest(text = "Urgent!", priority = TTSPriority.URGENT))
```

## HuggingFace Models

CopiloTTS supports HuggingFace open-source TTS models via ONNX Runtime on both Android and iOS. Models can be bundled with your app or downloaded on-demand with progress tracking.

### Model Management APIs

The SDK provides comprehensive APIs for building model management UIs:

```kotlin
// Get all supported models with metadata
val models: List<ModelInfo> = tts.getAllSupportedModels()

// Get model status (downloaded, downloading, available)
val statuses: List<ModelStatus> = tts.getModelStatuses()
statuses.forEach { status ->
    println("${status.model.displayName}: ${status.state}")
    // state can be: NOT_DOWNLOADED, DOWNLOADING, DOWNLOADED, BUNDLED
    // downloaded models include file path and size
}

// Check specific model status
val status = tts.getModelStatus("Supertone/supertonic")
```

### Download with Progress

```kotlin
// Download with Flow-based progress tracking
tts.downloadModelWithProgress(modelId = "Supertone/supertonic")
    .collect { progress ->
        when (progress) {
            is DownloadProgress.Starting -> showLoading()
            is DownloadProgress.Progress -> {
                updateProgressBar(progress.percent)
                updateProgressText("${progress.downloadedBytes} / ${progress.totalBytes}")
            }
            is DownloadProgress.Completed -> showSuccess()
            is DownloadProgress.Error -> showError(progress.message)
        }
    }

// Cancel ongoing download
tts.cancelModelDownload(modelId)

// Check if any download is in progress
val isDownloading = tts.isAnyModelDownloading()
```

### Storage Info

```kotlin
// Get storage information for UI
val storageInfo = tts.getStorageInfo()
println("Used: ${storageInfo.usedSpaceFormatted}")      // "1.2 GB"
println("Available: ${storageInfo.availableSpaceFormatted}") // "8.5 GB"
println("Downloaded models: ${storageInfo.downloadedModelCount}")
```

### Switch Between Engines

```kotlin
// Use a HuggingFace model
val result = tts.useHuggingFaceModel("Supertone/supertonic")
result.onSuccess { tts.speak("Now using Supertonic model") }
       .onError { println("Failed: ${it.message}") }

// Switch back to native engine
tts.useNativeEngine()

// Check current engine
val engineType = tts.getActiveEngineType()

// Check if HuggingFace is available (model downloaded)
val available = tts.isHuggingFaceAvailable()
```

### Delete Models

```kotlin
// Delete a downloaded model
tts.deleteModel("Supertone/supertonic")
```

### Example: Model Selection UI

```kotlin
@Composable
fun ModelSelectionScreen(viewModel: TTSViewModel) {
    val statuses by viewModel.modelStatuses.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()

    LazyColumn {
        items(statuses) { status ->
            ModelCard(
                model = status.model,
                state = status.state,
                fileSize = status.fileSizeFormatted,
                onDownload = { viewModel.downloadModel(status.model.id) },
                onDelete = { viewModel.deleteModel(status.model.id) },
                onSelect = { viewModel.selectModel(status.model.id) },
                downloadProgress = downloadProgress[status.model.id]
            )
        }
    }
}

// ViewModel
class TTSViewModel(private val tts: CopiloTTS) : ViewModel() {
    val modelStatuses = MutableStateFlow(tts.getModelStatuses())
    val downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())

    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            tts.downloadModelWithProgress(modelId).collect { progress ->
                when (progress) {
                    is DownloadProgress.Progress -> {
                        downloadProgress.update { it + (modelId to progress.percent) }
                    }
                    is DownloadProgress.Completed -> {
                        modelStatuses.value = tts.getModelStatuses()
                        downloadProgress.update { it - modelId }
                    }
                    else -> {}
                }
            }
        }
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            tts.useHuggingFaceModel(modelId)
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            tts.deleteModel(modelId)
            modelStatuses.value = tts.getModelStatuses()
        }
    }
}
```

## Dependency Injection (Koin)

```kotlin
// In your Application class
startKoin {
    androidContext(this@App)
    modules(copiloTTSModule())
}

// Inject CopiloTTS
class MyViewModel(private val tts: CopiloTTS) {
    // ...
}
```

## Android Setup

```kotlin
// In Application.onCreate()
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NativeTTSEngine.setContext(this)
        ModelStorage.ContextProvider.setContext(this)
    }
}
```

## iOS Setup

iOS integration uses Kotlin Multiplatform directly. The library exports protocols that you implement in Swift:
- `TTSNativeHandler` - bridges to `AVSpeechSynthesizer` for native TTS
- `HuggingFaceHandler` - bridges to ONNX Runtime for HuggingFace models

### 1. Build the Kotlin Framework

First, build the iOS framework from your KMP project:

```bash
# For simulator (development)
./gradlew :sample:linkDebugFrameworkIosSimulatorArm64

# For device (release)
./gradlew :sample:linkReleaseFrameworkIosArm64
```

### 2. Create Swift TTSNativeHandler Implementation

Create `TTSNativeHandler.swift` in your iOS app. Import your framework (the name matches your KMP module's `baseName`):
> **Note:** See `iosApp/iosApp/TTSNativeHandler.swift`.

### 2b. Add ONNX Runtime for HuggingFace Support (Optional)

If you want HuggingFace model support on iOS, add ONNX Runtime via Swift Package Manager:

1. In Xcode, go to **File → Add Package Dependencies**
2. Add: `https://github.com/microsoft/onnxruntime-swift-package-manager`
3. Select version `1.19.2` or later
4. Add the `onnxruntime` package product to your target

Then create `HuggingFaceHandler.swift`:
> **Note:** The full `HuggingFaceHandler.swift` implementation (~300 lines) including the complete Supertonic inference pipeline is available in the sample app at `iosApp/iosApp/HuggingFaceHandler.swift`.

### 3. Register Handlers Before Koin

In your iOS app's entry point, register the handlers **before** Koin starts (which happens when the Compose view loads):

```swift
import SwiftUI
import ComposeApp  // Your KMP framework name

@main
struct MyApp: App {
    init() {
        // Register handlers BEFORE Koin starts
        TTSNativeHandlerProvider.shared.setHandler(handler: TTSNativeHandlerImpl())
        HuggingFaceHandlerProvider.shared.setHandler(handler: HuggingFaceHandlerImpl())
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

### 4. Include Platform Modules in Koin

In your KMP project's `iosMain` source set, include the iOS handler modules:

```kotlin
// In iosMain/kotlin/.../PlatformModule.ios.kt
actual fun platformModules(): List<Module> = listOf(
    ttsNativeHandlerModule(),
    huggingFaceHandlerModule()
)
```

### 5. Configure Xcode Project

Your Xcode project needs to link the Kotlin framework.

### 6. Run the iOS App

```bash
# Open Xcode project
open iosApp/iosApp.xcodeproj

# Or build from command line
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 15'
```

## API Reference

### CopiloTTS

| Method | Description |
|--------|-------------|
| `initialize()` | Initialize the TTS engine |
| `speak(request)` | Speak text with options |
| `speak(text)` | Simple speak with text |
| `pause()` | Pause speech (iOS only) |
| `resume()` | Resume speech |
| `stop()` | Stop current speech |
| `stopAll()` | Stop and clear queue |
| `shutdown()` | Release resources |
| `setVoice(voice)` | Set the voice |
| `setSpeechRate(rate)` | Set speech rate (0.5-2.0) |
| `setVolume(volume)` | Set volume (0.0-1.0) |
| `downloadModel(id)` | Download HuggingFace model |
| `deleteModel(id)` | Delete downloaded model |

### Model Management

| Method | Description |
|--------|-------------|
| `getAllSupportedModels()` | Get list of all supported models |
| `getModelStatuses()` | Get download status of all models |
| `getModelStatus(id)` | Get status of specific model |
| `downloadModelWithProgress(id)` | Download with Flow progress |
| `cancelModelDownload(id)` | Cancel ongoing download |
| `isAnyModelDownloading()` | Check for active downloads |
| `getStorageInfo()` | Get storage usage info |
| `useHuggingFaceModel(id)` | Switch to HuggingFace model |
| `useNativeEngine()` | Switch to native TTS |
| `getActiveEngineType()` | Get current engine type |
| `isHuggingFaceAvailable()` | Check if HF is ready |

### StateFlows

| Flow | Description |
|------|-------------|
| `state` | Current TTS state |
| `isSpeaking` | Whether currently speaking |
| `progress` | Speech progress (0.0-1.0) |
| `currentVoice` | Currently selected voice |
| `availableVoices` | Available voices |
| `queueSize` | Number of queued items |

## Supported Models

| Model | Size | Language | Voices | Platform |
|-------|------|----------|--------|----------|
| Supertonic | 265 MB | English | 4 (F1, F2, M1, M2) | Android & iOS |

> **Note:** For non-English languages, use Native TTS (Android TextToSpeech / iOS AVSpeechSynthesizer) which provides excellent multilingual support with no downloads required.

## License

MIT License - Copyright (c) 2025 Sigma Delta BV

```
MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
