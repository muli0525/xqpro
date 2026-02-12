package com.chesspro.app.core.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ScreenCaptureService(private val context: Context) {
    companion object {
        const val TAG = "ScreenCapture"
        const val REQUEST_CODE = 1001

        @Volatile
        private var mediaProjection: MediaProjection? = null
        private var resultCode: Int = Activity.RESULT_CANCELED
        private var resultData: Intent? = null

        fun hasPermission(): Boolean = resultData != null && resultCode == Activity.RESULT_OK

        fun savePermission(code: Int, data: Intent?) {
            resultCode = code
            resultData = data
        }

        fun clearPermission() {
            mediaProjection?.stop()
            mediaProjection = null
            resultCode = Activity.RESULT_CANCELED
            resultData = null
        }
    }

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private fun getScreenMetrics(): DisplayMetrics {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun getOrCreateProjection(): MediaProjection? {
        if (mediaProjection != null) return mediaProjection
        val data = resultData ?: return null
        if (resultCode != Activity.RESULT_OK) return null

        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mgr.getMediaProjection(resultCode, data)
        return mediaProjection
    }

    suspend fun captureScreen(): Bitmap? = suspendCancellableCoroutine { cont ->
        try {
            val projection = getOrCreateProjection()
            if (projection == null) {
                Log.e(TAG, "No MediaProjection available")
                cont.resume(null)
                return@suspendCancellableCoroutine
            }

            val metrics = getScreenMetrics()
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

            virtualDisplay = projection.createVirtualDisplay(
                "ChessCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null, null
            )

            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                try {
                    val image = imageReader?.acquireLatestImage()
                    if (image != null) {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width

                        val bitmap = Bitmap.createBitmap(
                            width + rowPadding / pixelStride,
                            height,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.copyPixelsFromBuffer(buffer)
                        image.close()

                        // Crop to actual screen size
                        val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                        if (cropped != bitmap) bitmap.recycle()

                        cleanup()
                        if (cont.isActive) cont.resume(cropped)
                    } else {
                        cleanup()
                        if (cont.isActive) cont.resume(null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Capture error", e)
                    cleanup()
                    if (cont.isActive) cont.resume(null)
                }
            }, 150) // Small delay to let the virtual display render

            cont.invokeOnCancellation { cleanup() }
        } catch (e: Exception) {
            Log.e(TAG, "Setup error", e)
            cleanup()
            if (cont.isActive) cont.resume(null)
        }
    }

    private fun cleanup() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }
}
