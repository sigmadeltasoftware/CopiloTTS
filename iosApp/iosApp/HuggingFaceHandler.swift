//
//  HuggingFaceHandler.swift
//  iosApp
//
//  Copyright (c) 2025 Sigma Delta BV
//  Licensed under the MIT License
//
//  iOS implementation of HuggingFace TTS using ONNX Runtime
//  Supports Supertonic model architecture with 4 separate ONNX models
//

import Foundation
import AVFoundation
import ComposeApp
import OnnxRuntimeBindings

// MARK: - Configuration

private struct SupertonicConfig {
    let sampleRate: Int
    let baseChunkSize: Int
    let chunkCompressFactor: Int
    let latentDim: Int

    var latentDimVal: Int { latentDim * chunkCompressFactor }
    var chunkSize: Int { baseChunkSize * chunkCompressFactor }

    static let `default` = SupertonicConfig(
        sampleRate: 44100,
        baseChunkSize: 512,
        chunkCompressFactor: 6,
        latentDim: 24
    )
}

private struct VoiceStyleData {
    let ttl: [Float]
    let ttlDims: [Int]
    let dp: [Float]
    let dpDims: [Int]
}

// MARK: - HuggingFaceHandler Implementation

class HuggingFaceHandlerImpl: NSObject, HuggingFaceHandler {

    // MARK: - Properties

    private var ortEnvironment: ORTEnv?
    private var durationPredictor: ORTSession?
    private var textEncoder: ORTSession?
    private var vectorEstimator: ORTSession?
    private var vocoder: ORTSession?

    private var config: SupertonicConfig = .default
    private var unicodeIndexer: [Int] = []
    private var voiceStyle: VoiceStyleData?

    private var dpOutputNames: [String] = []
    private var teOutputNames: [String] = []
    private var veOutputNames: [String] = []
    private var vocOutputNames: [String] = []

    private var isInitializedFlag = false
    private var isModelLoadedFlag = false
    private var modelSampleRate: Int32 = 44100
    private var eventCallback: HuggingFaceHandlerHuggingFaceEventCallback?

    private var isStopped = false
    private var currentUtteranceId: String = ""

    // MARK: - HuggingFaceHandler Protocol

    func initialize() async throws -> KotlinBoolean {
        NSLog("HuggingFaceHandler: Initializing ONNX Runtime")
        do {
            ortEnvironment = try ORTEnv(loggingLevel: .warning)
            isInitializedFlag = true
            NSLog("HuggingFaceHandler: ONNX Runtime initialized")
            return KotlinBoolean(bool: true)
        } catch {
            NSLog("HuggingFaceHandler: Init failed: \(error)")
            return KotlinBoolean(bool: false)
        }
    }

    func loadModel(modelPath: String) async throws -> KotlinBoolean {
        guard isInitializedFlag, let env = ortEnvironment else {
            NSLog("HuggingFaceHandler: Not initialized")
            return KotlinBoolean(bool: false)
        }

        NSLog("HuggingFaceHandler: Loading from \(modelPath)")
        let modelDir = URL(fileURLWithPath: modelPath)

        do {
            let opts = try ORTSessionOptions()
            try opts.setGraphOptimizationLevel(.all)
            try opts.setIntraOpNumThreads(4)

            // Load 4 Supertonic models
            durationPredictor = try ORTSession(env: env, modelPath: modelDir.appendingPathComponent("duration_predictor.onnx").path, sessionOptions: opts)
            textEncoder = try ORTSession(env: env, modelPath: modelDir.appendingPathComponent("text_encoder.onnx").path, sessionOptions: opts)
            vectorEstimator = try ORTSession(env: env, modelPath: modelDir.appendingPathComponent("vector_estimator.onnx").path, sessionOptions: opts)
            vocoder = try ORTSession(env: env, modelPath: modelDir.appendingPathComponent("vocoder.onnx").path, sessionOptions: opts)

            dpOutputNames = try durationPredictor!.outputNames()
            teOutputNames = try textEncoder!.outputNames()
            veOutputNames = try vectorEstimator!.outputNames()
            vocOutputNames = try vocoder!.outputNames()

            NSLog("HuggingFaceHandler: Outputs - DP:\(dpOutputNames) TE:\(teOutputNames) VE:\(veOutputNames) VOC:\(vocOutputNames)")

            try loadConfig(modelDir)
            try loadUnicodeIndexer(modelDir)
            try loadVoiceStyle(modelDir)

            isModelLoadedFlag = true
            modelSampleRate = Int32(config.sampleRate)
            NSLog("HuggingFaceHandler: Model loaded, sampleRate=\(config.sampleRate)")
            return KotlinBoolean(bool: true)
        } catch {
            NSLog("HuggingFaceHandler: Load failed: \(error)")
            return KotlinBoolean(bool: false)
        }
    }

    func unloadModel() {
        durationPredictor = nil
        textEncoder = nil
        vectorEstimator = nil
        vocoder = nil
        isModelLoadedFlag = false
        voiceStyle = nil
        unicodeIndexer = []
    }

    func isModelLoaded() -> Bool { isModelLoadedFlag }

    func synthesize(text: String, voiceStylePath: String?, speechRate: Float, volume: Float) async throws -> KotlinFloatArray? {
        guard isModelLoadedFlag,
              let dp = durationPredictor, let te = textEncoder,
              let ve = vectorEstimator, let voc = vocoder,
              voiceStyle != nil else {
            NSLog("HuggingFaceHandler: Model not loaded")
            return nil
        }

        isStopped = false
        currentUtteranceId = UUID().uuidString
        eventCallback?.onSynthesisStart(utteranceId: currentUtteranceId)

        do {
            // Load voice style if path provided (must be done before capturing style)
            if let path = voiceStylePath {
                try? loadVoiceStyleFromPath(path)
                NSLog("HuggingFaceHandler: Loaded voice style from \(path)")
            }

            // Capture the current voice style after potential update
            guard let style = voiceStyle else {
                eventCallback?.onSynthesisError(utteranceId: currentUtteranceId, errorMessage: "Voice style not loaded")
                return nil
            }

            let tokens = tokenize(text)
            guard !tokens.isEmpty else {
                eventCallback?.onSynthesisError(utteranceId: currentUtteranceId, errorMessage: "Empty tokens")
                return nil
            }

            let seqLen = tokens.count
            if isStopped { return nil }

            // Create tensors
            let textIdsTensor = try createInt64Tensor(tokens, shape: [1, seqLen])
            let textMaskTensor = try createFloatTensor([Float](repeating: 1.0, count: seqLen), shape: [1, 1, seqLen])
            let dpStyleTensor = try createFloatTensor(style.dp, shape: [1, style.dpDims[1], style.dpDims[2]])
            let ttlStyleTensor = try createFloatTensor(style.ttl, shape: [1, style.ttlDims[1], style.ttlDims[2]])

            // Step 1: Duration Prediction
            if isStopped { return nil }

            let dpOut = try dp.run(withInputs: [
                "text_ids": textIdsTensor,
                "text_mask": textMaskTensor,
                "style_dp": dpStyleTensor
            ], outputNames: Set(dpOutputNames), runOptions: nil)

            guard let durOutput = dpOut[dpOutputNames.first!] else { throw NSError(domain: "HF", code: 1) }
            let durations: [Float] = try extractFloats(durOutput)

            var totalWavLen = 0
            for i in 0..<min(durations.count, seqLen) {
                totalWavLen += Int(max(0.01, durations[i] / speechRate) * Float(config.sampleRate))
            }
            let latentLen = max(1, (totalWavLen + config.chunkSize - 1) / config.chunkSize)

            // Step 2: Text Encoding
            if isStopped { return nil }

            let teOut = try te.run(withInputs: [
                "text_ids": textIdsTensor,
                "text_mask": textMaskTensor,
                "style_ttl": ttlStyleTensor
            ], outputNames: Set(teOutputNames), runOptions: nil)

            guard let textEmb = teOut[teOutputNames.first!] else { throw NSError(domain: "HF", code: 2) }

            // Step 3: Generate seeded noise & denoise
            // Use text hash as seed for reproducible results (same as Android)
            var noisyLatent = seededGaussianNoise(count: config.latentDimVal * latentLen, seed: text.hashValue)
            let latentMaskTensor = try createFloatTensor([Float](repeating: 1.0, count: latentLen), shape: [1, 1, latentLen])

            let totalSteps = 5
            for step in 0..<totalSteps {
                if isStopped { return nil }

                let veOut = try ve.run(withInputs: [
                    "noisy_latent": try createFloatTensor(noisyLatent, shape: [1, config.latentDimVal, latentLen]),
                    "text_emb": textEmb,
                    "style_ttl": ttlStyleTensor,
                    "text_mask": textMaskTensor,
                    "latent_mask": latentMaskTensor,
                    "current_step": try createFloatTensor([Float(step)], shape: [1]),
                    "total_step": try createFloatTensor([Float(totalSteps)], shape: [1])
                ], outputNames: Set(veOutputNames), runOptions: nil)

                guard let denoised = veOut[veOutputNames.first!] else { throw NSError(domain: "HF", code: 3) }
                noisyLatent = try extractFloats(denoised)
            }

            // Step 4: Vocoder
            if isStopped { return nil }

            let vocOut = try voc.run(withInputs: [
                "latent": try createFloatTensor(noisyLatent, shape: [1, config.latentDimVal, latentLen])
            ], outputNames: Set(vocOutputNames), runOptions: nil)

            guard let audioOut = vocOut[vocOutputNames.first!] else { throw NSError(domain: "HF", code: 4) }
            var samples: [Float] = try extractFloats(audioOut)

            if volume != 1.0 { samples = samples.map { $0 * volume } }

            let result = KotlinFloatArray(size: Int32(samples.count))
            for (i, s) in samples.enumerated() { result.set(index: Int32(i), value: s) }

            eventCallback?.onSynthesisComplete(utteranceId: currentUtteranceId)
            NSLog("HuggingFaceHandler: Done - \(samples.count) samples")
            return result

        } catch {
            NSLog("HuggingFaceHandler: Synthesis failed: \(error)")
            eventCallback?.onSynthesisError(utteranceId: currentUtteranceId, errorMessage: error.localizedDescription)
            return nil
        }
    }

    func getSampleRate() -> Int32 { modelSampleRate }
    func stop() { isStopped = true }
    func shutdown() { stop(); unloadModel(); ortEnvironment = nil; isInitializedFlag = false }
    func setEventCallback(callback: HuggingFaceHandlerHuggingFaceEventCallback?) { eventCallback = callback }

    // MARK: - Config Loading

    private func loadConfig(_ dir: URL) throws {
        let url = dir.appendingPathComponent("tts.json")
        guard FileManager.default.fileExists(atPath: url.path) else { return }
        let data = try Data(contentsOf: url)
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let ae = json["ae"] as? [String: Any],
              let ttl = json["ttl"] as? [String: Any] else { return }
        config = SupertonicConfig(
            sampleRate: ae["sample_rate"] as? Int ?? 44100,
            baseChunkSize: ae["base_chunk_size"] as? Int ?? 512,
            chunkCompressFactor: ttl["chunk_compress_factor"] as? Int ?? 6,
            latentDim: ttl["latent_dim"] as? Int ?? 24
        )
    }

    private func loadUnicodeIndexer(_ dir: URL) throws {
        let url = dir.appendingPathComponent("unicode_indexer.json")
        let data = try Data(contentsOf: url)
        if let list = try JSONSerialization.jsonObject(with: data) as? [Int] { unicodeIndexer = list }
    }

    private func loadVoiceStyle(_ dir: URL) throws {
        var url = dir.appendingPathComponent("voice_styles/F1.json")
        if !FileManager.default.fileExists(atPath: url.path) { url = dir.appendingPathComponent("F1.json") }
        try loadVoiceStyleFromPath(url.path)
    }

    private func loadVoiceStyleFromPath(_ path: String) throws {
        let data = try Data(contentsOf: URL(fileURLWithPath: path))
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let styleTtl = json["style_ttl"] as? [String: Any],
              let styleDp = json["style_dp"] as? [String: Any],
              let ttlDims = styleTtl["dims"] as? [Int],
              let dpDims = styleDp["dims"] as? [Int],
              let ttlData = styleTtl["data"] as? [[[Double]]],
              let dpData = styleDp["data"] as? [[[Double]]] else { return }
        voiceStyle = VoiceStyleData(
            ttl: ttlData.flatMap { $0.flatMap { $0.map { Float($0) } } },
            ttlDims: ttlDims,
            dp: dpData.flatMap { $0.flatMap { $0.map { Float($0) } } },
            dpDims: dpDims
        )
    }

    // MARK: - Helpers

    private func tokenize(_ text: String) -> [Int64] {
        let space = unicodeIndexer.count > 32 ? unicodeIndexer[32] : 0
        return text.unicodeScalars.map { c in
            let cp = Int(c.value)
            let id = cp < unicodeIndexer.count ? unicodeIndexer[cp] : space
            return Int64(id >= 0 ? id : space)
        }
    }

    private func createInt64Tensor(_ data: [Int64], shape: [Int]) throws -> ORTValue {
        try ORTValue(tensorData: NSMutableData(data: Data(bytes: data, count: data.count * 8)), elementType: .int64, shape: shape.map { NSNumber(value: $0) })
    }

    private func createFloatTensor(_ data: [Float], shape: [Int]) throws -> ORTValue {
        try ORTValue(tensorData: NSMutableData(data: Data(bytes: data, count: data.count * 4)), elementType: .float, shape: shape.map { NSNumber(value: $0) })
    }

    private func extractFloats(_ value: ORTValue) throws -> [Float] {
        let data = try value.tensorData() as Data
        return data.withUnsafeBytes { Array($0.bindMemory(to: Float.self)) }
    }

    /// Generates seeded Gaussian noise using Box-Muller transform
    /// Uses srand48/drand48 for reproducible random numbers based on seed
    private func seededGaussianNoise(count: Int, seed: Int) -> [Float] {
        srand48(seed)  // Seed the random generator for reproducibility
        var noise = [Float](repeating: 0, count: count)
        for i in stride(from: 0, to: count - 1, by: 2) {
            let u1 = max(0.0001, min(0.9999, drand48()))
            let u2 = max(0.0001, min(0.9999, drand48()))
            let r = sqrt(-2.0 * log(u1))
            noise[i] = Float(r * cos(2.0 * .pi * u2))
            if i + 1 < count { noise[i + 1] = Float(r * sin(2.0 * .pi * u2)) }
        }
        return noise
    }
}
