package com.example.t4

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ExpandableListView
import android.widget.SimpleExpandableListAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.t4.databinding.FragmentBleDeviceBinding
import com.example.t4.ble.BleConnectionManager
import java.util.*

class BleDeviceFragment : Fragment() {
    private var _binding: FragmentBleDeviceBinding? = null
    private val binding get() = _binding!!

    private var deviceName: String? = null
    private var deviceAddress: String? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var connected = false

    // GATT 服务和特征列表适配器
    private var gattServiceAdapter: SimpleExpandableListAdapter? = null
    private val gattServiceData = ArrayList<HashMap<String, String>>()
    private val gattCharacteristicData = ArrayList<ArrayList<HashMap<String, String>>>()

    // 用于存储发现的服务和特征
    private val discoveredGattServices = ArrayList<BluetoothGattService>()

    companion object {
        private const val TAG = "BleDeviceFragment"
        
        // 列表项的键
        private const val LIST_NAME = "NAME"
        private const val LIST_UUID = "UUID"
        private const val LIST_PROPERTIES = "PROPERTIES"
        private const val LIST_VALUE = "VALUE"
        
        // 服务和特征的 UUID 常量
        private val HEART_RATE_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        private val BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        private val DEVICE_INFO_SERVICE_UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            deviceName = it.getString("device_name")
            deviceAddress = it.getString("device_address")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBleDeviceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 设置设备信息
        binding.deviceName.text = deviceName ?: "未知设备"
        binding.deviceAddress.text = deviceAddress
        binding.connectionState.text = "未连接"
        
        // 设置连接按钮点击事件
        binding.connectButton.setOnClickListener {
            if (connected) {
                disconnectDevice()
            } else {
                connectDevice()
            }
        }
        
        // 设置 GATT 服务列表
        setupGattServicesList()
    }

    private fun setupGattServicesList() {
        // 设置 ExpandableListView 的适配器
        val gattServiceListView = binding.gattServicesList
        
        // 创建适配器
        gattServiceAdapter = SimpleExpandableListAdapter(
            requireContext(),
            gattServiceData,
            android.R.layout.simple_expandable_list_item_2,
            arrayOf(LIST_NAME, LIST_UUID),
            intArrayOf(android.R.id.text1, android.R.id.text2),
            gattCharacteristicData,
            android.R.layout.simple_expandable_list_item_2,
            arrayOf(LIST_NAME, LIST_PROPERTIES),
            intArrayOf(android.R.id.text1, android.R.id.text2)
        )
        
        gattServiceListView.setAdapter(gattServiceAdapter)
        
        // 设置子项点击事件
        gattServiceListView.setOnChildClickListener { _, _, groupPosition, childPosition, _ ->
            val service = discoveredGattServices[groupPosition]
            val characteristic = service.characteristics[childPosition]
            
            // 读取特征值
            readCharacteristic(characteristic)
            true
        }
    }

    private fun connectDevice() {
        if (deviceAddress == null) {
            Toast.makeText(requireContext(), "设备地址为空", Toast.LENGTH_SHORT).show()
            return
        }
        
        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
        
        // 连接设备
        try {
            bluetoothGatt = device.connectGatt(requireContext(), false, gattCallback)
            // 将 GATT 注册到全局管理器以保持连接
            BleConnectionManager.setGatt(bluetoothGatt)
            binding.connectionState.text = "正在连接..."
            binding.connectButton.text = "断开连接"
            Log.d(TAG, "正在连接到设备: $deviceAddress")
        } catch (e: SecurityException) {
            Log.e(TAG, "连接设备失败: ${e.message}")
            Toast.makeText(requireContext(), "缺少蓝牙连接权限", Toast.LENGTH_SHORT).show()
        }
    }

    private fun disconnectDevice() {
        // 使用全局管理器断开并清理
        try {
            BleConnectionManager.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "disconnectDevice manager error: ${e.message}")
        }

        bluetoothGatt = null
        connected = false
        binding.connectionState.text = "已断开连接"
        binding.connectButton.text = "连接设备"

        // 清空服务列表
        clearGattServices()
        Log.d(TAG, "已断开设备连接 (通过管理器)")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connected = true
                Log.d(TAG, "已连接到 GATT 服务器")
                
                // 在 UI 线程更新状态
                activity?.runOnUiThread {
                    binding.connectionState.text = "已连接"
                }
                
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
                
                // 在 UI 线程更新状态
                activity?.runOnUiThread {
                    binding.connectionState.text = "已断开连接"
                    binding.connectButton.text = "连接设备"
                    
                    // 清空服务列表
                    clearGattServices()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "发现服务成功")
                
                // 在 UI 线程更新服务列表
                activity?.runOnUiThread {
                    displayGattServices(gatt.services)
                }
            } else {
                Log.w(TAG, "发现服务失败，状态: $status")
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
                
                // 在 UI 线程显示特征值
                activity?.runOnUiThread {
                    displayCharacteristicValue(characteristic)
                }
            } else {
                Log.w(TAG, "读取特征值失败，状态: $status")
            }
        }
    }

    private fun displayGattServices(services: List<BluetoothGattService>) {
        if (services.isEmpty()) {
            Log.d(TAG, "没有发现服务")
            return
        }
        
        // 清空之前的数据
        clearGattServices()
        
        // 保存发现的服务
        discoveredGattServices.addAll(services)
        
        // 遍历所有服务
        for (service in services) {
            val serviceUuid = service.uuid
            val serviceName = getServiceName(serviceUuid)
            
            // 创建服务项
            val serviceItem = HashMap<String, String>()
            serviceItem[LIST_NAME] = serviceName
            serviceItem[LIST_UUID] = serviceUuid.toString()
            gattServiceData.add(serviceItem)
            
            // 创建特征项列表
            val characteristicItems = ArrayList<HashMap<String, String>>()
            
            // 遍历服务的所有特征
            for (characteristic in service.characteristics) {
                val characteristicUuid = characteristic.uuid
                val characteristicName = getCharacteristicName(characteristicUuid)
                val properties = getCharacteristicProperties(characteristic)
                
                // 创建特征项
                val characteristicItem = HashMap<String, String>()
                characteristicItem[LIST_NAME] = characteristicName
                characteristicItem[LIST_UUID] = characteristicUuid.toString()
                characteristicItem[LIST_PROPERTIES] = properties
                characteristicItems.add(characteristicItem)
                // 如果发现 MCU 常见的写特征（前缀 FFF2），注册到全局管理器
                try {
                    val charUuidStr = characteristicUuid.toString().uppercase()
                    if (charUuidStr.startsWith("0000FFF2")) {
                        BleConnectionManager.registerTargetWriteCharacteristic(characteristic)
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
            
            gattCharacteristicData.add(characteristicItems)
        }
        
        // 通知适配器数据已更新
        gattServiceAdapter?.notifyDataSetChanged()
    }

    private fun clearGattServices() {
        gattServiceData.clear()
        gattCharacteristicData.clear()
        discoveredGattServices.clear()
        gattServiceAdapter?.notifyDataSetChanged()
    }

    private fun readCharacteristic(characteristic: BluetoothGattCharacteristic) {
        bluetoothGatt?.let {
            try {
                it.readCharacteristic(characteristic)
                Log.d(TAG, "正在读取特征值: ${characteristic.uuid}")
            } catch (e: SecurityException) {
                Log.e(TAG, "读取特征值失败: ${e.message}")
                Toast.makeText(requireContext(), "缺少蓝牙连接权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayCharacteristicValue(characteristic: BluetoothGattCharacteristic) {
        val value = characteristic.value
        val valueString = if (value != null && value.isNotEmpty()) {
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
        
        // 显示特征值
        Toast.makeText(
            requireContext(),
            "特征值: $valueString",
            Toast.LENGTH_SHORT
        ).show()
        
        // 更新特征值显示
        // TODO: 在界面上添加一个区域来显示特征值
    }

    private fun getServiceName(uuid: UUID): String {
        return when (uuid) {
            HEART_RATE_SERVICE_UUID -> "心率服务"
            BATTERY_SERVICE_UUID -> "电池服务"
            DEVICE_INFO_SERVICE_UUID -> "设备信息服务"
            else -> "未知服务"
        }
    }

    private fun getCharacteristicName(uuid: UUID): String {
        // 这里可以添加更多已知特征的名称
        return "特征: ${uuid.toString().substring(0, 8)}"
    }

    private fun getCharacteristicProperties(characteristic: BluetoothGattCharacteristic): String {
        val properties = characteristic.properties
        val result = StringBuilder()
        
        if ((properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
            result.append("读 ")
        }
        if ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
            result.append("写 ")
        }
        if ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            result.append("无响应写 ")
        }
        if ((properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
            result.append("通知 ")
        }
        if ((properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
            result.append("指示 ")
        }
        
        return result.toString().trim()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 不在 fragment 销毁时强制断开连接，连接由 BleConnectionManager 管理，避免在切换主页时断开
        _binding = null
    }

    // 添加权限检查方法（与 BleScanFragment 中的方法类似）
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