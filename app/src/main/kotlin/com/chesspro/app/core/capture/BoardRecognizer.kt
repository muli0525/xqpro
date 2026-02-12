package com.chesspro.app.core.capture

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.chesspro.app.core.chess.PieceColor
import com.chesspro.app.core.chess.PieceType
import com.chesspro.app.core.chess.Position

data class RecognizedPiece(
    val type: PieceType,
    val color: PieceColor,
    val position: Position
)

data class RecognitionResult(
    val pieces: List<RecognizedPiece>,
    val fen: String,
    val boardRect: BoardRect?
)

data class BoardRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

/**
 * 棋盘识别器
 * 从屏幕截图中识别棋盘和棋子位置，转换为FEN字符串
 *
 * 识别策略：
 * 1. 扫描截图找到棋盘区域（通过检测网格线）
 * 2. 计算每个交叉点位置
 * 3. 在每个交叉点检测是否有棋子（通过颜色分析）
 * 4. 区分红方/黑方棋子
 * 5. 通过棋子颜色区域的文字特征识别棋子类型
 */
class BoardRecognizer {
    companion object {
        const val TAG = "BoardRecognizer"
        const val COLS = 9
        const val ROWS = 10
    }

    fun recognize(bitmap: Bitmap): RecognitionResult? {
        try {
            // Step 1: 找到棋盘区域
            val boardRect = findBoardRect(bitmap) ?: run {
                Log.w(TAG, "Could not find board rect")
                return null
            }
            Log.d(TAG, "Board rect: $boardRect")

            // Step 2: 计算格子大小
            val boardWidth = boardRect.right - boardRect.left
            val boardHeight = boardRect.bottom - boardRect.top
            val cellW = boardWidth.toFloat() / (COLS - 1)
            val cellH = boardHeight.toFloat() / (ROWS - 1)

            // Step 3: 在每个交叉点检测棋子
            val pieces = mutableListOf<RecognizedPiece>()
            for (row in 0 until ROWS) {
                for (col in 0 until COLS) {
                    val cx = (boardRect.left + col * cellW).toInt()
                    val cy = (boardRect.top + row * cellH).toInt()
                    val radius = (minOf(cellW, cellH) * 0.4f).toInt()

                    val pieceInfo = detectPieceAt(bitmap, cx, cy, radius)
                    if (pieceInfo != null) {
                        pieces.add(RecognizedPiece(pieceInfo.first, pieceInfo.second, Position(col, row)))
                    }
                }
            }

            // Step 4: 转FEN
            val fen = toFen(pieces)
            Log.d(TAG, "Recognized ${pieces.size} pieces, FEN: $fen")

            return RecognitionResult(pieces, fen, boardRect)
        } catch (e: Exception) {
            Log.e(TAG, "Recognition failed", e)
            return null
        }
    }

    /**
     * 找到棋盘区域
     * 策略：棋盘通常占屏幕中间大部分区域，背景是棕黄色
     * 扫描找到大面积棕黄色区域的边界
     */
    private fun findBoardRect(bitmap: Bitmap): BoardRect? {
        val w = bitmap.width
        val h = bitmap.height

        // 棋盘颜色范围（棕黄色系）
        var minX = w
        var maxX = 0
        var minY = h
        var maxY = 0

        val sampleStep = 4
        var boardPixelCount = 0

        for (y in 0 until h step sampleStep) {
            for (x in 0 until w step sampleStep) {
                val pixel = bitmap.getPixel(x, y)
                if (isBoardColor(pixel)) {
                    boardPixelCount++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (boardPixelCount < 100) return null

        // 棋盘区域应该是方形比例约 8:9 (宽:高)
        val boardW = maxX - minX
        val boardH = maxY - minY

        if (boardW < w * 0.3f || boardH < h * 0.2f) return null

        // 调整边界 - 内缩一点以排除边框
        val inset = (boardW * 0.02f).toInt()
        val cellW = (boardW - inset * 2).toFloat() / (COLS - 1)
        val cellH = cellW * 1.0f // 假设格子近似正方形

        // 重新计算基于格子大小的棋盘区域
        val actualH = (cellH * (ROWS - 1)).toInt()
        val centerY = (minY + maxY) / 2
        val adjustedTop = centerY - actualH / 2
        val adjustedBottom = centerY + actualH / 2

        return BoardRect(
            left = minX + inset,
            top = adjustedTop.coerceAtLeast(0),
            right = maxX - inset,
            bottom = adjustedBottom.coerceAtMost(h - 1)
        )
    }

    /**
     * 判断像素是否为棋盘背景色（棕黄色系）
     */
    private fun isBoardColor(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)

        // 棕黄色：R高，G中高，B较低
        return r > 150 && g > 120 && b > 60 &&
                r < 255 && g < 230 && b < 180 &&
                r > g && g > b &&
                (r - b) > 40
    }

    /**
     * 检测指定位置是否有棋子
     * 返回 (棋子类型, 棋子颜色) 或 null
     */
    private fun detectPieceAt(bitmap: Bitmap, cx: Int, cy: Int, radius: Int): Pair<PieceType, PieceColor>? {
        val w = bitmap.width
        val h = bitmap.height

        var redCount = 0
        var blackCount = 0
        var whiteCount = 0
        var totalCount = 0
        var nonBoardCount = 0

        // 用于字符密度分析的3x3网格
        val gridInk = IntArray(9) // 3x3 grid ink counts
        val gridTotal = IntArray(9)

        val sampleRadius = (radius * 0.7f).toInt()
        for (dy in -sampleRadius..sampleRadius step 2) {
            for (dx in -sampleRadius..sampleRadius step 2) {
                val x = cx + dx
                val y = cy + dy
                if (x < 0 || x >= w || y < 0 || y >= h) continue
                if (dx * dx + dy * dy > sampleRadius * sampleRadius) continue

                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                totalCount++

                if (!isBoardColor(pixel)) nonBoardCount++

                // 计算网格位置 (0-2)
                val gx = ((dx + sampleRadius) * 3 / (sampleRadius * 2 + 1)).coerceIn(0, 2)
                val gy = ((dy + sampleRadius) * 3 / (sampleRadius * 2 + 1)).coerceIn(0, 2)
                val gi = gy * 3 + gx
                gridTotal[gi]++

                val isInk: Boolean

                if (r > 160 && g < 80 && b < 80) {
                    redCount++
                    isInk = true
                } else if (r < 80 && g < 80 && b < 80) {
                    blackCount++
                    isInk = true
                } else {
                    isInk = false
                    if (r > 220 && g > 220 && b > 200) whiteCount++
                }

                if (isInk) gridInk[gi]++
            }
        }

        if (totalCount == 0) return null

        val nonBoardRatio = nonBoardCount.toFloat() / totalCount
        if (nonBoardRatio < 0.3f) return null

        val isRed = redCount > blackCount && redCount > totalCount * 0.03f
        val isBlack = blackCount > redCount && blackCount > totalCount * 0.03f

        if (!isRed && !isBlack) return null

        val color = if (isRed) PieceColor.RED else PieceColor.BLACK

        // 计算字符密度特征
        val inkCount = redCount + blackCount
        val inkRatio = inkCount.toFloat() / totalCount

        // 3x3网格密度
        val gridDensity = FloatArray(9) { i ->
            if (gridTotal[i] > 0) gridInk[i].toFloat() / gridTotal[i] else 0f
        }

        // 上半/下半密度比
        val topInk = gridDensity[0] + gridDensity[1] + gridDensity[2]
        val bottomInk = gridDensity[6] + gridDensity[7] + gridDensity[8]
        val midInk = gridDensity[3] + gridDensity[4] + gridDensity[5]
        val centerDensity = gridDensity[4]

        // 左右对称度
        val leftInk = gridDensity[0] + gridDensity[3] + gridDensity[6]
        val rightInk = gridDensity[2] + gridDensity[5] + gridDensity[8]
        val symmetry = 1f - kotlin.math.abs(leftInk - rightInk) / (leftInk + rightInk + 0.001f)

        val type = classifyPieceType(inkRatio, topInk, midInk, bottomInk, centerDensity, symmetry)

        return Pair(type, color)
    }

    /**
     * 基于字符密度特征分类棋子类型
     * 不同汉字笔画密度分布不同：
     * - 兵/卒: 笔画少，密度低，上下对称
     * - 士/仕: 笔画少，密度低
     * - 将/帅: 中等密度，较对称
     * - 车/車: 中等密度，上密下疏
     * - 马/馬: 中高密度，左右不太对称
     * - 象/相: 较高密度
     * - 炮/砲: 高密度，结构复杂
     */
    private fun classifyPieceType(
        inkRatio: Float,
        topInk: Float,
        midInk: Float,
        bottomInk: Float,
        centerDensity: Float,
        symmetry: Float
    ): PieceType {
        val totalDensity = topInk + midInk + bottomInk

        return when {
            // 极低密度 -> 兵/卒 或 士/仕
            totalDensity < 0.6f -> {
                if (symmetry > 0.7f) PieceType.BING else PieceType.SHI
            }
            // 低密度
            totalDensity < 0.9f -> {
                when {
                    topInk > bottomInk * 1.3f -> PieceType.JU  // 车: 上密下疏
                    centerDensity > 0.15f -> PieceType.JIANG    // 将: 中心密
                    else -> PieceType.SHI
                }
            }
            // 中等密度
            totalDensity < 1.3f -> {
                when {
                    symmetry < 0.6f -> PieceType.MA             // 马: 不对称
                    topInk > bottomInk * 1.2f -> PieceType.JU   // 车
                    bottomInk > topInk * 1.2f -> PieceType.JIANG // 将
                    else -> PieceType.XIANG                      // 象
                }
            }
            // 高密度 -> 炮/象/马
            else -> {
                when {
                    symmetry > 0.7f -> PieceType.PAO            // 炮: 对称且密
                    midInk > topInk && midInk > bottomInk -> PieceType.XIANG
                    else -> PieceType.MA
                }
            }
        }
    }

    /**
     * 将识别结果转为FEN字符串
     */
    private fun toFen(pieces: List<RecognizedPiece>): String {
        val board = Array(ROWS) { arrayOfNulls<RecognizedPiece>(COLS) }
        pieces.forEach { board[it.position.y][it.position.x] = it }

        val sb = StringBuilder()
        for (row in 0 until ROWS) {
            var emptyCount = 0
            for (col in 0 until COLS) {
                val piece = board[row][col]
                if (piece == null) {
                    emptyCount++
                } else {
                    if (emptyCount > 0) {
                        sb.append(emptyCount)
                        emptyCount = 0
                    }
                    sb.append(pieceToFenChar(piece.type, piece.color))
                }
            }
            if (emptyCount > 0) sb.append(emptyCount)
            if (row < ROWS - 1) sb.append('/')
        }
        sb.append(" w - - 0 1")
        return sb.toString()
    }

    private fun pieceToFenChar(type: PieceType, color: PieceColor): Char {
        val c = when (type) {
            PieceType.JU -> 'r'
            PieceType.MA -> 'n'
            PieceType.XIANG -> 'b'
            PieceType.SHI -> 'a'
            PieceType.JIANG -> 'k'
            PieceType.PAO -> 'c'
            PieceType.BING -> 'p'
        }
        return if (color == PieceColor.RED) c.uppercaseChar() else c
    }
}
