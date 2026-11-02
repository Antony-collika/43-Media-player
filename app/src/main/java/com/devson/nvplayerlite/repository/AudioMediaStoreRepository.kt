package com.devson.nvplayerlite.repository

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.devson.nvplayerlite.media.MediaEntry
import com.devson.nvplayerlite.media.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Reads audio metadata without coupling the audio library to VideoRepository. */
class AudioMediaStoreRepository(private val context: Context) {
    suspend fun getAllAudio(): List<MediaEntry> = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA
        )
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val entries = mutableListOf<MediaEntry>()
        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val displayNameColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val mimeTypeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
            val dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(collection, id)
                val path = cursor.optionalString(dataColumn)
                val title = cursor.optionalString(titleColumn)
                    ?.takeUnless { it.isBlank() }
                    ?: cursor.optionalString(displayNameColumn).orEmpty()
                val albumId = cursor.optionalLong(albumIdColumn)
                val artworkUri = albumId?.takeIf { it > 0L }?.let {
                    ContentUris.withAppendedId(
                        MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                        it
                    )
                }

                entries += MediaEntry(
                    id = uri.toString(),
                    uri = uri,
                    title = title,
                    artist = cursor.optionalString(artistColumn).normalizeMetadata(),
                    album = cursor.optionalString(albumColumn).normalizeMetadata(),
                    durationMs = cursor.optionalLong(durationColumn) ?: 0L,
                    mimeType = cursor.optionalString(mimeTypeColumn).normalizeMetadata(),
                    kind = MediaKind.AUDIO,
                    artworkUri = artworkUri,
                    folder = path?.let { File(it).parent },
                )
            }
        }
        entries
    }
}

private fun android.database.Cursor.optionalString(index: Int): String? =
    if (index >= 0 && !isNull(index)) getString(index) else null

private fun android.database.Cursor.optionalLong(index: Int): Long? =
    if (index >= 0 && !isNull(index)) getLong(index) else null

private fun String?.normalizeMetadata(): String? =
    this?.trim()?.takeUnless { it.isEmpty() || it == "<unknown>" }
