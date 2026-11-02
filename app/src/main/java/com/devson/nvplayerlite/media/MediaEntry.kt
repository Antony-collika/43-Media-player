package com.devson.nvplayerlite.media

import android.net.Uri

/**
 * Common media representation shared by library, queue and playback mapping.
 *
 * The existing Video model remains intact during the migration; new audio/video
 * features should use this model at their boundary instead of widening Video.
 */
data class MediaEntry(
    val id: String,
    val uri: Uri,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0L,
    val mimeType: String? = null,
    val kind: MediaKind,
    val artworkUri: Uri? = null,
    val folder: String? = null,
    val resumePositionMs: Long = 0L
)
