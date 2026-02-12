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

        // 采样棋子区域的颜色
        var redCount = 0
        var blackCount = 0
        var whiteCount = 0
        var totalCount = 0
        var nonBoardCount = 0

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

                if (!isBoardColor(pixel)) {
                    nonBoardCount++
                }

                // 红色棋子文字：红色系
                if (r > 160 && g < 80 && b < 80) {
                    redCount++
                }
                // 黑色棋子文字：深色系
                else if (r < 80 && g < 80 && b < 80) {
                    blackCount++
                }
                // 白色/浅色（棋子底色）
                else if (r > 220 && g > 220 && b > 200) {
                    whiteCount++
                }
            }
        }

        if (totalCount == 0) return null

        // 有棋子的判断：该区域有足够多的非棋盘颜色像素（棋子挡住了背景）
        val nonBoardRatio = nonBoardCount.toFloat() / totalCount
        if (nonBoardRatio < 0.3f) return null // 没有棋子

        // 判断颜色
        val isRed = redCount > blackCount && redCount > totalCount * 0.03f
        val isBlack = blackCount > redCount && blackCount > totalCount * 0.03f

        if (!isRed && !isBlack) return null

        val color = if (isRed) PieceColor.RED else PieceColor.BLACK

        // 暂时无法通过简单像素分析识别具体棋子类型
        // 默认返回兵/卒，后续可以用更高级的识别方法
        val type = PieceType.BING

        return Pair(type, color)
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
