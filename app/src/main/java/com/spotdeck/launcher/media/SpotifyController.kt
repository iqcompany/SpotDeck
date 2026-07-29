package com.spotdeck.launcher.media

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject

class SpotifyController(private val context: Context) {

    companion object {
        private const val TAG = "SpotDeckMedia"
        private const val SPOTIFY_PACKAGE = "com.spotify.music"
    }

    private val mediaSessionManager: MediaSessionManager? =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val listenerComponent = ComponentName(context, SpotDeckNotificationListener::class.java)

    private var activeController: MediaController? = null
    private var mediaCallback: MediaController.Callback? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Debounce: only notify on actual state changes, not position updates
    private var lastPlayingState: Boolean? = null
    private var lastTitle: String? = null

    // Listeners for state changes
    var onPlaybackStateChanged: ((String) -> Unit)? = null
    var onMetadataChanged: ((String) -> Unit)? = null

    private fun findSpotifyController(): MediaController? {
        return try {
            val controllers = mediaSessionManager?.getActiveSessions(listenerComponent)
            val controller = controllers?.firstOrNull { it.packageName == SPOTIFY_PACKAGE }
            if (controller != null) {
                Log.i(TAG, "Spotify MediaController found")
            } else {
                Log.w(TAG, "Spotify MediaSession not found (active sessions: ${controllers?.size ?: 0})")
            }
            controller
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification listener permission not granted", e)
            null
        }
    }

    fun startMonitoring() {
        val controller = findSpotifyController() ?: return
        stopMonitoring()

        activeController = controller
        mediaCallback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                val nowPlaying = state?.state == PlaybackState.STATE_PLAYING
                if (nowPlaying != lastPlayingState) {
                    lastPlayingState = nowPlaying
                    val json = buildPlaybackStatusJson(state)
                    this@SpotifyController.onPlaybackStateChanged?.invoke(json)
                    Log.i(TAG, "Playback state changed: ${if (nowPlaying) "PLAYING" else "PAUSED"}")
                }
            }

            override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
                val title = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
                if (title != lastTitle) {
                    lastTitle = title
                    val json = buildMetadataJson(metadata)
                    this@SpotifyController.onMetadataChanged?.invoke(json)
                    Log.i(TAG, "Track changed: $title")
                }
            }
        }
        controller.registerCallback(mediaCallback!!, mainHandler)
        Log.i(TAG, "MediaSession monitoring started")

        // Send initial state
        lastPlayingState = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        lastTitle = controller.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
        onPlaybackStateChanged?.invoke(buildPlaybackStatusJson(controller.playbackState))
        onMetadataChanged?.invoke(buildMetadataJson(controller.metadata))
    }

    fun stopMonitoring() {
        mediaCallback?.let { cb ->
            activeController?.unregisterCallback(cb)
        }
        activeController = null
        mediaCallback = null
    }

    fun play() {
        val controller = findSpotifyController() ?: return
        controller.transportControls.play()
        Log.i(TAG, "PLAY sent")
    }

    fun pause() {
        val controller = findSpotifyController() ?: return
        controller.transportControls.pause()
        Log.i(TAG, "PAUSE sent")
    }

    fun playPause() {
        val controller = findSpotifyController() ?: return
        val state = controller.playbackState
        if (state != null && state.state == PlaybackState.STATE_PLAYING) {
            controller.transportControls.pause()
            Log.i(TAG, "PAUSE sent (was playing)")
        } else {
            controller.transportControls.play()
            Log.i(TAG, "PLAY sent (was paused)")
        }
    }

    fun next() {
        val controller = findSpotifyController() ?: return
        controller.transportControls.skipToNext()
        Log.i(TAG, "NEXT sent")
    }

    fun previous() {
        val controller = findSpotifyController() ?: return
        controller.transportControls.skipToPrevious()
        Log.i(TAG, "PREVIOUS sent")
    }

    fun volumeUp() {
        audioManager?.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
        Log.i(TAG, "VOLUME_UP sent")
    }

    fun volumeDown() {
        audioManager?.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        Log.i(TAG, "VOLUME_DOWN sent")
    }

    fun mute() {
        audioManager?.adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
        Log.i(TAG, "MUTE toggled")
    }

    fun hasNotificationAccess(): Boolean {
        val enabledListeners = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(listenerComponent.flattenToString())
    }

    // ── JSON payload builders ──

    fun buildPlaybackStatusJson(state: PlaybackState? = null): String {
        val playbackState = state ?: findSpotifyController()?.playbackState
        val json = JSONObject()
        json.put("isPlaying", playbackState?.state == PlaybackState.STATE_PLAYING)
        json.put("position", playbackState?.position ?: 0)
        json.put("playbackSpeed", playbackState?.playbackSpeed ?: 0f)
        json.put("timestamp", System.currentTimeMillis())
        return json.toString()
    }

    fun buildMetadataJson(metadata: android.media.MediaMetadata? = null): String {
        val md = metadata ?: findSpotifyController()?.metadata
        val json = JSONObject()
        json.put("title", md?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "")
        json.put("artist", md?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: "")
        json.put("album", md?.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM) ?: "")
        json.put("duration", md?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: 0)
        return json.toString()
    }

    fun buildDeviceStatusJson(): String {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val currentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1

        val json = JSONObject()
        json.put("battery", batteryLevel)
        json.put("volume", currentVolume)
        json.put("maxVolume", maxVolume)
        json.put("timestamp", System.currentTimeMillis())
        return json.toString()
    }
}
