package com.example.t4.ble

import java.util.*

/**
 * 常见 BLE GATT 属性的查找表
 */
object BleGattAttributes {
    private val attributes: HashMap<String, String> = HashMap()
    
    // 常见服务 UUID
    private const val GENERIC_ACCESS_SERVICE = "00001800-0000-1000-8000-00805f9b34fb"
    private const val GENERIC_ATTRIBUTE_SERVICE = "00001801-0000-1000-8000-00805f9b34fb"
    private const val DEVICE_INFORMATION_SERVICE = "0000180a-0000-1000-8000-00805f9b34fb"
    private const val HEART_RATE_SERVICE = "0000180d-0000-1000-8000-00805f9b34fb"
    private const val BATTERY_SERVICE = "0000180f-0000-1000-8000-00805f9b34fb"
    
    // 常见特征 UUID
    private const val DEVICE_NAME = "00002a00-0000-1000-8000-00805f9b34fb"
    private const val APPEARANCE = "00002a01-0000-1000-8000-00805f9b34fb"
    private const val MANUFACTURER_NAME = "00002a29-0000-1000-8000-00805f9b34fb"
    private const val MODEL_NUMBER = "00002a24-0000-1000-8000-00805f9b34fb"
    private const val SERIAL_NUMBER = "00002a25-0000-1000-8000-00805f9b34fb"
    private const val FIRMWARE_REVISION = "00002a26-0000-1000-8000-00805f9b34fb"
    private const val HARDWARE_REVISION = "00002a27-0000-1000-8000-00805f9b34fb"
    private const val SOFTWARE_REVISION = "00002a28-0000-1000-8000-00805f9b34fb"
    private const val HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb"
    private const val BATTERY_LEVEL = "00002a19-0000-1000-8000-00805f9b34fb"
    
    init {
        // 添加常见服务
        attributes[GENERIC_ACCESS_SERVICE] = "通用访问服务"
        attributes[GENERIC_ATTRIBUTE_SERVICE] = "通用属性服务"
        attributes[DEVICE_INFORMATION_SERVICE] = "设备信息服务"
        attributes[HEART_RATE_SERVICE] = "心率服务"
        attributes[BATTERY_SERVICE] = "电池服务"
        
        // 添加常见特征
        attributes[DEVICE_NAME] = "设备名称"
        attributes[APPEARANCE] = "外观"
        attributes[MANUFACTURER_NAME] = "制造商名称"
        attributes[MODEL_NUMBER] = "型号"
        attributes[SERIAL_NUMBER] = "序列号"
        attributes[FIRMWARE_REVISION] = "固件版本"
        attributes[HARDWARE_REVISION] = "硬件版本"
        attributes[SOFTWARE_REVISION] = "软件版本"
        attributes[HEART_RATE_MEASUREMENT] = "心率测量"
        attributes[BATTERY_LEVEL] = "电池电量"
    }
    
    fun lookup(uuid: String, defaultName: String): String {
        val name = attributes[uuid]
        return name ?: defaultName
    }
}