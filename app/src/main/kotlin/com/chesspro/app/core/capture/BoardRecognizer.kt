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

            // Step 3: 在每个交叉点检测棋子（带置信度）
            data class PieceCandidate(
                val type: PieceType, val color: PieceColor,
                val position: Position, val confidence: Float
            )
            val candidates = mutableListOf<PieceCandidate>()
            for (row in 0 until ROWS) {
                for (col in 0 until COLS) {
                    val cx = (boardRect.left + col * cellW).toInt()
                    val cy = (boardRect.top + row * cellH).toInt()
                    val radius = (minOf(cellW, cellH) * 0.4f).toInt()

                    val pieceInfo = detectPieceAt(bitmap, cx, cy, radius)
                    if (pieceInfo != null) {
                        candidates.add(PieceCandidate(
                            pieceInfo.first, pieceInfo.second,
                            Position(col, row), pieceInfo.third
                        ))
                    }
                }
            }

            // 中国象棋最多32子，如果检测超过32就按置信度排序取前32
            val pieces = if (candidates.size > 32) {
                Log.w(TAG, "检测到${candidates.size}个候选，裁剪到32")
                candidates.sortedByDescending { it.confidence }
                    .take(32)
                    .map { RecognizedPiece(it.type, it.color, it.position) }
                    .toMutableList()
            } else {
                candidates.map { RecognizedPiece(it.type, it.color, it.position) }
                    .toMutableList()
            }

            // Step 4: 位置约束校正
            val corrected = applyPositionConstraints(pieces)

            // Step 5: 转FEN
            val fen = toFen(corrected)
            Log.d(TAG, "Recognized ${corrected.size} pieces, FEN: $fen")

            return RecognitionResult(corrected, fen, boardRect)
        } catch (e: Exception) {
            Log.e(TAG, "Recognition failed", e)
            return null
        }
    }

    /**
     * 位置约束校正 - 利用象棋规则修正棋子类型
     * 规则：
     * - 将/帅只能在九宫格(col 3-5)
     * - 士/仕只能在九宫格(col 3-5)
     * - 象/相不能过河(红方row 5-9, 黑方row 0-4)
     * - 每方最多: 1将, 2士, 2象, 2马, 2车, 2炮, 5兵
     */
    private fun applyPositionConstraints(pieces: List<RecognizedPiece>): List<RecognizedPiece> {
        val result = mutableListOf<RecognizedPiece>()

        // 按颜色分组
        val redPieces = pieces.filter { it.color == PieceColor.RED }.toMutableList()
        val blackPieces = pieces.filter { it.color == PieceColor.BLACK }.toMutableList()

        result.addAll(correctSide(redPieces, PieceColor.RED, 5..9))
        result.addAll(correctSide(blackPieces, PieceColor.BLACK, 0..4))

        return result
    }

    private fun correctSide(
        pieces: MutableList<RecognizedPiece>,
        color: PieceColor,
        homeSide: IntRange
    ): List<RecognizedPiece> {
        val result = mutableListOf<RecognizedPiece>()
        val counts = mutableMapOf<PieceType, Int>()
        val maxCounts = mapOf(
            PieceType.JIANG to 1,
            PieceType.SHI to 2,
            PieceType.XIANG to 2,
            PieceType.MA to 2,
            PieceType.JU to 2,
            PieceType.PAO to 2,
            PieceType.BING to 5
        )

        for (piece in pieces) {
            var type = piece.type
            val pos = piece.position

            // 九宫格约束 (col 3-5, 帅row 7-9/将row 0-2)
            val inPalace = pos.x in 3..5 &&
                    (if (color == PieceColor.RED) pos.y in 7..9 else pos.y in 0..2)

            // 如果在九宫格中心且被识别为将/帅，保持
            if (type == PieceType.JIANG && !inPalace) {
                type = PieceType.BING // 降级
            }
            if (type == PieceType.SHI && !inPalace) {
                type = PieceType.BING
            }

            // 象不能过河
            if (type == PieceType.XIANG && pos.y !in homeSide) {
                type = PieceType.MA // 过河的象可能是马
            }

            // 数量限制
            val current = counts.getOrDefault(type, 0)
            val max = maxCounts.getOrDefault(type, 5)
            if (current >= max) {
                type = PieceType.BING // 超出限制降级为兵
            }

            counts[type] = (counts.getOrDefault(type, 0)) + 1
            result.add(RecognizedPiece(type, color, pos))
        }

        // 确保有且仅有1个将/帅
        val generals = result.filter { it.type == PieceType.JIANG }
        if (generals.isEmpty() && result.isNotEmpty()) {
            // 找九宫格中的棋子，升级为将
            val candidate = result.find {
                it.position.x in 3..5 &&
                        (if (color == PieceColor.RED) it.position.y in 7..9 else it.position.y in 0..2)
            }
            if (candidate != null) {
                val idx = result.indexOf(candidate)
                result[idx] = RecognizedPiece(PieceType.JIANG, color, candidate.position)
            }
        }

        return result
    }

    /**
     * 找到棋盘区域
     * 策略：
     * 1. 按行扫描，找到连续多行都有大量棕黄色的区域
     * 2. 精确找到网格线边界
     */
    private fun findBoardRect(bitmap: Bitmap): BoardRect? {
        val w = bitmap.width
        val h = bitmap.height

        // 逐行统计棋盘色像素数
        val rowBoardCount = IntArray(h)
        val step = 3
        for (y in 0 until h step step) {
            var count = 0
            for (x in 0 until w step step) {
                if (isBoardColor(bitmap.getPixel(x, y))) count++
            }
            rowBoardCount[y] = count
        }

        // 找到棋盘的上下边界（连续有大量棋盘色的区域）
        val threshold = w / step / 4 // 至少1/4宽度是棋盘色
        var topY = -1
        var bottomY = -1
        for (y in 0 until h step step) {
            if (rowBoardCount[y] > threshold) {
                if (topY == -1) topY = y
                bottomY = y
            }
        }

        if (topY == -1 || bottomY - topY < h * 0.15f) {
            Log.w(TAG, "Board area not found (topY=$topY bottomY=$bottomY)")
            return null
        }

        // 逐列统计，找左右边界
        var leftX = w
        var rightX = 0
        for (x in 0 until w step step) {
            var count = 0
            for (y in topY..bottomY step step) {
                if (isBoardColor(bitmap.getPixel(x, y))) count++
            }
            if (count > (bottomY - topY) / step / 4) {
                if (x < leftX) leftX = x
                rightX = x
            }
        }

        if (rightX - leftX < w * 0.3f) {
            Log.w(TAG, "Board too narrow: ${rightX - leftX}")
            return null
        }

        // 内缩去掉边框
        val boardW = rightX - leftX
        val boardH = bottomY - topY
        val insetX = (boardW * 0.03f).toInt()
        val insetY = (boardH * 0.03f).toInt()

        // 棋盘网格: 9列8间距, 10行9间距
        // 实际使用检测到的宽高比来计算
        val gridLeft = leftX + insetX
        val gridRight = rightX - insetX
        val gridW = gridRight - gridLeft
        val cellW = gridW.toFloat() / (COLS - 1)

        // 格子高度 ≈ 格子宽度（中国象棋棋盘近似正方形格子）
        val cellH = cellW * 1.05f // 天天象棋格子略高
        val gridH = (cellH * (ROWS - 1)).toInt()
        val gridCenterY = (topY + bottomY) / 2
        val gridTop = gridCenterY - gridH / 2
        val gridBottom = gridCenterY + gridH / 2

        Log.d(TAG, "Board: left=$gridLeft top=$gridTop right=$gridRight bottom=$gridBottom " +
                "cellW=$cellW cellH=$cellH boardW=$boardW boardH=$boardH")

        return BoardRect(
            left = gridLeft,
            top = gridTop.coerceAtLeast(0),
            right = gridRight,
            bottom = gridBottom.coerceAtMost(h - 1)
        )
    }

    /**
     * 判断像素是否为棋盘背景色（天天象棋的木纹棕黄色）
     * 放宽范围以覆盖不同手机屏幕
     */
    private fun isBoardColor(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)

        // 棕黄色/木纹色范围
        return r in 140..245 && g in 110..210 && b in 50..170 &&
                r > g && g > b &&
                (r - b) > 30
    }

    /**
     * 判断像素是否为棋子体颜色（奶油/米色，比棋盘更亮）
     */
    private fun isPieceBodyColor(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        val brightness = (r + g + b) / 3

        // 棋子体: 比棋盘更亮的暖色调
        return brightness > 170 && r > 165 && g > 145 &&
                (r + g + b) > 520
    }

    /**
     * 检测指定位置是否有棋子
     * 核心改进：使用亮度对比检测棋子体 + 宽松的颜色墨水检测
     */
    private fun detectPieceAt(bitmap: Bitmap, cx: Int, cy: Int, radius: Int): Triple<PieceType, PieceColor, Float>? {
        val w = bitmap.width
        val h = bitmap.height

        var redInkCount = 0
        var darkInkCount = 0
        var pieceBodyCount = 0
        var boardCount = 0
        var totalCount = 0

        // 3x3网格用于字符密度分析
        val gridInk = IntArray(9)
        val gridTotal = IntArray(9)

        val sampleRadius = (radius * 0.75f).toInt()
        for (dy in -sampleRadius..sampleRadius step 2) {
            for (dx in -sampleRadius..sampleRadius step 2) {
                if (dx * dx + dy * dy > sampleRadius * sampleRadius) continue
                val x = cx + dx
                val y = cy + dy
                if (x < 0 || x >= w || y < 0 || y >= h) continue

                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val brightness = (r + g + b) / 3
                totalCount++

                // 网格位置
                val gx = ((dx + sampleRadius) * 3 / (sampleRadius * 2 + 1)).coerceIn(0, 2)
                val gy = ((dy + sampleRadius) * 3 / (sampleRadius * 2 + 1)).coerceIn(0, 2)
                val gi = gy * 3 + gx
                gridTotal[gi]++

                when {
                    // 红色墨水 (放宽: r > 120, g < 100, 红色明显高于绿蓝)
                    r > 120 && g < 110 && b < 110 && (r - g) > 40 -> {
                        redInkCount++
                        gridInk[gi]++
                    }
                    // 深色墨水 (黑色棋子文字)
                    brightness < 90 -> {
                        darkInkCount++
                        gridInk[gi]++
                    }
                    // 棋子体 (奶油色，比棋盘亮)
                    isPieceBodyColor(pixel) -> {
                        pieceBodyCount++
                    }
                    // 棋盘色
                    isBoardColor(pixel) -> {
                        boardCount++
                    }
                }
            }
        }

        if (totalCount == 0) return null

        val pieceBodyRatio = pieceBodyCount.toFloat() / totalCount
        val totalInk = redInkCount + darkInkCount
        val inkRatio = totalInk.toFloat() / totalCount
        val boardRatio = boardCount.toFloat() / totalCount

        // 检测条件（平衡版: 避免43子误检，也避免漏检）
        // 棋子特征: 有亮色棋子体 + 有文字墨水 + 棋盘色不太多
        // 关键: 棋子体+墨水 合计必须显著

        if (boardRatio > 0.50f) return null       // 超过一半是棋盘色 → 空位
        if (totalInk < 5) return null              // 墨水太少 → 没文字
        
        // 必须有棋子体或足够多的墨水（两者至少满足一个强条件）
        val hasPieceBody = pieceBodyRatio > 0.18f
        val hasStrongInk = inkRatio > 0.05f
        if (!hasPieceBody && !hasStrongInk) return null
        
        // 棋子体+墨水的总非棋盘占比必须显著
        val pieceSignal = pieceBodyRatio + inkRatio
        if (pieceSignal < 0.20f) return null       // 总信号太弱

        val isRed = redInkCount > darkInkCount
        val isBlack = darkInkCount >= redInkCount

        val color = if (isRed) PieceColor.RED else PieceColor.BLACK

        // 字符密度分析
        val gridDensity = FloatArray(9) { i ->
            if (gridTotal[i] > 0) gridInk[i].toFloat() / gridTotal[i] else 0f
        }

        val topInk = gridDensity[0] + gridDensity[1] + gridDensity[2]
        val bottomInk = gridDensity[6] + gridDensity[7] + gridDensity[8]
        val midInk = gridDensity[3] + gridDensity[4] + gridDensity[5]
        val centerDensity = gridDensity[4]

        val leftInk = gridDensity[0] + gridDensity[3] + gridDensity[6]
        val rightInk = gridDensity[2] + gridDensity[5] + gridDensity[8]
        val symmetry = 1f - kotlin.math.abs(leftInk - rightInk) / (leftInk + rightInk + 0.001f)

        val type = classifyPieceType(inkRatio, topInk, midInk, bottomInk, centerDensity, symmetry)

        // 置信度 = 棋子体占比 + 墨水占比（越高越可能是棋子）
        val confidence = pieceBodyRatio * 2f + inkRatio * 5f

        return Triple(type, color, confidence)
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
