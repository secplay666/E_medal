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
    private lateinit var btnConfirmCrop: android.widget.Button
    private var staticPreview: ImageView? = null
    
    private var originalBitmap: Bitmap? = null
    private var currentBitmap: Bitmap? = null
    private var imageUri: Uri? = null
    private var currentThreshold = 128
    private var currentMode = EditMode.ORIGINAL
    private val TARGET_WIDTH = 400
    private val TARGET_HEIGHT = 300
    
    private var cropConfirmed = false
    
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
        imagePreviewContainer = view.findViewById(R.id.imagePreviewContainer)
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
        
        // 设置阈值滑块监听器（实时应用二值化）
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
            } else {
                colorToggleBtn?.clearColorFilter()
                colorToggleBtn?.tag = "black"
            }
        }

        view.findViewById<ImageButton>(R.id.btnDownload).setOnClickListener {
            if (currentBitmap == null) {
                Toast.makeText(requireContext(), "当前无图片可发送", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val isRedSelected = colorToggleBtn?.tag == "red"

            // Reset pages on MCU to ensure we start from page 0
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

            Toast.makeText(requireContext(), "已请求发送整张图像，共 $pages 页，并触发 DISPLAY", Toast.LENGTH_SHORT).show()
        }
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
    
    private fun applyAdaptiveBinarize() {
        var sourceBmp: Bitmap? = null
        try {
            sourceBmp = imagePreview.getCroppedImage(TARGET_WIDTH, TARGET_HEIGHT)
        } catch (_: Exception) {}

        if (sourceBmp == null) sourceBmp = originalBitmap

        sourceBmp?.let {
            val scaled = Bitmap.createScaledBitmap(it, TARGET_WIDTH, TARGET_HEIGHT, true)
            currentBitmap = ImageProcessor.adaptiveBinarize(scaled)
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
}