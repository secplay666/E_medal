package com.example.t4

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import java.util.*

@Suppress("DEPRECATION")
class BleDebugFragment : Fragment() {
    private lateinit var deviceInfoTextView: TextView
    private lateinit var receiveBoxEditText: EditText
    private lateinit var sendBoxEditText: EditText
    private lateinit var disconnectButton: Button
    private lateinit var receiveButton: Button
    private lateinit var sendButton: Button

    private var deviceName: String? = null
    private var deviceAddress: String? = null
    private var isE104Device: Boolean = false

    private var bluetoothGatt: BluetoothGatt? = null
    private var connected = false

    // 目标服务和特征的UUID
    private val targetServiceUuidPrefix = "0000FFF0"
    private val targetReadCharUuidPrefix = "0000FFF1"
    private val targetWriteCharUuidPrefix = "0000FFF2"

    // 存储找到的服务和特征
    private var targetService: BluetoothGattService? = null
    private var targetReadCharacteristic: BluetoothGattCharacteristic? = null
    private var targetWriteCharacteristic: BluetoothGattCharacteristic? = null

    // 用于UI更新的Handler
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // 关联布局文件 fragment_ble_debug.xml
        return inflater.inflate(R.layout.fragment_ble_debug, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 获取传递的设备信息
        arguments?.let {
            deviceName = it.getString("device_name")
            deviceAddress = it.getString("device_address")
            isE104Device = it.getBoolean("is_e104_device", false)
        }

        // 初始化视图
        deviceInfoTextView = view.findViewById(R.id.deviceInfo)
        receiveBoxEditText = view.findViewById(R.id.receiveBox)
        sendBoxEditText = view.findViewById(R.id.sendBox)
        disconnectButton = view.findViewById(R.id.btnDisconnect)
        receiveButton = view.findViewById(R.id.btnReceive)
        sendButton = view.findViewById(R.id.btnSend)

        // 显示设备信息
        deviceInfoTextView.text =
                getString(
                        R.string.device_info_format,
                        deviceName ?: getString(R.string.unknown_device),
                        deviceAddress ?: ""
                )

        // 设置断开连接按钮
        disconnectButton.setOnClickListener {
            disconnectDevice()
            findNavController().navigateUp()
        }

        // 设置接收按钮
        receiveButton.setOnClickListener {
            // 读取特征值
            readCharacteristicData()
        }

        // 发送调试指令
        sendButton.setOnClickListener {
            val command = sendBoxEditText.text.toString()
            if (command.isNotEmpty()) {
                // 发送数据到写特征
                writeCharacteristicData(command)
                sendBoxEditText.text.clear()
            }
        }

        // 如果是E104设备，自动连接
        if (isE104Device && deviceAddress != null) {
            connectToDevice()
        }
    }

    private fun connectToDevice() {
        if (deviceAddress == null) {
            Toast.makeText(requireContext(), "设备地址为空", Toast.LENGTH_SHORT).show()
            return
        }

        val bluetoothManager =
                requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val device = bluetoothAdapter.getRemoteDevice(deviceAddress)

        // 连接设备
        try {
            bluetoothGatt = device.connectGatt(requireContext(), false, gattCallback)
            Log.d(TAG, "正在连接到设备: $deviceAddress")
            appendToReceiveBox("正在连接到设备: $deviceName ($deviceAddress)...")
        } catch (e: SecurityException) {
            Log.e(TAG, "连接设备失败: ${e.message}")
            Toast.makeText(requireContext(), "缺少蓝牙连接权限", Toast.LENGTH_SHORT).show()
        }
    }

    private fun disconnectDevice() {
        bluetoothGatt?.let {
            try {
                it.disconnect()
                it.close()
                bluetoothGatt = null
                connected = false

                // 清空目标服务和特征
                targetService = null
                targetReadCharacteristic = null
                targetWriteCharacteristic = null

                Log.d(TAG, "已断开设备连接")
                appendToReceiveBox("已断开设备连接")
            } catch (e: SecurityException) {
                Log.e(TAG, "断开设备连接失败: ${e.message}")
                Toast.makeText(requireContext(), "缺少蓝牙连接权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val gattCallback =
            object : BluetoothGattCallback() {
                override fun onConnectionStateChange(
                        gatt: BluetoothGatt,
                        status: Int,
                        newState: Int
                ) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        connected = true
                        Log.d(TAG, "已连接到 GATT 服务器")
                        appendToReceiveBox("已连接到设备")

                        // 开始发现服务
                        try {
                            Log.d(TAG, "开始发现服务")
                            gatt.discoverServices()
                        } catch (e: SecurityException) {
                            Log.e(TAG, "发现服务失败: ${e.message}")
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        connected = false
                        Log.d(TAG, "已断开与 GATT 服务器的连接")
                        appendToReceiveBox("已断开设备连接")

                        // 清空目标服务和特征
                        targetService = null
                        targetReadCharacteristic = null
                        targetWriteCharacteristic = null
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG, "发现服务成功")

                        // 查找目标服务和特征
                        findTargetServiceAndCharacteristics(gatt.services)
                    } else {
                        Log.w(TAG, "发现服务失败，状态: $status")
                        appendToReceiveBox("发现服务失败")
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onCharacteristicRead(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        status: Int
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG, "读取特征值成功")

                        // 显示读取到的数据
                        val value = characteristic.value
                        val valueString =
                                if (value != null && value.isNotEmpty()) {
                                    // 尝试将字节数组转换为字符串
                                    try {
                                        String(value)
                                    } catch (e: Exception) {
                                        // 如果转换失败，则显示十六进制值
                                        value.joinToString(" ") { String.format("%02X", it) }
                                    }
                                } else {
                                    "无数据"
                                }

                        Log.d(TAG, "特征值: $valueString")
                        appendToReceiveBox("接收: $valueString")
                    } else {
                        Log.w(TAG, "读取特征值失败，状态: $status")
                        appendToReceiveBox("读取特征值失败")
                    }
                }

                // 添加新的onCharacteristicRead方法（Android API 33+）
                override fun onCharacteristicRead(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        value: ByteArray,
                        status: Int
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG, "读取特征值成功 (新API)")

                        // 显示读取到的数据
                        val valueString =
                                if (value.isNotEmpty()) {
                                    // 尝试将字节数组转换为字符串
                                    try {
                                        String(value)
                                    } catch (e: Exception) {
                                        // 如果转换失败，则显示十六进制值
                                        value.joinToString(" ") { String.format("%02X", it) }
                                    }
                                } else {
                                    "无数据"
                                }

                        Log.d(TAG, "特征值: $valueString")
                        appendToReceiveBox("接收: $valueString")
                    } else {
                        Log.w(TAG, "读取特征值失败，状态: $status")
                        appendToReceiveBox("读取特征值失败")
                    }
                }

                override fun onCharacteristicWrite(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        status: Int
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG, "写入特征值成功")
                        appendToReceiveBox("发送成功")
                    } else {
                        Log.w(TAG, "写入特征值失败，状态: $status")
                        appendToReceiveBox("发送失败")
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onCharacteristicChanged(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic
                ) {
                    // 处理特征值变化通知
                    val value = characteristic.value
                    val valueString =
                            if (value != null && value.isNotEmpty()) {
                                // 尝试将字节数组转换为字符串
                                try {
                                    String(value)
                                } catch (e: Exception) {
                                    // 如果转换失败，则显示十六进制值
                                    value.joinToString(" ") { String.format("%02X", it) }
                                }
                            } else {
                                "无数据"
                            }

                    Log.d(TAG, "特征值变化: $valueString")
                    appendToReceiveBox("通知: $valueString")
                }

                // 添加新的onCharacteristicChanged方法（Android API 33+）
                override fun onCharacteristicChanged(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        value: ByteArray
                ) {
                    // 处理特征值变化通知
                    val valueString =
                            if (value.isNotEmpty()) {
                                // 尝试将字节数组转换为字符串
                                try {
                                    String(value)
                                } catch (e: Exception) {
                                    // 如果转换失败，则显示十六进制值
                                    value.joinToString(" ") { String.format("%02X", it) }
                                }
                            } else {
                                "无数据"
                            }

                    Log.d(TAG, "特征值变化 (新API): $valueString")
                    appendToReceiveBox("通知: $valueString")
                }
            }

    private fun findTargetServiceAndCharacteristics(services: List<BluetoothGattService>) {
        var foundTargetService = false

        for (service in services) {
            val serviceUuid = service.uuid.toString().uppercase()
            Log.d(TAG, "发现服务: $serviceUuid")

            // 检查是否是目标服务
            if (serviceUuid.startsWith(targetServiceUuidPrefix)) {
                Log.d(TAG, "找到目标服务: $serviceUuid")
                appendToReceiveBox("找到目标服务: $serviceUuid")
                targetService = service
                foundTargetService = true

                // 查找目标特征
                for (characteristic in service.characteristics) {
                    val charUuid = characteristic.uuid.toString().uppercase()
                    Log.d(TAG, "发现特征: $charUuid")

                    // 检查是否是目标读特征
                    if (charUuid.startsWith(targetReadCharUuidPrefix)) {
                        Log.d(TAG, "找到目标读特征: $charUuid")
                        appendToReceiveBox("找到目标读特征: $charUuid")
                        targetReadCharacteristic = characteristic

                        // 启用通知
                        enableNotification(characteristic)
                    }

                    // 检查是否是目标写特征
                    if (charUuid.startsWith(targetWriteCharUuidPrefix)) {
                        Log.d(TAG, "找到目标写特征: $charUuid")
                        appendToReceiveBox("找到目标写特征: $charUuid")
                        targetWriteCharacteristic = characteristic
                    }
                }

                // 如果找到了目标服务，就不需要继续查找了
                break
            }
        }

        if (!foundTargetService) {
            Log.d(TAG, "未找到目标服务")
            appendToReceiveBox("未找到目标服务 (0000FFF0)")
        }
    }

    private fun enableNotification(characteristic: BluetoothGattCharacteristic) {
        bluetoothGatt?.let { gatt ->
            // 检查特征是否支持通知
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                try {
                    // 检查是否有蓝牙连接权限
                    if (hasBluetoothPermissions()) {
                        // 设置客户端特征配置描述符
                        val descriptor =
                                characteristic.getDescriptor(
                                        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                                )
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

                        // 再次检查权限，确保在调用前已有权限
                        if (hasBluetoothPermissions()) {
                            gatt.writeDescriptor(descriptor)

                            // 再次检查权限
                            if (hasBluetoothPermissions()) {
                                // 启用通知
                                gatt.setCharacteristicNotification(characteristic, true)

                                Log.d(TAG, "已启用特征通知")
                                appendToReceiveBox("已启用数据通知")
                            } else {
                                Log.e(TAG, "缺少蓝牙连接权限")
                                appendToReceiveBox("缺少蓝牙连接权限")
                                Toast.makeText(requireContext(), "缺少蓝牙连接权限", Toast.LENGTH_SHORT)
                                        .show()
                            }
                        } else {
                            Log.e(TAG, "缺少蓝牙连接权限")
                            appendToReceiveBox("缺少蓝牙连接权限")
                            Toast.makeText(requireContext(), "缺少蓝牙连接权限", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.e(TAG, "缺少蓝牙连接权限")
                        appendToReceiveBox("缺少蓝牙连接权限")
                        Toast.makeText(requireContext(), "缺少蓝牙连接权限", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "启用通知失败: ${e.message}")
                    appendToReceiveBox("启用通知失败")
                }
            } else {
                Log.d(TAG, "特征不支持通知")
                appendToReceiveBox("特征不支持通知功能")
            }
        }
    }

    private fun readCharacteristicData() {
        if (!connected) {
            Toast.makeText(requireContext(), "设备未连接", Toast.LENGTH_SHORT).show()
            return
        }

        if (targetReadCharacteristic == null) {
            Toast.makeText(requireContext(), "未找到目标读特征", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            bluetoothGatt?.readCharacteristic(targetReadCharacteristic)
        } catch (e: SecurityException) {
            Log.e(TAG, "读取特征值失败: ${e.message}")
            Toast.makeText(requireContext(), "缺少蓝牙连接权限", Toast.LENGTH_SHORT).show()
        }
    }

    private fun writeCharacteristicData(data: String) {
        if (!connected) {
            Toast.makeText(requireContext(), "设备未连接", Toast.LENGTH_SHORT).show()
            return
        }

        if (targetWriteCharacteristic == null) {
            Toast.makeText(requireContext(), "未找到目标写特征", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // 将字符串转换为字节数组
            val bytes = data.toByteArray()

            // 写入特征值
            targetWriteCharacteristic?.value = bytes
            bluetoothGatt?.writeCharacteristic(targetWriteCharacteristic)

            Log.d(TAG, "发送数据: $data")
            appendToReceiveBox("发送: $data")
        } catch (e: SecurityException) {
            Log.e(TAG, "写入特征值失败: ${e.message}")
            Toast.makeText(requireContext(), "缺少蓝牙连接权限", Toast.LENGTH_SHORT).show()
        }
    }

    private fun appendToReceiveBox(message: String) {
        handler.post {
            receiveBoxEditText.append("$message\n")
            // 滚动到底部
            val scrollAmount =
                    receiveBoxEditText.layout.getLineTop(receiveBoxEditText.lineCount) -
                            receiveBoxEditText.height
            if (scrollAmount > 0) {
                receiveBoxEditText.scrollTo(0, scrollAmount)
            } else {
                receiveBoxEditText.scrollTo(0, 0)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disconnectDevice()
    }

    companion object {
        private const val TAG = "BleDebugFragment"
    }

    // 添加权限检查方法
    private fun hasBluetoothPermissions(): Boolean {
        // 检查 Fragment 是否仍然附加到 Activity
        if (!isAdded) return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 12 以下版本不需要 BLUETOOTH_CONNECT 权限
        }
    }
}
