package com.chesspro.app.core.chess

/**
 * 象棋走法类
 * @param from 起始位置
 * @param to 目标位置
 * @param piece 移动的棋子
 * @param capturedPiece 被吃掉的棋子（如果有）
 * @param isCheck 是否导致将军
 * @param isCheckmate 是否导致将死
 * @param moveNotation UCI走法记谱法
 */
data class Move(
    val from: Position,
    val to: Position,
    val piece: ChessPiece,
    val capturedPiece: ChessPiece? = null,
    val isCheck: Boolean = false,
    val isCheckmate: Boolean = false,
    val moveNotation: String = ""
) {
    /**
     * 获取走法的简洁描述
     */
    fun getDescription(): String {
        return "${piece.getSymbol()}${from}${to}"
    }

    /**
     * 判断是否吃子
     */
    fun isCapture(): Boolean = capturedPiece != null

    /**
     * 判断是否升变（兵过河）
     */
    fun isPromotion(): Boolean {
        return piece.type == PieceType.BING && 
               ((piece.color == PieceColor.RED && to.y < 5) ||
                (piece.color == PieceColor.BLACK && to.y > 4))
    }

    override fun toString(): String {
        return moveNotation.ifEmpty { "${piece.getSymbol()}${from}-${to}" }
    }

    companion object {
        /**
         * 创建标准走法记谱
         */
        fun createNotation(
            piece: ChessPiece,
            from: Position,
            to: Position,
            captured: Boolean = false,
            check: Boolean = false,
            checkmate: Boolean = false
        ): String {
            val pieceSymbol = getNotationSymbol(piece.type, piece.color)
            val fromStr = formatPosition(from)
            val toStr = formatPosition(to)
            
            var notation = "$pieceSymbol$fromStr$toStr"
            if (captured) notation = "$pieceSymbol$fromStr×$toStr"
            if (checkmate) notation += "#"
            else if (check) notation += "+"
            
            return notation
        }

        private fun getNotationSymbol(type: PieceType, color: PieceColor = PieceColor.RED): String {
            return when (type) {
                PieceType.JU -> "車"
                PieceType.MA -> "馬"
                PieceType.XIANG -> "象"
                PieceType.SHI -> "士"
                PieceType.JIANG -> if (color == PieceColor.RED) "帥" else "將"
                PieceType.PAO -> "炮"
                PieceType.BING -> ""
            }
        }

        private fun formatPosition(pos: Position): String {
            return pos.toString()
        }
    }
}
