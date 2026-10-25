package com.mardous.booming.data.remote.listenbrainz.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ListenBrainzSubmission(
    @SerialName("listen_type")
    val listenType: String,
    val payload: List<ListenBrainzListen>
)

@Serializable
data class ListenBrainzListen(
    @SerialName("listened_at")
    val listenedAt: Long? = null,
    @SerialName("track_metadata")
    val trackMetadata: ListenBrainzTrackMetadata
)

@Serializable
data class ListenBrainzTrackMetadata(
    @SerialName("artist_name")
    val artistName: String,
    @SerialName("track_name")
    val trackName: String,
    @SerialName("release_name")
    val releaseName: String? = null,
    @SerialName("additional_info")
    val additionalInfo: ListenBrainzTrackAdditionalInfo? = null
)

@Serializable
data class ListenBrainzTrackAdditionalInfo(
    @SerialName("media_player")
    val player: String? = null,
    @SerialName("media_player_version")
    val playerVersion: String? = null,
    @SerialName("submission_client")
    val submissionClient: String? = null,
    @SerialName("submission_client_version")
    val submissionClientVersion: String? = null,
    @SerialName("recording_mbid")
    val recordingMbid: String? = null,
    @SerialName("release_mbid")
    val releaseMbid: String? = null,
    @SerialName("release_group_mbid")
    val releaseGroupMbid: String? = null,
    @SerialName("track_mbid")
    val trackMbid: String? = null,
    @SerialName("artist_mbids")
    val artistMbids: List<String>? = null,
    @SerialName("work_mbids")
    val workMbids: List<String>? = null,
    @SerialName("tracknumber")
    val trackNumber: String? = null,
    @SerialName("duration_ms")
    val durationMs: Long? = null
)

@Serializable
data class ListenBrainzResponse(
    val status: String,
    val error: String? = null
)

@Serializable
data class ListenBrainzTokenValidationResponse(
    val code: Int,
    val message: String,
    val valid: Boolean,
    @SerialName("user_name")
    val userName: String? = null
)
