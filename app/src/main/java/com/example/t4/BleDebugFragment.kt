package com.example.t4

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class BleDebugFragment : Fragment() {
    private lateinit var deviceInfoTextView: TextView
    private lateinit var receiveBoxEditText: EditText
    private lateinit var sendBoxEditText: EditText
    private lateinit var disconnectButton: Button
    private lateinit var receiveButton: Button
    private lateinit var sendButton: Button
    
    private var deviceName: String? = null
    private var deviceAddress: String? = null

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
        }

        // 初始化视图
        deviceInfoTextView = view.findViewById(R.id.deviceInfo)
        receiveBoxEditText = view.findViewById(R.id.receiveBox)
        sendBoxEditText = view.findViewById(R.id.sendBox)
        disconnectButton = view.findViewById(R.id.btnDisconnect)
        receiveButton = view.findViewById(R.id.btnReceive)
        sendButton = view.findViewById(R.id.btnSend)
        
        // 显示设备信息
        deviceInfoTextView.text = getString(
            R.string.device_info_format,
            deviceName ?: getString(R.string.unknown_device),
            deviceAddress ?: ""
        )

        // 设置断开连接按钮
        disconnectButton.setOnClickListener {
            findNavController().navigateUp()
        }

        // 设置接收按钮
        receiveButton.setOnClickListener {
            // 这里添加接收数据的逻辑
            receiveBoxEditText.append("接收到的数据\n")
        }

        // 发送调试指令
        sendButton.setOnClickListener {
            val command = sendBoxEditText.text.toString()
            if (command.isNotEmpty()) {
                // 这里添加发送数据的逻辑
                receiveBoxEditText.append("发送: $command\n")
                sendBoxEditText.text.clear()
            }
        }
    }
}