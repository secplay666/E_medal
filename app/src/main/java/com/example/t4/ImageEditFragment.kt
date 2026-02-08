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
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Matrix
import android.graphics.Color
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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

    private lateinit var imagePreview: CropImageView
    private lateinit var imagePreviewContainer: ViewGroup
    private lateinit var thresholdControlLayout: LinearLayout
    private lateinit var thresholdSeekBar: SeekBar
    private lateinit var thresholdValueText: TextView
    private lateinit var redThresholdControlLayout: LinearLayout
    private lateinit var redThresholdSeekBar: SeekBar
    private lateinit var redThresholdValueText: TextView
    private lateinit var btnConfirmCrop: android.widget.Button
    private var staticPreview: ImageView? = null
    
    private var originalBitmap: Bitmap? = null
    private var currentBitmap: Bitmap? = null
    private var imageUri: Uri? = null
    private var currentThreshold = 128
    private var currentRedThreshold = 50
    private var currentMode = EditMode.ORIGINAL
    private val TARGET_WIDTH = 400
    private val TARGET_HEIGHT = 300
    private var selectedSlot = 1
    
    private var cropConfirmed = false
    
    enum class EditMode {
        ORIGINAL,
        BINARIZE,
        DETECT_RED
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
        imagePreviewContainer = view.findViewById(R.id.imagePreviewContainer)
        thresholdControlLayout = view.findViewById(R.id.thresholdControlLayout)
        thresholdSeekBar = view.findViewById(R.id.thresholdSeekBar)
        thresholdValueText = view.findViewById(R.id.thresholdValueText)
        redThresholdControlLayout = view.findViewById(R.id.redThresholdControlLayout)
        redThresholdSeekBar = view.findViewById(R.id.redThresholdSeekBar)
        redThresholdValueText = view.findViewById(R.id.redThresholdValueText)
        
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
        
        // 设置阈值滑块监听器（实时应用二值化）
        thresholdSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentThreshold = progress
                thresholdValueText.text = progress.toString()
                if (currentMode == EditMode.BINARIZE) {
                    applyBinarize()
                } else if (currentMode == EditMode.DETECT_RED) {
                    applyDetectRed()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // 设置红色敏感度滑块监听器（实时应用红色检测）
        redThresholdSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentRedThreshold = progress
                redThresholdValueText.text = progress.toString()
                if (currentMode == EditMode.DETECT_RED) {
                    applyDetectRed()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // 使用系统导航栏的返回按钮，所以不需要设置自定义返回按钮
        // 添加日志来检查工具面板的初始化
        Log.d("ImageEditFragment", "初始化界面元素")
        
        // 观察传输进度（异步，不阻塞UI主线程）
        var statusText = view.findViewById<TextView>(R.id.statusText)
        Log.d("ImageEditFragment", "statusText from layout found: ${statusText != null}")
        
        // 如果布局中找不到，动态创建一个
        if (statusText == null) {
            Log.w("ImageEditFragment", "Creating dynamic statusText TextView")
            statusText = TextView(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 60)
                setBackgroundColor(android.graphics.Color.parseColor("#FF6B6B"))
                setTextColor(android.graphics.Color.WHITE)
                textSize = 12f
                setPadding(16, 6, 16, 6)
                visibility = View.GONE
            }
            // 添加到根布局
            (view as? ViewGroup)?.addView(statusText, 0)
        }
        
        val finalStatusText = statusText
        BleConnectionManager.transferProgress.observe(viewLifecycleOwner) { progress ->
            Log.d("ImageEditFragment", "Progress update: $progress")
            if (progress.isNotEmpty()) {
                finalStatusText.text = progress
                finalStatusText.visibility = View.VISIBLE
            } else {
                finalStatusText.visibility = View.GONE
            }
        }
        
        // 原始图片按钮
        view.findViewById<ImageButton>(R.id.btnOriginal).setOnClickListener {
            currentMode = EditMode.ORIGINAL
            thresholdControlLayout.visibility = View.GONE
            redThresholdControlLayout.visibility = View.GONE
            showOriginalImage()
            // 进入原图/重置裁剪确认状态
            cropConfirmed = false
        }
        
        // 进入编辑页即显示可拖动的裁剪框（CropImageView 已在布局中配置为 4:3）
        
        // 确认裁剪按钮
        btnConfirmCrop = view.findViewById(R.id.btnConfirmCrop)
        btnConfirmCrop.setOnClickListener {
            // 在确认时从 CropImageView 获取裁剪结果并固定为原图
            var cropped: Bitmap? = null
            try {
                cropped = imagePreview.getCroppedImage(TARGET_WIDTH, TARGET_HEIGHT)
            } catch (_: Exception) {}
            if (cropped == null) cropped = originalBitmap
            cropped?.let {
                originalBitmap = it
                currentBitmap = it
                // 隐藏 CropImageView，用静态 ImageView 替代以移除裁剪交互
                imagePreview.visibility = View.GONE
                val static = ImageView(requireContext())
                static.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                static.scaleType = ImageView.ScaleType.FIT_CENTER
                static.setImageBitmap(it)
                imagePreviewContainer.addView(static)
                staticPreview = static
                cropConfirmed = true
                btnConfirmCrop.visibility = View.GONE
                Toast.makeText(requireContext(), "裁剪已确认 — 裁剪功能已禁用", Toast.LENGTH_SHORT).show()
            }
        }

        // 二值化按钮（只有在裁剪确认后才可用）
        view.findViewById<ImageButton>(R.id.btnBinarize).setOnClickListener {
            if (!cropConfirmed) {
                Toast.makeText(requireContext(), "请先确定裁剪后再二值化", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            currentMode = EditMode.BINARIZE
            thresholdControlLayout.visibility = View.VISIBLE
            redThresholdControlLayout.visibility = View.GONE
            applyBinarize()
        }
        
        // 红色检测按钮 - 检测并保留红色区域，其他部分二值化
        view.findViewById<ImageButton>(R.id.btnAdaptiveBinarize).setOnClickListener {
            if (!cropConfirmed) {
                Toast.makeText(requireContext(), "请先确定裁剪后再检测", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            currentMode = EditMode.DETECT_RED
            thresholdControlLayout.visibility = View.VISIBLE
            redThresholdControlLayout.visibility = View.VISIBLE
            applyDetectRed()
        }

        // 保存按钮
        view.findViewById<ImageButton>(R.id.btnSave).setOnClickListener {
            saveImage()
        }

        // 旋转按钮：顺时针 90 度
        view.findViewById<ImageButton>(R.id.btnRotate).setOnClickListener {
            try {
                // CropImageView 提供 rotateImage(degrees) 方法
                if (staticPreview != null) {
                    // 已确认裁剪并使用静态预览：旋转原始位图并更新静态视图
                    originalBitmap = rotateBitmap(originalBitmap, 90)
                    originalBitmap?.let { staticPreview?.setImageBitmap(it) }
                } else {
                    imagePreview.rotateImage(90)
                }
            } catch (e: Exception) {
                // 回退到对 Bitmap 旋转
                originalBitmap = rotateBitmap(originalBitmap, 90)
                // 重新加载到预览控件
                if (staticPreview != null) {
                    originalBitmap?.let { staticPreview?.setImageBitmap(it) }
                } else {
                    originalBitmap?.let { imagePreview.setImageBitmap(it) }
                }
            }
        }
        
        // 下载到下位机按钮（带颜色选择）
        val colorToggleBtn = view.findViewById<ImageButton>(R.id.btnColorToggle)
        colorToggleBtn?.tag = "black"
        colorToggleBtn?.setOnClickListener {
            val isNowRed = colorToggleBtn?.tag != "red"
            if (isNowRed == true) {
                colorToggleBtn?.setColorFilter(Color.RED)
                colorToggleBtn?.tag = "red"
                // 将当前二值化图像的黑色部分染成红色以预览
                colorizeImageToRed()
            } else {
                colorToggleBtn?.clearColorFilter()
                colorToggleBtn?.tag = "black"
                // 恢复为原始的二值化图像（黑白）
                restoreOriginalBinary()
            }
        }

        // 槽位选择改为点击下载时弹出对话框选择，以避免依赖布局中可能丢失的 Spinner id

        view.findViewById<ImageButton>(R.id.btnDownload).setOnClickListener {
            // 检查蓝牙连接
            if (!BleConnectionManager.isConnected()) {
                Toast.makeText(requireContext(), "未连接蓝牙，请先连接设备", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (currentBitmap == null) {
                Toast.makeText(requireContext(), "当前无图片可发送", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 先弹出槽位选择对话框
            showSlotSelectionDialog(isRed = colorToggleBtn?.tag == "red")
        }
    }

    private fun showSlotSelectionDialog(isRed: Boolean) {
        val items = Array(8) { i -> "槽位 ${i + 1}" }
        var choice = selectedSlot - 1
        AlertDialog.Builder(requireContext())
            .setTitle("选择图片槽位")
            .setSingleChoiceItems(items, choice) { _, which -> choice = which }
            .setPositiveButton("发送") { _, _ ->
                selectedSlot = choice + 1
                Toast.makeText(requireContext(), "已选槽位 $selectedSlot", Toast.LENGTH_SHORT).show()
                sendImageToMcu(isRed)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun sendImageToMcu(isRedSelected: Boolean) {
        // 在发送图像前告知 MCU 要写入的槽位，再重置页计数
        try {
            BleConnectionManager.sendDataWithFragments("SET_SLOT:$selectedSlot".toByteArray())
        } catch (_: Exception) {}
        
        // 判断是否为红黑合成模式（当前图像有红、黑、白三种颜色）
        val isRedBlackComposite = currentMode == EditMode.DETECT_RED
        
        if (isRedBlackComposite) {
            // 红黑合成模式：分别发送红色层和黑色层
            sendRedBlackCompositeImage()
        } else {
            // 普通模式：发送普通二值化图像
            BleConnectionManager.sendDataWithFragments("RESET_PAGES".toByteArray())
            val imageBytes = ImageProcessor.bitmapToMonochromeBytes(currentBitmap!!)
            val PAGE_SIZE = 248
            val totalToSend = imageBytes.size
            val pages = (totalToSend + PAGE_SIZE - 1) / PAGE_SIZE

            for (p in 0 until pages) {
                val offset = p * PAGE_SIZE
                val end = (offset + PAGE_SIZE).coerceAtMost(totalToSend)
                val pagePayload = ByteArray(PAGE_SIZE)
                for (j in pagePayload.indices) pagePayload[j] = 0xFF.toByte()
                System.arraycopy(imageBytes, offset, pagePayload, 0, end - offset)
                for (j in pagePayload.indices) pagePayload[j] = (pagePayload[j].toInt() xor 0xFF).toByte()
                BleConnectionManager.sendDataWithFragments(pagePayload, enableCompression = true, setColorBit = isRedSelected, isRed = isRedSelected)
            }

            BleConnectionManager.sendDataWithFragments("DISPLAY".toByteArray())
            Toast.makeText(requireContext(), "已请求发送整张图像，共 $pages 页，槽位 $selectedSlot，并触发 DISPLAY", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendRedBlackCompositeImage() {
        // 红黑合成模式：先发送红色层，再发送黑色层
        val PAGE_SIZE = 248
        
        // 第一步：发送红色层
        BleConnectionManager.sendDataWithFragments("RESET_PAGES".toByteArray())
        val redLayerBytes = ImageProcessor.extractRedLayer(currentBitmap!!)
        var totalToSend = redLayerBytes.size
        var pages = (totalToSend + PAGE_SIZE - 1) / PAGE_SIZE

        for (p in 0 until pages) {
            val offset = p * PAGE_SIZE
            val end = (offset + PAGE_SIZE).coerceAtMost(totalToSend)
            val pagePayload = ByteArray(PAGE_SIZE)
            for (j in pagePayload.indices) pagePayload[j] = 0x00.toByte()
            System.arraycopy(redLayerBytes, offset, pagePayload, 0, end - offset)
            // RED层需要按位取反：e-paper红色通道极性相反 (1=无红, 0=红)
            for (j in pagePayload.indices) pagePayload[j] = (pagePayload[j].toInt() xor 0xFF).toByte()
            BleConnectionManager.sendDataWithFragments(pagePayload, enableCompression = true, setColorBit = true, isRed = true)
        }
        Toast.makeText(requireContext(), "已发送红色层，共 $pages 页", Toast.LENGTH_SHORT).show()

        // 延迟一下，确保红色层已处理完
        Thread.sleep(500)

        // 第二步：发送黑色层（bit编码，需要在此手动取反）
        BleConnectionManager.sendDataWithFragments("RESET_PAGES".toByteArray())
        val blackLayerBytes = ImageProcessor.extractBlackLayer(currentBitmap!!)
        totalToSend = blackLayerBytes.size
        pages = (totalToSend + PAGE_SIZE - 1) / PAGE_SIZE

        for (p in 0 until pages) {
            val offset = p * PAGE_SIZE
            val end = (offset + PAGE_SIZE).coerceAtMost(totalToSend)
            val pagePayload = ByteArray(PAGE_SIZE)
            for (j in pagePayload.indices) pagePayload[j] = 0x00.toByte()
            System.arraycopy(blackLayerBytes, offset, pagePayload, 0, end - offset)
            // BW层需要按位取反：bit=1(黑)->0xFF, bit=0(白)->0x00
            for (j in pagePayload.indices) pagePayload[j] = (pagePayload[j].toInt() xor 0xFF).toByte()
            BleConnectionManager.sendDataWithFragments(pagePayload, enableCompression = true, setColorBit = false, isRed = false)
        }

        BleConnectionManager.sendDataWithFragments("DISPLAY".toByteArray())
        Toast.makeText(requireContext(), "已发送黑色层，共 $pages 页，红黑合成图像已完成", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == android.app.Activity.RESULT_OK) {
                val resultUri = extractCropResultUri(data)
                if (resultUri != null) {
                    imageUri = resultUri
                    loadImage()
                } else {
                    Toast.makeText(requireContext(), "无法获取裁剪结果", Toast.LENGTH_SHORT).show()
                }
            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                val err = try {
                    val parc = data?.getParcelableExtra<android.os.Parcelable>(CropImage.CROP_IMAGE_EXTRA_RESULT)
                    parc?.javaClass?.getMethod("getError")?.invoke(parc) as? Throwable
                } catch (e: Exception) {
                    null
                }
                Toast.makeText(requireContext(), "裁剪失败: ${err?.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                err?.printStackTrace()
            }
        }
    }

    private fun extractCropResultUri(data: android.content.Intent?): Uri? {
        if (data == null) return null

        try {
            @Suppress("DEPRECATION")
            val direct = data.getParcelableExtra<Uri>(CropImage.CROP_IMAGE_EXTRA_RESULT)
            if (direct != null) return direct
        } catch (_: Exception) {}

        try {
            val extra = data.extras?.get(CropImage.CROP_IMAGE_EXTRA_RESULT)
            if (extra is Uri) return extra
            if (extra is android.os.Bundle) {
                val u = extra.getParcelable<Uri>("uri") ?: extra.getParcelable("result") as? Uri
                if (u != null) return u
            }
        } catch (_: Exception) {}

        try {
            val parc = data.getParcelableExtra<android.os.Parcelable>(CropImage.CROP_IMAGE_EXTRA_RESULT)
            if (parc != null) {
                try {
                    val f = parc.javaClass.getDeclaredField("uri")
                    f.isAccessible = true
                    val v = f.get(parc) as? Uri
                    if (v != null) return v
                } catch (_: Exception) {}

                try {
                    val m = parc.javaClass.getMethod("getUri")
                    val v = m.invoke(parc) as? Uri
                    if (v != null) return v
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        return data.data
    }
    
    private fun loadImage() {
        try {
            // 使用ContentResolver打开输入流
            if (imageUri != null) {
                // 让 CropImageView 异步加载 Uri，用户可以直接拖动裁剪框
                imagePreview.setImageUriAsync(imageUri)
                // 同时也解码保留原始 Bitmap 以备后续处理
                val inputStream = requireContext().contentResolver.openInputStream(imageUri!!)
                originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
            } else {
                // 如果没有 Uri，显示原始 bitmap（若有）
                originalBitmap?.let { imagePreview.setImageBitmap(it) }
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.error_loading_image, Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }
    
    private fun showOriginalImage() {
        currentBitmap = originalBitmap
        // 如果存在静态预览（已确认裁剪），移除静态预览并恢复 CropImageView
        staticPreview?.let {
            imagePreviewContainer.removeView(it)
            staticPreview = null
            imagePreview.visibility = View.VISIBLE
        }
        currentBitmap?.let { imagePreview.setImageBitmap(it) }
    }
    
    private fun applyBinarize() {
        // 优先使用 CropImageView 的裁剪结果
        var sourceBmp: Bitmap? = null
        try {
            sourceBmp = imagePreview.getCroppedImage(TARGET_WIDTH, TARGET_HEIGHT)
        } catch (_: Exception) {}

        if (sourceBmp == null) sourceBmp = originalBitmap

        sourceBmp?.let {
            val scaled = Bitmap.createScaledBitmap(it, TARGET_WIDTH, TARGET_HEIGHT, true)
            currentBitmap = ImageProcessor.binarize(scaled, currentThreshold)
            if (staticPreview != null) {
                staticPreview?.setImageBitmap(currentBitmap)
            } else {
                imagePreview.setImageBitmap(currentBitmap)
            }
        }
    }
    
    private fun applyDetectRed() {
        var sourceBmp: Bitmap? = null
        try {
            sourceBmp = imagePreview.getCroppedImage(TARGET_WIDTH, TARGET_HEIGHT)
        } catch (_: Exception) {}

        if (sourceBmp == null) sourceBmp = originalBitmap

        sourceBmp?.let {
            val scaled = Bitmap.createScaledBitmap(it, TARGET_WIDTH, TARGET_HEIGHT, true)
            currentBitmap = ImageProcessor.detectRedAndBinarize(scaled, currentThreshold, currentRedThreshold)
            if (staticPreview != null) {
                staticPreview?.setImageBitmap(currentBitmap)
            } else {
                imagePreview.setImageBitmap(currentBitmap)
            }
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

    private fun rotateBitmap(src: Bitmap?, degrees: Int): Bitmap? {
        if (src == null) return null
        return try {
            val matrix = Matrix()
            matrix.postRotate(degrees.toFloat())
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 将二值化图像中的黑色部分染成深红色，白色保持不变
     * 用于预览红色显示效果
     */
    private fun colorizeImageToRed() {
        if (currentBitmap == null) return
        
        try {
            val width = currentBitmap!!.width
            val height = currentBitmap!!.height
            val colorizedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            val pixels = IntArray(width * height)
            currentBitmap!!.getPixels(pixels, 0, width, 0, 0, width, height)
            
            // 将黑色（Color.BLACK）转换为深红色，白色保持不变
            // 可以在这里调整红色深度：
            // Color.rgb(255, 0, 0) - 最亮的红色
            // Color.rgb(200, 0, 0) - 中等深度红色
            // Color.rgb(150, 0, 0) - 较深的红色
            // Color.rgb(100, 0, 0) - 最深的红色
            val darkRed = Color.rgb(180, 0, 0)  // 深红色
            
            for (i in pixels.indices) {
                pixels[i] = if (pixels[i] == Color.BLACK) {
                    darkRed  // 转换为深红
                } else {
                    Color.WHITE  // 保持白色
                }
            }
            
            colorizedBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            
            // 更新显示
            if (staticPreview != null) {
                staticPreview?.setImageBitmap(colorizedBitmap)
            } else {
                imagePreview.setImageBitmap(colorizedBitmap)
            }
        } catch (e: Exception) {
            Log.e("ImageEditFragment", "Error colorizing image to red: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 恢复显示原始的二值化图像（黑白版本）
     */
    private fun restoreOriginalBinary() {
        if (currentBitmap == null) return
        
        try {
            // 重新应用二值化以恢复黑白版本
            if (currentMode == EditMode.BINARIZE) {
                applyBinarize()
            } else if (currentMode == EditMode.DETECT_RED) {
                applyDetectRed()
            }
        } catch (e: Exception) {
            Log.e("ImageEditFragment", "Error restoring original binary image: ${e.message}")
            e.printStackTrace()
        }
    }
}