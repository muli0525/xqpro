package com.chesspro.app.core.engine

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*

/**
 * Pikafish引擎封装类
 * 通过UCI协议与Pikafish二进制文件通信
 */
class PikafishEngine(private val context: Context) {

    companion object {
        private const val TAG = "PikafishEngine"
        private const val ENGINE_ARMV8 = "pikafish-armv8"
        private const val ENGINE_DOTPROD = "pikafish-armv8-dotprod"
        private const val NNUE_FILE = "pikafish.nnue"
        private const val NNUE_DOWNLOAD_URL = "https://github.com/official-pikafish/Pikafish/releases/latest/download/pikafish.nnue"

        // 单例
        @Volatile
        private var instance: PikafishEngine? = null

        fun getInstance(context: Context): PikafishEngine {
            return instance ?: synchronized(this) {
                instance ?: PikafishEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    // 引擎进程
    private var process: Process? = null
    private var processWriter: BufferedWriter? = null
    private var processReader: BufferedReader? = null

    // 引擎状态
    private val _engineState = MutableStateFlow(EngineState.IDLE)
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    // 分析结果
    private val _analysisResult = MutableStateFlow(AnalysisResult())
    val analysisResult: StateFlow<AnalysisResult> = _analysisResult.asStateFlow()

    // 协程域
    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var readJob: Job? = null

    // 引擎配置
    private var searchDepth = 18
    private var searchTime = 5000L // 毫秒
    private var threads = 1
    private var hashSize = 64 // MB

    // 引擎文件路径
    private var enginePath: String = ""
    private var nnuePath: String = ""

    /**
     * 初始化引擎（提取二进制文件）
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            _engineState.value = EngineState.INITIALIZING

            // 提取引擎二进制文件
            val engineFile = extractEngine()
            if (engineFile == null) {
                Log.e(TAG, "引擎文件提取失败")
                _engineState.value = EngineState.ERROR
                return@withContext false
            }
            enginePath = engineFile.absolutePath

            // 提取NNUE文件（从assets复制到filesDir）
            val nnueFile = File(context.filesDir, NNUE_FILE)
            if (!nnueFile.exists()) {
                try {
                    context.assets.open(NNUE_FILE).use { input ->
                        FileOutputStream(nnueFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.i(TAG, "NNUE文件提取成功: ${nnueFile.absolutePath}")
                } catch (e: Exception) {
                    Log.w(TAG, "NNUE文件不存在于assets中，使用经典评估")
                }
            }
            if (nnueFile.exists()) {
                nnuePath = nnueFile.absolutePath
            }

            _engineState.value = EngineState.READY
            Log.i(TAG, "引擎初始化成功: $enginePath")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "引擎初始化失败", e)
            _engineState.value = EngineState.ERROR
            return@withContext false
        }
    }

    /**
     * 启动引擎进程
     */
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (enginePath.isEmpty()) {
            Log.e(TAG, "引擎路径为空，请先初始化")
            return@withContext false
        }

        try {
            // 停止之前的进程
            stop()

            _engineState.value = EngineState.STARTING

            // 启动进程
            val pb = ProcessBuilder(enginePath)
            pb.directory(context.filesDir)
            pb.redirectErrorStream(true)
            process = pb.start()

            processWriter = BufferedWriter(OutputStreamWriter(process!!.outputStream))
            processReader = BufferedReader(InputStreamReader(process!!.inputStream))

            // 启动读取线程
            startReadLoop()

            // 发送UCI初始化命令
            sendCommand("uci")
            delay(500)

            // 设置选项
            sendCommand("setoption name Threads value $threads")
            sendCommand("setoption name Hash value $hashSize")

            // 如果有NNUE文件，设置路径
            if (nnuePath.isNotEmpty()) {
                sendCommand("setoption name EvalFile value $nnuePath")
            } else {
                // 没有NNUE文件，使用经典评估
                sendCommand("setoption name Use NNUE value false")
            }

            sendCommand("isready")
            delay(500)

            _engineState.value = EngineState.RUNNING
            Log.i(TAG, "引擎启动成功")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "引擎启动失败", e)
            _engineState.value = EngineState.ERROR
            return@withContext false
        }
    }

    /**
     * 停止引擎
     */
    fun stop() {
        try {
            readJob?.cancel()
            readJob = null

            sendCommand("quit")

            processWriter?.close()
            processReader?.close()
            process?.destroyForcibly()

            processWriter = null
            processReader = null
            process = null

            _engineState.value = EngineState.IDLE
        } catch (e: Exception) {
            Log.e(TAG, "停止引擎异常", e)
        }
    }

    /**
     * 分析局面
     * @param fen FEN格式的棋局
     * @param depth 搜索深度（0表示使用默认值）
     * @param timeMs 搜索时间限制（毫秒，0表示使用深度限制）
     */
    fun analyze(fen: String, depth: Int = 0, timeMs: Long = 0) {
        if (_engineState.value != EngineState.RUNNING) {
            Log.w(TAG, "引擎未运行，当前状态: ${_engineState.value}")
            return
        }

        _engineState.value = EngineState.ANALYZING
        _analysisResult.value = AnalysisResult(isAnalyzing = true)

        // 设置局面
        sendCommand("position fen $fen")

        // 开始搜索
        val goCommand = buildString {
            append("go")
            if (depth > 0) {
                append(" depth $depth")
            } else if (timeMs > 0) {
                append(" movetime $timeMs")
            } else {
                append(" depth $searchDepth")
            }
        }
        sendCommand(goCommand)
    }

    /**
     * 分析局面并等待结果
     */
    suspend fun analyzeAndWait(
        fen: String,
        depth: Int = 0,
        timeMs: Long = 0,
        timeoutMs: Long = 30000
    ): AnalysisResult = withContext(Dispatchers.IO) {
        analyze(fen, depth, timeMs)

        // 等待分析完成
        val startTime = System.currentTimeMillis()
        while (_engineState.value == EngineState.ANALYZING) {
            delay(100)
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                stopAnalysis()
                break
            }
        }

        return@withContext _analysisResult.value
    }

    /**
     * 停止当前分析
     */
    fun stopAnalysis() {
        sendCommand("stop")
    }

    /**
     * 设置搜索深度
     */
    fun setSearchDepth(depth: Int) {
        searchDepth = depth.coerceIn(1, 30)
    }

    /**
     * 设置搜索时间（毫秒）
     */
    fun setSearchTime(timeMs: Long) {
        searchTime = timeMs.coerceIn(100, 60000)
    }

    /**
     * 设置线程数
     */
    fun setThreads(count: Int) {
        threads = count.coerceIn(1, 8)
        if (_engineState.value == EngineState.RUNNING) {
            sendCommand("setoption name Threads value $threads")
        }
    }

    /**
     * 设置哈希表大小（MB）
     */
    fun setHashSize(sizeMb: Int) {
        hashSize = sizeMb.coerceIn(16, 512)
        if (_engineState.value == EngineState.RUNNING) {
            sendCommand("setoption name Hash value $hashSize")
        }
    }

    /**
     * 发送命令到引擎
     */
    private fun sendCommand(command: String) {
        try {
            processWriter?.let { writer ->
                writer.write(command)
                writer.newLine()
                writer.flush()
                Log.d(TAG, ">>> $command")
            }
        } catch (e: Exception) {
            Log.e(TAG, "发送命令失败: $command", e)
        }
    }

    /**
     * 启动输出读取循环
     */
    private fun startReadLoop() {
        readJob = engineScope.launch {
            try {
                processReader?.let { reader ->
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        Log.d(TAG, "<<< $line")
                        parseLine(line)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "读取引擎输出异常", e)
                }
            }
        }
    }

    /**
     * 解析引擎输出行
     */
    private fun parseLine(line: String) {
        when {
            line.startsWith("bestmove") -> parseBestMove(line)
            line.startsWith("info") -> parseInfo(line)
            line == "uciok" -> Log.i(TAG, "UCI协议就绪")
            line == "readyok" -> Log.i(TAG, "引擎就绪")
        }
    }

    /**
     * 解析bestmove
     */
    private fun parseBestMove(line: String) {
        val parts = line.split(" ")
        val bestMove = if (parts.size > 1) parts[1] else null
        val ponderMove = if (parts.size > 3 && parts[2] == "ponder") parts[3] else null

        val current = _analysisResult.value
        _analysisResult.value = current.copy(
            bestMove = bestMove,
            ponderMove = ponderMove,
            isAnalyzing = false
        )

        if (_engineState.value == EngineState.ANALYZING) {
            _engineState.value = EngineState.RUNNING
        }

        Log.i(TAG, "最佳走法: $bestMove, 思考: $ponderMove")
    }

    /**
     * 解析info行
     */
    private fun parseInfo(line: String) {
        val parts = line.split(" ")
        var depth = 0
        var score = 0
        var scoreType = "cp"
        var nodes = 0L
        var nps = 0L
        var time = 0L
        var pvMoves = mutableListOf<String>()
        var multipv = 1

        var i = 1
        while (i < parts.size) {
            when (parts[i]) {
                "depth" -> {
                    depth = parts.getOrNull(i + 1)?.toIntOrNull() ?: 0
                    i += 2
                }
                "multipv" -> {
                    multipv = parts.getOrNull(i + 1)?.toIntOrNull() ?: 1
                    i += 2
                }
                "score" -> {
                    scoreType = parts.getOrNull(i + 1) ?: "cp"
                    score = parts.getOrNull(i + 2)?.toIntOrNull() ?: 0
                    i += 3
                }
                "nodes" -> {
                    nodes = parts.getOrNull(i + 1)?.toLongOrNull() ?: 0
                    i += 2
                }
                "nps" -> {
                    nps = parts.getOrNull(i + 1)?.toLongOrNull() ?: 0
                    i += 2
                }
                "time" -> {
                    time = parts.getOrNull(i + 1)?.toLongOrNull() ?: 0
                    i += 2
                }
                "pv" -> {
                    pvMoves = parts.subList(i + 1, parts.size).toMutableList()
                    i = parts.size
                }
                else -> i++
            }
        }

        // 更新分析结果
        if (pvMoves.isNotEmpty() || depth > 0) {
            val current = _analysisResult.value
            val scoreStr = when (scoreType) {
                "mate" -> if (score > 0) "杀棋 $score 步" else "被杀 ${-score} 步"
                else -> {
                    val cpScore = score / 100.0
                    if (cpScore >= 0) "+${String.format("%.2f", cpScore)}"
                    else String.format("%.2f", cpScore)
                }
            }

            _analysisResult.value = current.copy(
                depth = depth,
                score = score,
                scoreType = scoreType,
                scoreDisplay = scoreStr,
                nodes = nodes,
                nps = nps,
                timeMs = time,
                pvMoves = if (pvMoves.isNotEmpty()) pvMoves else current.pvMoves,
                isAnalyzing = true
            )
        }
    }

    /**
     * 提取引擎二进制文件到内部存储
     */
    private fun extractEngine(): File? {
        // 选择引擎变体
        val engineName = if (supportsDotProd()) ENGINE_DOTPROD else ENGINE_ARMV8
        val engineFile = File(context.filesDir, engineName)

        // 检查是否已提取
        if (engineFile.exists() && engineFile.canExecute()) {
            Log.i(TAG, "引擎文件已存在: ${engineFile.absolutePath}")
            return engineFile
        }

        return try {
            // 从assets中复制
            context.assets.open(engineName).use { input ->
                FileOutputStream(engineFile).use { output ->
                    input.copyTo(output)
                }
            }

            // 设置执行权限
            engineFile.setExecutable(true, true)
            engineFile.setReadable(true, true)

            Log.i(TAG, "引擎文件提取成功: ${engineFile.absolutePath}")
            engineFile
        } catch (e: Exception) {
            Log.e(TAG, "引擎文件提取失败", e)
            // 如果dotprod失败，尝试armv8
            if (engineName == ENGINE_DOTPROD) {
                try {
                    val fallbackFile = File(context.filesDir, ENGINE_ARMV8)
                    context.assets.open(ENGINE_ARMV8).use { input ->
                        FileOutputStream(fallbackFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    fallbackFile.setExecutable(true, true)
                    fallbackFile.setReadable(true, true)
                    Log.i(TAG, "使用armv8回退: ${fallbackFile.absolutePath}")
                    fallbackFile
                } catch (e2: Exception) {
                    Log.e(TAG, "armv8回退也失败", e2)
                    null
                }
            } else {
                null
            }
        }
    }

    /**
     * 检查设备是否支持dotprod指令集
     */
    private fun supportsDotProd(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cpuFeatures = File("/proc/cpuinfo").readText()
                cpuFeatures.contains("asimddp") || cpuFeatures.contains("dotprod")
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查NNUE文件是否存在
     */
    fun hasNnueFile(): Boolean {
        return File(context.filesDir, NNUE_FILE).exists()
    }

    /**
     * 导入NNUE文件
     */
    suspend fun importNnueFile(inputStream: InputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            val nnueFile = File(context.filesDir, NNUE_FILE)
            FileOutputStream(nnueFile).use { output ->
                inputStream.copyTo(output)
            }
            nnuePath = nnueFile.absolutePath
            Log.i(TAG, "NNUE文件导入成功")

            // 如果引擎正在运行，重新设置NNUE
            if (_engineState.value == EngineState.RUNNING) {
                sendCommand("setoption name EvalFile value $nnuePath")
                sendCommand("setoption name Use NNUE value true")
            }

            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "NNUE文件导入失败", e)
            return@withContext false
        }
    }

    /**
     * 释放资源
     */
    fun destroy() {
        stop()
        engineScope.cancel()
        instance = null
    }
}

/**
 * 引擎状态
 */
enum class EngineState {
    IDLE,           // 空闲
    INITIALIZING,   // 初始化中
    READY,          // 就绪（已提取，未启动）
    STARTING,       // 启动中
    RUNNING,        // 运行中
    ANALYZING,      // 分析中
    ERROR           // 错误
}

/**
 * 分析结果
 */
data class AnalysisResult(
    val bestMove: String? = null,       // 最佳走法（UCI格式 如 "h2e2"）
    val ponderMove: String? = null,     // 思考走法
    val depth: Int = 0,                 // 搜索深度
    val score: Int = 0,                 // 分数（厘兵值）
    val scoreType: String = "cp",       // 分数类型 "cp"=厘兵值 "mate"=杀棋
    val scoreDisplay: String = "0.00",  // 显示用分数
    val nodes: Long = 0,               // 搜索节点数
    val nps: Long = 0,                 // 每秒节点数
    val timeMs: Long = 0,              // 搜索时间
    val pvMoves: List<String> = emptyList(), // 主要变例
    val isAnalyzing: Boolean = false    // 是否正在分析
)
