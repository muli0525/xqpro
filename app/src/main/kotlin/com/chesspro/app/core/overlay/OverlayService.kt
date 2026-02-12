package com.chesspro.app.core.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PointF
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.chesspro.app.MainActivity
import com.chesspro.app.R
import com.chesspro.app.core.capture.BoardRecognizer
import com.chesspro.app.core.capture.BoardRect
import com.chesspro.app.core.capture.ScreenCaptureService
import com.chesspro.app.core.engine.AnalysisResult
import com.chesspro.app.core.engine.EngineState
import com.chesspro.app.core.engine.FenConverter
import com.chesspro.app.core.engine.PikafishEngine
import kotlinx.coroutines.*

/**
 * 悬浮窗服务 - 小按钮 + 箭头覆盖 + 自动识别模式
 *
 * 核心流程：
 * 1. 显示一个小圆形悬浮按钮（可拖动）
 * 2. 点击按钮 → 截屏 → 识别棋盘 → 引擎分析
 * 3. 在屏幕上画箭头显示最佳走法
 * 4. 自动模式：每隔几秒自动截屏检测变化
 */
class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        const val CHANNEL_ID = "chess_overlay_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_SHOW = "com.chesspro.app.ACTION_SHOW_OVERLAY"
        const val ACTION_STOP = "com.chesspro.app.ACTION_STOP"

        const val BUTTON_SIZE = 56 // dp
        const val AUTO_INTERVAL_MS = 3000L

        @Volatile
        private var instance: OverlayService? = null

        fun getInstance(): OverlayService? = instance
        fun isRunning(): Boolean = instance != null
    }

    private var windowManager: WindowManager? = null

    // 小悬浮按钮
    private var buttonView: View? = null
    private var buttonParams: WindowManager.LayoutParams? = null

    // 透明箭头覆盖层
    private var arrowOverlay: ArrowOverlayView? = null
    private var arrowParams: WindowManager.LayoutParams? = null

    // 引擎和截图
    private var engine: PikafishEngine? = null
    private var screenCapture: ScreenCaptureService? = null
    private val boardRecognizer = BoardRecognizer()

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 状态
    private var isAutoMode = false
    private var autoJob: Job? = null
    private var isAnalyzing = false
    private var lastFen = ""
    private var lastBoardRect: BoardRect? = null
    private var currentBestMove: String? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        engine = PikafishEngine.getInstance(applicationContext)
        screenCapture = ScreenCaptureService(applicationContext)

        // 监听引擎分析结果
        serviceScope.launch {
            engine?.analysisResult?.collect { result ->
                handleAnalysisResult(result)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                startForeground(NOTIFICATION_ID, createNotification())
                // 在前台服务启动后初始化截屏（Android 14要求）
                screenCapture?.initialize()
                // 初始化并启动引擎
                serviceScope.launch {
                    val ok = engine?.initialize() ?: false
                    if (ok) {
                        engine?.start()
                        Log.i(TAG, "引擎启动完成")
                    } else {
                        Log.e(TAG, "引擎初始化失败（可能缺少引擎文件）")
                    }
                }
                showButton()
                showArrowOverlay()
            }
            ACTION_STOP -> {
                stopAutoMode()
                hideAll()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopAutoMode()
        serviceScope.cancel()
        screenCapture?.release()
        hideAll()
        instance = null
        super.onDestroy()
    }

    // ====== 小悬浮按钮 ======

    private fun showButton() {
        if (buttonView != null) return

        val sizePx = dpToPx(BUTTON_SIZE)
        val metrics = resources.displayMetrics

        buttonParams = WindowManager.LayoutParams().apply {
            width = sizePx
            height = sizePx
            x = metrics.widthPixels - sizePx - dpToPx(16)
            y = metrics.heightPixels / 3
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            gravity = Gravity.TOP or Gravity.START
        }

        // 创建圆形按钮
        val button = FrameLayout(this).apply {
            val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.argb(230, 230, 168, 23)) // 金色
                setStroke(dpToPx(2), Color.WHITE)
            }
            background = bgDrawable

            // 图标
            val icon = ImageView(this@OverlayService).apply {
                setImageResource(android.R.drawable.ic_media_play)
                setColorFilter(Color.WHITE)
                val pad = dpToPx(14)
                setPadding(pad, pad, pad, pad)
            }
            addView(icon, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }

        setupButtonGestures(button)
        buttonView = button

        try {
            windowManager?.addView(button, buttonParams)
        } catch (e: Exception) {
            Log.e(TAG, "显示按钮失败", e)
        }
    }

    private fun setupButtonGestures(view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var downTime = 0L

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = buttonParams?.x ?: 0
                    initialY = buttonParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    downTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx * dx + dy * dy > 100) {
                        isDragging = true
                        buttonParams?.let { params ->
                            params.x = initialX + dx.toInt()
                            params.y = initialY + dy.toInt()
                            try {
                                windowManager?.updateViewLayout(buttonView, params)
                            } catch (_: Exception) {}
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - downTime
                    if (!isDragging) {
                        if (elapsed < 500) {
                            // 短按 = 单次识别
                            onButtonClick()
                        } else {
                            // 长按 = 切换自动模式
                            onButtonLongClick()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun onButtonClick() {
        if (isAnalyzing) return
        captureAndAnalyze()
    }

    private fun onButtonLongClick() {
        if (isAutoMode) {
            stopAutoMode()
            updateButtonColor(false)
            arrowOverlay?.setHint("自动模式已关闭")
            serviceScope.launch {
                delay(2000)
                arrowOverlay?.setHint(null)
            }
        } else {
            startAutoMode()
            updateButtonColor(true)
            arrowOverlay?.setHint("自动模式已开启")
        }
    }

    private fun updateButtonColor(auto: Boolean) {
        val bg = (buttonView as? FrameLayout)?.background as? android.graphics.drawable.GradientDrawable
        if (auto) {
            bg?.setColor(Color.argb(230, 76, 175, 80)) // 绿色=自动模式
        } else {
            bg?.setColor(Color.argb(230, 230, 168, 23)) // 金色=手动模式
        }
    }

    // ====== 透明箭头覆盖层 ======

    private fun showArrowOverlay() {
        if (arrowOverlay != null) return

        val metrics = resources.displayMetrics
        arrowParams = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            gravity = Gravity.TOP or Gravity.START
        }

        arrowOverlay = ArrowOverlayView(this)

        try {
            windowManager?.addView(arrowOverlay, arrowParams)
        } catch (e: Exception) {
            Log.e(TAG, "显示箭头覆盖层失败", e)
        }
    }

    /**
     * 在屏幕上画箭头
     */
    private fun drawArrow(uciMove: String, boardRect: BoardRect) {
        val positions = FenConverter.uciMoveToPositions(uciMove) ?: return

        val (from, to) = positions
        val boardW = boardRect.right - boardRect.left
        val boardH = boardRect.bottom - boardRect.top
        val cellW = boardW.toFloat() / 8f
        val cellH = boardH.toFloat() / 9f

        val fromX = boardRect.left + from.x * cellW
        val fromY = boardRect.top + from.y * cellH
        val toX = boardRect.left + to.x * cellW
        val toY = boardRect.top + to.y * cellH

        val radius = minOf(cellW, cellH) * 0.35f

        arrowOverlay?.setArrow(
            PointF(fromX, fromY),
            PointF(toX, toY),
            radius
        )
    }

    // ====== 自动模式 ======

    private fun startAutoMode() {
        isAutoMode = true
        autoJob = serviceScope.launch {
            while (isActive && isAutoMode) {
                if (!isAnalyzing) {
                    captureAndAnalyze()
                }
                delay(AUTO_INTERVAL_MS)
            }
        }
    }

    private fun stopAutoMode() {
        isAutoMode = false
        autoJob?.cancel()
        autoJob = null
    }

    // ====== 截图+识别+分析 ======

    private fun captureAndAnalyze() {
        if (!ScreenCaptureService.hasPermission()) {
            arrowOverlay?.setHint("需要屏幕录制权限")
            return
        }
        if (isAnalyzing) return
        isAnalyzing = true

        serviceScope.launch {
            // 隐藏按钮和箭头，避免截到自己
            buttonView?.visibility = View.INVISIBLE
            arrowOverlay?.visibility = View.INVISIBLE
            delay(150)

            try {
                val bitmap = screenCapture?.captureScreen()
                if (bitmap == null) {
                    arrowOverlay?.setHint("截屏失败")
                    isAnalyzing = false
                    return@launch
                }

                val result = withContext(Dispatchers.Default) {
                    boardRecognizer.recognize(bitmap)
                }
                bitmap.recycle()

                if (result == null || result.pieces.isEmpty()) {
                    if (!isAutoMode) {
                        arrowOverlay?.setHint("未识别到棋盘")
                        serviceScope.launch {
                            delay(2000)
                            arrowOverlay?.setHint(null)
                        }
                    }
                    isAnalyzing = false
                    return@launch
                }

                // 检查棋盘是否变化（自动模式下避免重复分析）
                if (isAutoMode && result.fen == lastFen) {
                    isAnalyzing = false
                    return@launch
                }

                lastFen = result.fen
                lastBoardRect = result.boardRect

                arrowOverlay?.setArrow(null, null)

                // 检查引擎状态
                val engineOk = engine?.engineState?.value
                if (engineOk == null || engineOk == EngineState.ERROR || engineOk == EngineState.IDLE) {
                    arrowOverlay?.setHint("引擎未就绪，识别${result.pieces.size}子 FEN已生成")
                    Log.w(TAG, "Engine not ready: $engineOk, FEN: ${result.fen}")
                    // 5秒后清除提示
                    serviceScope.launch {
                        delay(5000)
                        arrowOverlay?.setHint(null)
                    }
                    isAnalyzing = false
                    return@launch
                }

                arrowOverlay?.setHint("分析中...")

                // 发送给引擎分析（带超时）
                engine?.analyze(result.fen)

                // 超时保护 - 10秒没结果就放弃
                serviceScope.launch {
                    delay(10000)
                    if (isAnalyzing) {
                        Log.w(TAG, "分析超时，强制停止")
                        engine?.stopAnalysis()
                        arrowOverlay?.setHint("分析超时")
                        isAnalyzing = false
                        delay(2000)
                        arrowOverlay?.setHint(null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "截图分析失败", e)
                arrowOverlay?.setHint("识别失败: ${e.message}")
                isAnalyzing = false
            } finally {
                buttonView?.visibility = View.VISIBLE
                arrowOverlay?.visibility = View.VISIBLE
            }
        }
    }

    /**
     * 处理引擎分析结果 - 画箭头
     */
    private fun handleAnalysisResult(result: AnalysisResult) {
        if (result.bestMove != null && !result.isAnalyzing) {
            currentBestMove = result.bestMove
            isAnalyzing = false

            // 在棋盘上画箭头
            val boardRect = lastBoardRect
            if (boardRect != null) {
                drawArrow(result.bestMove, boardRect)
            }

            // 显示简短提示
            val notation = buildNotation(result.bestMove)
            arrowOverlay?.setHint("$notation  ${result.scoreDisplay}")

            // 几秒后隐藏文字提示（箭头保留）
            serviceScope.launch {
                delay(3000)
                arrowOverlay?.setHint(null)
            }
        } else if (result.isAnalyzing && result.depth > 0) {
            arrowOverlay?.setHint("d${result.depth} ${result.scoreDisplay}")
        }
    }

    private fun buildNotation(uciMove: String): String {
        val positions = FenConverter.uciMoveToPositions(uciMove) ?: return uciMove
        val (from, to) = positions
        if (lastFen.isNotEmpty()) {
            try {
                val (pieces, _) = FenConverter.fenToBoard(lastFen)
                val piece = pieces.find { it.position == from }
                if (piece != null) {
                    return FenConverter.moveToChineseNotation(piece.type, piece.color, from, to)
                }
            } catch (_: Exception) {}
        }
        return uciMove
    }

    // ====== 清理 ======

    private fun hideAll() {
        buttonView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            buttonView = null
        }
        arrowOverlay?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            arrowOverlay = null
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "象棋分析",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "象棋AI分析服务"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("象棋 Pro")
            .setContentText("Pikafish引擎运行中")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "关闭", stopIntent)
            .setOngoing(true)
            .build()
    }
}

/**
 * 建议走法
 */
data class SuggestedMove(
    val notation: String,
    val score: Int,
    val uciMove: String = ""
)

/**
 * 悬浮窗状态（保留兼容性）
 */
data class OverlayState(
    val isVisible: Boolean = false,
    val isAnalyzing: Boolean = false,
    val analysisStatus: String = "就绪",
    val engineStatus: String = "",
    val currentPlayer: String = "RED",
    val currentFen: String = "",
    val bestMoves: List<SuggestedMove> = emptyList(),
    val evaluation: String = "0.00",
    val lastMove: String? = null,
    val lastUpdateTime: Long = 0,
    val width: Int = 300,
    val height: Int = 380
)
