package com.chesspro.app.core.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * 屏幕截图服务 - 保持MediaProjection和VirtualDisplay常驻
 *
 * 关键：Android 10+的MediaProjection intent只能用一次，
 * 所以必须创建一次后保持存活，不能每次截图都重建。
 */
class ScreenCaptureService(private val context: Context) {
    companion object {
        const val TAG = "ScreenCapture"

        @Volatile
        private var resultCode: Int = Activity.RESULT_CANCELED
        @Volatile
        private var resultData: Intent? = null

        fun hasPermission(): Boolean = resultData != null && resultCode == Activity.RESULT_OK

        fun savePermission(code: Int, data: Intent?) {
            resultCode = code
            // 必须clone intent，否则原始intent可能被回收
            resultData = data?.clone() as? Intent
            Log.d(TAG, "Permission saved: code=$code, data=${data != null}")
        }

        fun clearPermission() {
            resultCode = Activity.RESULT_CANCELED
            resultData = null
        }

        fun getResultCode() = resultCode
        fun getResultData() = resultData
    }

    // 常驻组件 - 创建后不销毁，直到服务停止
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var isInitialized = false

    // 最新截图缓存
    @Volatile
    private var latestImage: Image? = null

    /**
     * 初始化 - 创建MediaProjection和VirtualDisplay（只调用一次）
     */
    fun initialize(): Boolean {
        if (isInitialized) return true

        val data = resultData
        if (data == null || resultCode != Activity.RESULT_OK) {
            Log.e(TAG, "No permission data available")
            return false
        }

        try {
            // 获取屏幕参数
            val metrics = getScreenMetrics()
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            val density = metrics.densityDpi

            Log.d(TAG, "Screen: ${screenWidth}x${screenHeight}, density=$density")

            // 创建后台线程
            handlerThread = HandlerThread("ScreenCapture").apply { start() }
            handler = Handler(handlerThread!!.looper)

            // 创建MediaProjection（只能创建一次！）
            val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mgr.getMediaProjection(resultCode, data)

            if (mediaProjection == null) {
                Log.e(TAG, "Failed to create MediaProjection")
                return false
            }

            // 注册停止回调
            mediaProjection!!.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection stopped")
                    release()
                }
            }, handler)

            // 创建ImageReader
            imageReader = ImageReader.newInstance(
                screenWidth, screenHeight, PixelFormat.RGBA_8888, 2
            )

            // 设置图片可用监听 - 持续接收最新帧
            imageReader!!.setOnImageAvailableListener({ reader ->
                val img = reader.acquireLatestImage()
                if (img != null) {
                    latestImage?.close()
                    latestImage = img
                }
            }, handler)

            // 创建VirtualDisplay（常驻）
            virtualDisplay = mediaProjection!!.createVirtualDisplay(
                "ChessCapture",
                screenWidth, screenHeight, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null, handler
            )

            isInitialized = true
            Log.d(TAG, "ScreenCapture initialized successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Initialize failed", e)
            release()
            return false
        }
    }

    /**
     * 截取当前屏幕 - 直接从缓存的最新帧获取
     */
    suspend fun captureScreen(): Bitmap? {
        if (!isInitialized && !initialize()) {
            Log.e(TAG, "Not initialized and initialize failed")
            return null
        }

        return try {
            // 等待一帧到达（最多500ms）
            withTimeout(500) {
                suspendCancellableCoroutine { cont ->
                    val h = handler ?: Handler(android.os.Looper.getMainLooper())
                    // 等待150ms让VirtualDisplay渲染新帧
                    h.postDelayed({
                        val bitmap = grabLatestFrame()
                        if (cont.isActive) cont.resume(bitmap)
                    }, 200)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Capture timeout or error", e)
            null
        }
    }

    /**
     * 从ImageReader获取最新帧并转为Bitmap
     */
    private fun grabLatestFrame(): Bitmap? {
        try {
            // 先尝试从缓存获取
            val img = latestImage ?: imageReader?.acquireLatestImage()
            if (img == null) {
                Log.w(TAG, "No image available")
                return null
            }

            val planes = img.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmapWidth = screenWidth + rowPadding / pixelStride
            val bitmap = Bitmap.createBitmap(bitmapWidth, screenHeight, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)

            img.close()
            latestImage = null

            // 裁剪到实际屏幕大小
            return if (bitmapWidth != screenWidth) {
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                bitmap.recycle()
                cropped
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "grabLatestFrame error", e)
            return null
        }
    }

    private fun getScreenMetrics(): DisplayMetrics {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    /**
     * 释放所有资源
     */
    fun release() {
        isInitialized = false
        latestImage?.close()
        latestImage = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
        Log.d(TAG, "ScreenCapture released")
    }
}
