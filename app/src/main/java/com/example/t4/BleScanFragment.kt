package com.example.t4

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class BleScanFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 关联布局文件 fragment_ble_scan.xml
        return inflater.inflate(R.layout.fragment_ble_scan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 返回主页
//        view.findViewById<View>(R.id.btn_back).setOnClickListener {
//            findNavController().popBackStack(R.id.homeFragment, false)
//        }

        // 添加下拉刷新监听
        val swipeRefreshLayout = view.findViewById<SwipeRefreshLayout>(R.id.refreshLayout)
        swipeRefreshLayout.setOnRefreshListener {
            // 触发扫描逻辑（原按钮点击的代码）
            startScan()

            // 扫描完成后停止刷新动画（需在扫描回调中调用）
            swipeRefreshLayout.isRefreshing = false
        }
    }
}

private fun startScan() {
    // 你的扫描逻辑（如蓝牙扫描、网络请求等）
}