/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.queue

import be.sigmadelta.copilotts.SpeakRequest
import be.sigmadelta.copilotts.TTSPriority
import kotlin.random.Random

/**
 * Item in the TTS queue.
 */
data class TTSQueueItem(
    /** Unique identifier for this utterance */
    val id: String = generateId(),

    /** The speak request */
    val request: SpeakRequest,

    /** Timestamp when item was added to queue */
    val timestamp: Long = currentTimeMillis()
) : Comparable<TTSQueueItem> {

    /**
     * Compare for priority ordering.
     * Higher priority items come first, then earlier timestamps.
     */
    override fun compareTo(other: TTSQueueItem): Int {
        // Higher priority should come first (descending order)
        val priorityCompare = other.request.priority.ordinal - this.request.priority.ordinal
        return if (priorityCompare != 0) {
            priorityCompare
        } else {
            // Earlier timestamp comes first
            (this.timestamp - other.timestamp).toInt()
        }
    }

    companion object {
        private fun generateId(): String {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            return (1..16).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        }
    }
}

/**
 * Get current time in milliseconds.
 */
expect fun currentTimeMillis(): Long
