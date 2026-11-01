package com.devson.nvplayerlite.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import com.devson.nvplayerlite.model.Video
import androidx.media3.common.PlaybackException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.devson.nvplayerlite.model.TrackInfo
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.MediaItem.SubtitleConfiguration
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import android.media.audiofx.LoudnessEnhancer
import com.devson.nvplayerlite.util.AppLogger
import androidx.media3.common.C
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.devson.nvplayerlite.repository.DecoderMode
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.exoplayer.Renderer
import android.os.Handler
import androidx.media3.common.MimeTypes

@OptIn(UnstableApi::class)
class HDRFallbackRenderersFactory(context: Context) : NextRenderersFactory(context) {
    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: java.util.ArrayList<Renderer>
    ) {
        val dvFallbackCodecSelector = object : MediaCodecSelector {
            override fun getDecoderInfos(
                mimeType: String,
                requiresSecureDecoder: Boolean,
                requiresTunnelingDecoder: Boolean
            ): MutableList<MediaCodecInfo> {
                val targetMimeType = if (mimeType == MimeTypes.VIDEO_DOLBY_VISION) {
                    MimeTypes.VIDEO_H265
                } else {
                    mimeType
                }
                return MediaCodecSelector.DEFAULT.getDecoderInfos(
                    targetMimeType,
                    requiresSecureDecoder,
                    requiresTunnelingDecoder
                )
            }
        }

        super.buildVideoRenderers(
            context,
            extensionRendererMode,
            dvFallbackCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            allowedVideoJoiningTimeMs,
            out
        )
    }
}

@OptIn(UnstableApi::class)
class PlayerManager(private val context: Context) {

    /** Player proxy backed by PlaybackService. UI never owns the ExoPlayer. */
    var player: Player? = null
        private set

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var onControllerReady: (() -> Unit)? = null

    var onVideoEnded: (() -> Unit)? = null

    var onPlaybackError: ((String) -> Unit)? = null

    var onPlayNext: (() -> Unit)? = null

    var onPlayPrevious: (() -> Unit)? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _bufferedPosition = MutableStateFlow(0L)
    val bufferedPosition: StateFlow<Long> = _bufferedPosition.asStateFlow()

    private val _isPortraitVideo = MutableStateFlow<Boolean?>(null)
    val isPortraitVideo: StateFlow<Boolean?> = _isPortraitVideo.asStateFlow()

    private val _videoFps = MutableStateFlow(0f)
    val videoFps: StateFlow<Float> = _videoFps.asStateFlow()

    private val _videoDecoderName = MutableStateFlow<String?>(null)
    val videoDecoderName: StateFlow<String?> = _videoDecoderName.asStateFlow()

    private val _playerError = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError.asStateFlow()

    // --- Audio Tracks ---
    private val _audioTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val audioTracks: StateFlow<List<TrackInfo>> = _audioTracks.asStateFlow()

    private val _selectedAudioIndex = MutableStateFlow(-1)
    val selectedAudioIndex: StateFlow<Int> = _selectedAudioIndex.asStateFlow()

    // --- Subtitle Tracks ---
    private val _subtitleTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val subtitleTracks: StateFlow<List<TrackInfo>> = _subtitleTracks.asStateFlow()

    private val _selectedSubtitleIndex = MutableStateFlow(-1)
    val selectedSubtitleIndex: StateFlow<Int> = _selectedSubtitleIndex.asStateFlow()

    private val _isAudioBoostEnabled = MutableStateFlow(false)
    val isAudioBoostEnabled: StateFlow<Boolean> = _isAudioBoostEnabled.asStateFlow()

    private var loudnessEnhancer: LoudnessEnhancer? = null

    fun initializePlayer(
        decoderMode: DecoderMode = DecoderMode.HW_PLUS,
        defaultAudioLang: String = "",
        defaultSubtitleLang: String = ""
    ) {
        if (player != null) {
            onControllerReady?.invoke()
            return
        }

        val future = MediaController.Builder(
            context.applicationContext,
            SessionToken(
                context.applicationContext,
                ComponentName(context.applicationContext, PlaybackService::class.java)
            )
        ).buildAsync()
        controllerFuture = future
        future.addListener({
            val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
            player = controller
            controller.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        _duration.value = controller.duration.coerceAtLeast(0L)
                    } else if (playbackState == Player.STATE_ENDED) {
                        onVideoEnded?.invoke()
                    }
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        _isPortraitVideo.value = videoSize.height > videoSize.width
                    }
                }

                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    updateTrackState(tracks)
                }

                override fun onPlayerError(error: PlaybackException) {
                    AppLogger.log("MediaController playback error: ${error.message} - $error")
                    _playerError.value = error.message ?: "Playback error"
                    onPlaybackError?.invoke(error.message ?: "Playback error")
                }
            })
            controller.trackSelectionParameters = controller.trackSelectionParameters
                .buildUpon()
                .setPreferredAudioLanguage(if (defaultAudioLang.isEmpty()) null else defaultAudioLang)
                .setPreferredTextLanguage(if (defaultSubtitleLang.isEmpty()) null else defaultSubtitleLang)
                .build()
            onControllerReady?.invoke()
        }, ContextCompat.getMainExecutor(context.applicationContext))
    }

    private fun updateTrackState(tracks: androidx.media3.common.Tracks) {
        val newAudioTracks = mutableListOf<TrackInfo>()
        val newSubtitleTracks = mutableListOf<TrackInfo>()
        var currentAudioIdx = -1
        var currentSubIdx = -1
        val audioGroupCount = tracks.groups.count { it.type == C.TRACK_TYPE_AUDIO }
        val subtitleGroupCount = tracks.groups.count { it.type == C.TRACK_TYPE_TEXT }
        for (group in tracks.groups) {
            val format = group.getTrackFormat(0)
            when (group.type) {
                C.TRACK_TYPE_AUDIO -> {
                    val index = newAudioTracks.size
                    val language = format.language?.let { java.util.Locale.forLanguageTag(it).displayLanguage }
                    val label = if (audioGroupCount == 1) language ?: "Default"
                    else "Track ${index + 1}" + (if (language != null) " - $language" else "")
                    newAudioTracks.add(TrackInfo(index, label, format.language))
                    if (group.isSelected) currentAudioIdx = index
                }
                C.TRACK_TYPE_TEXT -> {
                    val index = newSubtitleTracks.size
                    val language = format.language?.let { java.util.Locale.forLanguageTag(it).displayLanguage }
                    val label = if (subtitleGroupCount == 1) language ?: "Default"
                    else "Track ${index + 1}" + (if (language != null) " - $language" else "")
                    newSubtitleTracks.add(TrackInfo(index, label, format.language))
                    if (group.isSelected) currentSubIdx = index
                }
            }
        }
        _audioTracks.value = newAudioTracks
        _selectedAudioIndex.value = currentAudioIdx
        _subtitleTracks.value = newSubtitleTracks
        _selectedSubtitleIndex.value = currentSubIdx
    }

    fun updatePreferredLanguages(audioLang: String, subLang: String) {
        val player = player ?: return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setPreferredAudioLanguage(if (audioLang.isEmpty()) null else audioLang)
            .setPreferredTextLanguage(if (subLang.isEmpty()) null else subLang)
            .build()
    }

    private fun applyLoudnessEnhancer(audioSessionId: Int) {
        if (audioSessionId == androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) return
        try {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                // 1500 mB = 15 dB boost (roughly 2x to 3x perceived loudness)
                setTargetGain(1500)
                enabled = true
            }
        } catch (e: Exception) {
            AppLogger.log("Failed to initialize LoudnessEnhancer: ${e.message}")
        }
    }

    fun toggleAudioBoost(enabled: Boolean) {
        _isAudioBoostEnabled.value = enabled
        val player = player ?: return

        if (enabled) {
            player.volume = 2.0f
        } else {
            player.volume = 1.0f
            try {
                loudnessEnhancer?.enabled = false
                loudnessEnhancer?.release()
            } catch (e: Exception) {}
            loudnessEnhancer = null
        }
    }

    fun playVideo(video: Video) {
        val player = player ?: return
        _playerError.value = null
        val mediaItem = MediaItem.fromUri(video.uri)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    fun clearError() {
        _playerError.value = null
    }

    fun setPlaybackSpeed(speed: Float) {
        val player = player ?: return
        player.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
    }

    fun playPause() {
        val player = player ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun pause() {
        player?.pause()
    }

    fun resume() {
        player?.play()
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    fun seekSmooth(positionMs: Long) {
        val player = player ?: return
        val duration = player.duration.coerceAtLeast(0L)
        player.seekTo(positionMs.coerceIn(0L, duration))
    }

    fun seekForward(ms: Long = 10000L) {
        val player = player ?: return
        val currentPos = player.currentPosition
        val newPos = (currentPos + ms).coerceAtMost(player.duration.coerceAtLeast(0L))
        player.seekTo(newPos)
    }

    fun seekBackward(ms: Long = 10000L) {
        val player = player ?: return
        val currentPos = player.currentPosition
        val newPos = (currentPos - ms).coerceAtLeast(0L)
        player.seekTo(newPos)
    }

    fun updateProgress() {
        val player = player ?: return
        _currentPosition.value = player.currentPosition.coerceAtLeast(0L)
        _bufferedPosition.value = player.bufferedPosition.coerceAtLeast(0L)
        _duration.value = player.duration.coerceAtLeast(0L)
        
    }

    fun releasePlayerAndKeepState(): Pair<Long, Boolean> {
        val pos = player?.currentPosition ?: 0L
        val playing = player?.isPlaying ?: false
        releasePlayer()
        return Pair(pos, playing)
    }

    fun releasePlayer() {
        controllerFuture?.let { runCatching { MediaController.releaseFuture(it) } }
        controllerFuture = null
        player = null
        try {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        } catch (e: Exception) {}
        loudnessEnhancer = null
    }

    @UnstableApi
    fun setScrubbingMode(enabled: Boolean) {
        // Scrubbing mode is an ExoPlayer implementation detail. The service owns it now.
    }

    // --- Audio & Subtitle Selection ---

    fun selectAudioTrack(index: Int) {
        val player = player ?: return
        var audioGroupIndex = -1
        var matchCount = 0

        val tracks = player.currentTracks
        for (i in tracks.groups.indices) {
            val group = tracks.groups[i]
            if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                if (matchCount == index) {
                    audioGroupIndex = i
                    break
                }
                matchCount++
            }
        }

        if (audioGroupIndex != -1) {
            val trackGroup = tracks.groups[audioGroupIndex].mediaTrackGroup
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(TrackSelectionOverride(trackGroup, 0))
                .build()
        }
    }

    fun selectSubtitleTrack(index: Int) {
        val player = player ?: return

        if (index == -1) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                .build()
            return
        }

        var textGroupIndex = -1
        var matchCount = 0

        val tracks = player.currentTracks
        for (i in tracks.groups.indices) {
            val group = tracks.groups[i]
            if (group.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                if (matchCount == index) {
                    textGroupIndex = i
                    break
                }
                matchCount++
            }
        }

        if (textGroupIndex != -1) {
            val trackGroup = tracks.groups[textGroupIndex].mediaTrackGroup
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(trackGroup, 0))
                .build()
        }
    }

    fun loadExternalSubtitle(uri: Uri, mimeType: String, encodingLabel: String = "UTF-8") {
        val player = player ?: return
        val currentMediaItem = player.currentMediaItem ?: return

        val uriPath = uri.path ?: uri.toString()
        val ext = uriPath.substringAfterLast('.', "").lowercase()
        val resolvedMimeType = when {
            ext == "ass" || ext == "ssa" -> MimeTypes.TEXT_SSA
            mimeType.isNotEmpty() -> mimeType
            else -> MimeTypes.APPLICATION_SUBRIP
        }

        val subtitleConfig = SubtitleConfiguration.Builder(uri)
            .setMimeType(resolvedMimeType)
            .setLanguage("en")
            .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
            .setLabel(encodingLabel)
            .build()

        val newMediaItem = currentMediaItem.buildUpon()
            .setSubtitleConfigurations(listOf(subtitleConfig))
            .build()

        val currentPos = player.currentPosition
        val isPlaying = player.isPlaying

        player.setMediaItem(newMediaItem)
        player.prepare()
        player.seekTo(currentPos)
        player.playWhenReady = isPlaying

        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
            .build()
    }
}