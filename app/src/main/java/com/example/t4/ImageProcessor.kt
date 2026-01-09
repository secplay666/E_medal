package com.example.t4

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.roundToInt

/**
 * 图像处理工具类，提供各种图像处理功能
 */
class ImageProcessor {
    companion object {
        /**
         * 对图像进行二值化处理
         * @param original 原始图像
         * @param threshold 二值化阈值 (0-255)
         * @return 二值化后的图像
         */
        fun binarize(original: Bitmap, threshold: Int): Bitmap {
            // 创建一个新的位图，与原始位图大小相同
            val width = original.width
            val height = original.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            // 创建像素数组
            val pixels = IntArray(width * height)
            val resultPixels = IntArray(width * height)
            
            // 一次性获取所有像素
            original.getPixels(pixels, 0, width, 0, 0, width, height)
            
            // 处理所有像素
            for (i in pixels.indices) {
                val pixel = pixels[i]
                
                // 计算灰度值 (0.299R + 0.587G + 0.114B)
                val grayValue = (Color.red(pixel) * 0.299 + 
                                Color.green(pixel) * 0.587 + 
                                Color.blue(pixel) * 0.114).roundToInt()
                
                // 应用阈值，大于阈值为白色，小于等于阈值为黑色
                resultPixels[i] = if (grayValue > threshold) {
                    Color.WHITE
                } else {
                    Color.BLACK
                }
            }
            
            // 一次性设置所有像素
            result.setPixels(resultPixels, 0, width, 0, 0, width, height)
            
            return result
        }
        
        /**
         * 使用自适应阈值进行二值化处理
         * @param original 原始图像
         * @param c 常数调整值
         * @return 二值化后的图像
         */
        fun adaptiveBinarize(original: Bitmap, c: Int = 2): Bitmap {
            // 这里简化实现，实际应用中可以使用更复杂的自适应算法
            // 先转换为灰度图
            val grayBitmap = toGrayscale(original)
            
            // 创建结果位图
            val width = grayBitmap.width
            val height = grayBitmap.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            // 应用全局阈值作为简化的自适应处理
            val threshold = calculateAverageGray(grayBitmap) - c
            
            // 创建像素数组
            val pixels = IntArray(width * height)
            val resultPixels = IntArray(width * height)
            
            // 一次性获取所有像素
            grayBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            // 处理所有像素
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val gray = Color.red(pixel) // 灰度图的RGB通道值相同
                
                resultPixels[i] = if (gray > threshold) {
                    Color.WHITE
                } else {
                    Color.BLACK
                }
            }
            
            // 一次性设置所有像素
            result.setPixels(resultPixels, 0, width, 0, 0, width, height)
            
            return result
        }
        
        /**
         * 将图像转换为灰度图
         * @param original 原始图像
         * @return 灰度图像
         */
        fun toGrayscale(original: Bitmap): Bitmap {
            val width = original.width
            val height = original.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            // 创建像素数组
            val pixels = IntArray(width * height)
            val resultPixels = IntArray(width * height)
            
            // 一次性获取所有像素
            original.getPixels(pixels, 0, width, 0, 0, width, height)
            
            // 处理所有像素
            for (i in pixels.indices) {
                val pixel = pixels[i]
                
                // 计算灰度值
                val grayValue = (Color.red(pixel) * 0.299 + 
                                Color.green(pixel) * 0.587 + 
                                Color.blue(pixel) * 0.114).roundToInt()
                
                // 创建灰度像素 (R=G=B)
                resultPixels[i] = Color.rgb(grayValue, grayValue, grayValue)
            }
            
            // 一次性设置所有像素
            result.setPixels(resultPixels, 0, width, 0, 0, width, height)
            
            return result
        }
        
        /**
         * 计算图像的平均灰度值
         * @param grayBitmap 灰度图像
         * @return 平均灰度值
         */
        private fun calculateAverageGray(grayBitmap: Bitmap): Int {
            val width = grayBitmap.width
            val height = grayBitmap.height
            val totalPixels = width * height
            
            // 创建像素数组
            val pixels = IntArray(totalPixels)
            
            // 一次性获取所有像素
            grayBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            // 计算总灰度值
            var totalGray = 0L
            for (pixel in pixels) {
                totalGray += Color.red(pixel) // 灰度图的RGB通道值相同
            }
            
            return (totalGray / totalPixels).toInt()
        }

        /**
         * 检测红色部分并进行二值化处理
         * 红色区域保留为纯红色，其他区域进行二值化
         * @param original 原始图像
         * @param threshold 二值化阈值 (0-255)
         * @return 处理后的图像
         */
        fun detectRedAndBinarize(original: Bitmap, threshold: Int, redThreshold: Int = 50): Bitmap {
            val width = original.width
            val height = original.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            // 创建像素数组
            val pixels = IntArray(width * height)
            val resultPixels = IntArray(width * height)
            
            // 一次性获取所有像素
            original.getPixels(pixels, 0, width, 0, 0, width, height)
            
            // 根据 redThreshold 计算红色检测范围
            // redThreshold 范围 0-100，越大越敏感（识别更多红色）
            val sensitivity = redThreshold  // 0-100
            val rMin = (100 - sensitivity * 0.5).toInt()
            val gMax = (100 + sensitivity * 0.5).toInt()
            val bMax = (100 + sensitivity * 0.5).toInt()
            
            // 处理所有像素
            for (i in pixels.indices) {
                val pixel = pixels[i]
                
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                // 检测红色：根据敏感度调整检测范围
                val isRed = r > rMin && g < gMax && b < bMax
                
                if (isRed) {
                    // 保持纯红色
                    resultPixels[i] = Color.rgb(255, 0, 0)
                } else {
                    // 对非红色区域进行二值化
                    val grayValue = (r * 0.299 + g * 0.587 + b * 0.114).roundToInt()
                    
                    resultPixels[i] = if (grayValue > threshold) {
                        Color.WHITE
                    } else {
                        Color.BLACK
                    }
                }
            }
            
            // 一次性设置所有像素
            result.setPixels(resultPixels, 0, width, 0, 0, width, height)
            
            return result
        }

        /**
         * 将二值化后的位图转换为每位表示一个像素的单色字节数组（每字节 8 像素，按行优先，最高位为左侧像素）
         */
        fun bitmapToMonochromeBytes(bitmap: Bitmap): ByteArray {
            val width = bitmap.width
            val height = bitmap.height
            val totalBits = width * height
            val totalBytes = (totalBits + 7) / 8
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val out = ByteArray(totalBytes)
            var bitIndex = 0

            for (i in pixels.indices) {
                val px = pixels[i]
                // 认为黑色为 1，白色为 0
                val bit = if (px == android.graphics.Color.BLACK) 1 else 0
                val byteIndex = bitIndex / 8
                val shift = 7 - (bitIndex % 8)
                out[byteIndex] = (out[byteIndex].toInt() or (bit shl shift)).toByte()
                bitIndex++
            }

            return out
        }

        /**
         * 从红黑白图像中提取红色层
         * 红色像素 -> 1，其他像素 -> 0
         */
        fun extractRedLayer(bitmap: Bitmap): ByteArray {
            val width = bitmap.width
            val height = bitmap.height
            val totalBits = width * height
            val totalBytes = (totalBits + 7) / 8
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val out = ByteArray(totalBytes)
            var bitIndex = 0

            for (i in pixels.indices) {
                val px = pixels[i]
                // 红色像素为 1，其他为 0
                val bit = if (px == android.graphics.Color.RED) 1 else 0
                val byteIndex = bitIndex / 8
                val shift = 7 - (bitIndex % 8)
                out[byteIndex] = (out[byteIndex].toInt() or (bit shl shift)).toByte()
                bitIndex++
            }

            return out
        }

        /**
         * 从红黑白图像中提取黑色层（黑白图）
         * 返回 bit 编码：黑色像素 -> 1，白色像素 -> 0，红色像素 -> 0
         * 这与 extractRedLayer 返回格式一致，都是 bit 编码
         * Android 和 MCU 约定负责处理 bit->byte 的转换和取反
         */
        fun extractBlackLayer(bitmap: Bitmap): ByteArray {
            val width = bitmap.width
            val height = bitmap.height
            val totalBits = width * height
            val totalBytes = (totalBits + 7) / 8
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val out = ByteArray(totalBytes)
            var bitIndex = 0

            for (i in pixels.indices) {
                val px = pixels[i]
                // 黑色像素为 1，红色和白色为 0
                val bit = if (px == android.graphics.Color.BLACK) 1 else 0
                val byteIndex = bitIndex / 8
                val shift = 7 - (bitIndex % 8)
                out[byteIndex] = (out[byteIndex].toInt() or (bit shl shift)).toByte()
                bitIndex++
            }

            return out
        }
    }
}
