package com.example.t4

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context.BLUETOOTH_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.t4.ble.BleDevice
import com.example.t4.ble.BleDeviceAdapter
import com.example.t4.databinding.FragmentBleScanBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.util.Log

class BleScanFragment : Fragment() {
    private lateinit var binding: FragmentBleScanBinding
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private lateinit var deviceAdapter: BleDeviceAdapter
    private var isScanning = false

    // 替换旧的 startActivityForResult
    private lateinit var enableBluetoothLauncher: ActivityResultLauncher<Intent>
    
    // 替换旧的 requestPermissions
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    
    // 添加多权限请求
    private lateinit var requestMultiplePermissionsLauncher: ActivityResultLauncher<Array<String>>
    // 替换旧的 LeScanCallback 为新的 ScanCallback
    // BLE扫描回调
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val rssi = result.rssi
            val scanRecord = result.scanRecord?.bytes ?: ByteArray(0)
            
            Log.d("BLE_SCAN", "扫描到设备: ${device.address}")
            
            activity?.runOnUiThread {
                // 添加权限检查
                if (hasBluetoothPermissions()) {
                    try {
                        val deviceName = device.name // 这里需要 BLUETOOTH_CONNECT 权限
                        Log.d("BLE_SCAN", "设备名称: $deviceName, 信号强度: $rssi")
                        val bleDevice = BleDevice(deviceName, device.address, rssi, scanRecord)
                        if (!currentDevices.contains(bleDevice)) {
                            currentDevices.add(bleDevice)
                            deviceAdapter.updateList(currentDevices)
                            Log.d("BLE_SCAN", "添加设备到列表，当前列表大小: ${currentDevices.size}")
                        }
                    } catch (e: SecurityException) {
                        Log.e("BLE_SCAN", "权限错误: ${e.message}")
                        Toast.makeText(context, "缺少蓝牙连接权限", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("BLE_SCAN", "缺少蓝牙权限")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("TTTT", "onCreate: 6789876!!")

        // 初始化 ActivityResultLauncher
        enableBluetoothLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == -1) { // RESULT_OK
                startBleScan()
            } else {
                Toast.makeText(context, "需要启用蓝牙才能扫描设备", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 初始化权限请求
        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                startBleScan()
            } else {
                Toast.makeText(context, "需要位置权限才能扫描蓝牙设备", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 初始化多权限请求
        requestMultiplePermissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                startBleScan()
            } else {
                Toast.makeText(context, "需要蓝牙权限才能扫描设备", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentBleScanBinding.inflate(inflater, container, false)
        Log.d("TTTT", "onCreateView: 6789876!!")
        return binding.root
    }

    private fun logPermissionStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanPermission = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            
            val connectPermission = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            
            Log.d("BLE_SCAN", "权限状态 - BLUETOOTH_SCAN: $scanPermission, BLUETOOTH_CONNECT: $connectPermission")
        } else {
            val locationPermission = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            Log.d("BLE_SCAN", "权限状态 - ACCESS_FINE_LOCATION: $locationPermission")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView() // 确保先初始化 deviceAdapter
        setupSwipeRefresh()
        initBluetooth()
        logPermissionStatus() // 添加权限状态日志
        Log.d("TTTT", "onViewCreated: 6789876!!")
    }

    private fun initBluetooth() {
        val bluetoothManager = requireContext().getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        checkPermissions()
    }

    private fun setupRecyclerView() {
        deviceAdapter = BleDeviceAdapter { device ->
            // 点击设备后的连接逻辑
            connectToDevice(device)
        }
        binding.deviceList.layoutManager = LinearLayoutManager(requireContext())
        binding.deviceList.adapter = deviceAdapter
    }

    private fun setupSwipeRefresh() {
        binding.refreshLayout.setOnRefreshListener {
            if (!isScanning) startBleScan()
            else binding.refreshLayout.isRefreshing = false
        }

        // 设置刷新指示器的颜色
        binding.refreshLayout.setColorSchemeResources(R.color.purple_500)

        // 移除可能影响功能的设置
         binding.refreshLayout.setProgressBackgroundColorSchemeResource(android.R.color.transparent)
         binding.refreshLayout.setDistanceToTriggerSync(300)
    }

    // 添加一个 Handler 成员变量
    private val handler = Handler(Looper.getMainLooper())
    
    private fun startBleScan() {
        if (!::deviceAdapter.isInitialized) {
            // 如果 deviceAdapter 尚未初始化，先初始化它
            setupRecyclerView()
        }
        
        if (!checkBluetoothEnabled()) return
        
        // 检查蓝牙适配器状态
        if (!::bluetoothAdapter.isInitialized || !bluetoothAdapter.isEnabled) {
            Log.e("BLE_SCAN", "蓝牙适配器未初始化或未启用")
            Toast.makeText(context, "蓝牙未启用", Toast.LENGTH_SHORT).show()
            binding.refreshLayout.isRefreshing = false
            return
        }
        
        if (!::bluetoothLeScanner.isInitialized) {
            Log.e("BLE_SCAN", "蓝牙扫描器未初始化")
            try {
                bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
            } catch (e: Exception) {
                Log.e("BLE_SCAN", "初始化蓝牙扫描器失败: ${e.message}")
                Toast.makeText(context, "初始化蓝牙扫描器失败", Toast.LENGTH_SHORT).show()
                binding.refreshLayout.isRefreshing = false
                return
            }
        }
        
        currentDevices.clear()
        deviceAdapter.updateList(currentDevices)
        isScanning = true
        binding.refreshLayout.isRefreshing = true
        binding.scanProgress.visibility = View.VISIBLE

        // 扫描10秒后自动停止
        handler.postDelayed({
            stopBleScan()
        }, 10000)

        // 添加权限检查
        if (hasBluetoothPermissions()) {
            try {
                // 使用新的扫描方法
                bluetoothLeScanner.startScan(scanCallback)
                Log.d("BLE_SCAN", "扫描已启动")
            } catch (e: SecurityException) {
                Log.e("BLE_SCAN", "启动扫描失败: ${e.message}")
                Toast.makeText(context, "缺少蓝牙扫描权限", Toast.LENGTH_SHORT).show()
                binding.refreshLayout.isRefreshing = false
                binding.scanProgress.visibility = View.GONE
                isScanning = false
            }
        } else {
            Log.e("BLE_SCAN", "缺少蓝牙扫描权限")
            Toast.makeText(context, "缺少蓝牙扫描权限", Toast.LENGTH_SHORT).show()
            binding.refreshLayout.isRefreshing = false
            binding.scanProgress.visibility = View.GONE
            isScanning = false
        }
    }

    private fun stopBleScan() {
        // 检查 Fragment 是否仍然附加到 Activity
        if (!isAdded) return
        
        // 添加权限检查
        if (hasBluetoothPermissions()) {
            try {
                // 使用新的停止扫描方法
                bluetoothLeScanner.stopScan(scanCallback)
            } catch (e: SecurityException) {
                Toast.makeText(context, "缺少蓝牙扫描权限", Toast.LENGTH_SHORT).show()
            }
        }
        isScanning = false
        binding.refreshLayout.isRefreshing = false
        binding.scanProgress.visibility = View.GONE
    }

    private fun checkBluetoothEnabled(): Boolean {
        return if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            // 使用新的启动活动方法
            enableBluetoothLauncher.launch(enableBtIntent)
            false
        } else true
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 需要 BLUETOOTH_SCAN 和 BLUETOOTH_CONNECT 权限
            val requiredPermissions = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
            
            val missingPermissions = requiredPermissions.filter {
                ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
            }
            
            if (missingPermissions.isNotEmpty()) {
                // 使用新的权限请求方法
                requestMultiplePermissionsLauncher.launch(missingPermissions.toTypedArray())
                return
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0-11 需要位置权限
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // 权限已授予，开始扫描
                    if (::deviceAdapter.isInitialized) { // 添加检查
                        startBleScan()
                    }
                }
                shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                    showPermissionRationaleDialog()
                }
                else -> {
                    // 使用新的权限请求方法
                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
            return
        }
        
        // 所有权限都已授予或低于 Android 6.0，开始扫描
        if (::deviceAdapter.isInitialized) { // 添加检查
            startBleScan()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isScanning) {
            stopBleScan()
        }
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1001
        private const val REQUEST_PERMISSION_LOCATION = 1002
        private val currentDevices = mutableListOf<BleDevice>()
    }

    private fun connectToDevice(device: BleDevice) {
        // 停止扫描
        if (isScanning) {
            stopBleScan()
        }
        
        // 显示设备详情弹窗
        showDeviceDetailDialog(device)
    }

    private fun showDeviceDetailDialog(device: BleDevice) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_device_detail, null)
        
        // 设置设备信息
        dialogView.findViewById<TextView>(R.id.deviceName).text = device.name ?: "未知设备"
        dialogView.findViewById<TextView>(R.id.deviceAddress).text = device.address
        dialogView.findViewById<TextView>(R.id.deviceRssi).text = "${device.rssi} dBm"
        
        // 解析并显示广播数据
        val advertisementData = device.parseAdvertisementData()
        dialogView.findViewById<TextView>(R.id.advertisementData).text = advertisementData
        
        // 设置可连接状态
        val isConnectable = device.isConnectable
        dialogView.findViewById<TextView>(R.id.connectable).text = if (isConnectable) "可连接" else "不可连接"
        
        // 设置连接按钮状态
        val connectButton = dialogView.findViewById<Button>(R.id.connectButton)
        connectButton.isEnabled = isConnectable
        
        // 设置连接按钮点击事件
        connectButton.setOnClickListener {
            // 连接到设备的逻辑
            Toast.makeText(requireContext(), "正在连接到设备: ${device.address}", Toast.LENGTH_SHORT).show()
            // 这里添加实际的连接逻辑
            // ...
        }
        
        // 创建并显示对话框
        AlertDialog.Builder(requireContext())
            .setTitle("设备详情")
            .setView(dialogView)
            .setPositiveButton("关闭", null)
            .create()
            .show()
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("需要位置权限")
            .setMessage("蓝牙扫描需要位置权限才能正常工作")
            .setPositiveButton("授权") { _, _ ->
                // 使用新的权限请求方法
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            .setNegativeButton("取消", null)
            .create()
            .show()
    }

    // 添加一个辅助方法来检查蓝牙权限
    private fun hasBluetoothPermissions(): Boolean {
        // 检查 Fragment 是否仍然附加到 Activity
        if (!isAdded) return false
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        // 移除所有挂起的回调
        handler.removeCallbacksAndMessages(null)
    }
}

