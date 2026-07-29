package com.spotdeck.launcher.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log

@SuppressLint("MissingPermission")
class SpotDeckGattServer(
    private val context: Context,
    private val onCommandReceived: (Byte) -> Unit
) {
    companion object {
        private const val TAG = "SpotDeckBLE"
    }

    private var bluetoothManager: BluetoothManager? = null
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var isAdvertising = false
    private var connectedDevice: BluetoothDevice? = null

    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    connectedDevice = device
                    Log.i(TAG, "Device connected: ${device.address}")
                }
                BluetoothGatt.STATE_DISCONNECTED -> {
                    connectedDevice = null
                    Log.i(TAG, "Device disconnected: ${device.address}")
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (characteristic.uuid == BleConstants.COMMAND_CHAR_UUID) {
                if (value != null && value.isNotEmpty()) {
                    val cmd = value[0]
                    Log.i(TAG, "Command received: ${BleConstants.commandName(cmd)} (0x${String.format("%02X", cmd.toInt() and 0xFF)}) from ${device.address}")
                    onCommandReceived(cmd)
                } else {
                    Log.w(TAG, "Empty command received from ${device.address}")
                }
            } else {
                Log.w(TAG, "Write to unknown characteristic: ${characteristic.uuid}")
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            Log.i(TAG, "Read request for ${characteristic.uuid} offset=$offset from ${device.address}")

            val fullValue = when (characteristic.uuid) {
                BleConstants.PROTOCOL_INFO_CHAR_UUID -> {
                    """{"protocolVersion":1,"minimumClientVersion":1,"deviceName":"SpotDeck","capabilities":["playback-control"]}""".toByteArray()
                }
                else -> ByteArray(0)
            }

            if (offset >= fullValue.size) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
            } else {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, fullValue.copyOfRange(offset, fullValue.size))
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            Log.i(TAG, "Descriptor write for ${descriptor.characteristic.uuid} from ${device.address}")
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            gattServer?.sendResponse(
                device, requestId, BluetoothGatt.GATT_SUCCESS, offset,
                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            )
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Service added: ${service.uuid}")
            } else {
                Log.e(TAG, "Failed to add service: $status")
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            isAdvertising = true
            Log.i(TAG, "Advertising started")
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            val reason = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
                ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
                else -> "UNKNOWN($errorCode)"
            }
            Log.e(TAG, "Advertising failed: $reason")
        }
    }

    fun start(): Boolean {
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "Bluetooth not available or not enabled")
            return false
        }

        if (!adapter.isMultipleAdvertisementSupported) {
            Log.e(TAG, "BLE Advertising not supported on this device")
            return false
        }

        // Open GATT server
        gattServer = bluetoothManager?.openGattServer(context, gattServerCallback)
        if (gattServer == null) {
            Log.e(TAG, "Failed to open GATT server")
            return false
        }
        Log.i(TAG, "GATT server opened")

        // Add service
        val service = createService()
        gattServer?.addService(service)

        // Start advertising
        advertiser = adapter.bluetoothLeAdvertiser
        startAdvertising()

        return true
    }

    fun stop() {
        stopAdvertising()

        gattServer?.close()
        gattServer = null
        connectedDevice = null

        Log.i(TAG, "GATT server stopped")
    }

    val isRunning: Boolean
        get() = gattServer != null

    private fun createService(): BluetoothGattService {
        val service = BluetoothGattService(
            BleConstants.SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        // Command Characteristic (Write)
        val commandChar = BluetoothGattCharacteristic(
            BleConstants.COMMAND_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(commandChar)

        // Protocol Information Characteristic (Read)
        val protocolInfoChar = BluetoothGattCharacteristic(
            BleConstants.PROTOCOL_INFO_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(protocolInfoChar)

        Log.i(TAG, "Service created with Command and Protocol Info characteristics")
        return service
    }

    private fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0) // Advertise indefinitely
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        // Advertise data: Service UUID only (device name exceeds 31-byte limit)
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
            .build()

        // Scan response: device name
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        advertiser?.startAdvertising(settings, data, scanResponse, advertiseCallback)
        Log.i(TAG, "Advertising start requested")
    }

    private fun stopAdvertising() {
        if (isAdvertising) {
            advertiser?.stopAdvertising(advertiseCallback)
            isAdvertising = false
            Log.i(TAG, "Advertising stopped")
        }
    }
}
