package com.example.t4

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import com.example.t4.ble.BleConnectionManager
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HomeFragment : Fragment() {

    private lateinit var imageGalleryRecyclerView: RecyclerView
    private val imageList = mutableListOf<Uri>()
    private lateinit var adapter: ImageGalleryAdapter

    // 权限请求回调
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allGranted = true
        permissions.entries.forEach {
            if (!it.value) {
                allGranted = false
                return@forEach
            }
        }
        
        if (allGranted) {
            loadImages()
        } else {
            Toast.makeText(requireContext(), "需要存储权限来显示图片", Toast.LENGTH_SHORT).show()
        }
    }

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
        
        // 初始化RecyclerView
        imageGalleryRecyclerView = view.findViewById(R.id.imageGallery)
        imageGalleryRecyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        
        // 初始化适配器
        adapter = ImageGalleryAdapter(imageList) { selectedImageUri ->
            // 图片点击事件，导航到图像编辑页面
            val bundle = Bundle().apply {
                putString("image_uri", selectedImageUri.toString())
            }
            findNavController().navigate(R.id.actionToImageEdit, bundle)
        }
        
        imageGalleryRecyclerView.adapter = adapter
        
        // 检查并请求权限
        checkPermissionsAndLoadImages()

        // 槽位显示按钮：选择槽位并请求 MCU 显示该槽位的图像
        val btnShowSlot = view.findViewById<ImageButton>(R.id.btnShowSlot)
        btnShowSlot?.setOnClickListener {
            if (!BleConnectionManager.isConnected()) {
                Toast.makeText(requireContext(), "未连接蓝牙，请先连接设备", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showSlotDialog()
        }
    }

    private fun showSlotDialog() {
        val items = Array(8) { i -> "槽位 ${i + 1}" }
        var choice = 0
        AlertDialog.Builder(requireContext())
            .setTitle("选择要显示的槽位")
            .setSingleChoiceItems(items, choice) { _, which -> choice = which }
            .setPositiveButton("显示") { _, _ ->
                val slot = choice + 1
                Toast.makeText(requireContext(), "请求显示 槽位 $slot", Toast.LENGTH_SHORT).show()
                try {
                    BleConnectionManager.sendDataWithFragments("SET_SLOT:$slot".toByteArray())
                } catch (e: Exception) {
                    Log.w("HomeFragment", "SET_SLOT 发送失败: ${e.message}")
                }
                try {
                    BleConnectionManager.sendDataWithFragments("DISPLAY".toByteArray())
                } catch (e: Exception) {
                    Log.w("HomeFragment", "DISPLAY 发送失败: ${e.message}")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun checkPermissionsAndLoadImages() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13及以上使用READ_MEDIA_IMAGES权限
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED -> {
                    loadImages()
                }
                else -> {
                    requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES))
                }
            }
        } else {
            // Android 12及以下使用READ_EXTERNAL_STORAGE权限
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    loadImages()
                }
                else -> {
                    requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                }
            }
        }
    }
    
    private fun loadImages() {
        // 清空现有列表
        imageList.clear()
        
        // 添加日志：开始加载图片
        Log.d("HomeFragment", "开始加载图片")
        
        // 查询媒体库中的图片
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        
        // 按日期降序排序，最新的图片显示在前面
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        
        try {
            val query = requireContext().contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )
            
            Log.d("HomeFragment", "查询结果: ${query != null}")
            
            query?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val count = cursor.count
                Log.d("HomeFragment", "找到 $count 张图片")
                
                // 遍历查询结果
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    
                    // 添加到图片列表
                    imageList.add(contentUri)
                    Log.d("HomeFragment", "添加图片: $contentUri")
                }
            }
        } catch (e: Exception) {
            Log.e("HomeFragment", "加载图片时出错", e)
        }
        
        // 通知适配器数据已更新
        adapter.notifyDataSetChanged()
        Log.d("HomeFragment", "图片列表大小: ${imageList.size}")
        
        // 如果没有找到图片，显示提示
        if (imageList.isEmpty()) {
            Toast.makeText(requireContext(), "没有找到图片", Toast.LENGTH_SHORT).show()
            Log.d("HomeFragment", "没有找到图片，显示提示")
        }
    }
}