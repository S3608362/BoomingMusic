package com.mardous.booming.data.remote.listenbrainz

import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ListenBrainzRateLimiter {

    private val mutex = Mutex()
    private var lastRequestAt = 0L

    suspend fun await() {
        mutex.withLock {
            val elapsed = SystemClock.elapsedRealtime() - lastRequestAt
            if (lastRequestAt > 0 && elapsed < MIN_REQUEST_INTERVAL_MS) {
                delay(MIN_REQUEST_INTERVAL_MS - elapsed)
            }
            lastRequestAt = SystemClock.elapsedRealtime()
        }
    }

    companion object {
        private const val MIN_REQUEST_INTERVAL_MS = 1_000L
    }
}
