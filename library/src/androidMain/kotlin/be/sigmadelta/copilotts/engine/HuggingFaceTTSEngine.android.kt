/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.engine

import ai.onnxruntime.*
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import be.sigmadelta.copilotts.*
import be.sigmadelta.copilotts.model.ModelArchitecture
import be.sigmadelta.copilotts.model.ModelInfo
import be.sigmadelta.copilotts.model.ModelStorage
import be.sigmadelta.copilotts.model.PiperConfig
import be.sigmadelta.copilotts.model.SupertonicTtsConfig
import be.sigmadelta.copilotts.model.SupertonicVoiceStyle
import be.sigmadelta.copilotts.model.SupertonicVoiceStyleType
import java.io.File
import io.github.aakira.napier.Napier
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Android implementation of HuggingFace TTS engine using ONNX Runtime.
 * Supports various TTS models from HuggingFace converted to ONNX format.
 * Includes support for multi-model architectures like Supertonic.
 */
actual class HuggingFaceTTSEngine actual constructor(
    private val config: TTSConfig,
    private val modelStorage: ModelStorage
) : TTSEngine {

    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private var callback: TTSCallback? = null
    private var loadedModel: ModelInfo? = null

    // Multi-model sessions for Supertonic architecture
    private var textEncoderSession: OrtSession? = null
    private var durationPredictorSession: OrtSession? = null
    private var vectorEstimatorSession: OrtSession? = null
    private var vocoderSession: OrtSession? = null

    // Supertonic configuration and data
    private var supertonicConfig: SupertonicTtsConfig? = null
    private var unicodeIndexer: List<Int>? = null
    private var voiceStyle: SupertonicVoiceStyle? = null
    private var currentVoiceStyleType: SupertonicVoiceStyleType = SupertonicVoiceStyleType.default
    private val loadedVoiceStyles = mutableMapOf<SupertonicVoiceStyleType, SupertonicVoiceStyle>()

    // Piper configuration and data
    private var piperConfig: PiperConfig? = null
    private var currentPiperSpeakerId: Int = 0

    // JSON parser with lenient mode for config files
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(TTSState.UNINITIALIZED)
    actual override val state: StateFlow<TTSState> = _state.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    actual override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    actual override val progress: StateFlow<Float> = _progress.asStateFlow()

    actual override val engineType: TTSEngineType = TTSEngineType.HUGGINGFACE

    private var speechRate = config.defaultSpeechRate
    private var pitch = config.defaultPitch
    private var volume = config.defaultVolume
    private var currentVoice: TTSVoice? = null

    actual override suspend fun initialize(): TTSResult<Unit> {
        if (_state.value == TTSState.READY) {
            Napier.d { "HuggingFaceTTSEngine: Already initialized" }
            return TTSResult.Success(Unit)
        }

        _state.value = TTSState.INITIALIZING

        return try {
            // Initialize ONNX Runtime environment
            ortEnvironment = OrtEnvironment.getEnvironment()

            _state.value = TTSState.READY
            callback?.onReady()
            Napier.i { "HuggingFaceTTSEngine: Initialized successfully" }
            TTSResult.Success(Unit)
        } catch (e: Exception) {
            _state.value = TTSState.ERROR
            Napier.e(e) { "HuggingFaceTTSEngine: Initialization failed" }
            TTSResult.Error(TTSErrorCode.ENGINE_ERROR, e.message ?: "ONNX Runtime initialization failed", e)
        }
    }

    actual suspend fun loadModel(modelInfo: ModelInfo): TTSResult<Unit> {
        if (_state.value != TTSState.READY && _state.value != TTSState.UNINITIALIZED) {
            // Initialize first if needed
            val initResult = initialize()
            if (initResult is TTSResult.Error) return initResult
        }

        val env = ortEnvironment ?: return TTSResult.Error(
            TTSErrorCode.NOT_INITIALIZED,
            "ONNX Runtime environment not initialized"
        )

        // Get model path
        val modelPath = modelStorage.getModelPath(modelInfo.id)
            ?: return TTSResult.Error(
                TTSErrorCode.MODEL_NOT_FOUND,
                "Model not downloaded: ${modelInfo.id}"
            )

        return try {
            // Close existing sessions
            closeAllSessions()

            // Handle different model architectures
            when {
                modelInfo.architecture == ModelArchitecture.SUPERTONIC && modelInfo.isMultiFileModel -> {
                    // Multi-model architecture (Supertonic) - use NNAPI for acceleration
                    val sessionOptions = createSessionOptions(useNnapi = true)
                    loadSupertonicModel(env, modelPath, sessionOptions, modelInfo)
                }
                modelInfo.architecture == ModelArchitecture.PIPER -> {
                    // Piper TTS model - disable NNAPI due to tensor shape incompatibilities
                    val sessionOptions = createSessionOptions(useNnapi = false)
                    loadPiperModel(env, modelPath, sessionOptions, modelInfo)
                }
                modelInfo.isMultiFileModel && modelInfo.onnxFiles.size == 1 -> {
                    // Single ONNX file with custom name - use NNAPI
                    val sessionOptions = createSessionOptions(useNnapi = true)
                    val onnxFile = File(modelPath, modelInfo.onnxFiles.first())
                    if (!onnxFile.exists()) {
                        throw IllegalStateException("ONNX file not found: ${onnxFile.absolutePath}")
                    }
                    ortSession = env.createSession(onnxFile.absolutePath, sessionOptions)
                }
                else -> {
                    // Default: single model.onnx file - use NNAPI
                    val sessionOptions = createSessionOptions(useNnapi = true)
                    ortSession = env.createSession(modelPath, sessionOptions)
                }
            }

            loadedModel = modelInfo
            Napier.i { "HuggingFaceTTSEngine: Loaded model ${modelInfo.displayName}" }
            TTSResult.Success(Unit)
        } catch (e: Exception) {
            Napier.e(e) { "HuggingFaceTTSEngine: Failed to load model ${modelInfo.id}" }
            TTSResult.Error(TTSErrorCode.MODEL_LOAD_ERROR, e.message ?: "Failed to load model", e)
        }
    }

    private fun createSessionOptions(useNnapi: Boolean = true): OrtSession.SessionOptions {
        return OrtSession.SessionOptions().apply {
            // Enable NNAPI execution provider for Android neural network acceleration
            // Note: Piper models have tensor shape issues with NNAPI, so we disable it for them
            if (useNnapi) {
                try {
                    addNnapi()
                    Napier.d { "HuggingFaceTTSEngine: NNAPI enabled" }
                } catch (e: Exception) {
                    Napier.w { "HuggingFaceTTSEngine: NNAPI not available, using CPU" }
                }
            } else {
                Napier.d { "HuggingFaceTTSEngine: Using CPU execution provider" }
            }

            // Optimize for inference
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(4)
        }
    }

    private fun loadSupertonicModel(
        env: OrtEnvironment,
        modelDir: String,
        sessionOptions: OrtSession.SessionOptions,
        modelInfo: ModelInfo
    ) {
        val dir = File(modelDir)

        // Load each component model
        val textEncoderPath = File(dir, "text_encoder.onnx")
        val durationPredictorPath = File(dir, "duration_predictor.onnx")
        val vectorEstimatorPath = File(dir, "vector_estimator.onnx")
        val vocoderPath = File(dir, "vocoder.onnx")

        Napier.i { "HuggingFaceTTSEngine: Loading Supertonic multi-model from $modelDir" }

        if (!textEncoderPath.exists() || !durationPredictorPath.exists() ||
            !vectorEstimatorPath.exists() || !vocoderPath.exists()) {
            throw IllegalStateException("Missing Supertonic model files in $modelDir")
        }

        // Load ONNX models
        textEncoderSession = env.createSession(textEncoderPath.absolutePath, sessionOptions)
        Napier.d { "HuggingFaceTTSEngine: Loaded text_encoder.onnx" }

        durationPredictorSession = env.createSession(durationPredictorPath.absolutePath, sessionOptions)
        Napier.d { "HuggingFaceTTSEngine: Loaded duration_predictor.onnx" }

        vectorEstimatorSession = env.createSession(vectorEstimatorPath.absolutePath, sessionOptions)
        Napier.d { "HuggingFaceTTSEngine: Loaded vector_estimator.onnx" }

        vocoderSession = env.createSession(vocoderPath.absolutePath, sessionOptions)
        Napier.d { "HuggingFaceTTSEngine: Loaded vocoder.onnx" }

        // Load configuration files
        loadSupertonicConfig(dir)
        loadUnicodeIndexer(dir)
        loadVoiceStyle(dir)

        Napier.i { "HuggingFaceTTSEngine: All Supertonic models and configs loaded successfully" }
    }

    private fun loadPiperModel(
        env: OrtEnvironment,
        modelDir: String,
        sessionOptions: OrtSession.SessionOptions,
        modelInfo: ModelInfo
    ) {
        val dir = File(modelDir)

        // Get ONNX file name from model info
        val onnxFileName = modelInfo.onnxFiles.firstOrNull()
            ?: throw IllegalStateException("No ONNX file specified for Piper model")

        val onnxFile = File(dir, onnxFileName)
        if (!onnxFile.exists()) {
            throw IllegalStateException("ONNX file not found: ${onnxFile.absolutePath}")
        }

        Napier.i { "HuggingFaceTTSEngine: Loading Piper model from ${onnxFile.absolutePath}" }

        // Load the ONNX model
        ortSession = env.createSession(onnxFile.absolutePath, sessionOptions)
        Napier.d { "HuggingFaceTTSEngine: Loaded ${onnxFileName}" }

        // Load Piper config JSON
        val configFileName = modelInfo.configFiles.firstOrNull()
            ?: "${onnxFileName}.json"
        loadPiperConfig(dir, configFileName)

        Napier.i { "HuggingFaceTTSEngine: Piper model loaded successfully" }
    }

    private fun loadPiperConfig(modelDir: File, configFileName: String) {
        val configFile = File(modelDir, configFileName)
        if (!configFile.exists()) {
            Napier.w { "HuggingFaceTTSEngine: Piper config $configFileName not found, using defaults" }
            piperConfig = PiperConfig()
            return
        }

        try {
            piperConfig = json.decodeFromString<PiperConfig>(configFile.readText())
            val speakers = piperConfig?.numSpeakers ?: 1
            Napier.d { "HuggingFaceTTSEngine: Loaded Piper config - speakers=$speakers, sampleRate=${piperConfig?.sampleRate}" }

            // Set default speaker to 0
            currentPiperSpeakerId = 0
        } catch (e: Exception) {
            Napier.w(e) { "HuggingFaceTTSEngine: Failed to parse Piper config, using defaults" }
            piperConfig = PiperConfig()
        }
    }

    /**
     * Set the speaker ID for Piper multi-speaker models.
     * @param speakerId The speaker ID (0-based index)
     * @return true if successfully set
     */
    fun setPiperSpeakerId(speakerId: Int): Boolean {
        val config = piperConfig ?: return false
        if (speakerId < 0 || speakerId >= config.numSpeakers) {
            Napier.w { "HuggingFaceTTSEngine: Invalid speaker ID $speakerId (max: ${config.numSpeakers - 1})" }
            return false
        }
        currentPiperSpeakerId = speakerId
        Napier.d { "HuggingFaceTTSEngine: Set Piper speaker ID to $speakerId" }
        return true
    }

    /**
     * Get the current Piper speaker ID.
     */
    fun getPiperSpeakerId(): Int = currentPiperSpeakerId

    /**
     * Get the number of speakers available in the Piper model.
     */
    fun getPiperSpeakerCount(): Int = piperConfig?.numSpeakers ?: 1

    private fun loadSupertonicConfig(modelDir: File) {
        val configFile = File(modelDir, "tts.json")
        if (!configFile.exists()) {
            Napier.w { "HuggingFaceTTSEngine: tts.json not found, using defaults" }
            supertonicConfig = SupertonicTtsConfig()
            return
        }

        try {
            supertonicConfig = json.decodeFromString<SupertonicTtsConfig>(configFile.readText())
            Napier.d { "HuggingFaceTTSEngine: Loaded tts.json - sampleRate=${supertonicConfig?.sampleRate}, latentDimVal=${supertonicConfig?.latentDimVal}" }
        } catch (e: Exception) {
            Napier.w(e) { "HuggingFaceTTSEngine: Failed to parse tts.json, using defaults" }
            supertonicConfig = SupertonicTtsConfig()
        }
    }

    private fun loadUnicodeIndexer(modelDir: File) {
        val indexerFile = File(modelDir, "unicode_indexer.json")
        if (!indexerFile.exists()) {
            Napier.w { "HuggingFaceTTSEngine: unicode_indexer.json not found" }
            unicodeIndexer = null
            return
        }

        try {
            unicodeIndexer = json.decodeFromString<List<Int>>(indexerFile.readText())
            Napier.d { "HuggingFaceTTSEngine: Loaded unicode_indexer with ${unicodeIndexer?.size} entries" }
        } catch (e: Exception) {
            Napier.e(e) { "HuggingFaceTTSEngine: Failed to parse unicode_indexer.json" }
            unicodeIndexer = null
        }
    }

    private fun loadVoiceStyle(modelDir: File) {
        loadedVoiceStyles.clear()

        // Voice styles are in the voice_styles subdirectory
        val voiceStylesDir = File(modelDir, "voice_styles")

        // Load all available voice styles
        for (styleType in SupertonicVoiceStyleType.entries) {
            // Try voice_styles subdirectory first, then root directory
            val styleFile = if (voiceStylesDir.exists()) {
                File(voiceStylesDir, styleType.fileName)
            } else {
                File(modelDir, styleType.fileName)
            }

            if (!styleFile.exists()) {
                Napier.w { "HuggingFaceTTSEngine: ${styleType.fileName} voice style not found at ${styleFile.absolutePath}" }
                continue
            }

            try {
                val content = styleFile.readText()
                val style = json.decodeFromString<SupertonicVoiceStyle>(content)
                loadedVoiceStyles[styleType] = style
                Napier.d { "HuggingFaceTTSEngine: Loaded ${styleType.displayName} - ttl dims=${style.styleTtl.dims}, dp dims=${style.styleDp.dims}" }
            } catch (e: Exception) {
                Napier.e(e) { "HuggingFaceTTSEngine: Failed to parse ${styleType.fileName}: ${e.message}" }
            }
        }

        if (loadedVoiceStyles.isEmpty()) {
            Napier.e { "HuggingFaceTTSEngine: No voice styles loaded! Model directory contents: ${modelDir.listFiles()?.map { it.name }}" }
            return
        }

        // Set default style
        voiceStyle = loadedVoiceStyles[currentVoiceStyleType]
            ?: loadedVoiceStyles.values.first()
        currentVoiceStyleType = loadedVoiceStyles.keys.find { loadedVoiceStyles[it] == voiceStyle }
            ?: SupertonicVoiceStyleType.default

        Napier.i { "HuggingFaceTTSEngine: Loaded ${loadedVoiceStyles.size} voice styles, using ${currentVoiceStyleType.displayName}" }
    }

    /**
     * Set the voice style for Supertonic model.
     * @param styleType The voice style to use
     * @return true if successfully set, false if style not available
     */
    actual fun setSupertonicVoiceStyle(styleType: SupertonicVoiceStyleType): Boolean {
        val style = loadedVoiceStyles[styleType]
        if (style == null) {
            Napier.w { "HuggingFaceTTSEngine: Voice style ${styleType.displayName} not loaded" }
            return false
        }

        voiceStyle = style
        currentVoiceStyleType = styleType
        Napier.i { "HuggingFaceTTSEngine: Switched to voice style ${styleType.displayName}" }
        return true
    }

    /**
     * Get the currently selected voice style type.
     */
    actual fun getCurrentVoiceStyleType(): SupertonicVoiceStyleType = currentVoiceStyleType

    /**
     * Get all available voice styles for the loaded model.
     */
    actual fun getAvailableVoiceStyles(): List<SupertonicVoiceStyleType> = loadedVoiceStyles.keys.toList()

    private fun closeAllSessions() {
        ortSession?.close()
        ortSession = null
        textEncoderSession?.close()
        textEncoderSession = null
        durationPredictorSession?.close()
        durationPredictorSession = null
        vectorEstimatorSession?.close()
        vectorEstimatorSession = null
        vocoderSession?.close()
        vocoderSession = null

        // Clear Supertonic config
        supertonicConfig = null
        unicodeIndexer = null
        voiceStyle = null
        loadedVoiceStyles.clear()
        currentVoiceStyleType = SupertonicVoiceStyleType.default

        // Clear Piper config
        piperConfig = null
        currentPiperSpeakerId = 0
    }

    actual fun unloadModel() {
        closeAllSessions()
        loadedModel = null
        Napier.d { "HuggingFaceTTSEngine: Model unloaded" }
    }

    actual fun isModelLoaded(): Boolean = ortSession != null || textEncoderSession != null

    actual fun getLoadedModel(): ModelInfo? = loadedModel

    actual override suspend fun speak(request: SpeakRequest, utteranceId: String): TTSResult<Unit> {
        val model = loadedModel ?: return TTSResult.Error(
            TTSErrorCode.MODEL_NOT_FOUND,
            "No model information available"
        )

        // Check if model is loaded
        val isSupertonicLoaded = textEncoderSession != null && vocoderSession != null
        val isSingleModelLoaded = ortSession != null

        if (!isSupertonicLoaded && !isSingleModelLoaded) {
            return TTSResult.Error(
                TTSErrorCode.MODEL_NOT_FOUND,
                "No model loaded. Call loadModel() first."
            )
        }

        // Stop any current playback
        stop()

        val textToSpeak = request.ssml ?: request.text
        Napier.d { "HuggingFaceTTSEngine: Speaking '$textToSpeak'" }

        return withContext(Dispatchers.Default) {
            try {
                _isSpeaking.value = true
                _progress.value = 0f
                callback?.onStart(utteranceId)

                // Run inference based on model architecture
                val audioData = when {
                    model.architecture == ModelArchitecture.SUPERTONIC && isSupertonicLoaded -> {
                        runSupertonicInference(textToSpeak, model)
                    }
                    model.architecture == ModelArchitecture.PIPER && isSingleModelLoaded -> {
                        runPiperInference(textToSpeak, model)
                    }
                    else -> {
                        // Tokenize the input text for other single-model architectures
                        val inputIds = tokenizeText(textToSpeak, model)
                        runInference(ortSession!!, inputIds, model)
                    }
                }

                // Apply speech rate modification if needed
                val processedAudio = if (request.speechRate != null && request.speechRate != 1.0f) {
                    adjustSpeechRate(audioData, request.speechRate!!)
                } else {
                    audioData
                }

                // Play the audio
                playAudio(processedAudio, model.sampleRate, request.volume ?: volume, utteranceId)

                TTSResult.Success(Unit)
            } catch (e: Exception) {
                _isSpeaking.value = false
                Napier.e(e) { "HuggingFaceTTSEngine: Speech synthesis failed" }
                callback?.onError(utteranceId, TTSResult.Error(TTSErrorCode.SYNTHESIS_ERROR, e.message ?: "Synthesis failed"))
                TTSResult.Error(TTSErrorCode.SYNTHESIS_ERROR, e.message ?: "Speech synthesis failed", e)
            }
        }
    }

    /**
     * Run inference using the Supertonic multi-model pipeline.
     * Based on reference: https://github.com/supertone-inc/supertonic/blob/main/swift/Sources/Helper.swift
     *
     * Pipeline:
     * 1. Tokenize text using unicode_indexer
     * 2. Duration prediction: text_ids + text_mask + style_dp -> durations
     * 3. Text encoding: text_ids + text_mask + style_ttl -> text embeddings
     * 4. Vector estimation (denoising loop): noisy_latent + text_emb + style_ttl + masks + step -> denoised latent
     * 5. Vocoder: latent -> audio
     */
    private fun runSupertonicInference(text: String, model: ModelInfo, speed: Float = 1.0f, totalSteps: Int = 5): FloatArray {
        val env = ortEnvironment ?: throw IllegalStateException("ONNX environment not initialized")
        val config = supertonicConfig ?: SupertonicTtsConfig()

        val dp = durationPredictorSession ?: throw IllegalStateException("Duration predictor not loaded")
        val te = textEncoderSession ?: throw IllegalStateException("Text encoder not loaded")
        val ve = vectorEstimatorSession ?: throw IllegalStateException("Vector estimator not loaded")
        val voc = vocoderSession ?: throw IllegalStateException("Vocoder not loaded")

        val style = voiceStyle ?: throw IllegalStateException("Voice style not loaded")
        val styleTtl = style.styleTtl.toFloatArray()
        val styleTtlDims = style.styleTtl.dimsArray()
        val styleDp = style.styleDp.toFloatArray()
        val styleDpDims = style.styleDp.dimsArray()

        Napier.d { "HuggingFaceTTSEngine: Running Supertonic inference for: $text" }

        // Step 1: Tokenize text
        val tokens = tokenizeTextForSupertonic(text)
        if (tokens.isEmpty()) {
            throw IllegalStateException("Empty text after tokenization")
        }

        val seqLen = tokens.size
        val batchSize = 1

        // Create text_ids tensor: [batch_size, seq_len]
        val textIdsTensor = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(tokens),
            longArrayOf(batchSize.toLong(), seqLen.toLong())
        )

        // Create text_mask tensor: [batch_size, 1, seq_len] - all 1s
        val textMask = FloatArray(seqLen) { 1.0f }
        val textMaskTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(textMask),
            longArrayOf(batchSize.toLong(), 1L, seqLen.toLong())
        )

        // Create style_dp tensor: [batch_size, dp_dims[1], dp_dims[2]]
        val styleDpTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(styleDp),
            longArrayOf(batchSize.toLong(), styleDpDims[1].toLong(), styleDpDims[2].toLong())
        )

        // Create style_ttl tensor: [batch_size, ttl_dims[1], ttl_dims[2]]
        val styleTtlTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(styleTtl),
            longArrayOf(batchSize.toLong(), styleTtlDims[1].toLong(), styleTtlDims[2].toLong())
        )

        // Step 2: Duration Prediction
        Napier.d { "HuggingFaceTTSEngine: Running duration predictor" }
        val dpOutputName = dp.outputNames.first()
        val dpOutputs = dp.run(
            mapOf(
                "text_ids" to textIdsTensor,
                "text_mask" to textMaskTensor,
                "style_dp" to styleDpTensor
            )
        )
        val durationOutput = dpOutputs.get(dpOutputName).get() as OnnxTensor
        val durationBuffer = durationOutput.floatBuffer
        val durations = FloatArray(durationBuffer.remaining())
        durationBuffer.get(durations)
        Napier.d { "HuggingFaceTTSEngine: Duration output: ${durations.take(5)}..." }

        // Calculate total wav length and latent length
        var totalWavLen = 0
        for (i in 0 until minOf(durations.size, seqLen)) {
            val durSec = maxOf(0.01f, durations[i] / speed)
            totalWavLen += (durSec * config.sampleRate).toInt()
        }

        val latentLen = maxOf(1, (totalWavLen + config.chunkSize - 1) / config.chunkSize)
        val latentDimVal = config.latentDimVal

        Napier.d { "HuggingFaceTTSEngine: seqLen=$seqLen, totalWavLen=$totalWavLen, latentLen=$latentLen, latentDimVal=$latentDimVal" }

        // Step 3: Text Encoding
        Napier.d { "HuggingFaceTTSEngine: Running text encoder" }
        val teOutputName = te.outputNames.first()
        val teOutputs = te.run(
            mapOf(
                "text_ids" to textIdsTensor,
                "text_mask" to textMaskTensor,
                "style_ttl" to styleTtlTensor
            )
        )
        val textEmbOutput = teOutputs.get(teOutputName).get() as OnnxTensor
        Napier.d { "HuggingFaceTTSEngine: Text encoder output shape: ${textEmbOutput.info.shape.contentToString()}" }

        // Step 4: Generate initial noisy latent with Gaussian noise
        // Use seeded random based on text hash for consistent output
        val latentSize = latentDimVal * latentLen
        var noisyLatent = FloatArray(latentSize)

        // Seed random generator with text hash for reproducible results
        val seededRandom = Random(text.hashCode().toLong())

        // Box-Muller transform for Gaussian noise
        var i = 0
        while (i < latentSize - 1) {
            val u1 = seededRandom.nextFloat().coerceIn(0.0001f, 0.9999f)
            val u2 = seededRandom.nextFloat().coerceIn(0.0001f, 0.9999f)
            val r = sqrt(-2.0f * ln(u1))
            val theta = 2.0f * Math.PI.toFloat() * u2
            noisyLatent[i] = r * cos(theta)
            if (i + 1 < latentSize) {
                noisyLatent[i + 1] = r * sin(theta)
            }
            i += 2
        }

        // Create latent_mask: [batch_size, 1, latent_len]
        val latentMask = FloatArray(latentLen) { 1.0f }
        val latentMaskTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(latentMask),
            longArrayOf(batchSize.toLong(), 1L, latentLen.toLong())
        )

        // Step 5: Denoising loop
        Napier.d { "HuggingFaceTTSEngine: Running denoising loop ($totalSteps steps)" }
        val veOutputName = ve.outputNames.first()

        for (step in 0 until totalSteps) {
            // noisy_latent: [batch_size, latent_dim_val, latent_len]
            val noisyLatentTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(noisyLatent),
                longArrayOf(batchSize.toLong(), latentDimVal.toLong(), latentLen.toLong())
            )

            // current_step: [batch_size]
            val currentStepTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(floatArrayOf(step.toFloat())),
                longArrayOf(batchSize.toLong())
            )

            // total_step: [batch_size]
            val totalStepTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(floatArrayOf(totalSteps.toFloat())),
                longArrayOf(batchSize.toLong())
            )

            val veOutputs = ve.run(
                mapOf(
                    "noisy_latent" to noisyLatentTensor,
                    "text_emb" to textEmbOutput,
                    "style_ttl" to styleTtlTensor,
                    "text_mask" to textMaskTensor,
                    "latent_mask" to latentMaskTensor,
                    "current_step" to currentStepTensor,
                    "total_step" to totalStepTensor
                )
            )

            val denoisedOutput = veOutputs.get(veOutputName).get() as OnnxTensor
            val denoisedBuffer = denoisedOutput.floatBuffer
            noisyLatent = FloatArray(denoisedBuffer.remaining())
            denoisedBuffer.get(noisyLatent)

            // Clean up step tensors
            noisyLatentTensor.close()
            currentStepTensor.close()
            totalStepTensor.close()
            veOutputs.close()
        }

        // Step 6: Vocoder
        Napier.d { "HuggingFaceTTSEngine: Running vocoder" }
        val finalLatentTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(noisyLatent),
            longArrayOf(batchSize.toLong(), latentDimVal.toLong(), latentLen.toLong())
        )

        val vocOutputName = voc.outputNames.first()
        val vocOutputs = voc.run(mapOf("latent" to finalLatentTensor))
        val audioOutput = vocOutputs.get(vocOutputName).get() as OnnxTensor
        val audioBuffer = audioOutput.floatBuffer
        val audioData = FloatArray(audioBuffer.remaining())
        audioBuffer.get(audioData)

        Napier.d { "HuggingFaceTTSEngine: Supertonic generated ${audioData.size} audio samples at ${config.sampleRate}Hz" }

        // Clean up
        textIdsTensor.close()
        textMaskTensor.close()
        styleDpTensor.close()
        styleTtlTensor.close()
        dpOutputs.close()
        teOutputs.close()
        latentMaskTensor.close()
        finalLatentTensor.close()
        vocOutputs.close()

        return audioData
    }

    /**
     * Run inference using Piper TTS model.
     *
     * Piper requires:
     * - input: phoneme IDs [1, seq_len] (int64)
     * - input_lengths: [seq_len] (int64)
     * - scales: [noise_scale, length_scale, noise_w] (float32)
     * - sid: speaker ID [1] (int64) - only for multi-speaker models
     *
     * Note: Piper normally uses espeak for phonemization. Since we don't have espeak,
     * we use the phoneme_id_map from the config to convert text to phoneme IDs.
     */
    private fun runPiperInference(text: String, model: ModelInfo): FloatArray {
        val env = ortEnvironment ?: throw IllegalStateException("ONNX environment not initialized")
        val session = ortSession ?: throw IllegalStateException("Piper model not loaded")
        val config = piperConfig ?: PiperConfig()

        Napier.d { "HuggingFaceTTSEngine: Running Piper inference for: $text" }

        // Tokenize text to phoneme IDs using the phoneme_id_map
        val phonemeIds = tokenizeTextForPiper(text, config)
        if (phonemeIds.isEmpty()) {
            throw IllegalStateException("Empty text after tokenization")
        }

        val seqLen = phonemeIds.size

        // Create input tensor: [1, seq_len]
        val inputTensor = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(phonemeIds),
            longArrayOf(1L, seqLen.toLong())
        )

        // Create input_lengths tensor: [1]
        val inputLengthsTensor = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(longArrayOf(seqLen.toLong())),
            longArrayOf(1L)
        )

        // Create scales tensor: [3] - [noise_scale, length_scale, noise_w]
        val scalesTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(floatArrayOf(config.noiseScale, config.lengthScale, config.noiseW)),
            longArrayOf(3L)
        )

        // Build input map
        val inputs = mutableMapOf<String, OnnxTensor>(
            "input" to inputTensor,
            "input_lengths" to inputLengthsTensor,
            "scales" to scalesTensor
        )

        // Add speaker ID for multi-speaker models
        if (config.numSpeakers > 1) {
            val sidTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(longArrayOf(currentPiperSpeakerId.toLong())),
                longArrayOf(1L)
            )
            inputs["sid"] = sidTensor
        }

        // Run inference
        val outputs = session.run(inputs)
        val outputName = session.outputNames.firstOrNull() ?: "output"
        val outputTensor = outputs.get(outputName).get() as OnnxTensor
        val audioBuffer = outputTensor.floatBuffer
        val audioData = FloatArray(audioBuffer.remaining())
        audioBuffer.get(audioData)

        Napier.d { "HuggingFaceTTSEngine: Piper generated ${audioData.size} audio samples at ${config.sampleRate}Hz" }

        // Clean up
        inputTensor.close()
        inputLengthsTensor.close()
        scalesTensor.close()
        inputs["sid"]?.close()
        outputs.close()

        return audioData
    }

    /**
     * Tokenize text for Piper model using phoneme_id_map.
     * This is a simplified approach - real Piper uses espeak for phonemization.
     * We map characters to phoneme IDs based on the phoneme_id_map.
     */
    private fun tokenizeTextForPiper(text: String, config: PiperConfig): LongArray {
        val phonemeIdMap = config.phonemeIdMap
        val tokens = mutableListOf<Long>()

        // Add BOS (beginning of sequence) - typically phoneme ID for "^" or similar
        val bosId = phonemeIdMap["^"]?.firstOrNull()?.toLong() ?: 0L
        tokens.add(bosId)

        // Convert each character to phoneme ID
        // This is a simplified mapping - real implementation would use espeak
        for (char in text.lowercase()) {
            val charStr = char.toString()
            val phonemeId = phonemeIdMap[charStr]?.firstOrNull()?.toLong()

            if (phonemeId != null) {
                tokens.add(phonemeId)
            } else {
                // Try to find a reasonable fallback
                when (char) {
                    ' ' -> phonemeIdMap[" "]?.firstOrNull()?.toLong()?.let { tokens.add(it) }
                    '.' -> phonemeIdMap["."]?.firstOrNull()?.toLong()?.let { tokens.add(it) }
                    ',' -> phonemeIdMap[","]?.firstOrNull()?.toLong()?.let { tokens.add(it) }
                    '?' -> phonemeIdMap["?"]?.firstOrNull()?.toLong()?.let { tokens.add(it) }
                    '!' -> phonemeIdMap["!"]?.firstOrNull()?.toLong()?.let { tokens.add(it) }
                    else -> {
                        // Skip unknown characters or map to space
                        phonemeIdMap[" "]?.firstOrNull()?.toLong()?.let { tokens.add(it) }
                    }
                }
            }
        }

        // Add EOS (end of sequence) - typically phoneme ID for "$" or similar
        val eosId = phonemeIdMap["$"]?.firstOrNull()?.toLong() ?: 0L
        tokens.add(eosId)

        Napier.d { "HuggingFaceTTSEngine: Tokenized '$text' to ${tokens.size} phoneme IDs" }

        return tokens.toLongArray()
    }

    /**
     * Tokenize text for Supertonic model using unicode_indexer.
     */
    private fun tokenizeTextForSupertonic(text: String): LongArray {
        val indexer = unicodeIndexer
        if (indexer == null) {
            Napier.w { "HuggingFaceTTSEngine: unicode_indexer not loaded, using fallback tokenization" }
            return fallbackTokenize(text)
        }

        val tokens = mutableListOf<Long>()
        val spaceTokenId = if (indexer.size > 32) indexer[32] else 0

        for (char in text) {
            val codepoint = char.code
            val tokenId = if (codepoint < indexer.size) {
                val id = indexer[codepoint]
                if (id >= 0) id else spaceTokenId
            } else {
                spaceTokenId
            }
            tokens.add(tokenId.toLong())
        }

        return tokens.toLongArray()
    }

    /**
     * Fallback tokenization when unicode_indexer is not available.
     */
    private fun fallbackTokenize(text: String): LongArray {
        val tokens = mutableListOf<Long>()

        for (char in text.lowercase()) {
            val tokenId = when {
                char == ' ' -> 1L
                char in 'a'..'z' -> (char - 'a' + 2).toLong()
                char in '0'..'9' -> (char - '0' + 28).toLong()
                char == '.' -> 38L
                char == ',' -> 39L
                char == '!' -> 40L
                char == '?' -> 41L
                char == '\'' -> 42L
                char == '-' -> 43L
                char == ':' -> 44L
                char == ';' -> 45L
                else -> 1L // Unknown -> space
            }
            tokens.add(tokenId)
        }

        return tokens.toLongArray()
    }

    private fun tokenizeText(text: String, model: ModelInfo): LongArray {
        // Simple character-level tokenization for basic models
        // More sophisticated models would need proper tokenizers (e.g., SentencePiece, BPE)

        // Basic ASCII tokenization - each character maps to its code point
        // Real implementations should use model-specific tokenizers
        val tokens = mutableListOf<Long>()

        // Add start token (commonly 0 or 1)
        tokens.add(1L)

        // Convert text to token IDs
        for (char in text.lowercase()) {
            val tokenId = when {
                char == ' ' -> 2L
                char in 'a'..'z' -> (char - 'a' + 3).toLong()
                char in '0'..'9' -> (char - '0' + 29).toLong()
                char == '.' -> 39L
                char == ',' -> 40L
                char == '!' -> 41L
                char == '?' -> 42L
                char == '\'' -> 43L
                char == '-' -> 44L
                else -> 2L // Unknown -> space
            }
            tokens.add(tokenId)
        }

        // Add end token
        tokens.add(0L)

        return tokens.toLongArray()
    }

    private fun runInference(session: OrtSession, inputIds: LongArray, model: ModelInfo): FloatArray {
        val env = ortEnvironment ?: throw IllegalStateException("ONNX environment not initialized")

        // Create input tensor
        val inputShape = longArrayOf(1, inputIds.size.toLong())
        val inputTensor = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(inputIds),
            inputShape
        )

        // Get input/output names from model
        val inputName = session.inputNames.firstOrNull() ?: "input_ids"
        val outputName = session.outputNames.firstOrNull() ?: "audio"

        // Run inference
        val inputs = mapOf(inputName to inputTensor)
        val results = session.run(inputs)

        // Extract audio output
        val outputTensor = results.get(outputName).get() as OnnxTensor
        val audioBuffer = outputTensor.floatBuffer
        val audioData = FloatArray(audioBuffer.remaining())
        audioBuffer.get(audioData)

        // Clean up
        inputTensor.close()
        results.close()

        return audioData
    }

    private fun adjustSpeechRate(audio: FloatArray, rate: Float): FloatArray {
        if (rate == 1.0f) return audio

        // Simple time-stretching via linear interpolation
        // For production, use WSOLA or phase vocoder for better quality
        val newLength = (audio.size / rate).toInt()
        val result = FloatArray(newLength)

        for (i in result.indices) {
            val srcIndex = i * rate
            val srcIndexInt = srcIndex.toInt()
            val fraction = srcIndex - srcIndexInt

            result[i] = if (srcIndexInt + 1 < audio.size) {
                audio[srcIndexInt] * (1 - fraction) + audio[srcIndexInt + 1] * fraction
            } else {
                audio.getOrElse(srcIndexInt) { 0f }
            }
        }

        return result
    }

    private suspend fun playAudio(audioData: FloatArray, sampleRate: Int, volume: Float, utteranceId: String) {
        withContext(Dispatchers.Main) {
            // Convert float audio to 16-bit PCM
            val pcmData = ShortArray(audioData.size)
            for (i in audioData.indices) {
                val sample = (audioData[i] * 32767 * volume).toInt().coerceIn(-32768, 32767)
                pcmData[i] = sample.toShort()
            }

            // Create AudioTrack
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize.coerceAtLeast(pcmData.size * 2))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack?.apply {
                write(pcmData, 0, pcmData.size)
                play()
            }

            // Track playback progress
            playbackJob = CoroutineScope(Dispatchers.Default).launch {
                val totalDuration = pcmData.size.toFloat() / sampleRate
                var elapsed = 0f

                while (isActive && audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    val position = audioTrack?.playbackHeadPosition ?: 0
                    elapsed = position.toFloat() / sampleRate
                    _progress.value = (elapsed / totalDuration).coerceIn(0f, 1f)
                    callback?.onProgress(utteranceId, _progress.value, null)
                    delay(50)
                }

                if (isActive) {
                    _progress.value = 1f
                    _isSpeaking.value = false
                    // Release AudioTrack resources after playback completes
                    audioTrack?.stop()
                    audioTrack?.release()
                    audioTrack = null
                    playbackJob = null
                    callback?.onDone(utteranceId)
                }
            }
        }
    }

    actual override fun pause() {
        audioTrack?.pause()
        callback?.onPause(playbackJob?.hashCode()?.toString() ?: "")
        Napier.d { "HuggingFaceTTSEngine: Paused" }
    }

    actual override fun resume() {
        audioTrack?.play()
        callback?.onResume(playbackJob?.hashCode()?.toString() ?: "")
        Napier.d { "HuggingFaceTTSEngine: Resumed" }
    }

    actual override fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        _isSpeaking.value = false
        _progress.value = 0f
        Napier.d { "HuggingFaceTTSEngine: Stopped" }
    }

    actual override fun shutdown() {
        stop()
        unloadModel()
        ortEnvironment?.close()
        ortEnvironment = null
        _state.value = TTSState.UNINITIALIZED
        Napier.i { "HuggingFaceTTSEngine: Shutdown complete" }
    }

    actual override suspend fun getVoices(): TTSResult<List<TTSVoice>> {
        val voices = mutableListOf<TTSVoice>()

        // For the currently loaded model, return its specific voices
        val model = loadedModel
        if (model != null) {
            when (model.architecture) {
                ModelArchitecture.SUPERTONIC -> {
                    // Return voice styles for Supertonic
                    loadedVoiceStyles.keys.forEach { styleType ->
                        voices.add(
                            TTSVoice(
                                id = "${model.id}:${styleType.name}",
                                name = styleType.displayName,
                                languageCode = model.language,
                                gender = if (styleType.name.startsWith("F")) VoiceGender.FEMALE else VoiceGender.MALE,
                                engineType = TTSEngineType.HUGGINGFACE,
                                quality = VoiceQuality.PREMIUM,
                                metadata = mapOf(
                                    "modelId" to model.id,
                                    "architecture" to model.architecture.name,
                                    "voiceStyleType" to styleType.name,
                                    "description" to styleType.description
                                )
                            )
                        )
                    }
                }
                ModelArchitecture.PIPER -> {
                    // Return speakers for Piper multi-speaker models
                    val config = piperConfig
                    if (config != null && config.numSpeakers > 1) {
                        // Use speaker names from config if available
                        if (config.speakerIdMap.isNotEmpty()) {
                            config.speakerIdMap.forEach { (name, id) ->
                                voices.add(
                                    TTSVoice(
                                        id = "${model.id}:speaker_$id",
                                        name = "Speaker $name",
                                        languageCode = model.language,
                                        gender = VoiceGender.UNKNOWN,
                                        engineType = TTSEngineType.HUGGINGFACE,
                                        quality = VoiceQuality.STANDARD,
                                        metadata = mapOf(
                                            "modelId" to model.id,
                                            "architecture" to model.architecture.name,
                                            "speakerId" to id.toString()
                                        )
                                    )
                                )
                            }
                        } else {
                            // Fallback to numeric speaker IDs
                            for (i in 0 until config.numSpeakers) {
                                voices.add(
                                    TTSVoice(
                                        id = "${model.id}:speaker_$i",
                                        name = "Speaker ${i + 1}",
                                        languageCode = model.language,
                                        gender = VoiceGender.UNKNOWN,
                                        engineType = TTSEngineType.HUGGINGFACE,
                                        quality = VoiceQuality.STANDARD,
                                        metadata = mapOf(
                                            "modelId" to model.id,
                                            "architecture" to model.architecture.name,
                                            "speakerId" to i.toString()
                                        )
                                    )
                                )
                            }
                        }
                    } else {
                        // Single speaker model - just return one voice
                        voices.add(
                            TTSVoice(
                                id = model.id,
                                name = model.displayName,
                                languageCode = model.language,
                                gender = VoiceGender.UNKNOWN,
                                engineType = TTSEngineType.HUGGINGFACE,
                                quality = VoiceQuality.STANDARD,
                                metadata = mapOf(
                                    "modelId" to model.id,
                                    "architecture" to model.architecture.name
                                )
                            )
                        )
                    }
                }
                else -> {
                    // Other architectures - return single voice
                    voices.add(
                        TTSVoice(
                            id = model.id,
                            name = model.displayName,
                            languageCode = model.language,
                            gender = VoiceGender.UNKNOWN,
                            engineType = TTSEngineType.HUGGINGFACE,
                            quality = VoiceQuality.STANDARD,
                            metadata = mapOf(
                                "modelId" to model.id,
                                "architecture" to model.architecture.name
                            )
                        )
                    )
                }
            }
        }

        Napier.d { "HuggingFaceTTSEngine: Found ${voices.size} voices for loaded model" }
        return TTSResult.Success(voices)
    }

    actual override suspend fun setVoice(voice: TTSVoice): TTSResult<Unit> {
        val modelId = voice.metadata?.get("modelId") ?: voice.id.substringBefore(":")
        val modelInfo = modelStorage.getDownloadedModels().find { it.id == modelId }
            ?: modelStorage.getBundledModels().find { it.id == modelId }
            ?: return TTSResult.Error(TTSErrorCode.VOICE_NOT_FOUND, "Voice model not found: $modelId")

        // Load model if not already loaded
        if (loadedModel?.id != modelId) {
            val loadResult = loadModel(modelInfo)
            if (loadResult is TTSResult.Error) return loadResult
        }

        // Handle voice-specific settings
        when (modelInfo.architecture) {
            ModelArchitecture.SUPERTONIC -> {
                // Set voice style for Supertonic
                val styleTypeName = voice.metadata?.get("voiceStyleType")
                if (styleTypeName != null) {
                    val styleType = SupertonicVoiceStyleType.entries.find { it.name == styleTypeName }
                    if (styleType != null) {
                        setSupertonicVoiceStyle(styleType)
                    }
                }
            }
            ModelArchitecture.PIPER -> {
                // Set speaker ID for Piper
                val speakerIdStr = voice.metadata?.get("speakerId")
                if (speakerIdStr != null) {
                    val speakerId = speakerIdStr.toIntOrNull() ?: 0
                    setPiperSpeakerId(speakerId)
                }
            }
            else -> {
                // No additional settings needed
            }
        }

        currentVoice = voice
        Napier.d { "HuggingFaceTTSEngine: Set voice to ${voice.name}" }
        return TTSResult.Success(Unit)
    }

    actual override fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 2.0f)
        Napier.d { "HuggingFaceTTSEngine: Set speech rate to $speechRate" }
    }

    actual override fun setPitch(pitch: Float) {
        this.pitch = pitch.coerceIn(0.5f, 2.0f)
        Napier.d { "HuggingFaceTTSEngine: Set pitch to ${this.pitch}" }
    }

    actual override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        Napier.d { "HuggingFaceTTSEngine: Set volume to ${this.volume}" }
    }

    actual override fun setCallback(callback: TTSCallback) {
        this.callback = callback
    }

    actual override fun removeCallback() {
        this.callback = null
    }

    actual override fun isPauseSupported(): Boolean = true
}
