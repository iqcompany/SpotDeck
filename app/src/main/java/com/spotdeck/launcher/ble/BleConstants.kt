package com.spotdeck.launcher.ble

import java.util.UUID

object BleConstants {

    // Service UUID
    val SERVICE_UUID: UUID = UUID.fromString("8f5a0000-6c4b-4c7e-9f26-7cb53a4e0000")

    // Characteristic UUIDs
    val COMMAND_CHAR_UUID: UUID = UUID.fromString("8f5a0001-6c4b-4c7e-9f26-7cb53a4e0000")
    val PLAYBACK_STATUS_CHAR_UUID: UUID = UUID.fromString("8f5a0002-6c4b-4c7e-9f26-7cb53a4e0000")
    val METADATA_CHAR_UUID: UUID = UUID.fromString("8f5a0003-6c4b-4c7e-9f26-7cb53a4e0000")
    val DEVICE_STATUS_CHAR_UUID: UUID = UUID.fromString("8f5a0004-6c4b-4c7e-9f26-7cb53a4e0000")
    val PROTOCOL_INFO_CHAR_UUID: UUID = UUID.fromString("8f5a0005-6c4b-4c7e-9f26-7cb53a4e0000")

    // Client Characteristic Configuration Descriptor (for Notify)
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Command values
    const val CMD_PLAY: Byte = 0x01
    const val CMD_PAUSE: Byte = 0x02
    const val CMD_PLAY_PAUSE: Byte = 0x03
    const val CMD_NEXT: Byte = 0x04
    const val CMD_PREVIOUS: Byte = 0x05
    const val CMD_VOLUME_UP: Byte = 0x06
    const val CMD_VOLUME_DOWN: Byte = 0x07
    const val CMD_MUTE: Byte = 0x08
    const val CMD_SHUFFLE_TOGGLE: Byte = 0x09
    const val CMD_REPEAT_TOGGLE: Byte = 0x0A
    const val CMD_REQUEST_STATUS: Byte = 0x0B
    const val CMD_REQUEST_METADATA: Byte = 0x0C
    const val CMD_REQUEST_DEVICE_STATUS: Byte = 0x0D

    fun commandName(cmd: Byte): String = when (cmd) {
        CMD_PLAY -> "PLAY"
        CMD_PAUSE -> "PAUSE"
        CMD_PLAY_PAUSE -> "PLAY_PAUSE"
        CMD_NEXT -> "NEXT"
        CMD_PREVIOUS -> "PREVIOUS"
        CMD_VOLUME_UP -> "VOLUME_UP"
        CMD_VOLUME_DOWN -> "VOLUME_DOWN"
        CMD_MUTE -> "MUTE"
        CMD_SHUFFLE_TOGGLE -> "SHUFFLE_TOGGLE"
        CMD_REPEAT_TOGGLE -> "REPEAT_TOGGLE"
        CMD_REQUEST_STATUS -> "REQUEST_STATUS"
        CMD_REQUEST_METADATA -> "REQUEST_METADATA"
        CMD_REQUEST_DEVICE_STATUS -> "REQUEST_DEVICE_STATUS"
        else -> "UNKNOWN(0x${String.format("%02X", cmd.toInt() and 0xFF)})"
    }
}
