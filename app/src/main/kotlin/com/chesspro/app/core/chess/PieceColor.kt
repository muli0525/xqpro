package com.chesspro.app.core.chess

/**
 * 棋子颜色枚举
 */
enum class PieceColor {
    RED,    // 红方
    BLACK;  // 黑方

    /**
     * 获取对方颜色
     */
    fun other(): PieceColor {
        return when (this) {
            RED -> BLACK
            BLACK -> RED
        }
    }
}
