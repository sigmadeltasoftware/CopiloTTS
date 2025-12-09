/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample.ui.utils

/**
 * Utility functions for converting language codes to flag emojis.
 */
object FlagUtils {

    /**
     * Maps language codes to their corresponding flag emojis.
     */
    fun getFlagEmoji(languageCode: String): String {
        val code = languageCode.lowercase()

        // Extract country code from language code (e.g., "en-US" -> "US", "en_us" -> "US")
        val countryCode = if (code.contains("-") || code.contains("_")) {
            val extracted = code.substringAfter("-").substringAfter("_").uppercase()
            // Handle some edge cases where country code might be weird
            normalizeCountryCode(extracted)
        } else {
            languageToCountryCode(code)
        }

        return countryCode?.let { countryCodeToFlag(it) } ?: "\uD83C\uDF10"
    }

    /**
     * Normalizes country codes to handle variations.
     */
    private fun normalizeCountryCode(code: String): String {
        return when (code) {
            "UK" -> "GB"  // UK should be GB for flag
            else -> code
        }
    }

    /**
     * Maps language-only codes to their most common country code.
     */
    private fun languageToCountryCode(langCode: String): String? {
        return when (langCode) {
            "en" -> "US"
            "es" -> "ES"
            "zh" -> "CN"
            "hi" -> "IN"
            "fr" -> "FR"
            "de" -> "DE"
            "it" -> "IT"
            "pt" -> "PT"
            "ru" -> "RU"
            "ja" -> "JP"
            "ko" -> "KR"
            "ar" -> "SA"
            "nl" -> "NL"
            "pl" -> "PL"
            "tr" -> "TR"
            "vi" -> "VN"
            "th" -> "TH"
            "sv" -> "SE"
            "da" -> "DK"
            "no", "nb", "nn" -> "NO"
            "fi" -> "FI"
            "el" -> "GR"
            "he", "iw" -> "IL"
            "id" -> "ID"
            "ms" -> "MY"
            "cs" -> "CZ"
            "sk" -> "SK"
            "uk" -> "UA"
            "ro" -> "RO"
            "hu" -> "HU"
            "ca" -> "ES"
            "hr" -> "HR"
            "bg" -> "BG"
            "sr" -> "RS"
            "sl" -> "SI"
            "et" -> "EE"
            "lv" -> "LV"
            "lt" -> "LT"
            "fa" -> "IR"
            "bn" -> "BD"
            "ta" -> "IN"
            "te" -> "IN"
            "mr" -> "IN"
            "gu" -> "IN"
            "kn" -> "IN"
            "ml" -> "IN"
            "pa" -> "IN"
            "sw" -> "KE"
            "af" -> "ZA"
            "zu" -> "ZA"
            else -> null
        }
    }

    /**
     * Converts a 2-letter country code to a flag emoji using regional indicator symbols.
     */
    private fun countryCodeToFlag(countryCode: String): String {
        if (countryCode.length != 2) return "\uD83C\uDF10"

        val firstChar = countryCode[0].uppercaseChar()
        val secondChar = countryCode[1].uppercaseChar()

        val base = 0x1F1E6 - 'A'.code
        val firstCodePoint = base + firstChar.code
        val secondCodePoint = base + secondChar.code

        return StringBuilder()
            .appendCodePoint(firstCodePoint)
            .appendCodePoint(secondCodePoint)
            .toString()
    }

    /**
     * Appends a Unicode code point to a StringBuilder, handling surrogate pairs.
     */
    private fun StringBuilder.appendCodePoint(codePoint: Int): StringBuilder {
        if (codePoint in 0x10000..0x10FFFF) {
            val highSurrogate = ((codePoint - 0x10000) shr 10) + 0xD800
            val lowSurrogate = ((codePoint - 0x10000) and 0x3FF) + 0xDC00
            append(highSurrogate.toChar())
            append(lowSurrogate.toChar())
        } else {
            append(codePoint.toChar())
        }
        return this
    }
}
