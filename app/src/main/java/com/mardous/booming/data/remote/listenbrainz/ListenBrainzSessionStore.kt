package com.mardous.booming.data.remote.listenbrainz

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.mardous.booming.util.CryptoUtil
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

class ListenBrainzSessionStore(private val preferences: SharedPreferences) {

    fun userName(): String? = session()?.userName

    fun token(): String? = session()?.token

    fun save(token: String, userName: String): Boolean {
        return try {
            val encryptedToken = CryptoUtil.encrypt(token)
            val sessionInfo = Json.encodeToString(ListenBrainzSessionInfo(userName, encryptedToken))
            val encodedValue = Base64.encode(sessionInfo.toByteArray())
            preferences.edit(commit = true) {
                putString(LISTEN_BRAINZ_SESSION_INFO, encodedValue)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't save ListenBrainz token.", e)
            false
        }
    }

    fun clear() {
        preferences.edit(commit = true) {
            remove(LISTEN_BRAINZ_SESSION_INFO)
        }
    }

    private fun session(): ListenBrainzSessionInfo? {
        val encodedValue = preferences.getString(LISTEN_BRAINZ_SESSION_INFO, null)
        if (encodedValue.isNullOrBlank()) return null

        return try {
            val decodedValue = Base64.decode(encodedValue)
            val sessionInfo = Json.decodeFromString<ListenBrainzSessionInfo>(String(decodedValue))
            sessionInfo.copy(token = CryptoUtil.decrypt(sessionInfo.token))
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't decrypt ListenBrainz session info. Removing...", e)
            preferences.edit { remove(LISTEN_BRAINZ_SESSION_INFO) }
            null
        }
    }

    @Serializable
    private data class ListenBrainzSessionInfo(
        @SerialName("user_name")
        val userName: String,
        val token: String
    )

    companion object {
        private const val TAG = "ListenBrainzSessionStore"
        private const val LISTEN_BRAINZ_SESSION_INFO = "listenbrainz_session"
    }
}
