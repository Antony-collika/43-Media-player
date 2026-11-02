package com.devson.nvplayerlite.media

import android.net.Uri
import androidx.media3.common.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MediaItemMapperTest {
    @Test
    fun mapsAudioEntryToMediaItemWithMetadata() {
        val entry = MediaEntry(
            id = "content://media/external/audio/media/42",
            uri = Uri.parse("content://media/external/audio/media/42"),
            title = "Track title",
            artist = "Artist",
            album = "Album",
            durationMs = 1234L,
            mimeType = "audio/mpeg",
            kind = MediaKind.AUDIO,
            artworkUri = Uri.parse("content://media/external/audio/albumart/7")
        )

        val item = MediaItemMapper.toMediaItem(entry)

        assertEquals(entry.id, item.mediaId)
        assertEquals(entry.uri, item.localConfiguration?.uri)
        assertEquals(entry.mimeType, item.localConfiguration?.mimeType)
        assertEquals(entry.title, item.mediaMetadata.title)
        assertEquals(entry.artist, item.mediaMetadata.artist)
        assertEquals(entry.album, item.mediaMetadata.albumTitle)
        assertNotNull(item.mediaMetadata.artworkUri)
    }
}
