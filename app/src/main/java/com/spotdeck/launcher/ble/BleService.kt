package com.spotdeck.launcher.ble

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.spotdeck.launcher.MainActivity
import com.spotdeck.launcher.R
import com.spotdeck.launcher.media.SpotifyController

@SuppressLint("MissingPermission")
class BleService : Service() {

    companion object {
        private const val TAG = "SpotDeckBLE"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "spotdeck_ble_channel"
    }

    private var gattServer: SpotDeckGattServer? = null
    private var spotifyController: SpotifyController? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "BleService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "BleService onStartCommand")
        startForeground(NOTIFICATION_ID, createNotification("Waiting for connection..."))
        startGattServer()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        spotifyController?.stopMonitoring()
        gattServer?.stop()
        gattServer = null
        spotifyController = null
        Log.i(TAG, "BleService destroyed")
    }

    private fun startGattServer() {
        if (gattServer?.isRunning == true) return

        spotifyController = SpotifyController(this)

        gattServer = SpotDeckGattServer(this) { cmd, data ->
            Log.i(TAG, "Command received: ${BleConstants.commandName(cmd)}")
            handleCommand(cmd, data)
        }

        gattServer?.onConnectionStateChanged = { connected ->
            if (connected) {
                updateNotification("Remote connected")
                startMediaMonitoring()
            } else {
                updateNotification("Waiting for connection...")
                spotifyController?.stopMonitoring()
            }
        }

        if (gattServer!!.start()) {
            Log.i(TAG, "BLE GATT Server started in foreground service")
        } else {
            Log.e(TAG, "Failed to start BLE GATT Server")
            stopSelf()
        }
    }

    private fun startMediaMonitoring() {
        val controller = spotifyController ?: return
        controller.onPlaybackStateChanged = { json ->
            gattServer?.notifyCharacteristic(BleConstants.PLAYBACK_STATUS_CHAR_UUID, json.toByteArray())
        }
        controller.onMetadataChanged = { json ->
            gattServer?.notifyCharacteristic(BleConstants.METADATA_CHAR_UUID, json.toByteArray())
        }
        controller.startMonitoring()
    }

    private fun handleCommand(cmd: Byte, data: ByteArray?) {
        val controller = spotifyController ?: return
        when (cmd) {
            BleConstants.CMD_PLAY -> controller.play()
            BleConstants.CMD_PAUSE -> controller.pause()
            BleConstants.CMD_PLAY_PAUSE -> controller.playPause()
            BleConstants.CMD_NEXT -> controller.next()
            BleConstants.CMD_PREVIOUS -> controller.previous()
            BleConstants.CMD_VOLUME_UP -> controller.volumeUp()
            BleConstants.CMD_VOLUME_DOWN -> controller.volumeDown()
            BleConstants.CMD_MUTE -> controller.mute()
            BleConstants.CMD_SET_VOLUME -> {
                if (data != null && data.size >= 2) {
                    controller.setVolume(data[1].toInt() and 0xFF)
                }
            }
            BleConstants.CMD_REQUEST_STATUS -> {
                val json = controller.buildPlaybackStatusJson()
                gattServer?.notifyCharacteristic(BleConstants.PLAYBACK_STATUS_CHAR_UUID, json.toByteArray())
            }
            BleConstants.CMD_REQUEST_METADATA -> {
                val json = controller.buildMetadataJson()
                gattServer?.notifyCharacteristic(BleConstants.METADATA_CHAR_UUID, json.toByteArray())
            }
            BleConstants.CMD_REQUEST_DEVICE_STATUS -> {
                val json = controller.buildDeviceStatusJson()
                gattServer?.notifyCharacteristic(BleConstants.DEVICE_STATUS_CHAR_UUID, json.toByteArray())
            }
            else -> Log.w(TAG, "Unhandled command: ${BleConstants.commandName(cmd)}")
        }
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SpotDeck BLE Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "BLE GATT Server for remote control"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SpotDeck")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, createNotification(text))
    }
}
