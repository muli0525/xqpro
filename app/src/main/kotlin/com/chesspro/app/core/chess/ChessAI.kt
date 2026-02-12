package com.chesspro.app.core.chess

/**
 * 象棋局面评估器
 * 基于棋子价值和位置价值进行评估
 */
class ChessEvaluator {
    companion object {
        // 棋子基础价值
        private val PIECE_VALUES = mapOf(
            PieceType.JU to 1000,
            PieceType.MA to 400,
            PieceType.XIANG to 200,
            PieceType.SHI to 200,
            PieceType.JIANG to 10000, // 将/帅价值极高，被吃即失败
            PieceType.PAO to 450,
            PieceType.BING to 100
        )
    }
    
    /**
     * 评估局面分数（正数对红方有利，负数对黑方有利）
     */
    fun evaluate(board: ChessBoard): Int {
        var score = 0
        
        for (piece in board.pieces) {
            val value = PIECE_VALUES[piece.type] ?: 0
            val positionBonus = getPositionBonus(piece)
            
            if (piece.color == PieceColor.RED) {
                score += value + positionBonus
            } else {
                score -= value + positionBonus
            }
        }
        
        return score
    }
    
    /**
     * 获取位置价值分
     */
    private fun getPositionBonus(piece: ChessPiece): Int {
        val x = piece.position.x
        val y = piece.position.y
        
        return when (piece.type) {
            PieceType.JU -> getJuBonus(x, y, piece.color)
            PieceType.MA -> getMaBonus(x, y, piece.color)
            PieceType.XIANG -> getXiangBonus(x, y, piece.color)
            PieceType.SHI -> getShiBonus(x, y, piece.color)
            PieceType.JIANG -> getJiangBonus(x, y, piece.color)
            PieceType.PAO -> getPaoBonus(x, y, piece.color)
            PieceType.BING -> getBingBonus(x, y, piece.color)
        }
    }
    
    private fun getJuBonus(x: Int, y: Int, color: PieceColor): Int {
        // 车在中线更有价值
        return when (x) {
            3, 4, 5 -> 10
            else -> 0
        }
    }
    
    private fun getMaBonus(x: Int, y: Int, color: PieceColor): Int {
        // 马在中间位置更灵活
        return when {
            x in 2..6 && y in 3..6 -> 10
            else -> 0
        }
    }
    
    private fun getXiangBonus(x: Int, y: Int, color: PieceColor): Int {
        // 象应该在本方区域
        val isRed = color == PieceColor.RED
        return if ((isRed && y >= 5) || (!isRed && y <= 4)) 5 else -5
    }
    
    private fun getShiBonus(x: Int, y: Int, color: PieceColor): Int {
        // 士在九宫格中心更好
        return if (x == 4) 5 else 0
    }
    
    private fun getJiangBonus(x: Int, y: Int, color: PieceColor): Int {
        // 将在九宫格深处更安全
        val isRed = color == PieceColor.RED
        return if ((isRed && y == 9) || (!isRed && y == 0)) 5 else 0
    }
    
    private fun getPaoBonus(x: Int, y: Int, color: PieceColor): Int {
        // 炮在中间线更有攻击性
        return when (y) {
            2, 3, 6, 7 -> 5
            else -> 0
        }
    }
    
    private fun getBingBonus(x: Int, y: Int, color: PieceColor): Int {
        // 过河的兵更有价值
        val isRed = color == PieceColor.RED
        val riverLine = if (isRed) 5 else 4
        
        return when {
            isRed && y < riverLine -> 20 // 过河兵价值更高
            !isRed && y > riverLine -> 20
            y == riverLine - 1 || y == riverLine + 1 -> 5 // 临近河边的兵
            else -> 0
        }
    }
}

/**
 * 象棋AI引擎
 * 使用Alpha-Beta剪枝搜索
 */
class ChessAI(private val maxDepth: Int = 3) {
    private val evaluator = ChessEvaluator()
    
    /**
     * 找到最佳走法
     */
    fun findBestMove(board: ChessBoard): Move? {
        val moves = getAllValidMoves(board, board.currentPlayer)
        if (moves.isEmpty()) return null
        
        var bestMove: Move? = null
        var bestValue = if (board.currentPlayer == PieceColor.RED) Int.MIN_VALUE else Int.MAX_VALUE
        
        for (move in moves) {
            // 应用走法
            board.makeMove(move)
            
            // 搜索
            val value = alphaBeta(board, maxDepth - 1, Int.MIN_VALUE, Int.MAX_VALUE, false)
            
            // 撤销走法
            board.undoMove()
            
            if (board.currentPlayer == PieceColor.RED) {
                if (value > bestValue) {
                    bestValue = value
                    bestMove = move
                }
            } else {
                if (value < bestValue) {
                    bestValue = value
                    bestMove = move
                }
            }
        }
        
        return bestMove
    }
    
    /**
     * Alpha-Beta剪枝搜索
     */
    private fun alphaBeta(
        board: ChessBoard,
        depth: Int,
        alpha: Int,
        beta: Int,
        isMaximizingPlayer: Boolean
    ): Int {
        // 检查是否到达叶子节点
        if (depth == 0) {
            return evaluator.evaluate(board)
        }
        
        val color = if (isMaximizingPlayer) PieceColor.RED else PieceColor.BLACK
        val moves = getAllValidMoves(board, color)
        
        if (moves.isEmpty()) {
            // 无路可走，检查是否被将军
            return if (board.isInCheck(color)) {
                if (isMaximizingPlayer) Int.MIN_VALUE + 100000 else Int.MAX_VALUE - 100000
            } else {
                0 // 和棋
            }
        }
        
        return if (isMaximizingPlayer) {
            var alpha = alpha
            var value = Int.MIN_VALUE
            
            for (move in moves) {
                board.makeMove(move)
                value = maxOf(value, alphaBeta(board, depth - 1, alpha, beta, false))
                board.undoMove()
                
                alpha = maxOf(alpha, value)
                if (value >= beta) break // Beta剪枝
            }
            value
        } else {
            var beta = beta
            var value = Int.MAX_VALUE
            
            for (move in moves) {
                board.makeMove(move)
                value = minOf(value, alphaBeta(board, depth - 1, alpha, beta, true))
                board.undoMove()
                
                beta = minOf(beta, value)
                if (value <= alpha) break // Alpha剪枝
            }
            value
        }
    }
    
    /**
     * 获取指定方的所有合法走法
     */
    private fun getAllValidMoves(board: ChessBoard, color: PieceColor): List<Move> {
        return board.getValidMoves(color)
    }
}
