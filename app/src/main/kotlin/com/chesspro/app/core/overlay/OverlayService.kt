package com.chesspro.app.core.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.chesspro.app.MainActivity
import com.chesspro.app.R
import com.chesspro.app.core.capture.BoardRecognizer
import com.chesspro.app.core.capture.ScreenCaptureService
import com.chesspro.app.core.engine.AnalysisResult
import com.chesspro.app.core.engine.EngineState
import com.chesspro.app.core.engine.FenConverter
import com.chesspro.app.core.engine.PikafishEngine
import com.chesspro.app.ui.theme.ChineseChessProTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 悬浮窗服务
 * 提供可移动、可调整大小的象棋分析悬浮窗
 * 连接Pikafish引擎进行实时分析
 */
class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        const val CHANNEL_ID = "chess_overlay_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_SHOW = "com.chesspro.app.ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE = "com.chesspro.app.ACTION_HIDE_OVERLAY"
        const val ACTION_ANALYZE = "com.chesspro.app.ACTION_ANALYZE"
        const val ACTION_STOP = "com.chesspro.app.ACTION_STOP"
        const val ACTION_UPDATE_FEN = "com.chesspro.app.ACTION_UPDATE_FEN"
        const val ACTION_CAPTURE = "com.chesspro.app.ACTION_CAPTURE"

        const val EXTRA_FEN = "extra_fen"

        // 默认尺寸
        const val DEFAULT_WIDTH = 300
        const val DEFAULT_HEIGHT = 380

        @Volatile
        private var instance: OverlayService? = null

        fun getInstance(): OverlayService? = instance
        fun isRunning(): Boolean = instance != null
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var engine: PikafishEngine? = null
    private var screenCapture: ScreenCaptureService? = null
    private val boardRecognizer = BoardRecognizer()

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 悬浮窗状态
    private val _overlayState = MutableStateFlow(OverlayState())
    val overlayState: StateFlow<OverlayState> = _overlayState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        // 获取引擎实例
        engine = PikafishEngine.getInstance(applicationContext)
        screenCapture = ScreenCaptureService(applicationContext)

        // 监听引擎分析结果
        serviceScope.launch {
            engine?.analysisResult?.collect { result ->
                handleAnalysisResult(result)
            }
        }

        // 监听引擎状态
        serviceScope.launch {
            engine?.engineState?.collect { state ->
                _overlayState.value = _overlayState.value.copy(
                    engineStatus = when (state) {
                        EngineState.RUNNING -> "引擎就绪"
                        EngineState.ANALYZING -> "分析中..."
                        EngineState.ERROR -> "引擎错误"
                        else -> "引擎: ${state.name}"
                    }
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                startForeground(NOTIFICATION_ID, createNotification())
                showOverlay()
            }
            ACTION_HIDE -> hideOverlay()
            ACTION_ANALYZE -> {
                val fen = intent.getStringExtra(EXTRA_FEN)
                if (fen != null) analyzePosition(fen)
            }
            ACTION_UPDATE_FEN -> {
                val fen = intent.getStringExtra(EXTRA_FEN)
                if (fen != null) {
                    _overlayState.value = _overlayState.value.copy(currentFen = fen)
                }
            }
            ACTION_CAPTURE -> captureAndAnalyze()
            ACTION_STOP -> {
                hideOverlay()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        hideOverlay()
        instance = null
        super.onDestroy()
    }

    /**
     * 显示悬浮窗
     */
    private fun showOverlay() {
        if (overlayView != null) return

        val params = WindowManager.LayoutParams().apply {
            val metrics = resources.displayMetrics
            x = metrics.widthPixels / 2 - dpToPx(DEFAULT_WIDTH) / 2
            y = 100

            width = dpToPx(DEFAULT_WIDTH)
            height = dpToPx(DEFAULT_HEIGHT)

            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

            gravity = Gravity.TOP or Gravity.START
        }
        layoutParams = params

        val lifecycleOwner = OverlayLifecycleOwner()
        lifecycleOwner.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_CREATE)
        lifecycleOwner.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)

        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                val state by _overlayState.collectAsState()
                ChineseChessProTheme {
                    OverlayContent(
                        state = state,
                        onClose = {
                            hideOverlay()
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        },
                        onAnalyze = { captureAndAnalyze() },
                        onMoveClick = { move -> applyMove(move) },
                        onResize = { w, h -> resizeOverlay(w, h) },
                        onDrag = { x, y -> moveOverlay(x, y) }
                    )
                }
            }
        }

        setupGestures(overlayView!!)

        try {
            windowManager?.addView(overlayView, params)
            _overlayState.value = _overlayState.value.copy(isVisible = true)
        } catch (e: Exception) {
            Log.e(TAG, "显示悬浮窗失败", e)
        }
    }

    /**
     * 隐藏悬浮窗
     */
    private fun hideOverlay() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "隐藏悬浮窗失败", e)
            }
            overlayView = null
        }
        _overlayState.value = _overlayState.value.copy(isVisible = false)
    }

    /**
     * 截图 -> 识别棋盘 -> 引擎分析 一体化流程
     */
    private fun captureAndAnalyze() {
        if (!ScreenCaptureService.hasPermission()) {
            _overlayState.value = _overlayState.value.copy(
                analysisStatus = "需要屏幕录制权限，请返回APP授权"
            )
            return
        }

        serviceScope.launch {
            _overlayState.value = _overlayState.value.copy(
                isAnalyzing = true,
                analysisStatus = "截屏中..."
            )

            // 先隐藏悬浮窗，避免截到自己
            val wasVisible = overlayView != null
            if (wasVisible) {
                overlayView?.let { it.visibility = View.INVISIBLE }
                delay(200)
            }

            try {
                val bitmap = screenCapture?.captureScreen()
                if (bitmap == null) {
                    _overlayState.value = _overlayState.value.copy(
                        isAnalyzing = false,
                        analysisStatus = "截屏失败"
                    )
                    return@launch
                }

                _overlayState.value = _overlayState.value.copy(analysisStatus = "识别中...")

                val result = withContext(Dispatchers.Default) {
                    boardRecognizer.recognize(bitmap)
                }
                bitmap.recycle()

                if (result == null || result.pieces.isEmpty()) {
                    _overlayState.value = _overlayState.value.copy(
                        isAnalyzing = false,
                        analysisStatus = "未识别到棋盘，请确保屏幕上有棋盘"
                    )
                    return@launch
                }

                _overlayState.value = _overlayState.value.copy(
                    analysisStatus = "识别到${result.pieces.size}个棋子，分析中...",
                    currentFen = result.fen
                )

                // 发送给引擎分析
                analyzePosition(result.fen)
            } catch (e: Exception) {
                Log.e(TAG, "截图分析失败", e)
                _overlayState.value = _overlayState.value.copy(
                    isAnalyzing = false,
                    analysisStatus = "识别失败: ${e.message}"
                )
            } finally {
                if (wasVisible) {
                    overlayView?.let { it.visibility = View.VISIBLE }
                }
            }
        }
    }

    /**
     * 使用Pikafish分析局面
     */
    private fun analyzePosition(fen: String) {
        _overlayState.value = _overlayState.value.copy(
            isAnalyzing = true,
            analysisStatus = "分析中...",
            currentFen = fen
        )
        engine?.analyze(fen)
    }

    /**
     * 处理引擎分析结果
     */
    private fun handleAnalysisResult(result: AnalysisResult) {
        if (result.bestMove != null && !result.isAnalyzing) {
            val bestUci = result.bestMove
            val positions = FenConverter.uciMoveToPositions(bestUci)

            val notation = if (positions != null) {
                val (from, to) = positions
                // 尝试从FEN获取棋子信息来生成中文记谱
                val fen = _overlayState.value.currentFen
                if (fen.isNotEmpty()) {
                    val (pieces, _) = FenConverter.fenToBoard(fen)
                    val piece = pieces.find { it.position == from }
                    if (piece != null) {
                        FenConverter.moveToChineseNotation(piece.type, piece.color, from, to)
                    } else bestUci
                } else bestUci
            } else bestUci

            val moves = mutableListOf(
                SuggestedMove(notation, result.score, bestUci)
            )

            // 添加PV中的后续走法
            if (result.pvMoves.size > 1) {
                result.pvMoves.drop(1).take(2).forEachIndexed { idx, uci ->
                    moves.add(SuggestedMove("后续: $uci", 0, uci))
                }
            }

            _overlayState.value = _overlayState.value.copy(
                isAnalyzing = false,
                analysisStatus = "分析完成 (d${result.depth})",
                bestMoves = moves,
                evaluation = result.scoreDisplay,
                lastMove = notation
            )
        } else if (result.isAnalyzing && result.depth > 0) {
            // 更新中间状态
            _overlayState.value = _overlayState.value.copy(
                analysisStatus = "分析中... d${result.depth}",
                evaluation = result.scoreDisplay
            )
        }
    }

    /**
     * 应用走法
     */
    private fun applyMove(move: SuggestedMove) {
        _overlayState.value = _overlayState.value.copy(
            lastMove = move.notation,
            analysisStatus = "已选择: ${move.notation}"
        )
    }

    /**
     * 手势处理
     */
    private fun setupGestures(view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams?.x ?: 0
                    initialY = layoutParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx * dx + dy * dy > 100) {
                        isDragging = true
                        moveOverlay(initialX + dx.toInt(), initialY + dy.toInt())
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // 单击 - 不做特殊处理，交给Compose
                        false
                    } else {
                        true
                    }
                }
                else -> false
            }
        }
    }

    /**
     * 移动悬浮窗
     */
    private fun moveOverlay(x: Int, y: Int) {
        layoutParams?.let { params ->
            params.x = x
            params.y = y
            try {
                windowManager?.updateViewLayout(overlayView, params)
            } catch (e: Exception) {
                Log.e(TAG, "移动悬浮窗失败", e)
            }
        }
    }

    /**
     * 调整悬浮窗大小
     */
    private fun resizeOverlay(width: Int, height: Int) {
        layoutParams?.let { params ->
            params.width = dpToPx(width)
            params.height = dpToPx(height)
            try {
                windowManager?.updateViewLayout(overlayView, params)
                _overlayState.value = _overlayState.value.copy(width = width, height = height)
            } catch (e: Exception) {
                Log.e(TAG, "调整悬浮窗大小失败", e)
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "象棋悬浮窗",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "象棋AI分析悬浮窗"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
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
            .setContentText("悬浮窗运行中 - Pikafish引擎")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "关闭", stopIntent)
            .setOngoing(true)
            .build()
    }
}

/**
 * 悬浮窗生命周期管理
 */
class OverlayLifecycleOwner :
    androidx.lifecycle.LifecycleOwner,
    androidx.savedstate.SavedStateRegistryOwner {

    private val lifecycleRegistry = androidx.lifecycle.LifecycleRegistry(this)
    private val savedStateRegistryController =
        androidx.savedstate.SavedStateRegistryController.create(this)

    override val lifecycle: androidx.lifecycle.Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: androidx.savedstate.SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun handleLifecycleEvent(event: androidx.lifecycle.Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }

    init {
        savedStateRegistryController.performRestore(null)
    }
}

/**
 * 悬浮窗状态
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
    val width: Int = OverlayService.DEFAULT_WIDTH,
    val height: Int = OverlayService.DEFAULT_HEIGHT
)

/**
 * 建议走法
 */
data class SuggestedMove(
    val notation: String,
    val score: Int,
    val uciMove: String = ""
)
