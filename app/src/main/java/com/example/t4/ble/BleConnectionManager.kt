package com.example.t4.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import com.example.t4.ImageProcessor
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.LinkedList
import java.util.Queue

object BleConnectionManager {
    private const val TAG = "BleConnectionManager"

    @Volatile
    var bluetoothGatt: BluetoothGatt? = null

    @Volatile
    var targetWriteCharacteristic: BluetoothGattCharacteristic? = null

    fun setGatt(gatt: BluetoothGatt?) {
        bluetoothGatt = gatt
        Log.d(TAG, "setGatt: $gatt")
    }

    fun registerTargetWriteCharacteristic(char: BluetoothGattCharacteristic?) {
        targetWriteCharacteristic = char
        Log.d(TAG, "registerTargetWriteCharacteristic: ${char?.uuid}")
    }

    fun disconnect() {
        try {
            bluetoothGatt?.let {
                it.disconnect()
                it.close()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "disconnect error: ${e.message}")
        } finally {
            bluetoothGatt = null
            targetWriteCharacteristic = null
        }
    }

    // MTU 管理，默认 ATT MTU = 23
    @Volatile
    private var currentMtu: Int = 23

    fun setMtu(mtu: Int) {
        currentMtu = mtu
        Log.d(TAG, "setMtu: $mtu")
    }

    // 发送队列
    private val writeQueue: Queue<ByteArray> = LinkedList()
    @Volatile
    private var isWriting: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 将数据按 currentMtu-3 分片并加入队列，尾部附加 CRC16 (高字节在前)，并开始发送
     */
    fun sendDataWithFragments(payload: ByteArray) {
        val gatt = bluetoothGatt
        val char = targetWriteCharacteristic
        if (gatt == null || char == null) {
            Log.w(TAG, "sendDataWithFragments: no gatt or char")
            return
        }
        // New frame format: [MAGIC(2)][LEN(2)][PAYLOAD(len)][CRC(2)]
        val magic = byteArrayOf(0xAB.toByte(), 0xCD.toByte())


        // New simple frame: [MAGIC(2)][LEN(2)][PAYLOAD(len)][CRC(2)]
        val crc = crc16Ccitt(payload)
        val len = payload.size
        val lenHi = ((len shr 8) and 0xFF).toByte()
        val lenLo = (len and 0xFF).toByte()

        val frame = ByteArray(2 + 2 + len + 2)
        frame[0] = magic[0]
        frame[1] = magic[1]
        frame[2] = lenHi
        frame[3] = lenLo
        System.arraycopy(payload, 0, frame, 4, len)
        // CRC on payload (hi, lo)
        frame[4 + len] = ((crc shr 8) and 0xFF).toByte()
        frame[4 + len + 1] = (crc and 0xFF).toByte()

        try {
            val hex = frame.joinToString(" ") { String.format("%02X", it) }
            Log.d(TAG, "[MANAGER FRAGMENT] outgoing frame: $hex")
        } catch (e: Exception) { /* ignore */ }

        val chunkSize = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) (currentMtu - 3) else 20).coerceAtLeast(1)
        var offset = 0
        synchronized(writeQueue) {
            while (offset < frame.size) {
                val end = (offset + chunkSize).coerceAtMost(frame.size)
                val chunk = frame.copyOfRange(offset, end)
                writeQueue.add(chunk)
                offset = end
            }
        }

        // 触发发送
        mainHandler.post { sendNextFragment() }
    }

    private fun sendNextFragment() {
        if (isWriting) return
        val next: ByteArray? = synchronized(writeQueue) { if (writeQueue.isEmpty()) null else writeQueue.poll() }
        if (next == null) return

        val gatt = bluetoothGatt
        val char = targetWriteCharacteristic
        if (gatt == null || char == null) {
            Log.w(TAG, "sendNextFragment: no gatt or char")
            return
        }

        try {
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            char.value = next
            val requested = gatt.writeCharacteristic(char)
            Log.d(TAG, "[MANAGER FRAGMENT] 写入片段 请求返回: $requested, 长度=${next.size}")
            if (requested) {
                isWriting = true
            } else {
                // 放回队列并稍后重试
                synchronized(writeQueue) { writeQueue.add(next) }
                mainHandler.postDelayed({ sendNextFragment() }, 200)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "[MANAGER FRAGMENT] 写入片段异常: ${e.message}")
        }
    }

    // 由 GATT 回调在 write 完成后调用
    fun onCharacteristicWrite(status: Int) {
        isWriting = false
        Log.d(TAG, "onCharacteristicWrite status=$status")
        // 继续发送下一片
        mainHandler.post { sendNextFragment() }
    }

    // CRC16-CCITT (poly 0x1021)
    private fun crc16Ccitt(data: ByteArray): Int {
        var crc = 0xFFFF
        for (b in data) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            for (i in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) ((crc shl 1) xor 0x1021) else (crc shl 1)
                crc = crc and 0xFFFF
            }
        }
        return crc and 0xFFFF
    }

    // PackBits compression (simple, well-known):
    // - If a run of >=3 identical bytes is found, encode as header = (1 - runLen) (signed), then one byte value
    // - Otherwise output literal chunks with header = (len - 1) where 0 <= header <= 127 followed by len bytes
    // Compression removed: frames are sent raw (MAGIC+LEN+PAYLOAD+CRC)

    /**
     * 发送二值化位图的前 248 字节到 MCU（直接写入目标写特征）
     */
    fun writeImageFirst248(context: Context, bitmapProvider: () -> android.graphics.Bitmap?) {
        val bmp = bitmapProvider() ?: run {
            Log.w(TAG, "writeImageFirst248: bitmap is null")
            return
        }

        val imageBytes = ImageProcessor.bitmapToMonochromeBytes(bmp)
        if (imageBytes.isEmpty()) {
            Log.w(TAG, "writeImageFirst248: imageBytes empty")
            return
        }

        val sendLen = minOf(248, imageBytes.size)
        val payload = imageBytes.sliceArray(0 until sendLen)
        // 反转位映射以匹配 MCU（黑白反向），然后使用分片/队列发送逻辑
        for (i in payload.indices) payload[i] = (payload[i].toInt() xor 0xFF).toByte()
        sendDataWithFragments(payload)
    }
}
