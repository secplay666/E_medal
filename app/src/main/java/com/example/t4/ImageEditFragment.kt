package com.example.t4

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.t4.ble.BleConnectionManager
import androidx.navigation.fragment.findNavController
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageEditFragment : Fragment() {

    private lateinit var imagePreview: ImageView
    private lateinit var thresholdControlLayout: LinearLayout
    private lateinit var thresholdSeekBar: SeekBar
    private lateinit var thresholdValueText: TextView
    
    private var originalBitmap: Bitmap? = null
    private var currentBitmap: Bitmap? = null
    private var imageUri: Uri? = null
    private var currentThreshold = 128
    private var currentMode = EditMode.ORIGINAL
    private val TARGET_WIDTH = 400
    private val TARGET_HEIGHT = 300
    
    enum class EditMode {
        ORIGINAL,
        BINARIZE,
        ADAPTIVE_BINARIZE
    }

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

        Log.d("ImageEditFragment", "开始初始化视图")
        
        // 初始化视图
        imagePreview = view.findViewById(R.id.imagePreview)
        thresholdControlLayout = view.findViewById(R.id.thresholdControlLayout)
        thresholdSeekBar = view.findViewById(R.id.thresholdSeekBar)
        thresholdValueText = view.findViewById(R.id.thresholdValueText)
        
        // 检查工具面板
        val toolPanel = view.findViewById<View>(R.id.toolPanel)
        Log.d("ImageEditFragment", "工具面板是否存在: ${toolPanel != null}")
        
        // 检查按钮
        val btnOriginal = view.findViewById<View>(R.id.btnOriginal)
        val btnBinarize = view.findViewById<View>(R.id.btnBinarize)
        val btnAdaptiveBinarize = view.findViewById<View>(R.id.btnAdaptiveBinarize)
        Log.d("ImageEditFragment", "原图按钮是否存在: ${btnOriginal != null}")
        Log.d("ImageEditFragment", "二值化按钮是否存在: ${btnBinarize != null}")
        Log.d("ImageEditFragment", "自适应按钮是否存在: ${btnAdaptiveBinarize != null}")
        
        // 获取传递的图片URI
        arguments?.getString("image_uri")?.let { uriString ->
            imageUri = Uri.parse(uriString)
            loadImage()
        }
        
        // 设置阈值滑块监听器
        thresholdSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentThreshold = progress
                thresholdValueText.text = progress.toString()
                if (currentMode == EditMode.BINARIZE) {
                    applyBinarize()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // 使用系统导航栏的返回按钮，所以不需要设置自定义返回按钮
        // 添加日志来检查工具面板的初始化
        Log.d("ImageEditFragment", "初始化界面元素")
        
        // 原始图片按钮
        view.findViewById<ImageButton>(R.id.btnOriginal).setOnClickListener {
            currentMode = EditMode.ORIGINAL
            thresholdControlLayout.visibility = View.GONE
            showOriginalImage()
        }
        
        // 二值化按钮
        view.findViewById<ImageButton>(R.id.btnBinarize).setOnClickListener {
            currentMode = EditMode.BINARIZE
            thresholdControlLayout.visibility = View.VISIBLE
            applyBinarize()
        }
        
        // 自适应二值化按钮
        view.findViewById<ImageButton>(R.id.btnAdaptiveBinarize).setOnClickListener {
            currentMode = EditMode.ADAPTIVE_BINARIZE
            thresholdControlLayout.visibility = View.GONE
            applyAdaptiveBinarize()
        }

        // 保存按钮
        view.findViewById<ImageButton>(R.id.btnSave).setOnClickListener {
            saveImage()
        }
        
        // 下载到下位机按钮
        view.findViewById<ImageButton>(R.id.btnDownload).setOnClickListener {
            // 发送当前二值化图片的前 248 字节到下位机
            if (currentBitmap == null) {
                Toast.makeText(requireContext(), "当前无图片可发送", Toast.LENGTH_SHORT).show()
            } else {
                BleConnectionManager.writeImageFirst248(requireContext()) { currentBitmap }
                Toast.makeText(requireContext(), "已请求发送图片前 248 字节", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun loadImage() {
        try {
            // 使用ContentResolver打开输入流
            val inputStream = requireContext().contentResolver.openInputStream(imageUri!!)
            
            // 解码为Bitmap
            originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            // 显示原始图片
            showOriginalImage()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.error_loading_image, Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }
    
    private fun showOriginalImage() {
        currentBitmap = originalBitmap
        imagePreview.setImageBitmap(currentBitmap)
    }
    
    private fun applyBinarize() {
        originalBitmap?.let {
            // 先缩放为 e-paper 期望的 400x300 再二值化
            val scaled = Bitmap.createScaledBitmap(it, TARGET_WIDTH, TARGET_HEIGHT, true)
            currentBitmap = ImageProcessor.binarize(scaled, currentThreshold)
            imagePreview.setImageBitmap(currentBitmap)
        }
    }
    
    private fun applyAdaptiveBinarize() {
        originalBitmap?.let {
            // 先缩放为 e-paper 期望的 400x300 再自适应二值化
            val scaled = Bitmap.createScaledBitmap(it, TARGET_WIDTH, TARGET_HEIGHT, true)
            currentBitmap = ImageProcessor.adaptiveBinarize(scaled)
            imagePreview.setImageBitmap(currentBitmap)
        }
    }
    
    private fun saveImage() {
        currentBitmap?.let { bitmap ->
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val filename = "IMG_${timestamp}.jpg"
                
                var outputStream: OutputStream? = null
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10及以上使用MediaStore API
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    }
                    
                    val uri = requireContext().contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 
                        contentValues
                    )
                    
                    uri?.let {
                        outputStream = requireContext().contentResolver.openOutputStream(it)
                    }
                } else {
                    // Android 9及以下使用传统文件API
                    val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val image = File(imagesDir, filename)
                    outputStream = FileOutputStream(image)
                }
                
                outputStream?.use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
                    Toast.makeText(requireContext(), R.string.image_saved, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "保存图片失败", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }
}