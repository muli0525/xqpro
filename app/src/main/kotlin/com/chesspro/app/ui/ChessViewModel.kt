package com.chesspro.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chesspro.app.ChineseChessProApp
import com.chesspro.app.core.chess.*
import com.chesspro.app.core.engine.AnalysisResult
import com.chesspro.app.core.engine.EngineState
import com.chesspro.app.core.engine.FenConverter
import com.chesspro.app.core.engine.PikafishEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 象棋APP主ViewModel
 * 管理棋盘状态、Pikafish引擎分析、摆棋等
 */
class ChessViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ChessViewModel"
    }

    // 棋盘实例
    private val _chessBoard = ChessBoard()
    val chessBoard: ChessBoard get() = _chessBoard

    // Pikafish引擎
    private val engine: PikafishEngine = PikafishEngine.getInstance(application)

    // UI状态
    private val _uiState = MutableStateFlow(ChessUiState())
    val uiState: StateFlow<ChessUiState> = _uiState.asStateFlow()

    // 当前选中的位置
    private val _selectedPosition = MutableStateFlow<Position?>(null)
    val selectedPosition: StateFlow<Position?> = _selectedPosition.asStateFlow()

    // 建议走法
    private val _suggestedMove = MutableStateFlow<Move?>(null)
    val suggestedMove: StateFlow<Move?> = _suggestedMove.asStateFlow()

    // 当前模式
    private val _currentMode = MutableStateFlow(GameMode.BOARD_EDIT)
    val currentMode: StateFlow<GameMode> = _currentMode.asStateFlow()

    // 引擎分析结果
    private val _engineResult = MutableStateFlow(AnalysisResult())
    val engineResult: StateFlow<AnalysisResult> = _engineResult.asStateFlow()

    // 摆棋模式选中的棋子类型
    private val _editPieceType = MutableStateFlow<PieceType?>(null)
    val editPieceType: StateFlow<PieceType?> = _editPieceType.asStateFlow()

    // 摆棋模式选中的棋子颜色
    private val _editPieceColor = MutableStateFlow(PieceColor.RED)
    val editPieceColor: StateFlow<PieceColor> = _editPieceColor.asStateFlow()

    init {
        // 监听引擎分析结果
        viewModelScope.launch {
            engine.analysisResult.collect { result ->
                _engineResult.value = result
                if (!result.isAnalyzing && result.bestMove != null) {
                    handleEngineResult(result)
                }
            }
        }

        // 监听引擎状态
        viewModelScope.launch {
            engine.engineState.collect { state ->
                _uiState.value = _uiState.value.copy(
                    engineReady = state == EngineState.RUNNING,
                    engineStatus = when (state) {
                        EngineState.IDLE -> "未启动"
                        EngineState.INITIALIZING -> "初始化中..."
                        EngineState.READY -> "就绪"
                        EngineState.STARTING -> "启动中..."
                        EngineState.RUNNING -> "运行中"
                        EngineState.ANALYZING -> "分析中..."
                        EngineState.ERROR -> "错误"
                    }
                )
            }
        }
    }

    /**
     * 点击棋盘位置
     */
    fun onPositionClick(position: Position) {
        when (_currentMode.value) {
            GameMode.PLAY -> {
                handleBoardClick(position)
            }
            GameMode.BOARD_EDIT -> {
                handleEditClick(position)
            }
        }
    }

    /**
     * 处理棋盘点击（走棋模式）
     */
    private fun handleBoardClick(position: Position) {
        val piece = _chessBoard.getPieceAt(position)

        // 如果有选中的棋子，尝试移动
        _selectedPosition.value?.let { selected ->
            if (selected == position) {
                _selectedPosition.value = null
                return
            }

            val movingPiece = _chessBoard.getPieceAt(selected) ?: return
            val move = Move(from = selected, to = position, piece = movingPiece)

            if (_chessBoard.makeMove(move)) {
                _selectedPosition.value = null
                _suggestedMove.value = null
                updateUiState()

                // 走棋后自动分析新局面
                analyzeCurrentPosition()
            } else {
                if (piece != null && piece.color == _chessBoard.currentPlayer) {
                    _selectedPosition.value = position
                }
            }
        } ?: run {
            if (piece != null && piece.color == _chessBoard.currentPlayer) {
                _selectedPosition.value = position
            }
        }
    }

    /**
     * 处理摆棋模式点击
     */
    private fun handleEditClick(position: Position) {
        val existingPiece = _chessBoard.getPieceAt(position)

        if (existingPiece != null) {
            // 已有棋子则移除
            _chessBoard.removePiece(position)
        } else {
            // 添加选中类型的棋子
            val type = _editPieceType.value
            if (type != null) {
                val piece = ChessPiece(type, _editPieceColor.value, position)
                _chessBoard.addPiece(piece)
            } else {
                // 没有选中棋子类型，显示选择器
                _uiState.value = _uiState.value.copy(
                    showPiecePicker = true,
                    pickedPosition = position
                )
            }
        }
        updateUiState()
    }

    /**
     * 设置摆棋模式的棋子类型
     */
    fun setEditPieceType(type: PieceType?) {
        _editPieceType.value = type
    }

    /**
     * 设置摆棋模式的棋子颜色
     */
    fun setEditPieceColor(color: PieceColor) {
        _editPieceColor.value = color
    }

    /**
     * 处理拖拽走法
     */
    fun onPieceDrag(from: Position, to: Position) {
        val piece = _chessBoard.getPieceAt(from) ?: return

        if (_currentMode.value == GameMode.BOARD_EDIT) {
            // 摆棋模式：移动棋子位置
            _chessBoard.removePiece(from)
            _chessBoard.addPiece(piece.withPosition(to))
            updateUiState()
            return
        }

        if (piece.color != _chessBoard.currentPlayer) return

        val move = Move(from = from, to = to, piece = piece)
        if (_chessBoard.makeMove(move)) {
            _suggestedMove.value = null
            updateUiState()
            analyzeCurrentPosition()
        }
    }

    /**
     * 使用Pikafish分析当前局面
     */
    fun analyzeCurrentPosition() {
        viewModelScope.launch {
            if (engine.engineState.value != EngineState.RUNNING) {
                Log.w(TAG, "引擎未运行")
                return@launch
            }

            _uiState.value = _uiState.value.copy(isThinking = true)
            val fen = _chessBoard.toFen()
            Log.d(TAG, "分析FEN: $fen")
            engine.analyze(fen)
        }
    }

    /**
     * 处理引擎分析结果
     */
    private fun handleEngineResult(result: AnalysisResult) {
        val bestUci = result.bestMove ?: return

        val positions = FenConverter.uciMoveToPositions(bestUci) ?: return
        val (from, to) = positions

        val piece = _chessBoard.getPieceAt(from)
        if (piece != null) {
            val notation = FenConverter.moveToChineseNotation(
                piece.type, piece.color, from, to
            )

            _suggestedMove.value = Move(
                from = from,
                to = to,
                piece = piece,
                moveNotation = notation
            )

            _uiState.value = _uiState.value.copy(
                isThinking = false,
                lastAnalysis = notation,
                evaluation = result.scoreDisplay,
                analysisDepth = result.depth
            )
        } else {
            _uiState.value = _uiState.value.copy(isThinking = false)
        }
    }

    /**
     * 停止分析
     */
    fun stopAnalysis() {
        engine.stopAnalysis()
        _uiState.value = _uiState.value.copy(isThinking = false)
    }

    /**
     * 设置游戏模式
     */
    fun setMode(mode: GameMode) {
        _currentMode.value = mode
        _selectedPosition.value = null
        _suggestedMove.value = null

        when (mode) {
            GameMode.PLAY -> {
                // 不重置棋盘，保持当前摆好的局面
            }
            GameMode.BOARD_EDIT -> {
                // 进入摆棋模式
            }
        }

        updateUiState()
    }

    /**
     * 撤销上一步
     */
    fun undoMove() {
        if (_chessBoard.undoMove()) {
            _suggestedMove.value = null
            updateUiState()
        }
    }

    /**
     * 重新开始（恢复初始局面）
     */
    fun restart() {
        _chessBoard.reset()
        _selectedPosition.value = null
        _suggestedMove.value = null
        updateUiState()
    }

    /**
     * 清空棋盘
     */
    fun clearBoard() {
        _chessBoard.clearBoard()
        _selectedPosition.value = null
        _suggestedMove.value = null
        updateUiState()
    }

    /**
     * 切换执子方
     */
    fun toggleCurrentPlayer() {
        val newColor = _chessBoard.currentPlayer.other()
        _chessBoard.setCurrentPlayer(newColor)
        updateUiState()
    }

    /**
     * 添加棋子（摆棋模式）
     */
    fun addPiece(type: PieceType, color: PieceColor, position: Position) {
        val piece = ChessPiece(type, color, position)
        _chessBoard.addPiece(piece)
        _uiState.value = _uiState.value.copy(showPiecePicker = false)
        updateUiState()
    }

    /**
     * 从FEN加载局面
     */
    fun loadFen(fen: String) {
        try {
            _chessBoard.fromFen(fen)
            _selectedPosition.value = null
            _suggestedMove.value = null
            updateUiState()
        } catch (e: Exception) {
            Log.e(TAG, "加载FEN失败: $fen", e)
        }
    }

    /**
     * 获取当前FEN
     */
    fun getCurrentFen(): String {
        return _chessBoard.toFen()
    }

    /**
     * 设置引擎搜索深度
     */
    fun setEngineDepth(depth: Int) {
        engine.setSearchDepth(depth)
    }

    /**
     * 更新UI状态
     */
    private fun updateUiState() {
        _uiState.value = _uiState.value.copy(
            currentPlayer = _chessBoard.currentPlayer,
            gameState = _chessBoard.gameState,
            moveCount = _chessBoard.getMoveCount()
        )
    }
}

/**
 * 象棋APP UI状态
 */
data class ChessUiState(
    val currentPlayer: PieceColor = PieceColor.RED,
    val gameState: GameState = GameState.PLAYING,
    val moveCount: Int = 0,
    val isThinking: Boolean = false,
    val engineReady: Boolean = false,
    val engineStatus: String = "未启动",
    val showPiecePicker: Boolean = false,
    val pickedPosition: Position? = null,
    val lastAnalysis: String = "",
    val evaluation: String = "0.00",
    val analysisDepth: Int = 0
)

/**
 * 游戏模式
 */
enum class GameMode {
    BOARD_EDIT,   // 摆棋模式
    PLAY          // 走棋/分析模式
}
