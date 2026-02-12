package com.chesspro.app.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.chesspro.app.core.chess.*
import kotlin.math.roundToInt

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
    val boardSizePx = with(density) { boardSize.toPx() }
    val cellSize = boardSizePx / 10f
    val pad = cellSize

    var dragPosition by remember { mutableStateOf<Position?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    val piecePaint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
    }

    Canvas(
        modifier = modifier
            .size(boardSize, boardSize * 11f / 10f)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val col = ((offset.x - pad) / cellSize).roundToInt()
                    val row = ((offset.y - pad) / cellSize).roundToInt()
                    if (col in 0..8 && row in 0..9) onPositionClick(Position(col, row))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val col = ((offset.x - pad) / cellSize).roundToInt()
                        val row = ((offset.y - pad) / cellSize).roundToInt()
                        if (col in 0..8 && row in 0..9) {
                            dragPosition = Position(col, row)
                            dragOffset = offset
                        }
                    },
                    onDrag = { change, offset ->
                        change.consume()
                        dragOffset = Offset(dragOffset.x + offset.x, dragOffset.y + offset.y)
                    },
                    onDragEnd = {
                        dragPosition?.let { from ->
                            val col = ((dragOffset.x - pad) / cellSize).roundToInt()
                            val row = ((dragOffset.y - pad) / cellSize).roundToInt()
                            if (col in 0..8 && row in 0..9) onPieceDrag(from, Position(col, row))
                        }
                        dragPosition = null
                    }
                )
            }
    ) {
        val bw = cellSize * 8
        val bh = cellSize * 9

        // 背景
        drawRect(brush = Brush.verticalGradient(listOf(Color(0xFFF5DEB3), Color(0xFFDEB887), Color(0xFFF5DEB3))))

        // 外框
        drawRect(color = Color(0xFF5D4037), topLeft = Offset(pad, pad), size = Size(bw, bh), style = Stroke(3f))

        // 横线 10条
        for (i in 0..9) {
            val y = pad + i * cellSize
            drawLine(Color(0xFF5D4037), Offset(pad, y), Offset(pad + bw, y), 1.5f)
        }
        // 竖线 9条（楚河两侧边线通长，中间只画上下半区）
        for (i in 0..8) {
            val x = pad + i * cellSize
            if (i == 0 || i == 8) {
                drawLine(Color(0xFF5D4037), Offset(x, pad), Offset(x, pad + bh), 1.5f)
            } else {
                drawLine(Color(0xFF5D4037), Offset(x, pad), Offset(x, pad + cellSize * 4), 1.5f)
                drawLine(Color(0xFF5D4037), Offset(x, pad + cellSize * 5), Offset(x, pad + bh), 1.5f)
            }
        }

        // 九宫格斜线
        // 上方（黑方）
        drawLine(Color(0xFF5D4037), Offset(pad + 3 * cellSize, pad), Offset(pad + 5 * cellSize, pad + 2 * cellSize), 1.5f)
        drawLine(Color(0xFF5D4037), Offset(pad + 5 * cellSize, pad), Offset(pad + 3 * cellSize, pad + 2 * cellSize), 1.5f)
        // 下方（红方）
        drawLine(Color(0xFF5D4037), Offset(pad + 3 * cellSize, pad + 7 * cellSize), Offset(pad + 5 * cellSize, pad + 9 * cellSize), 1.5f)
        drawLine(Color(0xFF5D4037), Offset(pad + 5 * cellSize, pad + 7 * cellSize), Offset(pad + 3 * cellSize, pad + 9 * cellSize), 1.5f)

        // 楚河汉界
        drawContext.canvas.nativeCanvas.apply {
            val riverPaint = Paint().apply {
                color = android.graphics.Color.rgb(100, 60, 20)
                textSize = cellSize * 0.55f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                typeface = Typeface.SERIF
            }
            val riverY = pad + cellSize * 4.65f
            drawText("楚  河", pad + bw * 0.25f, riverY, riverPaint)
            drawText("漢  界", pad + bw * 0.75f, riverY, riverPaint)
        }

        // 选中高亮
        selectedPosition?.let { pos ->
            drawCircle(Color(0x664CAF50), cellSize * 0.46f, Offset(pad + pos.x * cellSize, pad + pos.y * cellSize))
        }

        // 建议走法
        suggestedMove?.let { move ->
            val fromC = Offset(pad + move.from.x * cellSize, pad + move.from.y * cellSize)
            val toC = Offset(pad + move.to.x * cellSize, pad + move.to.y * cellSize)
            drawLine(Color(0xCC4CAF50), fromC, toC, cellSize * 0.1f, StrokeCap.Round)
            drawCircle(Color(0x554CAF50), cellSize * 0.3f, fromC)
            drawCircle(Color(0x884CAF50), cellSize * 0.3f, toC)
        }

        // 棋子
        val pieceRadius = cellSize * 0.43f
        piecePaint.textSize = cellSize * 0.52f

        board.pieces.forEach { piece ->
            val isDragging = dragPosition == piece.position
            val cx = if (isDragging) dragOffset.x else pad + piece.position.x * cellSize
            val cy = if (isDragging) dragOffset.y else pad + piece.position.y * cellSize
            val isRed = piece.color == PieceColor.RED

            // 棋子阴影
            drawCircle(Color(0x40000000), pieceRadius + 2f, Offset(cx + 2f, cy + 3f))

            // 棋子底色
            val bgColor = if (isRed) Color(0xFFFFF8E1) else Color(0xFFFFF8E1)
            drawCircle(bgColor, pieceRadius, Offset(cx, cy))

            // 棋子边框
            val borderColor = if (isRed) Color(0xFFC41E3A) else Color(0xFF1A1A1A)
            drawCircle(borderColor, pieceRadius, Offset(cx, cy), style = Stroke(2.5f))
            drawCircle(borderColor, pieceRadius - 4f, Offset(cx, cy), style = Stroke(1.5f))

            // 棋子文字
            val text = getPieceChar(piece.type, piece.color)
            piecePaint.color = if (isRed) android.graphics.Color.rgb(196, 30, 58) else android.graphics.Color.rgb(26, 26, 26)
            val textY = cy - (piecePaint.ascent() + piecePaint.descent()) / 2f
            drawContext.canvas.nativeCanvas.drawText(text, cx, textY, piecePaint)
        }
    }
}

private fun getPieceChar(type: PieceType, color: PieceColor): String {
    return if (color == PieceColor.RED) {
        when (type) {
            PieceType.JU -> "車"
            PieceType.MA -> "馬"
            PieceType.XIANG -> "相"
            PieceType.SHI -> "仕"
            PieceType.JIANG -> "帥"
            PieceType.PAO -> "炮"
            PieceType.BING -> "兵"
        }
    } else {
        when (type) {
            PieceType.JU -> "車"
            PieceType.MA -> "馬"
            PieceType.XIANG -> "象"
            PieceType.SHI -> "士"
            PieceType.JIANG -> "將"
            PieceType.PAO -> "砲"
            PieceType.BING -> "卒"
        }
    }
}
