package com.example.t4

import android.graphics.*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.FrameLayout
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.t4.ble.BleConnectionManager
import com.example.t4.ImageProcessor
import com.example.t4.databinding.FragmentTextToImageBinding

class TextToImageFragment : Fragment() {
    private var _binding: FragmentTextToImageBinding? = null
    private val binding get() = _binding!!
    private val WIDTH = 400
    private val HEIGHT = 300
    private var currentBitmap: Bitmap? = null
    private var lines = mutableListOf<String>()
    private var lineCount = 4

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentTextToImageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 初始化空白画布和默认行
        currentBitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        resetLines(lineCount)
        drawLinesToBitmap()
        // 预览使用 FIT_CENTER 展示放大后的 300x400 位图（位图像素不变，ImageView 缩放显示）
        binding.previewImage.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        binding.previewImage.setImageBitmap(currentBitmap)

        // 设置行数按钮
        binding.setLinesBtn.setOnClickListener {
            val n = binding.lineCountInput.text.toString().toIntOrNull() ?: 4
            lineCount = n.coerceIn(1, 20)
            resetLines(lineCount)
            drawLinesToBitmap()
            binding.previewImage.setImageBitmap(currentBitmap)
            Toast.makeText(requireContext(), "已设置 $lineCount 行", Toast.LENGTH_SHORT).show()
        }

        // 点击预览以编辑对应行文本（映射到位图坐标）
        binding.previewImage.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val (bx, by) = viewToBitmapCoords(binding.previewImage, event.x, event.y)
                if (bx >= 0 && by >= 0) {
                    val idx = ((by.toFloat() / HEIGHT) * lineCount).toInt().coerceIn(0, lineCount - 1)
                    // 弹出输入对话修改第 idx 行
                    val et = EditText(requireContext())
                    et.setText(lines[idx])
                    AlertDialog.Builder(requireContext())
                        .setTitle("编辑第 ${idx + 1} 行")
                        .setView(et)
                        .setPositiveButton("确定") { _, _ ->
                            lines[idx] = et.text.toString()
                            drawLinesToBitmap()
                            binding.previewImage.setImageBitmap(currentBitmap)
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
            true
        }

        // 发送当前位图
        binding.sendBtn.setOnClickListener {
            // 发送前检查蓝牙连接
            if (!BleConnectionManager.isConnected()) {
                Toast.makeText(requireContext(), "未连接蓝牙，请先连接设备", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val bmp = currentBitmap ?: return@setOnClickListener
            showSlotSelectionAndSend(bmp)
        }

        // 观察传输进度，更新顶部状态红条
        val statusText = binding.statusText
        BleConnectionManager.transferProgress.observe(viewLifecycleOwner) { progress ->
            if (!progress.isNullOrEmpty()) {
                statusText.text = progress
                statusText.visibility = View.VISIBLE
            } else {
                statusText.visibility = View.GONE
            }
        }
    }

    // 旧的行输入/生成逻辑已移除，使用直接在画布上编辑文本的方式

    private fun resetLines(n: Int) {
        lines.clear()
        for (i in 0 until n) lines.add("")
    }

    private fun drawLinesToBitmap() {
        val bmp = currentBitmap ?: return
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val paint = Paint()
        paint.color = Color.BLACK
        paint.strokeWidth = 2f
        val textPaint = Paint()
        textPaint.color = Color.BLACK
        textPaint.isAntiAlias = true
        // 使用粗体以提升水墨屏可读性
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.isFakeBoldText = false

        val h = bmp.height
        val w = bmp.width
        val lineHeight = h.toFloat() / lineCount
        // 使用较大的文字尺寸，按行高度比例设置，保证水墨屏上有更好可读性
        textPaint.textSize = lineHeight * 0.5f

        for (i in 0 until lineCount) {
            val yBase = ((i + 0.8f) * lineHeight)
            // 下划线位置在行底部
            val lineY = (i + 1) * lineHeight - 6f
            c.drawLine(8f, lineY, w - 8f, lineY, paint)
            // 绘制文本（居左）
            val text = lines.getOrNull(i) ?: ""
            if (text.isNotEmpty()) {
                c.drawText(text, 12f, yBase, textPaint)
            }
        }
    }

    private fun viewToBitmapCoords(imageView: android.widget.ImageView, x: Float, y: Float): Pair<Int, Int> {
        // 处理 ImageView 的 fitCenter 缩放映射到位图坐标
        val drawable = imageView.drawable ?: return Pair(-1, -1)
        val dw = drawable.intrinsicWidth
        val dh = drawable.intrinsicHeight
        val vw = imageView.width
        val vh = imageView.height
        val scale = Math.min(vw.toFloat() / dw, vh.toFloat() / dh)
        val scaledW = scale * dw
        val scaledH = scale * dh
        val left = (vw - scaledW) / 2f
        val top = (vh - scaledH) / 2f
        val bx = ((x - left) / scaledW) * WIDTH
        val by = ((y - top) / scaledH) * HEIGHT
        val ix = bx.toInt()
        val iy = by.toInt()
        return if (ix in 0 until WIDTH && iy in 0 until HEIGHT) Pair(ix, iy) else Pair(-1, -1)
    }

    private fun toBlackWhite(src: Bitmap): Bitmap {
        val bw = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bw)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        val f = ColorMatrixColorFilter(cm)
        paint.colorFilter = f
        canvas.drawBitmap(src, 0f, 0f, paint)
        return bw
    }

    private fun showSlotSelectionAndSend(bmp: Bitmap) {
        val items = Array(8) { i -> "槽位 ${i + 1}" }
        var choice = 0
        AlertDialog.Builder(requireContext())
            .setTitle("选择图片槽位")
            .setSingleChoiceItems(items, choice) { _, which -> choice = which }
            .setPositiveButton("发送") { _, _ ->
                val selectedSlot = choice + 1
                Toast.makeText(requireContext(), "已选槽位 $selectedSlot，开始发送...", Toast.LENGTH_SHORT).show()
                sendBitmapToMcu(bmp, selectedSlot)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun sendBitmapToMcu(bmp: Bitmap, selectedSlot: Int) {
        try {
            BleConnectionManager.sendDataWithFragments("SET_SLOT:$selectedSlot".toByteArray())
        } catch (_: Exception) {}

        BleConnectionManager.sendDataWithFragments("RESET_PAGES".toByteArray())
        val imageBytes = ImageProcessor.bitmapToMonochromeBytes(bmp)
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
            BleConnectionManager.sendDataWithFragments(pagePayload, enableCompression = true, setColorBit = false, isRed = false)
        }

        BleConnectionManager.sendDataWithFragments("DISPLAY".toByteArray())
        Toast.makeText(requireContext(), "已请求发送整张图像，共 $pages 页，槽位 $selectedSlot", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
