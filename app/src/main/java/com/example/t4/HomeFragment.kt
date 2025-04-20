package com.example.t4

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 关联布局文件 fragment_home.xml
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 点击按钮跳转到图片编辑界面（对应 nav_graph.xml 中的 action_to_image_edit）
        view.findViewById<View>(R.id.btnAdd).setOnClickListener {
            findNavController().navigate(R.id.action_to_image_edit)
        }

        // 跳转到蓝牙扫描界面（action_home_to_ble_scan）
        view.findViewById<View>(R.id.btnScan).setOnClickListener {
            findNavController().navigate(R.id.action_to_ble_scan)
        }

        // 跳转到蓝牙调试界面（action_home_to_ble_debug）
        view.findViewById<View>(R.id.btnDebug).setOnClickListener {
            findNavController().navigate(R.id.action_to_ble_debug)
        }
    }
}