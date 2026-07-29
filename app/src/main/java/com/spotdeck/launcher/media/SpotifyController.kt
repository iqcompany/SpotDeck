package com.spotdeck.launcher.media

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.util.Log

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

    private fun findSpotifyController(): MediaController? {
        return try {
            val controllers = mediaSessionManager?.getActiveSessions(listenerComponent)
            val controller = controllers?.firstOrNull { it.packageName == SPOTIFY_PACKAGE }
            if (controller != null) {
                Log.i(TAG, "Spotify MediaController found")
            } else {
                // Fallback: use any active media session
                val fallback = controllers?.firstOrNull()
                if (fallback != null) {
                    Log.i(TAG, "Spotify not found, using active session: ${fallback.packageName}")
                } else {
                    Log.w(TAG, "No active media sessions found")
                }
                fallback
            }
            controller ?: controllers?.firstOrNull()
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification listener permission not granted", e)
            null
        }
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
        if (state != null && state.state == android.media.session.PlaybackState.STATE_PLAYING) {
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
}
