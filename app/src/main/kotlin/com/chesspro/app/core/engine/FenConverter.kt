package com.chesspro.app.core.engine

import com.chesspro.app.core.chess.*

/**
 * FEN格式转换器
 * 将棋盘状态与FEN字符串互相转换
 * Pikafish使用标准UCI协议，需要FEN格式的棋局描述
 */
object FenConverter {

    // 初始局面FEN
    const val INITIAL_FEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"

    /**
     * 将棋盘状态转换为FEN字符串
     * FEN格式：棋子位置 执子方 - - 半回合数 总回合数
     */
    fun boardToFen(pieces: List<ChessPiece>, currentPlayer: PieceColor): String {
        val board = Array(10) { CharArray(9) { '0' } }

        // 将棋子放到二维数组中
        for (piece in pieces) {
            val x = piece.position.x
            val y = piece.position.y
            if (x in 0..8 && y in 0..9) {
                board[y][x] = pieceToFenChar(piece.type, piece.color)
            }
        }

        // 生成FEN棋子部分
        val fenRows = mutableListOf<String>()
        for (row in 0..9) {
            val sb = StringBuilder()
            var emptyCount = 0
            for (col in 0..8) {
                if (board[row][col] == '0') {
                    emptyCount++
                } else {
                    if (emptyCount > 0) {
                        sb.append(emptyCount)
                        emptyCount = 0
                    }
                    sb.append(board[row][col])
                }
            }
            if (emptyCount > 0) {
                sb.append(emptyCount)
            }
            fenRows.add(sb.toString())
        }

        val piecePart = fenRows.joinToString("/")
        val playerPart = if (currentPlayer == PieceColor.RED) "w" else "b"

        return "$piecePart $playerPart - - 0 1"
    }

    /**
     * 从FEN字符串解析棋盘状态
     */
    fun fenToBoard(fen: String): Pair<List<ChessPiece>, PieceColor> {
        val parts = fen.trim().split(" ")
        val piecePart = parts[0]
        val playerPart = if (parts.size > 1) parts[1] else "w"

        val pieces = mutableListOf<ChessPiece>()
        val rows = piecePart.split("/")

        for ((rowIdx, row) in rows.withIndex()) {
            var colIdx = 0
            for (ch in row) {
                if (ch.isDigit()) {
                    colIdx += ch.digitToInt()
                } else {
                    val pieceInfo = fenCharToPiece(ch)
                    if (pieceInfo != null) {
                        val (type, color) = pieceInfo
                        try {
                            pieces.add(ChessPiece(type, color, Position(colIdx, rowIdx)))
                        } catch (_: Exception) {
                            // 忽略无效位置
                        }
                    }
                    colIdx++
                }
            }
        }

        val currentPlayer = if (playerPart == "b") PieceColor.BLACK else PieceColor.RED
        return Pair(pieces, currentPlayer)
    }

    /**
     * 将UCI走法字符串转换为起止位置
     * UCI走法格式: "a0b0" (列行列行, 0-indexed from bottom-left for red)
     * Pikafish使用: "a9a8" 表示 (0,0) -> (0,1) 的走法
     */
    fun uciMoveToPositions(uciMove: String): Pair<Position, Position>? {
        if (uciMove.length < 4) return null

        try {
            val fromCol = uciMove[0] - 'a'
            val fromRow = uciMove[1].digitToInt()
            val toCol = uciMove[2] - 'a'
            val toRow = uciMove[3].digitToInt()

            val from = Position(fromCol, fromRow)
            val to = Position(toCol, toRow)
            return Pair(from, to)
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * 将棋盘位置转换为UCI走法字符串
     */
    fun positionsToUciMove(from: Position, to: Position): String {
        val fromCol = ('a' + from.x)
        val fromRow = from.y
        val toCol = ('a' + to.x)
        val toRow = to.y
        return "$fromCol$fromRow$toCol$toRow"
    }

    /**
     * 棋子类型和颜色转FEN字符
     * 红方(w)用大写，黑方(b)用小写
     */
    private fun pieceToFenChar(type: PieceType, color: PieceColor): Char {
        val ch = when (type) {
            PieceType.JU -> 'r'
            PieceType.MA -> 'n'
            PieceType.XIANG -> 'b'
            PieceType.SHI -> 'a'
            PieceType.JIANG -> 'k'
            PieceType.PAO -> 'c'
            PieceType.BING -> 'p'
        }
        return if (color == PieceColor.RED) ch.uppercaseChar() else ch
    }

    /**
     * FEN字符转棋子类型和颜色
     */
    private fun fenCharToPiece(ch: Char): Pair<PieceType, PieceColor>? {
        val color = if (ch.isUpperCase()) PieceColor.RED else PieceColor.BLACK
        val type = when (ch.lowercaseChar()) {
            'r' -> PieceType.JU
            'n' -> PieceType.MA
            'b' -> PieceType.XIANG
            'a' -> PieceType.SHI
            'k' -> PieceType.JIANG
            'c' -> PieceType.PAO
            'p' -> PieceType.BING
            else -> return null
        }
        return Pair(type, color)
    }

    /**
     * 将走法转换为中文记谱法
     */
    fun moveToChineseNotation(
        pieceType: PieceType,
        pieceColor: PieceColor,
        from: Position,
        to: Position
    ): String {
        val pieceName = when (pieceType) {
            PieceType.JU -> if (pieceColor == PieceColor.RED) "車" else "车"
            PieceType.MA -> if (pieceColor == PieceColor.RED) "馬" else "马"
            PieceType.XIANG -> if (pieceColor == PieceColor.RED) "相" else "象"
            PieceType.SHI -> if (pieceColor == PieceColor.RED) "仕" else "士"
            PieceType.JIANG -> if (pieceColor == PieceColor.RED) "帥" else "將"
            PieceType.PAO -> if (pieceColor == PieceColor.RED) "炮" else "砲"
            PieceType.BING -> if (pieceColor == PieceColor.RED) "兵" else "卒"
        }

        // 简化的中文记谱: 棋子名 起始列 -> 目标列
        val redNumbers = "九八七六五四三二一"
        val blackNumbers = "１２３４５６７８９"

        val fromColStr = if (pieceColor == PieceColor.RED) {
            redNumbers[from.x].toString()
        } else {
            blackNumbers[from.x].toString()
        }

        val direction: String
        val toStr: String

        val dy = to.y - from.y
        val dx = to.x - from.x

        if (pieceColor == PieceColor.RED) {
            direction = when {
                dy < 0 -> "进"
                dy > 0 -> "退"
                else -> "平"
            }
            toStr = if (dy == 0) {
                redNumbers[to.x].toString()
            } else if (dx == 0) {
                kotlin.math.abs(dy).toString()
            } else {
                redNumbers[to.x].toString()
            }
        } else {
            direction = when {
                dy > 0 -> "进"
                dy < 0 -> "退"
                else -> "平"
            }
            toStr = if (dy == 0) {
                blackNumbers[to.x].toString()
            } else if (dx == 0) {
                kotlin.math.abs(dy).toString()
            } else {
                blackNumbers[to.x].toString()
            }
        }

        return "$pieceName$fromColStr$direction$toStr"
    }
}
