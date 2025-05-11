package com.example.t4.ble

import android.bluetooth.le.ScanRecord
import android.os.ParcelUuid
import android.util.SparseArray
import java.util.*

data class BleDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val scanRecord: ByteArray
) {
    // 判断设备是否可连接
    val isConnectable: Boolean
        get() {
            if (scanRecord.isEmpty()) return false
            
            // 正确解析广播数据中的 Flags 字段
            return try {
                var index = 0
                while (index < scanRecord.size) {
                    val length = scanRecord[index].toInt() and 0xFF
                    if (length == 0) break
                    
                    if (index + 1 < scanRecord.size) {
                        val type = scanRecord[index + 1].toInt() and 0xFF
                        
                        // 查找 Flags 字段 (AD Type = 0x01)
                        if (type == 0x01 && index + 2 < scanRecord.size) {
                            val flags = scanRecord[index + 2].toInt() and 0xFF
                            // 检查 LE General Discoverable Mode 位 (bit 1)
                            // 或 LE Limited Discoverable Mode 位 (bit 0)
                            return (flags and 0x03) != 0
                        }
                    }
                    
                    index += (length + 1)
                }
                
                // 如果没有找到 Flags 字段，默认认为设备可连接
                // 很多设备可能不包含 Flags 字段，但仍然可以连接
                true
            } catch (e: Exception) {
                false
            }
        }

    // 解析广播数据
    fun parseAdvertisementData(): String {
        if (scanRecord.isEmpty()) return "无广播数据"

        val result = StringBuilder()

        // 添加原始数据的十六进制表示
        result.append("原始数据 (HEX): \n")
        for (i in scanRecord.indices) {
            val hex = String.format("%02X", scanRecord[i])
            result.append(hex)
            if ((i + 1) % 16 == 0) result.append("\n")
            else if ((i + 1) % 2 == 0) result.append(" ")
        }
        result.append("\n\n")

        // 尝试解析广播数据中的标准字段
        try {
            var index = 0
            while (index < scanRecord.size) {
                val length = scanRecord[index].toInt() and 0xFF
                if (length == 0) break

                if (index + 1 < scanRecord.size) {
                    // 解析不同类型的广播数据
                    when (val type = scanRecord[index + 1].toInt() and 0xFF) {
                        0x01 -> { // Flags
                            if (index + 2 < scanRecord.size) {
                                val flags = scanRecord[index + 2].toInt() and 0xFF
                                result.append("Flags: 0x${String.format("%02X", flags)}\n")
                                result.append(" - LE Limited Discoverable: ${(flags and 0x01) != 0}\n")
                                result.append(" - LE General Discoverable: ${(flags and 0x02) != 0}\n")
                                result.append(" - BR/EDR Not Supported: ${(flags and 0x04) != 0}\n")
                                result.append(" - LE and BR/EDR Controller: ${(flags and 0x08) != 0}\n")
                                result.append(" - LE and BR/EDR Host: ${(flags and 0x10) != 0}\n")
                            }
                        }
                        0x09 -> { // Complete Local Name
                            if (index + 2 < scanRecord.size) {
                                val nameBytes = ByteArray(length - 1)
                                System.arraycopy(scanRecord, index + 2, nameBytes, 0, length - 1)
                                result.append("完整设备名称: ${String(nameBytes)}\n")
                            }
                        }
                        0x08 -> { // Shortened Local Name
                            if (index + 2 < scanRecord.size) {
                                val nameBytes = ByteArray(length - 1)
                                System.arraycopy(scanRecord, index + 2, nameBytes, 0, length - 1)
                                result.append("简短设备名称: ${String(nameBytes)}\n")
                            }
                        }
                        0x0A -> { // TX Power Level
                            if (index + 2 < scanRecord.size) {
                                val txPower = scanRecord[index + 2].toInt()
                                result.append("发射功率: ${txPower}dBm\n")
                            }
                        }
                        0xFF -> { // Manufacturer Specific Data
                            if (index + 3 < scanRecord.size) {
                                val manufacturerId = ((scanRecord[index + 3].toInt() and 0xFF) shl 8) or
                                        (scanRecord[index + 2].toInt() and 0xFF)
                                result.append("厂商数据 (ID: 0x${String.format("%04X", manufacturerId)}): ")

                                for (i in 4 until length) {
                                    if (index + i < scanRecord.size) {
                                        result.append(String.format("%02X ", scanRecord[index + i]))
                                    }
                                }
                                result.append("\n")
                            }
                        }
                        else -> {
                            result.append("类型 0x${String.format("%02X", type)}: ")
                            for (i in 2 until length) {
                                if (index + i < scanRecord.size) {
                                    result.append(String.format("%02X ", scanRecord[index + i]))
                                }
                            }
                            result.append("\n")
                        }
                    }
                }

                index += (length + 1)
            }
        } catch (e: Exception) {
            result.append("解析广播数据时出错: ${e.message}\n")
        }

        return result.toString()
    }

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