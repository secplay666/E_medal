package com.example.t4

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomnavigation.BottomNavigationView

class ImageEditFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 关联布局文件 fragment_image_edit.xml
        return inflater.inflate(R.layout.fragment_image_edit, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        // 返回主页逻辑（对应 action_image_edit_to_home）
//        view.findViewById<View>(R.id.btn_back).setOnClickListener {
//            findNavController().navigateUp() // 或指定 action
//        }

        // 保存编辑后的图片（示例：携带参数跳转）
        view.findViewById<View>(R.id.btnSave).setOnClickListener {
            val bundle = Bundle().apply {
                putString("image_path", "/sdcard/edited_image.jpg")
            }
        }

        // 设置底部导航栏选中状态和点击监听
//        val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigation)
//        bottomNav.selectedItemId = R.id.navigation_image
//
//        bottomNav.setOnItemSelectedListener { item ->
//            when (item.itemId) {
//                R.id.navigation_home -> {
//                    findNavController().navigate(R.id.actionFromImageEditToHome)
//                    true
//                }
//                R.id.navigation_ble -> {
//                    findNavController().navigate(R.id.actionFromImageEditToBleScan)
//                    true
//                }
//                R.id.navigation_image -> true
//                R.id.navigation_debug -> {
//                    findNavController().navigate(R.id.actionFromImageEditToBleDebug)
//                    true
//                }
//                else -> false
//            }
//        }
    }
}