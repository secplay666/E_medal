package com.example.t4.ble

import java.util.Arrays

data class BleDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val scanRecord: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BleDevice
        return address == other.address
    }

    override fun hashCode(): Int {
        return address.hashCode()
    }
}