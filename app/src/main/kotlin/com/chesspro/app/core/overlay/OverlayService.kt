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
import android.graphics.Typeface
import android.os.IBinder
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.chesspro.app.MainActivity
import com.chesspro.app.R
import com.chesspro.app.core.capture.BoardRecognizer
import com.chesspro.app.core.capture.BoardRect
import com.chesspro.app.core.capture.RecognizedPiece
import com.chesspro.app.core.capture.ScreenCaptureService
import com.chesspro.app.core.engine.AnalysisResult
import com.chesspro.app.core.engine.EngineState
import com.chesspro.app.core.engine.FenConverter
import com.chesspro.app.core.engine.PikafishEngine
import kotlinx.coroutines.*

/**
 * 悬浮窗服务 - Pro象棋风格
 *
 * 悬浮窗结构：
 * ┌──────────────────────────────────┐
 * │ 🔗 Ⓐ ⚙ ✂  单步时长  识别中  📋 ✕ │  ← 工具栏（可拖动）
 * │ |17 (12) [558k] 兵三进一 炮8平5.. │  ← 分析文字（可滚动）
 * │ ┌──────────┐                      │
 * │ │ 迷你棋盘  │                      │  ← 左下迷你棋盘+箭头
 * │ │ + 箭头    │                ⤡     │  ← 右下缩放手柄
 * │ └──────────┘                      │
 * └──────────────────────────────────┘
 */
class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        const val CHANNEL_ID = "chess_overlay_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_SHOW = "com.chesspro.app.ACTION_SHOW_OVERLAY"
        const val ACTION_STOP = "com.chesspro.app.ACTION_STOP"

        const val AUTO_INTERVAL_MS = 3000L
        const val DEFAULT_WIN_W = 340
        const val DEFAULT_WIN_H = 260

        @Volatile
        private var instance: OverlayService? = null

        fun getInstance(): OverlayService? = instance
        fun isRunning(): Boolean = instance != null
    }

    private var windowManager: WindowManager? = null

    // Pro象棋风格悬浮窗
    private var floatingView: View? = null
    private var floatingParams: WindowManager.LayoutParams? = null
    private var miniBoardView: MiniBoardView? = null
    private var analysisText: TextView? = null
    private var statusText: TextView? = null

    // 透明箭头覆盖层（画在实际棋盘上）
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
    private var lastPieces: List<RecognizedPiece> = emptyList()

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
                screenCapture?.initialize()
                // 初始化并启动引擎
                serviceScope.launch {
                    val ok = engine?.initialize() ?: false
                    if (ok) {
                        engine?.start()
                        Log.i(TAG, "引擎启动完成")
                        statusText?.text = "就绪"
                    } else {
                        Log.e(TAG, "引擎初始化失败")
                        statusText?.text = "引擎缺失"
                    }
                }
                showFloatingWindow()
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

    // ====== Pro象棋风格悬浮窗 ======

    private fun showFloatingWindow() {
        if (floatingView != null) return

        val metrics = resources.displayMetrics
        val winW = dpToPx(DEFAULT_WIN_W)
        val winH = dpToPx(DEFAULT_WIN_H)

        floatingParams = WindowManager.LayoutParams().apply {
            width = winW
            height = winH
            x = 0
            y = metrics.heightPixels - winH - dpToPx(80)
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            gravity = Gravity.TOP or Gravity.START
        }

        val rootLayout = buildFloatingLayout()
        floatingView = rootLayout

        try {
            windowManager?.addView(rootLayout, floatingParams)
        } catch (e: Exception) {
            Log.e(TAG, "显示悬浮窗失败", e)
        }
    }

    /**
     * 构建Pro象棋风格悬浮窗布局
     */
    private fun buildFloatingLayout(): FrameLayout {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(240, 50, 50, 60))
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // === 顶部工具栏 ===
        val toolbar = buildToolbar()
        content.addView(toolbar)

        // === 分析文字行 ===
        val scrollView = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dpToPx(4), 0, dpToPx(4), 0) }
        }
        analysisText = TextView(this).apply {
            text = "等待识别..."
            setTextColor(Color.rgb(200, 200, 200))
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MARQUEE
            setPadding(dpToPx(8), dpToPx(2), dpToPx(8), dpToPx(2))
            setBackgroundColor(Color.argb(40, 255, 255, 255))
        }
        scrollView.addView(analysisText)
        content.addView(scrollView)

        // === 底部: 迷你棋盘 ===
        val miniBoard = MiniBoardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply { setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4)) }
        }
        miniBoardView = miniBoard
        content.addView(miniBoard)

        root.addView(content)

        // === 右下角缩放手柄 ===
        val resizeHandle = View(this).apply {
            setBackgroundColor(Color.argb(80, 255, 255, 255))
            layoutParams = FrameLayout.LayoutParams(dpToPx(20), dpToPx(20)).apply {
                gravity = Gravity.BOTTOM or Gravity.END
            }
        }
        setupResizeGesture(resizeHandle)
        root.addView(resizeHandle)

        // 拖动手势设在toolbar上
        return root
    }

    /**
     * 顶部工具栏 - 图标 + 状态 + 关闭
     */
    private fun buildToolbar(): LinearLayout {
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.argb(200, 40, 40, 50))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 链接图标（切换自动模式）
        val autoIcon = makeToolbarIcon(android.R.drawable.ic_menu_share) {
            toggleAutoMode()
        }
        toolbar.addView(autoIcon)

        // A 识别按钮
        val recognizeBtn = TextView(this).apply {
            text = "Ⓐ"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(dpToPx(6), 0, dpToPx(6), 0)
            setOnClickListener { onRecognizeClick() }
        }
        toolbar.addView(recognizeBtn)

        // 分隔
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        toolbar.addView(spacer)

        // 单步时长文字
        val stepLabel = TextView(this).apply {
            text = "单步时长"
            setTextColor(Color.rgb(230, 168, 23))
            textSize = 11f
            setPadding(dpToPx(4), 0, dpToPx(4), 0)
        }
        toolbar.addView(stepLabel)

        // 状态标签
        statusText = TextView(this).apply {
            text = "就绪"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(120, 38, 198, 176))
            textSize = 10f
            setPadding(dpToPx(6), dpToPx(1), dpToPx(6), dpToPx(1))
        }
        toolbar.addView(statusText)

        // 关闭按钮
        val closeBtn = makeToolbarIcon(android.R.drawable.ic_menu_close_clear_cancel) {
            val intent = Intent(this, OverlayService::class.java).apply { action = ACTION_STOP }
            startService(intent)
        }
        toolbar.addView(closeBtn)

        // 设置拖动
        setupDragGesture(toolbar)

        return toolbar
    }

    private fun makeToolbarIcon(resId: Int, onClick: () -> Unit): ImageView {
        return ImageView(this).apply {
            setImageResource(resId)
            setColorFilter(Color.WHITE)
            val pad = dpToPx(4)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(dpToPx(28), dpToPx(28))
            setOnClickListener { onClick() }
        }
    }

    // ====== 手势 ======

    private fun setupDragGesture(view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = floatingParams?.x ?: 0
                    initialY = floatingParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    floatingParams?.let { params ->
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        try {
                            windowManager?.updateViewLayout(floatingView, params)
                        } catch (_: Exception) {}
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupResizeGesture(view: View) {
        var initialW = 0
        var initialH = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialW = floatingParams?.width ?: 0
                    initialH = floatingParams?.height ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    floatingParams?.let { params ->
                        params.width = (initialW + (event.rawX - initialTouchX).toInt())
                            .coerceIn(dpToPx(200), dpToPx(500))
                        params.height = (initialH + (event.rawY - initialTouchY).toInt())
                            .coerceIn(dpToPx(150), dpToPx(500))
                        try {
                            windowManager?.updateViewLayout(floatingView, params)
                        } catch (_: Exception) {}
                    }
                    true
                }
                else -> false
            }
        }
    }

    // ====== 操作 ======

    private fun onRecognizeClick() {
        if (isAnalyzing) return
        captureAndAnalyze()
    }

    private fun toggleAutoMode() {
        if (isAutoMode) {
            stopAutoMode()
            statusText?.text = "已停止"
            statusText?.setBackgroundColor(Color.argb(120, 150, 150, 150))
        } else {
            startAutoMode()
            statusText?.text = "识别中"
            statusText?.setBackgroundColor(Color.argb(120, 38, 198, 176))
        }
    }

    // ====== 透明箭头覆盖层（画在实际棋盘上） ======

    private fun showArrowOverlay() {
        if (arrowOverlay != null) return

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

        arrowOverlay?.setArrow(
            PointF(fromX, fromY),
            PointF(toX, toY),
            minOf(cellW, cellH) * 0.35f
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
            statusText?.text = "无权限"
            return
        }
        if (isAnalyzing) return
        isAnalyzing = true
        statusText?.text = "识别中"
        statusText?.setBackgroundColor(Color.argb(120, 38, 198, 176))

        serviceScope.launch {
            // 只隐藏箭头overlay，不隐藏悬浮窗（避免闪烁）
            arrowOverlay?.visibility = View.INVISIBLE
            delay(100)

            try {
                val bitmap = screenCapture?.captureScreen()
                if (bitmap == null) {
                    analysisText?.text = "截屏失败"
                    statusText?.text = "失败"
                    isAnalyzing = false
                    return@launch
                }

                val result = withContext(Dispatchers.Default) {
                    boardRecognizer.recognize(bitmap)
                }
                bitmap.recycle()

                if (result == null || result.pieces.isEmpty()) {
                    if (!isAutoMode) {
                        analysisText?.text = "未识别到棋盘"
                        statusText?.text = "未识别"
                    }
                    isAnalyzing = false
                    return@launch
                }

                // 自动模式下避免重复分析
                if (isAutoMode && result.fen == lastFen) {
                    isAnalyzing = false
                    return@launch
                }

                lastFen = result.fen
                lastBoardRect = result.boardRect
                lastPieces = result.pieces

                // 更新迷你棋盘
                miniBoardView?.updateBoard(result.pieces, null)
                analysisText?.text = "识别${result.pieces.size}子，分析中..."

                arrowOverlay?.setArrow(null, null)

                // 检查引擎
                val engineOk = engine?.engineState?.value
                if (engineOk == null || engineOk == EngineState.ERROR || engineOk == EngineState.IDLE) {
                    analysisText?.text = "识别${result.pieces.size}子 | 引擎未就绪"
                    statusText?.text = "引擎缺失"
                    statusText?.setBackgroundColor(Color.argb(120, 200, 50, 50))
                    Log.w(TAG, "Engine not ready: $engineOk, FEN: ${result.fen}")
                    isAnalyzing = false
                    return@launch
                }

                statusText?.text = "分析中"
                analysisStartTime = System.currentTimeMillis()
                engine?.analyze(result.fen)

                // 超时保护
                serviceScope.launch {
                    delay(10000)
                    if (isAnalyzing) {
                        engine?.stopAnalysis()
                        statusText?.text = "超时"
                        isAnalyzing = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "截图分析失败", e)
                analysisText?.text = "错误: ${e.message}"
                isAnalyzing = false
            } finally {
                arrowOverlay?.visibility = View.VISIBLE
            }
        }
    }

    // 分析计时
    private var analysisStartTime = 0L

    /**
     * 处理引擎分析结果 - 即时显示中间结果
     */
    private fun handleAnalysisResult(result: AnalysisResult) {
        if (result.bestMove != null && !result.isAnalyzing) {
            // === 最终结果 ===
            currentBestMove = result.bestMove
            isAnalyzing = false

            val elapsed = System.currentTimeMillis() - analysisStartTime
            val notation = buildNotation(result.bestMove)
            val pvMoves = result.pvMoves.take(6).joinToString(" ") { buildNotation(it) }
            val nodesK = if (result.nodes > 0) "${result.nodes / 1000}k" else ""

            // 显示格式: |深度 (分数) [节点] 用时  最佳走法 后续走法...
            analysisText?.text = "|${result.depth} ${result.scoreDisplay} [$nodesK] ${elapsed}ms  $notation $pvMoves"

            statusText?.text = "✓ $notation"
            statusText?.setBackgroundColor(Color.argb(200, 76, 175, 80))

            // 在实际棋盘上画箭头
            lastBoardRect?.let { drawArrow(result.bestMove, it) }

            // 更新迷你棋盘箭头
            miniBoardView?.updateBoard(lastPieces, result.bestMove)

        } else if (result.isAnalyzing && result.depth > 0) {
            // === 中间结果 - 即时显示，不等最终结果 ===
            val pvMoves = result.pvMoves.take(4).joinToString(" ") { buildNotation(it) }
            val nodesK = if (result.nodes > 0) "${result.nodes / 1000}k" else ""

            analysisText?.text = "|${result.depth} ${result.scoreDisplay} [$nodesK] $pvMoves"
            statusText?.text = "d${result.depth}"

            // 深度>=8就开始画箭头（不等最终结果，秒出）
            if (result.depth >= 8 && result.pvMoves.isNotEmpty()) {
                val firstMove = result.pvMoves[0]
                if (firstMove.length >= 4 && firstMove != currentBestMove) {
                    currentBestMove = firstMove
                    lastBoardRect?.let { drawArrow(firstMove, it) }
                    miniBoardView?.updateBoard(lastPieces, firstMove)

                    val notation = buildNotation(firstMove)
                    statusText?.text = "d${result.depth} $notation"
                    statusText?.setBackgroundColor(Color.argb(160, 38, 198, 176))
                }
            }
        }
    }

    private fun buildNotation(uciMove: String): String {
        if (uciMove.length < 4) return uciMove
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
        floatingView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            floatingView = null
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
