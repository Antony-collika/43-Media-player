package com.devson.nvplayerlite.media

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/** Converts the common domain model to the single Media3 playback model. */
object MediaItemMapper {
    fun toMediaItem(entry: MediaEntry): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(entry.title)
            .apply { entry.artist?.let(::setArtist) }
            .apply { entry.album?.let(::setAlbumTitle) }
            .apply { entry.artworkUri?.let(::setArtworkUri) }
            .build()

        return MediaItem.Builder()
            .setMediaId(entry.id)
            .setUri(entry.uri)
            .setMediaMetadata(metadata)
            .apply { entry.mimeType?.let(::setMimeType) }
            .build()
    }
}
