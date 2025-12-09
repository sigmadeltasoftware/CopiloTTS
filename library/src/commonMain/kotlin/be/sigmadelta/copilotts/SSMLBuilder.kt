/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts

/**
 * Builder for constructing SSML (Speech Synthesis Markup Language) documents.
 * SSML allows fine-grained control over speech synthesis.
 *
 * Example:
 * ```kotlin
 * val ssml = ssml {
 *     text("Hello, ")
 *     emphasis("world", EmphasisLevel.STRONG)
 *     pause(500)
 *     sayAs("12345", InterpretAs.DIGITS)
 * }
 * ```
 */
class SSMLBuilder {
    private val content = StringBuilder()

    /**
     * Add plain text.
     */
    fun text(text: String): SSMLBuilder {
        content.append(escapeXml(text))
        return this
    }

    /**
     * Add a pause/break.
     * @param milliseconds Duration of the pause in milliseconds
     */
    fun pause(milliseconds: Int): SSMLBuilder {
        content.append("<break time=\"${milliseconds}ms\"/>")
        return this
    }

    /**
     * Add a pause using strength.
     * @param strength Pause strength
     */
    fun pause(strength: BreakStrength): SSMLBuilder {
        content.append("<break strength=\"${strength.value}\"/>")
        return this
    }

    /**
     * Add emphasized text.
     * @param text Text to emphasize
     * @param level Emphasis level
     */
    fun emphasis(text: String, level: EmphasisLevel = EmphasisLevel.MODERATE): SSMLBuilder {
        content.append("<emphasis level=\"${level.value}\">${escapeXml(text)}</emphasis>")
        return this
    }

    /**
     * Add text with prosody modifications.
     * @param text Text to speak
     * @param rate Speech rate (e.g., "slow", "fast", "150%", "+20%")
     * @param pitch Pitch (e.g., "low", "high", "+10%", "-5st")
     * @param volume Volume (e.g., "soft", "loud", "+5dB", "-10dB")
     */
    fun prosody(
        text: String,
        rate: String? = null,
        pitch: String? = null,
        volume: String? = null
    ): SSMLBuilder {
        val attrs = buildList {
            rate?.let { add("rate=\"$it\"") }
            pitch?.let { add("pitch=\"$it\"") }
            volume?.let { add("volume=\"$it\"") }
        }
        if (attrs.isEmpty()) {
            content.append(escapeXml(text))
        } else {
            content.append("<prosody ${attrs.joinToString(" ")}>${escapeXml(text)}</prosody>")
        }
        return this
    }

    /**
     * Add text interpreted in a specific way.
     * @param text Text to interpret
     * @param interpretAs How to interpret the text
     */
    fun sayAs(text: String, interpretAs: InterpretAs): SSMLBuilder {
        content.append("<say-as interpret-as=\"${interpretAs.value}\">${escapeXml(text)}</say-as>")
        return this
    }

    /**
     * Add text with date interpretation.
     * @param text Date text
     * @param format Date format (e.g., "mdy", "dmy", "ymd")
     */
    fun date(text: String, format: String = "mdy"): SSMLBuilder {
        content.append("<say-as interpret-as=\"date\" format=\"$format\">${escapeXml(text)}</say-as>")
        return this
    }

    /**
     * Add phonetic pronunciation.
     * @param text Display text
     * @param alphabet Phonetic alphabet (e.g., "ipa", "x-sampa")
     * @param ph Phonetic representation
     */
    fun phoneme(text: String, alphabet: String, ph: String): SSMLBuilder {
        content.append("<phoneme alphabet=\"$alphabet\" ph=\"$ph\">${escapeXml(text)}</phoneme>")
        return this
    }

    /**
     * Add substitution (display one thing, say another).
     * @param text Text to display
     * @param alias Text to speak
     */
    fun sub(text: String, alias: String): SSMLBuilder {
        content.append("<sub alias=\"${escapeXml(alias)}\">${escapeXml(text)}</sub>")
        return this
    }

    /**
     * Wrap text in a sentence tag.
     * @param text Sentence text
     */
    fun sentence(text: String): SSMLBuilder {
        content.append("<s>${escapeXml(text)}</s>")
        return this
    }

    /**
     * Wrap content in a sentence tag using a builder.
     */
    fun sentence(block: SSMLBuilder.() -> Unit): SSMLBuilder {
        content.append("<s>")
        block()
        content.append("</s>")
        return this
    }

    /**
     * Wrap text in a paragraph tag.
     * @param text Paragraph text
     */
    fun paragraph(text: String): SSMLBuilder {
        content.append("<p>${escapeXml(text)}</p>")
        return this
    }

    /**
     * Wrap content in a paragraph tag using a builder.
     */
    fun paragraph(block: SSMLBuilder.() -> Unit): SSMLBuilder {
        content.append("<p>")
        block()
        content.append("</p>")
        return this
    }

    /**
     * Build the final SSML string.
     */
    fun build(): String {
        return "<speak>$content</speak>"
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}

/**
 * Emphasis levels for SSML.
 */
enum class EmphasisLevel(val value: String) {
    STRONG("strong"),
    MODERATE("moderate"),
    REDUCED("reduced"),
    NONE("none")
}

/**
 * Break strength for SSML pauses.
 */
enum class BreakStrength(val value: String) {
    NONE("none"),
    X_WEAK("x-weak"),
    WEAK("weak"),
    MEDIUM("medium"),
    STRONG("strong"),
    X_STRONG("x-strong")
}

/**
 * Interpretation types for say-as element.
 */
enum class InterpretAs(val value: String) {
    /** Read as individual characters */
    CHARACTERS("characters"),
    /** Spell out */
    SPELL_OUT("spell-out"),
    /** Read as cardinal number */
    CARDINAL("cardinal"),
    /** Read as ordinal number */
    ORDINAL("ordinal"),
    /** Read as digits */
    DIGITS("digits"),
    /** Read as fraction */
    FRACTION("fraction"),
    /** Read as unit */
    UNIT("unit"),
    /** Read as date */
    DATE("date"),
    /** Read as time */
    TIME("time"),
    /** Read as telephone number */
    TELEPHONE("telephone"),
    /** Read as address */
    ADDRESS("address"),
    /** Bleep out (for expletives) */
    EXPLETIVE("expletive")
}

/**
 * Create SSML using DSL syntax.
 */
fun ssml(block: SSMLBuilder.() -> Unit): String {
    return SSMLBuilder().apply(block).build()
}
