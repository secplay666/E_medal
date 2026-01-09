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
import androidx.lifecycle.MutableLiveData

object BleConnectionManager {
    private const val TAG = "BleConnectionManager"

    // 进度回调
    val transferProgress = MutableLiveData<String>()
    
    private var totalFramesToSend = 0
    private var framesSent = 0
    private var isTransferring = false

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
    
    fun getMtu(): Int = currentMtu

    /**
     * MTU 测试：逐步测试不同大小，找出最大可发送字节数
     */
    fun testMtuSendRaw(size: Int) {
        val gatt = bluetoothGatt
        val char = targetWriteCharacteristic
        if (gatt == null || char == null) {
            Log.e(TAG, "[MTU_TEST] No gatt or characteristic!")
            return
        }
        
        Log.d(TAG, "[MTU_TEST] ========================================")
        Log.d(TAG, "[MTU_TEST] Current MTU setting: $currentMtu")
        Log.d(TAG, "[MTU_TEST] Testing different sizes...")
        Log.d(TAG, "[MTU_TEST] ========================================")
        
        // 测试不同大小: 20, 50, 100, 150, 200
        val testSizes = listOf(20, 50, 100, 150, 200)
        
        for (testSize in testSizes) {
            val testData = ByteArray(testSize) { i -> (i and 0xFF).toByte() }
            
            try {
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                char.value = testData
                val result = gatt.writeCharacteristic(char)
                Log.d(TAG, "[MTU_TEST] Size $testSize bytes: request=${if (result) "OK" else "FAIL"}")
                
                if (result) {
                    // 等待一下让回调执行
                    Thread.sleep(500)
                } else {
                    Log.e(TAG, "[MTU_TEST] Size $testSize FAILED to send!")
                    break
                }
            } catch (e: Exception) {
                Log.e(TAG, "[MTU_TEST] Size $testSize Exception: ${e.message}")
                break
            }
        }
    }
    
    /**
     * 发送固定 20 字节测试数据（几乎所有 BLE 都支持）
     */
    fun testMtuSend20() {
        val gatt = bluetoothGatt
        val char = targetWriteCharacteristic
        if (gatt == null || char == null) {
            Log.e(TAG, "[MTU_TEST] No gatt or characteristic!")
            return
        }
        
        val testData = ByteArray(20) { i -> (i and 0xFF).toByte() }
        Log.d(TAG, "[MTU_TEST] Sending 20 bytes: ${testData.joinToString(" ") { String.format("%02X", it) }}")
        
        try {
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            char.value = testData
            val result = gatt.writeCharacteristic(char)
            Log.d(TAG, "[MTU_TEST] writeCharacteristic returned: $result")
        } catch (e: SecurityException) {
            Log.e(TAG, "[MTU_TEST] SecurityException: ${e.message}")
        }
    }

    // 发送队列
    private val writeQueue: Queue<ByteArray> = LinkedList()
    @Volatile
    private var isWriting: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 将数据按 currentMtu-3 分片并加入队列，支持可选的RLE压缩，并开始发送
     * @param payload 有效载荷数据
     * @param enableCompression 是否启用压缩（仅对248字节页数据有效）
     */
    fun sendDataWithFragments(payload: ByteArray, enableCompression: Boolean = true, setColorBit: Boolean = false, isRed: Boolean = false) {
        val gatt = bluetoothGatt
        val char = targetWriteCharacteristic
        if (gatt == null || char == null) {
            Log.w(TAG, "sendDataWithFragments: no gatt or char")
            return
        }

        // 只对248字节的页数据启用压缩
        val shouldCompress = enableCompression && payload.size == 248

        // 如果需要在安卓端对 RED 通道取反，必须在压缩前对原始数据取反，
        // 否则会破坏 RLE 格式导致解压失败。
        val payloadForCompression: ByteArray = if (setColorBit && isRed) {
            val inv = payload.copyOf()
            for (i in inv.indices) inv[i] = (inv[i].toInt() xor 0xFF).toByte()
            inv
        } else {
            payload
        }

        val processedPayload = if (shouldCompress) compressPageRLE(payloadForCompression) else payloadForCompression

        if (shouldCompress) {
            val ratio = (processedPayload.size.toFloat() / payload.size * 100).toInt()
            Log.d(TAG, "Page compressed: ${payload.size}B -> ${processedPayload.size}B ($ratio%)")
        }

        // New frame format: [MAGIC(2)][FLAGS(1)][LEN(2)][PAYLOAD(len)][CRC(2)]
        val magic = byteArrayOf(0xAB.toByte(), 0xCD.toByte())
        var flags: Int = if (shouldCompress) 0x01 else 0x00
        if (setColorBit && isRed) flags = flags or 0x02
        val flagsByte: Byte = (flags and 0xFF).toByte()

        // CRC 对处理后的 payload 计算（已在压缩前/未压缩时对原始数据做了取反）
        val crc = crc16Ccitt(processedPayload)
        val len = processedPayload.size
        val lenHi = ((len shr 8) and 0xFF).toByte()
        val lenLo = (len and 0xFF).toByte()

        // 帧结构：MAGIC(2) + FLAGS(1) + LEN(2) + PAYLOAD(len) + CRC(2)
        val frame = ByteArray(2 + 1 + 2 + len + 2)
        var offset = 0

        frame[offset++] = magic[0]
        frame[offset++] = magic[1]
        frame[offset++] = flagsByte
        frame[offset++] = lenHi
        frame[offset++] = lenLo
        System.arraycopy(processedPayload, 0, frame, offset, len)
        offset += len
        frame[offset++] = ((crc shr 8) and 0xFF).toByte()
        frame[offset++] = (crc and 0xFF).toByte()

        try {
            val hex = if (frame.size <= 32) frame.joinToString(" ") { String.format("%02X", it) }
                      else frame.take(32).joinToString(" ") { String.format("%02X", it) } + " ..."
            Log.d(TAG, "[MANAGER] TX frame: flags=0x%02X len=%d total=%d [%s]".format(flags, len, frame.size, hex))
        } catch (e: Exception) { /* ignore */ }

        // 分片发送
        val chunkSize = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) (currentMtu - 3) else 20).coerceAtLeast(1)
        var chunkOffset = 0
        synchronized(writeQueue) {
            // 如果不在传输中，重新开始计数
            if (!isTransferring) {
                totalFramesToSend = 0
                framesSent = 0
                isTransferring = true
                Log.d(TAG, "Starting new transfer")
            }
            
            val startQueueSize = writeQueue.size
            while (chunkOffset < frame.size) {
                val end = (chunkOffset + chunkSize).coerceAtMost(frame.size)
                val chunk = frame.copyOfRange(chunkOffset, end)
                writeQueue.add(chunk)
                chunkOffset = end
            }
            // 累加总数
            val newChunks = (writeQueue.size - startQueueSize)
            totalFramesToSend += newChunks
            Log.d(TAG, "Added $newChunks chunks, total=$totalFramesToSend, sent=$framesSent")
        }

        // 触发发送
        mainHandler.post { sendNextFragment() }
    }

    // 标记是否使用 NO_RESPONSE 模式（不等待 GATT 回调）
    private var useNoResponse = true  // 设为 true 启用快速模式

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
            if (useNoResponse) {
                // NO_RESPONSE 模式：不等待 ACK，速度更快
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            char.value = next
            val requested = gatt.writeCharacteristic(char)
            Log.d(TAG, "[MANAGER FRAGMENT] 写入片段 请求返回: $requested, 长度=${next.size}")
            if (requested) {
                isWriting = true
                
                if (useNoResponse) {
                    // NO_RESPONSE 模式：立即更新进度并发送下一包
                    isWriting = false
                    synchronized(writeQueue) {
                        if (isTransferring && totalFramesToSend > 0) {
                            framesSent++
                            // 每 10 包更新一次 UI，减少 UI 开销
                            if (framesSent % 10 == 0 || writeQueue.isEmpty()) {
                                val progress = (framesSent * 100) / totalFramesToSend
                                transferProgress.postValue("已发送: $framesSent / $totalFramesToSend (${progress}%)")
                            }
                            
                            if (framesSent >= totalFramesToSend && writeQueue.isEmpty()) {
                                totalFramesToSend = 0
                                framesSent = 0
                                isTransferring = false
                                transferProgress.postValue("")
                                Log.d(TAG, "Transfer complete!")
                            }
                        }
                    }
                    // 立即发送下一包，不等待
                    mainHandler.post { sendNextFragment() }
                }
                // DEFAULT 模式会等待 onCharacteristicWrite 回调
            } else {
                // 放回队列并稍后重试
                synchronized(writeQueue) { writeQueue.add(next) }
                mainHandler.postDelayed({ sendNextFragment() }, 200)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "[MANAGER FRAGMENT] 写入片段异常: ${e.message}")
        }
    }

    // 由 GATT 回调在 write 完成后调用（仅 DEFAULT 模式使用）
    fun onCharacteristicWrite(status: Int) {
        if (useNoResponse) {
            // NO_RESPONSE 模式下忽略 GATT 回调，避免重复计数
            return
        }
        
        isWriting = false
        Log.d(TAG, "onCharacteristicWrite status=$status")
        // 更新进度（非阻塞）
        synchronized(writeQueue) {
            if (isTransferring && totalFramesToSend > 0) {
                framesSent++
                val progress = (framesSent * 100) / totalFramesToSend
                transferProgress.postValue("已发送: $framesSent / $totalFramesToSend (${progress}%)")
                Log.d(TAG, "Progress: $framesSent / $totalFramesToSend")
                
                // 完成时重置
                if (framesSent >= totalFramesToSend && writeQueue.isEmpty()) {
                    totalFramesToSend = 0
                    framesSent = 0
                    isTransferring = false
                    transferProgress.postValue("")
                    Log.d(TAG, "Transfer complete!")
                }
            }
        }
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

    /**
     * RLE压缩：适用于单页数据（248字节）
     * 格式：[count][value] 循环
     * - count >= 128: 重复模式，实际重复次数 = (257 - count)，后跟1字节重复值
     * - count < 128: 字面量模式，后跟count个原始字节
     */
    private fun compressPageRLE(data: ByteArray): ByteArray {
        val out = mutableListOf<Byte>()
        var i = 0

        while (i < data.size) {
            // 检查重复字节
            var runLength = 1
            while (i + runLength < data.size
                   && data[i] == data[i + runLength]
                   && runLength < 129) {
                runLength++
            }

            if (runLength >= 3) {
                // 重复模式：值得压缩（3字节以上重复）
                out.add((257 - runLength).toByte())
                out.add(data[i])
                i += runLength
            } else {
                // 字面量模式：收集不重复的字节
                val literalStart = i
                var literalLen = 0

                while (i < data.size && literalLen < 127) {
                    var nextRun = 1
                    while (i + nextRun < data.size
                           && data[i] == data[i + nextRun]
                           && nextRun < 3) {
                        nextRun++
                    }

                    if (nextRun >= 3) break // 发现3+重复，停止收集

                    i++
                    literalLen++
                }

                // 输出字面量块
                out.add(literalLen.toByte())
                for (j in literalStart until literalStart + literalLen) {
                    out.add(data[j])
                }
            }
        }

        return out.toByteArray()
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
