package com.mardous.booming.data.remote.listenbrainz

import android.util.Log
import com.mardous.booming.data.local.room.ListenBrainzQueueDao
import com.mardous.booming.data.local.room.ListenBrainzQueueEntity
import com.mardous.booming.data.model.network.NetworkFeature
import com.mardous.booming.data.remote.listenbrainz.model.ListenBrainzListen
import com.mardous.booming.data.remote.listenbrainz.model.ListenBrainzSubmission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class ListenBrainzQueue(
    private val dao: ListenBrainzQueueDao,
    private val service: ListenBrainzService,
    private val sessionStore: ListenBrainzSessionStore
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Volatile
    private var retryScheduled = false

    fun start() {
        scope.launch { flush() }
    }

    suspend fun enqueue(listen: ListenBrainzListen) {
        try {
            val listenedAt = listen.listenedAt ?: return

            val count = dao.count()
            if (count >= MAX_QUEUE_SIZE) {
                Log.w(TAG, "ListenBrainz: queue full, dropping oldest listens")
                dao.deleteOldest(count - MAX_QUEUE_SIZE + 1)
            }
            dao.insert(
                ListenBrainzQueueEntity(
                    listenedAt = listenedAt,
                    listenJson = json.encodeToString(ListenBrainzListen.serializer(), listen),
                    createdAt = System.currentTimeMillis()
                )
            )
            scope.launch { flush() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "ListenBrainz: could not queue listen", e)
        }
    }

    suspend fun clear() {
        retryScheduled = false
        mutex.withLock { dao.clear() }
    }

    suspend fun flush() {
        try {
            flushPendingListens()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "ListenBrainz: could not flush queued listens", e)
        }
    }

    private suspend fun flushPendingListens() {
        mutex.withLock {
            if (!NetworkFeature.ListenBrainz.Scrobbling.isAvailable) {
                if (NetworkFeature.ListenBrainz.Scrobbling.isEnabled && dao.count() > 0) {
                    scheduleRetry(NETWORK_RETRY_DELAY_MS)
                }
                return
            }
            val token = sessionStore.token() ?: return

            while (true) {
                val entries = dao.pendingListens(BATCH_SIZE)
                if (entries.isEmpty()) return

                val decodedEntries = entries.map { entry ->
                    entry to runCatching {
                        json.decodeFromString(ListenBrainzListen.serializer(), entry.listenJson)
                    }.getOrNull()
                }
                val rejectedIds = decodedEntries
                    .filter { it.second == null }
                    .map { it.first.id }
                if (rejectedIds.isNotEmpty()) {
                    dao.delete(rejectedIds)
                }
                val listens = decodedEntries.mapNotNull { it.second }
                if (listens.isEmpty()) continue

                val result = service.submitListens(
                    token = token,
                    submission = ListenBrainzSubmission(IMPORT, listens)
                )
                val ids = entries.map { it.id }

                when (result) {
                    ListenBrainzResult.Success -> {
                        dao.delete(ids)
                    }

                    ListenBrainzResult.Unauthorized -> {
                        return
                    }

                    is ListenBrainzResult.RateLimited -> {
                        scheduleRetry(result.retryAfterSeconds?.times(1000) ?: RATE_LIMIT_RETRY_DELAY_MS)
                        return
                    }

                    is ListenBrainzResult.Rejected -> {
                        Log.e(TAG, "ListenBrainz: rejected queued listens: ${result.message}")
                        dao.delete(ids)
                    }

                    ListenBrainzResult.Retry -> {
                        dao.incrementAttempts(ids)
                        val attempts = entries.maxOf { it.attempts } + 1
                        scheduleRetry(retryDelay(attempts))
                        return
                    }
                }
            }
        }
    }

    private fun scheduleRetry(delayMillis: Long) {
        if (retryScheduled) return
        retryScheduled = true
        scope.launch {
            delay(delayMillis)
            retryScheduled = false
            flush()
        }
    }

    private fun retryDelay(attempts: Int): Long {
        val exponent = (attempts - 1).coerceIn(0, 6)
        return (BASE_RETRY_DELAY_MS shl exponent).coerceAtMost(MAX_RETRY_DELAY_MS)
    }

    companion object {
        private const val TAG = "ListenBrainzQueue"
        private const val IMPORT = "import"
        private const val BATCH_SIZE = 100
        private const val MAX_QUEUE_SIZE = 1000
        private const val NETWORK_RETRY_DELAY_MS = 5 * 60_000L
        private const val RATE_LIMIT_RETRY_DELAY_MS = 60_000L
        private const val BASE_RETRY_DELAY_MS = 30_000L
        private const val MAX_RETRY_DELAY_MS = 30 * 60_000L
    }
}
