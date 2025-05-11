package com.example.t4.ble

import android.bluetooth.BluetoothGattCharacteristic
import java.util.*

data class BleGattCharacteristic(
    val characteristic: BluetoothGattCharacteristic,
    var value: ByteArray? = null
) {
    val uuid: UUID = characteristic.uuid
    private val properties: Int = characteristic.properties
    
    // 获取特征名称（基于已知的 UUID）
    val name: String
        get() = BleGattAttributes.lookup(uuid.toString(), "未知特征")
    
    // 检查特征是否可读
    val isReadable: Boolean
        get() = properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
    
    // 检查特征是否可写
    val isWritable: Boolean
        get() = (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) ||
                (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
    
    // 检查特征是否支持通知
    val isNotifiable: Boolean
        get() = (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) ||
                (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)
    
    // 获取特征属性的字符串表示
    val propertiesString: String
        get() {
            val props = mutableListOf<String>()
            
            if (isReadable) props.add("读")
            if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) props.add("写")
            if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) props.add("无响应写")
            if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) props.add("通知")
            if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) props.add("指示")
            if (properties and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) props.add("签名写")
            if (properties and BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS != 0) props.add("扩展属性")
            if (properties and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) props.add("广播")
            
            return if (props.isEmpty()) "无" else props.joinToString(", ")
        }
    
    // 将特征值转换为可读的字符串
    fun getValueAsString(): String {
        val bytes = value ?: return "无数据"
        
        // 尝试将数据解析为 UTF-8 字符串
        val utf8String = try {
            String(bytes)
        } catch (e: Exception) {
            null
        }
        
        // 如果字符串包含可打印字符，则显示字符串
        if (utf8String != null && utf8String.all { it.code in 32..126 }) {
            return "\"$utf8String\" (${bytesToHex(bytes)})"
        }
        
        // 否则只显示十六进制
        return bytesToHex(bytes)
    }
    
    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789ABCPDF".toCharArray()
        val result = StringBuilder(bytes.size * 3)
        
        for (b in bytes) {
            result.append(hexChars[b.toInt() shr 4 and 0xF])
            result.append(hexChars[b.toInt() and 0xF])
            result.append(' ')
        }
        
        return result.toString().trim()
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as BleGattCharacteristic
        
        return uuid == other.uuid
    }
    
    override fun hashCode(): Int {
        return uuid.hashCode()
    }
}