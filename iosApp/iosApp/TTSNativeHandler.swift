//
//  TTSNativeHandler.swift
//  iosApp
//
//  Copyright (c) 2025 Sigma Delta BV
//  Licensed under the MIT License
//
//  iOS implementation of TTS using AVSpeechSynthesizer
//

import Foundation
import AVFoundation
import ComposeApp

/// iOS native TTS handler using AVSpeechSynthesizer
/// Implements TTSNativeHandler protocol from Kotlin for cross-platform TTS support
class TTSNativeHandlerImpl: NSObject, TTSNativeHandler, AVSpeechSynthesizerDelegate {

    // MARK: - Properties

    private var synthesizer: AVSpeechSynthesizer?
    private var speechRate: Float = AVSpeechUtteranceDefaultSpeechRate
    private var pitch: Float = 1.0
    private var volume: Float = 1.0
    private var isInitializedFlag: Bool = false
    private var isPausedFlag: Bool = false
    private var selectedVoiceId: String?
    private var eventCallback: TTSNativeHandlerTTSEventCallback?

    // Track current utterance for progress reporting
    private var currentUtteranceId: String = ""
    private var currentUtteranceText: String = ""

    // Priority levels matching Kotlin TTSPriority enum
    private let PRIORITY_LOW = 0
    private let PRIORITY_NORMAL = 1
    private let PRIORITY_HIGH = 2
    private let PRIORITY_URGENT = 3

    // MARK: - TTSNativeHandler Protocol Implementation

    func initialize() async throws -> KotlinBoolean {
        NSLog("TTSNativeHandler: Initializing AVSpeechSynthesizer")

        // Configure audio session for speech
        do {
            let audioSession = AVAudioSession.sharedInstance()

            // Use playback category with duck others option for navigation guidance
            try audioSession.setCategory(
                .playback,
                mode: .voicePrompt,
                options: [.duckOthers, .interruptSpokenAudioAndMixWithOthers]
            )
            try audioSession.setActive(true)
            NSLog("TTSNativeHandler: Audio session configured for TTS")
        } catch {
            NSLog("TTSNativeHandler: Failed to configure audio session: \(error)")
            return KotlinBoolean(bool: false)
        }

        // Create synthesizer
        synthesizer = AVSpeechSynthesizer()
        synthesizer?.delegate = self

        isInitializedFlag = true
        NSLog("TTSNativeHandler: AVSpeechSynthesizer initialized successfully")
        return KotlinBoolean(bool: true)
    }

    func speak(
        text: String,
        priority: Int32,
        rate: KotlinFloat?,
        pitch: KotlinFloat?,
        volume: KotlinFloat?,
        voiceId: String?
    ) {
        guard isInitializedFlag, let synthesizer = synthesizer else {
            NSLog("TTSNativeHandler: Cannot speak - not initialized")
            return
        }

        NSLog("TTSNativeHandler: Speaking: '\(text)' (priority=\(priority))")

        // Generate utterance ID
        currentUtteranceId = UUID().uuidString
        currentUtteranceText = text

        // For HIGH and URGENT priority, stop current speech first
        if priority >= PRIORITY_HIGH && synthesizer.isSpeaking {
            NSLog("TTSNativeHandler: High priority - stopping current speech")
            synthesizer.stopSpeaking(at: .immediate)
        }

        // Create utterance
        let utterance = AVSpeechUtterance(string: text)

        // Configure utterance - use overrides if provided, else defaults
        if let rateValue = rate?.floatValue {
            utterance.rate = mapSpeechRate(rateValue)
        } else {
            utterance.rate = speechRate
        }
        utterance.pitchMultiplier = pitch?.floatValue ?? self.pitch
        utterance.volume = volume?.floatValue ?? self.volume

        // Set voice
        let voiceToUse = voiceId ?? selectedVoiceId
        if let vId = voiceToUse, let voice = AVSpeechSynthesisVoice(identifier: vId) {
            utterance.voice = voice
        } else if let voice = AVSpeechSynthesisVoice(language: Locale.current.language.languageCode?.identifier ?? "en-US") {
            utterance.voice = voice
        } else if let englishVoice = AVSpeechSynthesisVoice(language: "en-US") {
            utterance.voice = englishVoice
        }

        // Pre/post utterance delays based on priority
        switch Int(priority) {
        case PRIORITY_URGENT:
            utterance.preUtteranceDelay = 0
            utterance.postUtteranceDelay = 0.1
        case PRIORITY_HIGH:
            utterance.preUtteranceDelay = 0.1
            utterance.postUtteranceDelay = 0.2
        default:
            utterance.preUtteranceDelay = 0.2
            utterance.postUtteranceDelay = 0.3
        }

        // Reset paused flag
        isPausedFlag = false

        // Speak
        synthesizer.speak(utterance)
    }

    func pause() {
        NSLog("TTSNativeHandler: Pausing speech")
        if synthesizer?.isSpeaking == true {
            synthesizer?.pauseSpeaking(at: .word)
            isPausedFlag = true
        }
    }

    func resume() {
        NSLog("TTSNativeHandler: Resuming speech")
        if isPausedFlag {
            synthesizer?.continueSpeaking()
            isPausedFlag = false
        }
    }

    func cancelAll() {
        NSLog("TTSNativeHandler: Cancelling all speech")
        synthesizer?.stopSpeaking(at: .immediate)
        isPausedFlag = false
    }

    func shutdown() {
        NSLog("TTSNativeHandler: Shutting down")
        synthesizer?.stopSpeaking(at: .immediate)
        synthesizer?.delegate = nil
        synthesizer = nil
        isInitializedFlag = false
        isPausedFlag = false
        eventCallback = nil

        // Deactivate audio session
        do {
            try AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
            NSLog("TTSNativeHandler: Audio session deactivated")
        } catch {
            NSLog("TTSNativeHandler: Failed to deactivate audio session: \(error)")
        }
    }

    func setSpeechRate(rate: Float) {
        speechRate = mapSpeechRate(rate)
        NSLog("TTSNativeHandler: Speech rate set to \(speechRate) (input: \(rate))")
    }

    func setPitch(pitch: Float) {
        // iOS pitch range is 0.5 to 2.0, same as our API
        self.pitch = max(0.5, min(2.0, pitch))
        NSLog("TTSNativeHandler: Pitch set to \(self.pitch)")
    }

    func setVolume(volume: Float) {
        self.volume = max(0.0, min(1.0, volume))
        NSLog("TTSNativeHandler: Volume set to \(self.volume)")
    }

    func setVoice(voiceId: String) {
        selectedVoiceId = voiceId
        NSLog("TTSNativeHandler: Voice set to \(voiceId)")
    }

    func isSpeaking() -> Bool {
        return synthesizer?.isSpeaking ?? false
    }

    func isPaused() -> Bool {
        return isPausedFlag
    }

    func getAvailableVoices() -> [TTSNativeHandlerVoiceInfo] {
        let systemVoices = AVSpeechSynthesisVoice.speechVoices()

        return systemVoices.map { voice in
            let gender: String
            switch voice.gender {
            case .male:
                gender = "MALE"
            case .female:
                gender = "FEMALE"
            default:
                gender = "UNKNOWN"
            }

            let quality: String
            switch voice.quality {
            case .enhanced:
                quality = "ENHANCED"
            case .premium:
                quality = "PREMIUM"
            default:
                quality = "DEFAULT"
            }

            return TTSNativeHandlerVoiceInfo(
                identifier: voice.identifier,
                name: voice.name,
                language: voice.language,
                gender: gender,
                quality: quality
            )
        }
    }

    func setEventCallback(callback_: TTSNativeHandlerTTSEventCallback?) {
        eventCallback = callback_
    }

    // MARK: - Helper Methods

    /// Map from Kotlin scale (0.5-2.0) to iOS scale
    /// iOS: AVSpeechUtteranceMinimumSpeechRate (0.0) to AVSpeechUtteranceMaximumSpeechRate (1.0)
    /// Default is around 0.5
    private func mapSpeechRate(_ rate: Float) -> Float {
        let mappedRate = (rate - 0.5) * (AVSpeechUtteranceMaximumSpeechRate - AVSpeechUtteranceMinimumSpeechRate) / 1.5 + AVSpeechUtteranceMinimumSpeechRate
        return max(AVSpeechUtteranceMinimumSpeechRate, min(AVSpeechUtteranceMaximumSpeechRate, mappedRate))
    }

    // MARK: - AVSpeechSynthesizerDelegate

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didStart utterance: AVSpeechUtterance) {
        NSLog("TTSNativeHandler: Speech started")
        eventCallback?.onStart(utteranceId: currentUtteranceId)
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        NSLog("TTSNativeHandler: Speech finished")
        eventCallback?.onDone(utteranceId: currentUtteranceId)
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        NSLog("TTSNativeHandler: Speech cancelled")
        eventCallback?.onCancelled(utteranceId: currentUtteranceId)
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didPause utterance: AVSpeechUtterance) {
        NSLog("TTSNativeHandler: Speech paused")
        isPausedFlag = true
        eventCallback?.onPause(utteranceId: currentUtteranceId)
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didContinue utterance: AVSpeechUtterance) {
        NSLog("TTSNativeHandler: Speech continued")
        isPausedFlag = false
        eventCallback?.onResume(utteranceId: currentUtteranceId)
    }

    func speechSynthesizer(
        _ synthesizer: AVSpeechSynthesizer,
        willSpeakRangeOfSpeechString characterRange: NSRange,
        utterance: AVSpeechUtterance
    ) {
        // Calculate progress
        let totalLength = Float(currentUtteranceText.count)
        let currentPosition = Float(characterRange.location + characterRange.length)
        let progress = totalLength > 0 ? currentPosition / totalLength : 0

        // Extract current word
        let nsString = currentUtteranceText as NSString
        let word = nsString.substring(with: characterRange)

        eventCallback?.onProgress(utteranceId: currentUtteranceId, progress: progress, word: word)
    }
}
