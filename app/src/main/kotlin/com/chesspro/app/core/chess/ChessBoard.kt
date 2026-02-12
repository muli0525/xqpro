package com.chesspro.app.core.chess

import com.chesspro.app.core.engine.FenConverter

/**
 * 象棋棋盘类
 * 管理棋局状态、棋子、走法历史
 */
class ChessBoard {
    // 棋盘上的棋子
    private val _pieces = mutableListOf<ChessPiece>()
    val pieces: List<ChessPiece> get() = _pieces.toList()

    // 当前执子方
    var currentPlayer: PieceColor = PieceColor.RED
        private set

    // 走法历史（用于撤销）
    private val moveHistory = mutableListOf<Move>()

    // 是否处于被将军状态
    private var isInCheck = false

    // 游戏状态
    var gameState: GameState = GameState.PLAYING
        private set

    // 九宫格边界
    private val redPalaceX = 3..5
    private val redPalaceY = 7..9
    private val blackPalaceX = 3..5
    private val blackPalaceY = 0..2

    // 河界
    private val riverLine = 4..5

    /**
     * 构造函数：初始化棋盘
     */
    init {
        setupInitialPosition()
    }

    /**
     * 设置初始棋局
     */
    private fun setupInitialPosition() {
        _pieces.clear()
        
        // 红方棋子
        _pieces.add(ChessPiece(PieceType.JU, PieceColor.RED, Position(0, 9)))
        _pieces.add(ChessPiece(PieceType.JU, PieceColor.RED, Position(8, 9)))
        _pieces.add(ChessPiece(PieceType.MA, PieceColor.RED, Position(1, 9)))
        _pieces.add(ChessPiece(PieceType.MA, PieceColor.RED, Position(7, 9)))
        _pieces.add(ChessPiece(PieceType.XIANG, PieceColor.RED, Position(2, 9)))
        _pieces.add(ChessPiece(PieceType.XIANG, PieceColor.RED, Position(6, 9)))
        _pieces.add(ChessPiece(PieceType.SHI, PieceColor.RED, Position(3, 9)))
        _pieces.add(ChessPiece(PieceType.SHI, PieceColor.RED, Position(5, 9)))
        _pieces.add(ChessPiece(PieceType.JIANG, PieceColor.RED, Position(4, 9)))
        _pieces.add(ChessPiece(PieceType.PAO, PieceColor.RED, Position(1, 7)))
        _pieces.add(ChessPiece(PieceType.PAO, PieceColor.RED, Position(7, 7)))
        _pieces.add(ChessPiece(PieceType.BING, PieceColor.RED, Position(0, 6)))
        _pieces.add(ChessPiece(PieceType.BING, PieceColor.RED, Position(2, 6)))
        _pieces.add(ChessPiece(PieceType.BING, PieceColor.RED, Position(4, 6)))
        _pieces.add(ChessPiece(PieceType.BING, PieceColor.RED, Position(6, 6)))
        _pieces.add(ChessPiece(PieceType.BING, PieceColor.RED, Position(8, 6)))

        // 黑方棋子
        _pieces.add(ChessPiece(PieceType.JU, PieceColor.BLACK, Position(0, 0)))
        _pieces.add(ChessPiece(PieceType.JU, PieceColor.BLACK, Position(8, 0)))
        _pieces.add(ChessPiece(PieceType.MA, PieceColor.BLACK, Position(1, 0)))
        _pieces.add(ChessPiece(PieceType.MA, PieceColor.BLACK, Position(7, 0)))
        _pieces.add(ChessPiece(PieceType.XIANG, PieceColor.BLACK, Position(2, 0)))
        _pieces.add(ChessPiece(PieceType.XIANG, PieceColor.BLACK, Position(6, 0)))
        _pieces.add(ChessPiece(PieceType.SHI, PieceColor.BLACK, Position(3, 0)))
        _pieces.add(ChessPiece(PieceType.SHI, PieceColor.BLACK, Position(5, 0)))
        _pieces.add(ChessPiece(PieceType.JIANG, PieceColor.BLACK, Position(4, 0)))
        _pieces.add(ChessPiece(PieceType.PAO, PieceColor.BLACK, Position(1, 3)))
        _pieces.add(ChessPiece(PieceType.PAO, PieceColor.BLACK, Position(7, 3)))
        _pieces.add(ChessPiece(PieceType.BING, PieceColor.BLACK, Position(0, 3)))
        _pieces.add(ChessPiece(PieceType.BING, PieceColor.BLACK, Position(2, 3)))
        _pieces.add(ChessPiece(PieceType.BING, PieceColor.BLACK, Position(4, 3)))
        _pieces.add(ChessPiece(PieceType.BING, PieceColor.BLACK, Position(6, 3)))
        _pieces.add(ChessPiece(PieceType.BING, PieceColor.BLACK, Position(8, 3)))
    }

    /**
     * 获取指定位置的棋子
     */
    fun getPieceAt(position: Position): ChessPiece? {
        return _pieces.find { it.position == position }
    }

    /**
     * 执行走法
     */
    fun makeMove(move: Move): Boolean {
        // 验证走法是否合法
        if (!validateMove(move)) {
            return false
        }

        val piece = getPieceAt(move.from) ?: return false
        val captured = getPieceAt(move.to)

        // 如果目标位置有己方棋子，不能走
        if (captured != null && captured.color == piece.color) {
            return false
        }

        // 记录被吃掉的棋子
        val capturedPiece = captured

        // 移动棋子
        val newPiece = piece.withPosition(move.to)
        _pieces.removeAll { it.position == move.from || it.position == move.to }
        _pieces.add(newPiece)

        // 检查是否导致将军
        val check = isInCheck(currentPlayer.other())

        // 切换执子方
        currentPlayer = currentPlayer.other()

        // 更新游戏状态
        updateGameState()

        // 记录到历史
        moveHistory.add(move.copy(
            capturedPiece = capturedPiece,
            isCheck = check,
            moveNotation = Move.createNotation(piece, move.from, move.to, captured != null, check)
        ))

        return true
    }

    /**
     * 撤销上一步走法
     */
    fun undoMove(): Boolean {
        if (moveHistory.isEmpty()) {
            return false
        }

        val lastMove = moveHistory.removeAt(moveHistory.lastIndex)

        // 恢复棋子位置
        val movedPiece = getPieceAt(lastMove.to) ?: return false
        val restoredPiece = movedPiece.withPosition(lastMove.from)
        _pieces.removeAll { it.position == lastMove.to }
        _pieces.add(restoredPiece)

        // 恢复被吃掉的棋子
        lastMove.capturedPiece?.let { captured ->
            _pieces.add(captured)
        }

        // 切换回原来的执子方
        currentPlayer = currentPlayer.other()

        // 重置游戏状态
        gameState = GameState.PLAYING
        isInCheck = false

        return true
    }

    /**
     * 验证走法是否合法
     */
    fun validateMove(move: Move): Boolean {
        val piece = getPieceAt(move.from) ?: return false
        
        // 只能移动己方棋子
        if (piece.color != currentPlayer) {
            return false
        }

        // 不能移动到已有己方棋子的位置
        val targetPiece = getPieceAt(move.to)
        if (targetPiece != null && targetPiece.color == piece.color) {
            return false
        }

        // 根据棋子类型验证走法
        return when (piece.type) {
            PieceType.JU -> validateJuMove(piece, move.to)
            PieceType.MA -> validateMaMove(piece, move.to)
            PieceType.XIANG -> validateXiangMove(piece, move.to)
            PieceType.SHI -> validateShiMove(piece, move.to)
            PieceType.JIANG -> validateJiangMove(piece, move.to)
            PieceType.PAO -> validatePaoMove(piece, move.to)
            PieceType.BING -> validateBingMove(piece, move.to)
        }
    }

    /**
     * 车的走法验证（直线）
     */
    private fun validateJuMove(piece: ChessPiece, to: Position): Boolean {
        val dx = to.x - piece.position.x
        val dy = to.y - piece.position.y

        // 必须直线移动
        if (dx != 0 && dy != 0) {
            return false
        }

        // 检查路径上是否有障碍
        val steps = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
        val stepX = if (dx == 0) 0 else dx / kotlin.math.abs(dx)
        val stepY = if (dy == 0) 0 else dy / kotlin.math.abs(dy)

        for (i in 1 until steps) {
            val checkPos = Position(piece.position.x + stepX * i, piece.position.y + stepY * i)
            if (getPieceAt(checkPos) != null) {
                return false
            }
        }

        return true
    }

    /**
     * 马的走法验证（马走日）
     */
    private fun validateMaMove(piece: ChessPiece, to: Position): Boolean {
        val dx = kotlin.math.abs(to.x - piece.position.x)
        val dy = kotlin.math.abs(to.y - piece.position.y)

        // 必须走日字格
        if (!((dx == 1 && dy == 2) || (dx == 2 && dy == 1))) {
            return false
        }

        // 检查蹩马腿
        val offsetX = if (dx == 2) (to.x - piece.position.x) / 2 else 0
        val offsetY = if (dy == 2) (to.y - piece.position.y) / 2 else 0
        
        val blockPos = Position(piece.position.x + offsetX, piece.position.y + offsetY)
        return getPieceAt(blockPos) == null
    }

    /**
     * 象的走法验证（象走田）
     */
    private fun validateXiangMove(piece: ChessPiece, to: Position): Boolean {
        val dx = kotlin.math.abs(to.x - piece.position.x)
        val dy = kotlin.math.abs(to.y - piece.position.y)

        // 必须走田字格
        if (dx != 2 || dy != 2) {
            return false
        }

        // 不能过河
        if (!to.isInPalace(piece.color)) {
            return false
        }

        // 检查象眼
        val eyePos = Position(
            piece.position.x + (to.x - piece.position.x) / 2,
            piece.position.y + (to.y - piece.position.y) / 2
        )
        return getPieceAt(eyePos) == null
    }

    /**
     * 士的走法验证
     */
    private fun validateShiMove(piece: ChessPiece, to: Position): Boolean {
        val dx = kotlin.math.abs(to.x - piece.position.x)
        val dy = kotlin.math.abs(to.y - piece.position.y)

        // 必须斜走一格
        if (dx != 1 || dy != 1) {
            return false
        }

        // 必须在九宫格内
        return to.isInPalace(piece.color)
    }

    /**
     * 将/帅的走法验证
     */
    private fun validateJiangMove(piece: ChessPiece, to: Position): Boolean {
        val dx = kotlin.math.abs(to.x - piece.position.x)
        val dy = kotlin.math.abs(to.y - piece.position.y)

        // 必须直线移动一格
        if ((dx != 1 || dy != 0) && (dx != 0 || dy != 1)) {
            return false
        }

        // 必须在九宫格内
        if (!to.isInPalace(piece.color)) {
            return false
        }

        // 检查是否形成"对面将"（将帅面对面）
        val opponentJiang = _pieces.find { 
            it.type == PieceType.JIANG && it.color != piece.color 
        }
        if (opponentJiang != null) {
            // 如果在同一列且中间没有棋子，则不能移动
            if (opponentJiang.position.x == to.x) {
                val dy = minOf(piece.position.y, opponentJiang.position.y) until 
                         maxOf(piece.position.y, opponentJiang.position.y)
                if (dy.all { y -> getPieceAt(Position(to.x, y)) == null }) {
                    return false
                }
            }
        }

        return true
    }

    /**
     * 炮的走法验证
     */
    private fun validatePaoMove(piece: ChessPiece, to: Position): Boolean {
        val dx = to.x - piece.position.x
        val dy = to.y - piece.position.y

        // 必须直线移动
        if (dx != 0 && dy != 0) {
            return false
        }

        val steps = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
        val stepX = if (dx == 0) 0 else dx / kotlin.math.abs(dx)
        val stepY = if (dy == 0) 0 else dy / kotlin.math.abs(dy)

        val targetPiece = getPieceAt(to)
        
        // 统计路径上的棋子数
        var obstacleCount = 0
        for (i in 1 until steps) {
            val checkPos = Position(
                piece.position.x + stepX * i,
                piece.position.y + stepY * i
            )
            if (getPieceAt(checkPos) != null) {
                obstacleCount++
            }
        }

        return when {
            // 不吃子时，路径必须畅通
            targetPiece == null -> obstacleCount == 0
            // 吃子时，必须有一个炮架
            targetPiece != null && targetPiece.color != piece.color -> obstacleCount == 1
            else -> false
        }
    }

    /**
     * 兵/卒的走法验证
     */
    private fun validateBingMove(piece: ChessPiece, to: Position): Boolean {
        val dx = kotlin.math.abs(to.x - piece.position.x)
        val dy = to.y - piece.position.y

        val isAcrossRiver = piece.position.isAcrossRiver(piece.color)
        val targetAcrossRiver = to.isAcrossRiver(piece.color)

        // 不能后退
        if ((piece.color == PieceColor.RED && dy > 0) ||
            (piece.color == PieceColor.BLACK && dy < 0)) {
            return false
        }

        // 过河前只能前进，过河后可以平移或前进
        return when {
            !isAcrossRiver -> {
                // 过河前只能向前走一格
                dx == 0 && kotlin.math.abs(dy) == 1
            }
            isAcrossRiver && !targetAcrossRiver -> {
                // 刚过河，只能向前
                dx == 0 && kotlin.math.abs(dy) == 1
            }
            else -> {
                // 过河后可以向前或平移一格
                (dx == 1 && dy == 0) || (dx == 0 && kotlin.math.abs(dy) == 1)
            }
        }
    }

    /**
     * 检查是否被将军
     */
    fun isInCheck(color: PieceColor): Boolean {
        val king = _pieces.find { it.type == PieceType.JIANG && it.color == color } ?: return false
        
        // 检查对方是否能吃掉将/帅
        val opponentPieces = _pieces.filter { it.color != color }
        return opponentPieces.any { piece ->
            val move = Move(piece.position, king.position, piece)
            validateMove(move)
        }
    }

    /**
     * 检查是否有合法走法
     */
    fun hasValidMoves(color: PieceColor): Boolean {
        val playerPieces = _pieces.filter { it.color == color }
        
        for (piece in playerPieces) {
            for (y in 0..9) {
                for (x in 0..8) {
                    val to = Position(x, y)
                    val move = Move(piece.position, to, piece)
                    if (validateMove(move)) {
                        return true
                    }
                }
            }
        }
        
        return false
    }

    /**
     * 更新游戏状态
     */
    private fun updateGameState() {
        // 检查是否被将军
        isInCheck = isInCheck(currentPlayer)
        
        // 检查是否无路可走
        if (!hasValidMoves(currentPlayer)) {
            gameState = if (isInCheck) {
                if (currentPlayer == PieceColor.RED) GameState.BLACK_WINS else GameState.RED_WINS
            } else {
                GameState.DRAW
            }
        }
    }

    /**
     * 获取所有合法走法
     */
    fun getValidMoves(color: PieceColor): List<Move> {
        val moves = mutableListOf<Move>()
        val playerPieces = _pieces.filter { it.color == color }
        
        for (piece in playerPieces) {
            for (y in 0..9) {
                for (x in 0..8) {
                    val to = Position(x, y)
                    val move = Move(piece.position, to, piece)
                    if (validateMove(move)) {
                        moves.add(move)
                    }
                }
            }
        }
        
        return moves
    }

    /**
     * 重置棋盘
     */
    fun reset() {
        _pieces.clear()
        moveHistory.clear()
        currentPlayer = PieceColor.RED
        gameState = GameState.PLAYING
        isInCheck = false
        setupInitialPosition()
    }

    /**
     * 设置自定义局面
     */
    fun setPosition(newPieces: List<ChessPiece>, player: PieceColor = PieceColor.RED) {
        _pieces.clear()
        _pieces.addAll(newPieces)
        currentPlayer = player
        moveHistory.clear()
        gameState = GameState.PLAYING
        isInCheck = isInCheck(player)
    }

    /**
     * 获取历史走法数量
     */
    fun getMoveCount(): Int = moveHistory.size

    /**
     * 获取最后一步走法
     */
    fun getLastMove(): Move? = moveHistory.lastOrNull()

    /**
     * 添加棋子（摆棋模式）
     */
    fun addPiece(piece: ChessPiece) {
        _pieces.removeAll { it.position == piece.position }
        _pieces.add(piece)
    }

    /**
     * 移除棋子（摆棋模式）
     */
    fun removePiece(position: Position): ChessPiece? {
        val piece = _pieces.find { it.position == position }
        if (piece != null) {
            _pieces.remove(piece)
        }
        return piece
    }

    /**
     * 清空棋盘
     */
    fun clearBoard() {
        _pieces.clear()
        moveHistory.clear()
        currentPlayer = PieceColor.RED
        gameState = GameState.PLAYING
        isInCheck = false
    }

    /**
     * 将当前棋盘转换为FEN字符串
     */
    fun toFen(): String {
        return FenConverter.boardToFen(_pieces, currentPlayer)
    }

    /**
     * 从FEN字符串加载棋盘
     */
    fun fromFen(fen: String) {
        val (pieces, player) = FenConverter.fenToBoard(fen)
        setPosition(pieces, player)
    }

    /**
     * 设置当前执子方
     */
    fun setCurrentPlayer(color: PieceColor) {
        currentPlayer = color
    }
}

/**
 * 游戏状态枚举
 */
enum class GameState {
    PLAYING,        // 进行中
    RED_WINS,       // 红方胜
    BLACK_WINS,     // 黑方胜
    DRAW            // 和棋
}

