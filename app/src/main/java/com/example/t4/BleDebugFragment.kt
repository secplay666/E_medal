package com.example.t4

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class BleDebugFragment : Fragment() {

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

        // 发送调试指令（示例：携带参数）
        view.findViewById<View>(R.id.btnSend).setOnClickListener {
            val command = "AT+TEST"
            findNavController().previousBackStackEntry?.savedStateHandle?.set(
                "debug_result",
                "Command sent: $command"
            )
            findNavController().navigateUp()
        }
    }
}