package com.example.t4.ble

import android.bluetooth.BluetoothGattService
import java.util.*

data class BleGattService(
    val service: BluetoothGattService,
    val characteristics: MutableList<BleGattCharacteristic> = mutableListOf()
) {
    val uuid: UUID = service.uuid
    private val type: Int = service.type
    
    // 获取服务类型的字符串表示
    val typeString: String
        get() = when (type) {
            BluetoothGattService.SERVICE_TYPE_PRIMARY -> "主要服务"
            BluetoothGattService.SERVICE_TYPE_SECONDARY -> "次要服务"
            else -> "未知类型"
        }
    
    // 获取服务名称（基于已知的 UUID）
    val name: String
        get() = BleGattAttributes.lookup(uuid.toString(), "未知服务")
}