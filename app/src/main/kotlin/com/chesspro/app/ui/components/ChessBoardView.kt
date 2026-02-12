package com.chesspro.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesspro.app.core.chess.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 高质量象棋棋盘视图
 * - 木纹背景
 * - 精美棋子图片
 * - 选中动画效果
 * - 着法箭头指示
 */
@Composable
fun ChessBoardView(
    board: ChessBoard,
    selectedPosition: Position? = null,
    suggestedMove: Move? = null,
    onPositionClick: (Position) -> Unit = {},
    onPieceDrag: (Position, Position) -> Unit = { _, _ -> },
    boardSize: androidx.compose.ui.unit.Dp = 350.dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // 将dp转换为像素
    val boardSizePx = with(density) { boardSize.toPx() }
    val cellSize = boardSizePx / 9f

    // 动画状态
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // 拖拽状态
    var dragPosition by remember { mutableStateOf<Position?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .size(boardSize)
            .shadow(
                elevation = 8.dp,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                ambientColor = Color.Black.copy(alpha = 0.3f),
                spotColor = Color.Black.copy(alpha = 0.4f)
            )
    ) {
        // 棋盘背景（木纹效果）
        ChessBoardBackground(
            boardSize = boardSizePx,
            cellSize = cellSize
        )

        // 棋盘网格和棋子
        ChessBoardContent(
            board = board,
            boardSize = boardSizePx,
            cellSize = cellSize,
            selectedPosition = selectedPosition,
            suggestedMove = suggestedMove,
            dragPosition = dragPosition,
            dragOffset = dragOffset,
            pulseAlpha = pulseAlpha,
            onPositionClick = { position ->
                if (dragPosition == null) {
                    onPositionClick(position)
                }
            },
            onDragStart = { position ->
                val piece = board.getPieceAt(position)
                if (piece != null && piece.color == board.currentPlayer) {
                    dragPosition = position
                    dragOffset = Offset.Zero
                }
            },
            onDrag = { change, dragAmount ->
                change.consume()
                dragOffset = dragOffset + dragAmount
            },
            onDragEnd = {
                dragPosition?.let { from ->
                    val to = pixelToPosition(dragOffset, boardSizePx, cellSize)
                    if (to != null && board.getPieceAt(from)?.color == board.currentPlayer) {
                        onPieceDrag(from, to)
                    }
                }
                dragPosition = null
                dragOffset = Offset.Zero
            }
        )

        // 着法箭头指示
        suggestedMove?.let { move ->
            val fromX = move.from.x * cellSize + cellSize / 2
            val fromY = move.from.y * cellSize + cellSize / 2
            val toX = move.to.x * cellSize + cellSize / 2
            val toY = move.to.y * cellSize + cellSize / 2

            SuggestionArrow(
                from = Offset(fromX.toFloat(), fromY.toFloat()),
                to = Offset(toX.toFloat(), toY.toFloat()),
                cellSize = cellSize,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 棋盘背景（木纹效果）
 */
@Composable
private fun ChessBoardBackground(
    boardSize: Float,
    cellSize: Float
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // 木纹背景色
        val woodColor = Color(0xFFE8C87A)
        val woodDarkColor = Color(0xFFD4A84B)

        // 绘制木纹背景
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFF5DEB3),
                    Color(0xFFE8D4A8),
                    Color(0xFFDEB887),
                    Color(0xFFE8D4A8),
                    Color(0xFFF5DEB3)
                )
            )
        )

        // 绘制边框
        val padding = cellSize * 0.3f
        val boardWithoutPadding = boardSize - padding * 2

        // 棋盘外框
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF8B4513),
                    Color(0xFFA0522D),
                    Color(0xFF8B4513)
                )
            ),
            topLeft = Offset(padding - 8f, padding - 8f),
            size = Size(boardWithoutPadding + 16f, boardWithoutPadding * 1.11f + 16f),
            cornerRadius = CornerRadius(8f, 8f)
        )

        // 棋盘内框
        drawRoundRect(
            color = Color(0xFFE8D4A8),
            topLeft = Offset(padding, padding),
            size = Size(boardWithoutPadding, boardWithoutPadding * 1.11f),
            cornerRadius = CornerRadius(4f, 4f)
        )
    }
}

/**
 * 棋盘内容（网格线和棋子）
 */
@Composable
private fun ChessBoardContent(
    board: ChessBoard,
    boardSize: Float,
    cellSize: Float,
    selectedPosition: Position?,
    suggestedMove: Move?,
    dragPosition: Position?,
    dragOffset: Offset,
    pulseAlpha: Float,
    onPositionClick: (Position) -> Unit,
    onDragStart: (Position) -> Unit,
    onDrag: (androidx.compose.ui.input.pointer.PointerInputChange, Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    val density = LocalDensity.current
    val pieceSize = with(density) { (cellSize * 0.85f).toDp() }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val position = pixelToPosition(offset, boardSize, cellSize)
                    position?.let { onPositionClick(it) }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val position = pixelToPosition(offset, boardSize, cellSize)
                        position?.let { onDragStart(it) }
                    },
                    onDrag = onDrag,
                    onDragEnd = onDragEnd
                )
            }
    ) {
        val padding = cellSize * 0.3f
        val boardWidth = boardSize - padding * 2
        val boardHeight = boardWidth * 1.11f

        // 绘制楚河汉界
        drawRiver(padding, boardHeight, boardWidth, cellSize)

        // 绘制网格线
        drawGridLines(padding, boardWidth, boardHeight, cellSize)

        // 绘制九宫格斜线
        drawPalaceLines(padding, boardWidth, boardHeight, cellSize)

        // 绘制棋子位置标记
        drawPositionMarkers(padding, cellSize)

        // 绘制被将军标记
        if (board.isInCheck(board.currentPlayer)) {
            val kingPos = board.pieces.find { it.type == PieceType.JIANG && it.color == board.currentPlayer }
            kingPos?.let {
                val x = it.position.x * cellSize + cellSize / 2 + padding
                val y = it.position.y * cellSize + cellSize / 2 + padding
                drawCircle(
                    color = Color.Red.copy(alpha = 0.3f),
                    radius = cellSize * 0.4f,
                    center = Offset(x, y)
                )
            }
        }
    }

    // 绘制棋子（在Canvas之上）
    board.pieces.forEach { piece ->
        val isDragging = dragPosition == piece.position
        val isSelected = selectedPosition == piece.position
        val isSuggestedFrom = suggestedMove?.from == piece.position
        val isSuggestedTo = suggestedMove?.to == piece.position

        val x = if (isDragging) {
            with(density) { (dragOffset.x + cellSize / 2).toDp() }
        } else {
            with(density) { (piece.position.x * cellSize + cellSize / 2).toDp() }
        }

        val y = if (isDragging) {
            with(density) { (dragOffset.y + cellSize / 2).toDp() }
        } else {
            with(density) { (piece.position.y * cellSize + cellSize / 2).toDp() }
        }

        // 选中效果
        if (isSelected) {
            Box(
                modifier = Modifier
                    .offset(x = x, y = y)
                    .size(pieceSize)
                    .shadow(
                        elevation = 12.dp,
                        shape = CircleShape,
                        ambientColor = Color(0xFF4CAF50),
                        spotColor = Color(0xFF4CAF50)
                    )
            )
        }

        // 建议着法起点标记
        if (isSuggestedFrom) {
            Box(
                modifier = Modifier
                    .offset(x = x, y = y)
                    .size(pieceSize)
                    .background(
                        color = Color(0x4D4CAF51),
                        shape = CircleShape
                    )
            )
        }

        // 建议着法终点标记
        if (isSuggestedTo) {
            Box(
                modifier = Modifier
                    .offset(x = x, y = y)
                    .size(pieceSize)
                    .background(
                        color = Color(0x4D2196F3),
                        shape = CircleShape
                    )
            )
        }

        // 绘制棋子图片
        Image(
            painter = painterResource(id = getPieceDrawable(piece.type, piece.color)),
            contentDescription = "${piece.color} ${piece.type}",
            modifier = Modifier
                .offset(x = x - pieceSize / 2, y = y - pieceSize / 2)
                .size(pieceSize)
                .shadow(
                    elevation = if (isDragging) 16.dp else 4.dp,
                    shape = CircleShape
                )
                .clip(CircleShape),
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * 绘制楚河汉界
 */
private fun DrawScope.drawRiver(padding: Float, boardHeight: Float, boardWidth: Float, cellSize: Float) {
    val riverY = boardHeight / 2 + padding - cellSize / 2

    // 楚河汉界背景
    drawRect(
        color = Color(0xFFE8D4A8),
        topLeft = Offset(padding + cellSize, riverY),
        size = Size(boardWidth - cellSize * 2, cellSize)
    )

    // "楚河" "汉界" 文字
    // 楚河
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(139, 69, 19)
            textSize = cellSize * 0.4f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }

        val textY = riverY + cellSize / 2 + cellSize * 0.15f
        drawText("楚河", padding + boardWidth / 4, textY, paint)
        drawText("汉界", padding + boardWidth * 3 / 4, textY, paint)
    }
}

/**
 * 绘制网格线
 */
private fun DrawScope.drawGridLines(padding: Float, boardWidth: Float, boardHeight: Float, cellSize: Float) {
    val lineColor = Color(0xFF5D4037)
    val strokeWidth = 2f

    // 垂直线（9列）
    for (i in 0..8) {
        val x = padding + i * cellSize
        val lineHeight = if (i == 0 || i == 8) {
            boardHeight
        } else {
            boardHeight / 2
        }
        val startY = if (i == 0 || i == 8) padding else padding + boardHeight / 2

        drawLine(
            color = lineColor,
            start = Offset(x, startY),
            end = Offset(x, startY + lineHeight),
            strokeWidth = strokeWidth
        )
    }

    // 水平线（10行）
    for (i in 0..9) {
        val y = padding + i * cellSize
        drawLine(
            color = lineColor,
            start = Offset(padding, y),
            end = Offset(padding + boardWidth, y),
            strokeWidth = strokeWidth
        )
    }

    // 边框
    drawRoundRect(
        color = lineColor,
        topLeft = Offset(padding, padding),
        size = Size(boardWidth, boardHeight),
        cornerRadius = CornerRadius(4f, 4f),
        style = Stroke(width = 4f)
    )
}

/**
 * 绘制九宫格斜线
 */
private fun DrawScope.drawPalaceLines(padding: Float, boardWidth: Float, boardHeight: Float, cellSize: Float) {
    val lineColor = Color(0xFF5D4037)
    val strokeWidth = 2f

    // 红方九宫格斜线
    // 左上
    drawLine(
        color = lineColor,
        start = Offset(padding + cellSize * 3, padding + cellSize * 7),
        end = Offset(padding + cellSize * 4, padding + cellSize * 8),
        strokeWidth = strokeWidth
    )
    // 右上
    drawLine(
        color = lineColor,
        start = Offset(padding + cellSize * 5, padding + cellSize * 7),
        end = Offset(padding + cellSize * 6, padding + cellSize * 8),
        strokeWidth = strokeWidth
    )
    // 左下
    drawLine(
        color = lineColor,
        start = Offset(padding + cellSize * 3, padding + cellSize * 9),
        end = Offset(padding + cellSize * 4, padding + cellSize * 8),
        strokeWidth = strokeWidth
    )
    // 右下
    drawLine(
        color = lineColor,
        start = Offset(padding + cellSize * 5, padding + cellSize * 9),
        end = Offset(padding + cellSize * 6, padding + cellSize * 8),
        strokeWidth = strokeWidth
    )

    // 黑方九宫格斜线
    // 左上
    drawLine(
        color = lineColor,
        start = Offset(padding + cellSize * 3, padding + cellSize * 1),
        end = Offset(padding + cellSize * 4, padding),
        strokeWidth = strokeWidth
    )
    // 右上
    drawLine(
        color = lineColor,
        start = Offset(padding + cellSize * 5, padding + cellSize * 1),
        end = Offset(padding + cellSize * 6, padding),
        strokeWidth = strokeWidth
    )
    // 左下
    drawLine(
        color = lineColor,
        start = Offset(padding + cellSize * 3, padding + cellSize * 2),
        end = Offset(padding + cellSize * 4, padding + cellSize * 1),
        strokeWidth = strokeWidth
    )
    // 右下
    drawLine(
        color = lineColor,
        start = Offset(padding + cellSize * 5, padding + cellSize * 2),
        end = Offset(padding + cellSize * 6, padding + cellSize * 1),
        strokeWidth = strokeWidth
    )
}

/**
 * 绘制位置标记
 */
private fun DrawScope.drawPositionMarkers(padding: Float, cellSize: Float) {
    val markerColor = Color(0xFF5D4037)

    // 在特定位置绘制十字标记
    val markerPositions = listOf(
        // 炮位置
        Pair(1, 2), Pair(7, 2),
        Pair(1, 7), Pair(7, 7),
        // 兵位置
        Pair(0, 3), Pair(2, 3), Pair(4, 3), Pair(6, 3), Pair(8, 3),
        Pair(0, 6), Pair(2, 6), Pair(4, 6), Pair(6, 6), Pair(8, 6)
    )

    val markerSize = cellSize * 0.08f

    markerPositions.forEach { (col, row) ->
        val x = padding + col * cellSize
        val y = padding + row * cellSize

        // 横向短线
        drawLine(
            color = markerColor,
            start = Offset(x - markerSize, y),
            end = Offset(x + markerSize, y),
            strokeWidth = 2f
        )
        // 纵向短线
        drawLine(
            color = markerColor,
            start = Offset(x, y - markerSize),
            end = Offset(x, y + markerSize),
            strokeWidth = 2f
        )
    }
}

/**
 * 建议着法箭头
 */
@Composable
private fun SuggestionArrow(
    from: Offset,
    to: Offset,
    cellSize: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "arrow")
    val arrowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowAlpha"
    )

    Canvas(modifier = modifier) {
        val arrowColor = Color(0xFF4CAF51).copy(alpha = arrowAlpha)
        val arrowWidth = cellSize * 0.08f

        // 计算箭头方向
        val dx = to.x - from.x
        val dy = to.y - from.y
        val length = sqrt(dx * dx + dy * dy)
        val normalizedDx = dx / length
        val normalizedDy = dy / length

        // 缩短箭头以避免覆盖棋子
        val shortenAmount = cellSize * 0.6f
        val actualLength = (length - shortenAmount).coerceAtLeast(cellSize * 0.3f)
        val endX = from.x + normalizedDx * actualLength
        val endY = from.y + normalizedDy * actualLength

        // 绘制箭头主体
        drawLine(
            color = arrowColor,
            start = Offset(from.x, from.y),
            end = Offset(endX, endY),
            strokeWidth = arrowWidth * 2,
            cap = StrokeCap.Round
        )

        // 绘制箭头头部
        val arrowHeadSize = cellSize * 0.2f
        val arrowHeadAngle = 30f
        val angle1 = Math.toRadians(arrowHeadAngle.toDouble())
        val angle2 = Math.toRadians((-arrowHeadAngle).toDouble())

        val baseAngle = atan2(dy.toDouble(), dx.toDouble())
        val headX1 = endX - arrowHeadSize * cos(angle1 - baseAngle).toFloat()
        val headY1 = endY - arrowHeadSize * sin(angle1 - baseAngle).toFloat()
        val headX2 = endX - arrowHeadSize * cos(angle2 - baseAngle).toFloat()
        val headY2 = endY - arrowHeadSize * sin(angle2 - baseAngle).toFloat()

        drawPath(
            path = Path().apply {
                moveTo(endX, endY)
                lineTo(headX1, headY1)
                moveTo(endX, endY)
                lineTo(headX2, headY2)
            },
            color = arrowColor,
            style = Stroke(width = arrowWidth * 2, cap = StrokeCap.Round)
        )
    }
}

/**
 * 将像素坐标转换为棋盘位置
 */
private fun pixelToPosition(
    pixel: Offset,
    boardSize: Float,
    cellSize: Float
): Position? {
    val padding = cellSize * 0.3f

    // 检查是否在棋盘范围内
    if (pixel.x < padding || pixel.x > boardSize - padding ||
        pixel.y < padding || pixel.y > boardSize - padding * 1.11f
    ) {
        return null
    }

    val col = ((pixel.x - padding) / cellSize).toInt()
    val row = ((pixel.y - padding) / cellSize).toInt()

    return if (col in 0..8 && row in 0..9) {
        Position(col, row)
    } else {
        null
    }
}

/**
 * 获取棋子对应的drawable资源
 */
private fun getPieceDrawable(type: PieceType, color: PieceColor): Int {
    val colorPrefix = if (color == PieceColor.RED) "red" else "black"
    val typeName = when (type) {
        PieceType.JU -> "ju"
        PieceType.MA -> "ma"
        PieceType.XIANG -> "xiang"
        PieceType.SHI -> "shi"
        PieceType.JIANG -> "jiang"
        PieceType.PAO -> "pao"
        PieceType.BING -> "bing"
    }

    // 根据颜色选择正确的drawable
    return when (color) {
        PieceColor.RED -> when (type) {
            PieceType.JU -> com.chesspro.app.R.drawable.piece_red_ju
            PieceType.MA -> com.chesspro.app.R.drawable.piece_red_ma
            PieceType.XIANG -> com.chesspro.app.R.drawable.piece_red_xiang
            PieceType.SHI -> com.chesspro.app.R.drawable.piece_red_shi
            PieceType.JIANG -> com.chesspro.app.R.drawable.piece_red_jiang
            PieceType.PAO -> com.chesspro.app.R.drawable.piece_red_pao
            PieceType.BING -> com.chesspro.app.R.drawable.piece_red_bing
        }
        PieceColor.BLACK -> when (type) {
            PieceType.JU -> com.chesspro.app.R.drawable.piece_black_ju
            PieceType.MA -> com.chesspro.app.R.drawable.piece_black_ma
            PieceType.XIANG -> com.chesspro.app.R.drawable.piece_black_xiang
            PieceType.SHI -> com.chesspro.app.R.drawable.piece_black_shi
            PieceType.JIANG -> com.chesspro.app.R.drawable.piece_black_jiang
            PieceType.PAO -> com.chesspro.app.R.drawable.piece_black_pao
            PieceType.BING -> com.chesspro.app.R.drawable.piece_black_bing
        }
    }
}
