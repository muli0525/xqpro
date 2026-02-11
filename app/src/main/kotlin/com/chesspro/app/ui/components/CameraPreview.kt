package com.chesspro.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.chesspro.app.core.chess.*
import com.chesspro.app.core.recognition.ChessBoardRecognition
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 相机预览组件
 * 用于拍摄棋盘图像进行识别
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onImageCaptured: (Bitmap) -> Unit,
    isEnabled: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraExecutor by remember { mutableStateOf<ExecutorService?>(null) }
    
    DisposableEffect(isEnabled) {
        if (isEnabled) {
            cameraExecutor = Executors.newSingleThreadExecutor()
            
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                cameraProvider = cameraProviderFuture.get()
            }, ContextCompat.getMainExecutor(context))
        } else {
            cameraExecutor?.shutdown()
            cameraProvider?.unbindAll()
        }
        
        onDispose {
            cameraExecutor?.shutdown()
            cameraProvider?.unbindAll()
        }
    }
    
    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { preview ->
                    previewView = preview
                    preview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    
                    // 设置相机
                    cameraProvider?.let { provider ->
                        bindCamera(
                            provider = provider,
                            lifecycleOwner = lifecycleOwner,
                            previewView = preview,
                            imageAnalyzer = ChessBoardAnalyzer(ctx) { bitmap ->
                                onImageCaptured(bitmap)
                            }
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                cameraProvider?.let { provider ->
                    bindCamera(
                        provider = provider,
                        lifecycleOwner = lifecycleOwner,
                        previewView = view,
                        imageAnalyzer = ChessBoardAnalyzer(context) { bitmap ->
                            onImageCaptured(bitmap)
                        }
                    )
                }
            }
        )
        
        // 识别框提示
        if (isEnabled) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = "将棋盘放入框内，点击识别",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * 绑定相机
 */
private fun bindCamera(
    provider: ProcessCameraProvider,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    imageAnalyzer: ImageAnalysis
) {
    // 预览
    val preview = Preview.Builder()
        .build()
        .also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
    
    // 选择后置相机
    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    
    try {
        // 解除之前的绑定
        provider.unbindAll()
        
        // 绑定
        provider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageAnalyzer
        )
    } catch (e: Exception) {
        Log.e("CameraPreview", "相机绑定失败", e)
    }
}

/**
 * 棋盘图像分析器
 */
class ChessBoardAnalyzer(
    private val context: Context,
    private val onChessBoardDetected: (Bitmap) -> Unit
) : ImageAnalysis.Analyzer {
    
    private val analyzer = ChessBoardRecognition(context)
    
    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }
        
        try {
            // 转换图像
            val bitmap = imageProxyToBitmap(imageProxy)
            
            // 识别棋盘
            analyzer.analyzeBoard(bitmap) { result ->
                result?.let {
                    // 获取棋盘图像并回调
                    onChessBoardDetected(bitmap)
                }
            }
        } catch (e: Exception) {
            Log.e("ChessBoardAnalyzer", "分析失败", e)
        } finally {
            imageProxy.close()
        }
    }
    
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        
        // 旋转图像
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        return if (rotationDegrees != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }
}
