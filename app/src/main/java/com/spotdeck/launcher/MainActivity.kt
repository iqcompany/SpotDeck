package com.spotdeck.launcher

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.spotdeck.launcher.ble.BleConstants
import com.spotdeck.launcher.ble.SpotDeckGattServer
import com.spotdeck.launcher.media.SpotifyController
import com.spotdeck.launcher.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SpotDeck"
        private const val REQUEST_BLE_PERMISSIONS = 100
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var appsAdapter: AppsAdapter
    private val autoLaunchHandler = Handler(Looper.getMainLooper())
    private var autoLaunchRunnable: Runnable? = null
    private var isAppListVisible = false

    // BLE & Media
    private var gattServer: SpotDeckGattServer? = null
    private lateinit var spotifyController: SpotifyController

    // Status display
    private val timeHandler = Handler(Looper.getMainLooper())
    private var timeUpdateRunnable: Runnable? = null
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateBatteryLevel()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        spotifyController = SpotifyController(this)
        setupUI()
        setupGestureDetector()
        setupBackPressedHandler()
        setupStatusDisplay()
        loadInstalledApps()
        checkNotificationAccess()
        startBleServer()
    }

    private fun setupUI() {
        binding.spotifyIcon.setOnClickListener {
            launchSpotify()
        }

        // Setup RecyclerView for app list
        appsAdapter = AppsAdapter { appInfo ->
            launchApp(appInfo.packageName)
        }
        binding.appListRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = appsAdapter
        }

        // Initially hide app list
        binding.appListContainer.visibility = View.GONE
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 != null) {
                    val diffY = e2.y - e1.y
                    val diffX = e2.x - e1.x

                    // Check if it's a vertical swipe (more vertical than horizontal)
                    if (abs(diffY) > abs(diffX)) {
                        if (abs(diffY) > 100 && abs(velocityY) > 100) {
                            if (diffY < 0 && !isAppListVisible) {
                                // Swipe up - show app list
                                showAppList()
                            } else if (diffY > 0 && isAppListVisible) {
                                // Swipe down - hide app list
                                hideAppList()
                            }
                        }
                    }
                }
                return true
            }
        })
    }

    private fun setupBackPressedHandler() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isAppListVisible) {
                    hideAppList()
                }
                // Don't exit the launcher - just ignore back press when on home screen
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    private fun showAppList() {
        isAppListVisible = true
        binding.homeContainer.visibility = View.GONE
        binding.appListContainer.visibility = View.VISIBLE
        cancelAutoLaunch()
    }

    private fun hideAppList() {
        isAppListVisible = false
        binding.appListContainer.visibility = View.GONE
        binding.homeContainer.visibility = View.VISIBLE
        scheduleAutoLaunch()
    }

    override fun onResume() {
        super.onResume()
        if (!isAppListVisible) {
            scheduleAutoLaunch()
        }
    }

    override fun onPause() {
        super.onPause()
        cancelAutoLaunch()
    }

    private fun scheduleAutoLaunch() {
        cancelAutoLaunch() // Cancel any existing scheduled launch

        autoLaunchRunnable = Runnable {
            if (!isAppListVisible) {
                launchSpotify()
            }
        }

        // Auto-launch Spotify after 3 seconds
        autoLaunchHandler.postDelayed(autoLaunchRunnable!!, 3000)
    }

    private fun cancelAutoLaunch() {
        autoLaunchRunnable?.let {
            autoLaunchHandler.removeCallbacks(it)
            autoLaunchRunnable = null
        }
    }

    private fun launchSpotify() {
        val spotifyPackage = "com.spotify.music"
        try {
            val intent = packageManager.getLaunchIntentForPackage(spotifyPackage)
            if (intent != null) {
                startActivity(intent)
            } else {
                // Spotify not installed, try to open Play Store
                launchPlayStore(spotifyPackage)
            }
        } catch (e: Exception) {
            launchPlayStore(spotifyPackage)
        }
    }

    private fun launchApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivity(intent)
            }
        } catch (e: Exception) {
            // Handle error
        }
    }

    private fun launchPlayStore(packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("market://details?id=$packageName")
                setPackage("com.android.vending")
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to browser
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            }
            startActivity(intent)
        }
    }

    private fun loadInstalledApps() {
        lifecycleScope.launch {
            val apps = getInstalledApps()
            appsAdapter.updateApps(apps)
        }
    }

    private fun getInstalledApps(): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        val packageManager = packageManager

        val installedPackages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        for (appInfo in installedPackages) {
            // Only include apps that have a launcher intent (excludes system components)
            val launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
            if (launchIntent != null && appInfo.packageName != packageName) {
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                apps.add(AppInfo(appName, appInfo.packageName))
            }
        }

        return apps.sortedBy { it.name }
    }

    private fun setupStatusDisplay() {
        // Register battery change receiver
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)

        // Initialize status display
        updateTime()
        updateBatteryLevel()

        // Start time update loop
        startTimeUpdates()
    }

    private fun startTimeUpdates() {
        timeUpdateRunnable = object : Runnable {
            override fun run() {
                updateTime()
                timeHandler.postDelayed(this, 60000) // Update every minute
            }
        }
        timeHandler.post(timeUpdateRunnable!!)
    }

    private fun stopTimeUpdates() {
        timeUpdateRunnable?.let {
            timeHandler.removeCallbacks(it)
            timeUpdateRunnable = null
        }
    }

    private fun updateTime() {
        val currentTime = timeFormat.format(Date())
        binding.currentTime.text = currentTime
    }

    private fun updateBatteryLevel() {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        binding.batteryLevel.text = "🔋 ${batteryLevel}%"
    }

    // ── BLE ──

    private fun startBleServer() {
        Log.i(TAG, "startBleServer called")
        if (!hasBlePermissions()) {
            Log.i(TAG, "BLE permissions not granted, requesting...")
            cancelAutoLaunch()
            requestBlePermissions()
            return
        }
        initBleServer()
    }

    private fun initBleServer() {
        if (gattServer?.isRunning == true) return

        Log.i(TAG, "Initializing BLE GATT Server...")
        gattServer = SpotDeckGattServer(this) { cmd ->
            Log.i(TAG, "Command received: ${BleConstants.commandName(cmd)}")
            handleCommand(cmd)
        }

        if (gattServer!!.start()) {
            Log.i(TAG, "BLE GATT Server started successfully")
            startMediaMonitoring()
        } else {
            Log.e(TAG, "Failed to start BLE GATT Server")
        }
    }

    private fun startMediaMonitoring() {
        spotifyController.onPlaybackStateChanged = { json ->
            Log.i(TAG, "Playback state changed, notifying client")
            gattServer?.notifyCharacteristic(BleConstants.PLAYBACK_STATUS_CHAR_UUID, json.toByteArray())
        }
        spotifyController.onMetadataChanged = { json ->
            Log.i(TAG, "Metadata changed, notifying client")
            gattServer?.notifyCharacteristic(BleConstants.METADATA_CHAR_UUID, json.toByteArray())
        }
        spotifyController.startMonitoring()
    }

    private fun handleCommand(cmd: Byte) {
        when (cmd) {
            BleConstants.CMD_PLAY -> spotifyController.play()
            BleConstants.CMD_PAUSE -> spotifyController.pause()
            BleConstants.CMD_PLAY_PAUSE -> spotifyController.playPause()
            BleConstants.CMD_NEXT -> spotifyController.next()
            BleConstants.CMD_PREVIOUS -> spotifyController.previous()
            BleConstants.CMD_VOLUME_UP -> spotifyController.volumeUp()
            BleConstants.CMD_VOLUME_DOWN -> spotifyController.volumeDown()
            BleConstants.CMD_MUTE -> spotifyController.mute()
            BleConstants.CMD_REQUEST_STATUS -> {
                val json = spotifyController.buildPlaybackStatusJson()
                gattServer?.notifyCharacteristic(BleConstants.PLAYBACK_STATUS_CHAR_UUID, json.toByteArray())
            }
            BleConstants.CMD_REQUEST_METADATA -> {
                val json = spotifyController.buildMetadataJson()
                gattServer?.notifyCharacteristic(BleConstants.METADATA_CHAR_UUID, json.toByteArray())
            }
            BleConstants.CMD_REQUEST_DEVICE_STATUS -> {
                val json = spotifyController.buildDeviceStatusJson()
                gattServer?.notifyCharacteristic(BleConstants.DEVICE_STATUS_CHAR_UUID, json.toByteArray())
            }
            else -> Log.w(TAG, "Unhandled command: ${BleConstants.commandName(cmd)}")
        }
    }

    private fun checkNotificationAccess() {
        if (!spotifyController.hasNotificationAccess()) {
            Log.w(TAG, "Notification listener access not granted, opening settings...")
            try {
                val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open notification listener settings", e)
            }
        } else {
            Log.i(TAG, "Notification listener access granted")
        }
    }

    private fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT
                ),
                REQUEST_BLE_PERMISSIONS
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initBleServer()
            } else {
                Log.w(TAG, "BLE permissions denied")
                Toast.makeText(this, "BLE permissions required for remote control", Toast.LENGTH_LONG).show()
            }
            if (!isAppListVisible) {
                scheduleAutoLaunch()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        spotifyController.stopMonitoring()
        gattServer?.stop()
        gattServer = null
        stopTimeUpdates()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }
}