package com.mardous.booming.util

import com.mardous.booming.data.model.Song

class SongPlayCountHelper {

    private val stopWatch = StopWatch()
    private var startTimestamp = 0L
    private var scrobbled = false

    var song = Song.emptySong
        private set

    fun shouldBumpPlayCount(): Boolean {
        return song.duration * 0.5 < stopWatch.elapsedTime
    }

    fun takeScrobble(): Pair<Song, Long>? {
        synchronized(this) {
            if (song == Song.emptySong || song.resolvedFromFile) return null
            if (scrobbled || !shouldScrobble()) return null
            scrobbled = true
            return song to startTimestampSeconds
        }
    }

    fun notifySongChanged(song: Song, isPlaying: Boolean) {
        synchronized(this) {
            stopWatch.reset()
            scrobbled = false
            startTimestamp = if (isPlaying) System.currentTimeMillis() else 0L
            if (isPlaying) {
                stopWatch.start()
            }
            this.song = song
        }
    }

    fun notifyPlayStateChanged(isPlaying: Boolean) {
        synchronized(this) {
            if (isPlaying) {
                if (startTimestamp == 0L) {
                    startTimestamp = System.currentTimeMillis()
                }
                stopWatch.start()
            } else {
                stopWatch.pause()
            }
        }
    }

    private fun shouldScrobble(): Boolean {
        if (song.duration <= 0) return false
        val threshold = minOf(song.duration / 2, MAX_SCROBBLE_THRESHOLD)
        return stopWatch.elapsedTime >= threshold
    }

    private val startTimestampSeconds: Long
        get() = if (startTimestamp > 0) startTimestamp / 1000
        else System.currentTimeMillis() / 1000

    companion object {
        private const val MAX_SCROBBLE_THRESHOLD = 4 * 60 * 1000L
        val TAG: String = SongPlayCountHelper::class.java.simpleName
    }
}
