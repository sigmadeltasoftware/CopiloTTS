/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample.domain.usecase

import be.sigmadelta.copilotts.CopiloTTS
import be.sigmadelta.copilotts.TTSResult
import be.sigmadelta.copilotts.TTSVoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Use case for voice selection operations.
 * Handles voice listing, filtering, and selection logic.
 */
class VoiceSelectionUseCase(
    private val copiloTTS: CopiloTTS
) {
    companion object {
        /**
         * Priority order for most common languages (by number of speakers worldwide).
         */
        val LANGUAGE_PRIORITY = listOf(
            "en",  // English
            "es",  // Spanish
            "zh",  // Mandarin Chinese
            "hi",  // Hindi
            "fr"   // French
        )

        /**
         * Display names for priority languages.
         */
        val LANGUAGE_DISPLAY_NAMES = mapOf(
            "en" to "English",
            "es" to "Spanish",
            "zh" to "Chinese",
            "hi" to "Hindi",
            "fr" to "French"
        )
    }

    /**
     * Get all available voices as a Flow.
     */
    val availableVoices: Flow<List<TTSVoice>> = copiloTTS.availableVoices

    /**
     * Get voices sorted by language priority.
     */
    val sortedVoices: Flow<List<TTSVoice>> = availableVoices.map { voices ->
        voices.sortedByLanguagePriority()
    }

    /**
     * Get the prominent voices (first voice for each top language).
     */
    fun getProminentVoices(voices: List<TTSVoice>): List<TTSVoice> {
        return LANGUAGE_PRIORITY.mapNotNull { lang ->
            voices.firstOrNull { it.getBaseLanguage() == lang }
        }
    }

    /**
     * Get other voices not in the prominent list.
     */
    fun getOtherVoices(voices: List<TTSVoice>, prominentVoices: List<TTSVoice>): List<TTSVoice> {
        val prominentIds = prominentVoices.map { it.id }.toSet()
        return voices.filter { it.id !in prominentIds }.sortedByLanguagePriority()
    }

    /**
     * Find the default voice (prefer US English).
     */
    fun findDefaultVoice(voices: List<TTSVoice>): TTSVoice? {
        return voices.firstOrNull { it.languageCode.equals("en-US", ignoreCase = true) }
            ?: voices.firstOrNull { it.languageCode.startsWith("en", ignoreCase = true) }
            ?: voices.firstOrNull()
    }

    /**
     * Select a voice.
     */
    suspend fun selectVoice(voice: TTSVoice): TTSResult<Unit> {
        return copiloTTS.setVoice(voice)
    }

    /**
     * Set speech rate.
     */
    fun setSpeechRate(rate: Float) {
        copiloTTS.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }

    /**
     * Set pitch.
     */
    fun setPitch(pitch: Float) {
        copiloTTS.setPitch(pitch.coerceIn(0.5f, 2.0f))
    }

    /**
     * Set volume.
     */
    fun setVolume(volume: Float) {
        copiloTTS.setVolume(volume.coerceIn(0f, 1f))
    }
}

/**
 * Get the base language code (e.g., "en" from "en-US").
 */
fun TTSVoice.getBaseLanguage(): String =
    languageCode.substringBefore("-").substringBefore("_").lowercase()

/**
 * Sort voices by language priority.
 */
fun List<TTSVoice>.sortedByLanguagePriority(): List<TTSVoice> {
    return sortedWith(compareBy(
        { voice ->
            val priority = VoiceSelectionUseCase.LANGUAGE_PRIORITY.indexOf(voice.getBaseLanguage())
            if (priority >= 0) priority else Int.MAX_VALUE
        },
        { it.languageCode },
        { it.name }
    ))
}
