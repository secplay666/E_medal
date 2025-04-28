package com.example.t4

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class BleScanFragment : Fragment() {
    private lateinit var binding: FragmentBleScanBinding
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var deviceAdapter: BleDeviceAdapter
    private var isScanning = false

    // BLE扫描回调[6](@ref)
    private val scanCallback = object : BluetoothAdapter.LeScanCallback {
        override fun onLeScan(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray) {
            activity?.runOnUiThread {
                val bleDevice = BleDevice(device.name, device.address, rssi, scanRecord)
                currentDevices.add(bleDevice)
                deviceAdapter.updateList(currentDevices)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentBleScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initBluetooth()
        setupRecyclerView()
        setupSwipeRefresh()
    }

    private fun initBluetooth() {
        val bluetoothManager = requireContext().getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        checkPermissions()
    }

    private fun setupRecyclerView() {
        deviceAdapter = BleDeviceAdapter { device ->
            // 点击设备后的连接逻辑（需自行实现）
            connectToDevice(device)
        }
        binding.rvDevices.adapter = deviceAdapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            if (!isScanning) startBleScan()
            else binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun startBleScan() {
        if (!checkBluetoothEnabled()) return

        currentDevices.clear()
        isScanning = true
        binding.swipeRefreshLayout.isRefreshing = true

        // 扫描10秒后自动停止[6](@ref)
        Handler(Looper.getMainLooper()).postDelayed({
            stopBleScan()
        }, 10000)

        bluetoothAdapter.startLeScan(scanCallback)
    }

    private fun stopBleScan() {
        bluetoothAdapter.stopLeScan(scanCallback)
        isScanning = false
        binding.swipeRefreshLayout.isRefreshing = false
    }

    private fun checkBluetoothEnabled(): Boolean {
        return if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            false
        } else true
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED -> return
                shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                    showPermissionRationaleDialog()
                }
                else -> {
                    requestPermissions(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        REQUEST_PERMISSION_LOCATION
                    )
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_PERMISSION_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startBleScan()
                } else {
                    Toast.makeText(context, "需要位置权限才能扫描蓝牙设备", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1001
        private const val REQUEST_PERMISSION_LOCATION = 1002
        private val currentDevices = mutableListOf<BleDevice>()
    }
}