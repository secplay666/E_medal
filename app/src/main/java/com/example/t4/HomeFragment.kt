package com.example.t4

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomnavigation.BottomNavigationView

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

        // 设置底部导航栏选中状态和点击监听
//        val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigation)
//        bottomNav.selectedItemId = R.id.navigation_home
//
//        bottomNav.setOnItemSelectedListener { item ->
//            when (item.itemId) {
//                R.id.navigation_home -> true
//                R.id.navigation_ble -> {
//                    findNavController().navigate(R.id.actionFromHomeToBleScan)
//                    true
//                }
//
//                R.id.navigation_image -> {
//                    findNavController().navigate(R.id.actionFromHomeToImageEdit)
//                    true
//                }
//
//                R.id.navigation_debug -> {
//                    findNavController().navigate(R.id.actionFromHomeToBleDebug)
//                    true
//                }
//
//                else -> false
//            }
//        }
    }
}