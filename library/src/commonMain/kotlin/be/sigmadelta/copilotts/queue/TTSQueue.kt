/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.queue

import be.sigmadelta.copilotts.SpeakRequest
import be.sigmadelta.copilotts.TTSPriority
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Priority-based queue for TTS utterances.
 * Higher priority items are processed first.
 */
class TTSQueue(
    private val maxSize: Int = 100
) {
    private val queue = mutableListOf<TTSQueueItem>()
    private val mutex = Mutex()

    private val _queueSize = MutableStateFlow(0)
    val queueSize: StateFlow<Int> = _queueSize.asStateFlow()

    private val _isEmpty = MutableStateFlow(true)
    val isEmpty: StateFlow<Boolean> = _isEmpty.asStateFlow()

    /**
     * Add an item to the queue.
     * @param request The speak request
     * @return The utterance ID, or null if queue is full and cannot accommodate
     */
    suspend fun enqueue(request: SpeakRequest): String? {
        return mutex.withLock {
            if (queue.size >= maxSize) {
                // Try to make room by removing lowest priority item
                val lowestPriority = queue.minByOrNull { it.request.priority.ordinal }
                if (lowestPriority != null &&
                    lowestPriority.request.priority.ordinal < request.priority.ordinal
                ) {
                    queue.remove(lowestPriority)
                    Napier.d { "TTSQueue: Removed lower priority item to make room" }
                } else {
                    Napier.w { "TTSQueue: Queue full, cannot enqueue" }
                    return@withLock null
                }
            }

            val item = TTSQueueItem(request = request)
            queue.add(item)
            queue.sortDescending() // Sort by priority (descending)
            updateState()
            Napier.d { "TTSQueue: Enqueued ${item.id} (priority: ${request.priority}, size: ${queue.size})" }
            item.id
        }
    }

    /**
     * Remove and return the highest priority item.
     * @return The next item, or null if queue is empty
     */
    suspend fun dequeue(): TTSQueueItem? {
        return mutex.withLock {
            if (queue.isEmpty()) {
                null
            } else {
                val item = queue.removeAt(0)
                updateState()
                Napier.d { "TTSQueue: Dequeued ${item.id} (size: ${queue.size})" }
                item
            }
        }
    }

    /**
     * Peek at the highest priority item without removing it.
     * @return The next item, or null if queue is empty
     */
    suspend fun peek(): TTSQueueItem? {
        return mutex.withLock {
            queue.firstOrNull()
        }
    }

    /**
     * Clear the entire queue.
     */
    suspend fun clear() {
        mutex.withLock {
            queue.clear()
            updateState()
            Napier.d { "TTSQueue: Cleared" }
        }
    }

    /**
     * Remove a specific item by ID.
     * @param utteranceId The ID of the item to remove
     * @return true if item was removed
     */
    suspend fun remove(utteranceId: String): Boolean {
        return mutex.withLock {
            val item = queue.find { it.id == utteranceId }
            val removed = item?.let { queue.remove(it) } ?: false
            if (removed) {
                updateState()
                Napier.d { "TTSQueue: Removed $utteranceId (size: ${queue.size})" }
            }
            removed
        }
    }

    /**
     * Clear all items with priority lower than the specified priority.
     * @param priority Minimum priority to keep
     */
    suspend fun clearLowerPriority(priority: TTSPriority) {
        mutex.withLock {
            val sizeBefore = queue.size
            queue.removeAll { it.request.priority.ordinal < priority.ordinal }
            updateState()
            val removed = sizeBefore - queue.size
            if (removed > 0) {
                Napier.d { "TTSQueue: Cleared $removed items below $priority priority" }
            }
        }
    }

    /**
     * Get all items in the queue (for inspection).
     * @return Copy of the queue items
     */
    suspend fun getAll(): List<TTSQueueItem> {
        return mutex.withLock {
            queue.toList()
        }
    }

    /**
     * Check if queue contains an item with the given ID.
     */
    suspend fun contains(utteranceId: String): Boolean {
        return mutex.withLock {
            queue.any { it.id == utteranceId }
        }
    }

    /**
     * Get the current size of the queue.
     */
    suspend fun size(): Int {
        return mutex.withLock {
            queue.size
        }
    }

    private fun updateState() {
        _queueSize.value = queue.size
        _isEmpty.value = queue.isEmpty()
    }
}
