package com.mardous.booming.data.remote.listenbrainz

import com.mardous.booming.data.local.MetadataReader
import com.mardous.booming.data.model.Album
import com.mardous.booming.data.model.Artist
import com.mardous.booming.data.model.Song

data class ListenMetadata(
    val artistName: String,
    val trackName: String,
    val releaseName: String?,
    val recordingMbid: String?,
    val releaseMbid: String?,
    val releaseGroupMbid: String?,
    val trackMbid: String?,
    val artistMbids: List<String>,
    val workMbids: List<String>,
    val trackNumber: String?,
    val durationMs: Long?
)

class ListenMetadataResolver {

    fun resolve(song: Song): ListenMetadata {
        val reader = MetadataReader(song.uri)
        val tags = reader.all()

        val artistValues = tagValues(reader, MetadataReader.ARTIST)
            .ifEmpty { tagValues(reader, MetadataReader.ARTISTS) }
        val artistName = artistValues.joinToString(" & ")
            .ifBlank { song.artistName }
            .takeUnless { it.isBlank() || it.equals(Artist.UNKNOWN, ignoreCase = true) }
            ?: UNKNOWN_ARTIST
        val trackName = tagValue(reader, MetadataReader.TITLE)
            .ifBlank { song.title }
            .ifBlank { UNKNOWN_TRACK }
        val releaseName = tagValue(reader, MetadataReader.ALBUM)
            .ifBlank { song.albumName }
            .takeUnless {
                it.isBlank() ||
                        it.equals(Album.UNKNOWN_ALBUM_DISPLAY_NAME, ignoreCase = true) ||
                        it.equals(Artist.UNKNOWN, ignoreCase = true)
            }
        val trackNumber = tagValue(reader, MetadataReader.TRACK_NUMBER)
            .substringBefore("/")
            .toIntOrNull()
            ?.takeIf { it > 0 }
            ?: song.trackNumber.takeIf { it > 0 }

        return ListenMetadata(
            artistName = artistName,
            trackName = trackName,
            releaseName = releaseName,
            recordingMbid = singleMbid(tags, MetadataReader.MUSICBRAINZ_TRACKID),
            releaseMbid = singleMbid(tags, MetadataReader.MUSICBRAINZ_ALBUMID),
            releaseGroupMbid = singleMbid(tags, MetadataReader.MUSICBRAINZ_RELEASEGROUPID),
            trackMbid = singleMbid(tags, MetadataReader.MUSICBRAINZ_RELEASETRACKID),
            artistMbids = multipleMbids(tags, MetadataReader.MUSICBRAINZ_ARTISTID),
            workMbids = multipleMbids(tags, MetadataReader.MUSICBRAINZ_WORKID),
            trackNumber = trackNumber?.toString(),
            durationMs = song.duration.takeIf { it > 0 }
        )
    }

    private fun tagValue(reader: MetadataReader, key: String): String =
        tagValues(reader, key).firstOrNull().orEmpty()

    private fun tagValues(reader: MetadataReader, key: String): List<String> =
        reader.all(key, delimiters = emptyArray())
            .orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun singleMbid(tags: Map<String, Array<String>>, key: String): String? =
        mbidValues(tags[key]).firstOrNull()

    private fun multipleMbids(tags: Map<String, Array<String>>, key: String): List<String> =
        mbidValues(tags[key])

    private fun mbidValues(values: Array<String>?): List<String> {
        return values.orEmpty()
            .asSequence()
            .flatMap { it.split('/', ';', ',').asSequence() }
            .map { it.trim() }
            .filter { MBID_PATTERN.matches(it) }
            .distinct()
            .toList()
    }

    companion object {
        private const val UNKNOWN_ARTIST = "Unknown Artist"
        private const val UNKNOWN_TRACK = "Unknown Track"
        private val MBID_PATTERN = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )
    }
}
