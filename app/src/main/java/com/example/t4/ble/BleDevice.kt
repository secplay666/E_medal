package com.example.t4.ble

data class BleDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val scanRecord: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean = other is BleDevice && address == other.address
    override fun hashCode(): Int = address.hashCode()
}