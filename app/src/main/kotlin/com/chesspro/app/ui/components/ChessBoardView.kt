package com.chesspro.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesspro.app.core.chess.*

/**
 * 象棋棋盘绘制组件
 */
@Composable
fun ChessBoardView(
    board: ChessBoard,
    selectedPosition: Position?,
    suggestedMove: Move?,
    onPositionClick: (Position) -> Unit,
    onPieceDrag: (Position, Position) -> Unit,
    modifier: Modifier = Modifier,
    boardSize: Dp = 350.dp,
    showCoordinates: Boolean = true
) {
    var dragStart by remember { mutableStateOf<Position?>(null) }
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column {
            // 棋盘主体
            ChessBoardCanvas(
                board = board,
                selectedPosition = selectedPosition,
                suggestedMove = suggestedMove,
                boardSize = boardSize,
                onPositionClick = onPositionClick,
                onDragStart = { dragStart = it },
                onDragEnd = { end ->
                    dragStart?.let { start ->
                        onPieceDrag(start, end)
                    }
                    dragStart = null
                }
            )
            
            // 坐标显示
            if (showCoordinates) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    (0..8).forEach { x ->
                        Text(
                            text = x.toString(),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChessBoardCanvas(
    board: ChessBoard,
    selectedPosition: Position?,
    suggestedMove: Move?,
    boardSize: Dp,
    onPositionClick: (Position) -> Unit,
    onDragStart: (Position) -> Unit,
    onDragEnd: (Position) -> Unit
) {
    var currentDragOffset by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    val cellSize = boardSize / 9
    
    Canvas(
        modifier = Modifier
            .size(boardSize + 24.dp, boardSize / 9 * 10 + 24.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(Color(0xFFDEB887))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val x = ((offset.x - 12.dp.toPx()) / cellSize.toPx()).toInt().coerceIn(0, 8)
                        val y = ((offset.y - 12.dp.toPx()) / cellSize.toPx()).toInt().coerceIn(0, 9)
                        currentDragOffset = offset
                        onDragStart(Position(x, y))
                    },
                    onDragEnd = {
                        currentDragOffset?.let { offset ->
                            val x = ((offset.x - 12.dp.toPx()) / cellSize.toPx()).toInt().coerceIn(0, 8)
                            val y = ((offset.y - 12.dp.toPx()) / cellSize.toPx()).toInt().coerceIn(0, 9)
                            onDragEnd(Position(x, y))
                        }
                        currentDragOffset = null
                    },
                    onDrag = { change, dragAmount ->
                        currentDragOffset = change.position
                        change.consume()
                    }
                )
            }
    ) {
        val padding = 12.dp.toPx()
        val lineSpacing = cellSize.toPx()
        val halfCell = lineSpacing / 2
        
        // 绘制棋盘线
        for (i in 0..9) {
            val y = padding + i * lineSpacing
            
            // 横线
            if (i != 5) {
                drawLine(
                    color = Color.Black,
                    start = Offset(padding, y),
                    end = Offset(padding + 8 * lineSpacing, y),
                    strokeWidth = 2f
                )
            }
        }
        
        // 竖线和特殊横线
        for (j in 0..8) {
            val x = padding + j * lineSpacing
            
            // 上半部分竖线
            drawLine(
                color = Color.Black,
                start = Offset(x, padding),
                end = Offset(x, padding + 4 * lineSpacing),
                strokeWidth = 2f
            )
            
            // 下半部分竖线
            drawLine(
                color = Color.Black,
                start = Offset(x, padding + 5 * lineSpacing),
                end = Offset(x, padding + 9 * lineSpacing),
                strokeWidth = 2f
            )
            
            // 楚河汉界两边的短竖线
            if (j != 0 && j != 8) {
                // 上半部分
                drawLine(
                    color = Color.Black,
                    start = Offset(x, padding + 4 * lineSpacing),
                    end = Offset(x, padding + 5 * lineSpacing),
                    strokeWidth = 2f
                )
            }
        }
        
        // 楚河汉界文字
        val canvas = drawContext.canvas.nativeCanvas
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 24f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.drawText("楚", (padding + 1 * lineSpacing).toFloat(), (padding + 4.5f * lineSpacing), paint)
        canvas.drawText("河", (padding + 3 * lineSpacing).toFloat(), (padding + 4.5f * lineSpacing), paint)
        canvas.drawText("漢", (padding + 5 * lineSpacing).toFloat(), (padding + 4.5f * lineSpacing), paint)
        canvas.drawText("界", (padding + 7 * lineSpacing).toFloat(), (padding + 4.5f * lineSpacing), paint)
        
        // 九宫格斜线
        // 右上
        drawLine(
            color = Color.Black,
            start = Offset(padding + 3 * lineSpacing, padding + 0 * lineSpacing),
            end = Offset(padding + 5 * lineSpacing, padding + 2 * lineSpacing),
            strokeWidth = 2f
        )
        // 左上
        drawLine(
            color = Color.Black,
            start = Offset(padding + 5 * lineSpacing, padding + 0 * lineSpacing),
            end = Offset(padding + 3 * lineSpacing, padding + 2 * lineSpacing),
            strokeWidth = 2f
        )
        // 右下
        drawLine(
            color = Color.Black,
            start = Offset(padding + 3 * lineSpacing, padding + 7 * lineSpacing),
            end = Offset(padding + 5 * lineSpacing, padding + 9 * lineSpacing),
            strokeWidth = 2f
        )
        // 左下
        drawLine(
            color = Color.Black,
            start = Offset(padding + 5 * lineSpacing, padding + 7 * lineSpacing),
            end = Offset(padding + 3 * lineSpacing, padding + 9 * lineSpacing),
            strokeWidth = 2f
        )
        
        // 绘制棋子
        for (piece in board.pieces) {
            val cx = padding + piece.position.x * lineSpacing
            val cy = padding + piece.position.y * lineSpacing
            
            // 棋子背景
            drawCircle(
                color = if (piece.color == PieceColor.RED) Color(0xFFFFCC80) else Color(0xFFE0E0E0),
                radius = halfCell - 4f,
                center = Offset(cx, cy)
            )
            
            drawCircle(
                color = Color.Black,
                radius = halfCell - 4f,
                center = Offset(cx, cy),
                style = Stroke(width = 2f)
            )
            
            // 高亮选中棋子
            if (selectedPosition == piece.position) {
                drawCircle(
                    color = Color(0xFFFFEB3B),
                    radius = halfCell - 8f,
                    center = Offset(cx, cy)
                )
            }
            
            // 高亮建议走法
            if (suggestedMove != null) {
                if (suggestedMove.from == piece.position) {
                    drawCircle(
                        color = Color(0xFF4CAF50),
                        radius = halfCell - 8f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 4f)
                    )
                }
                if (suggestedMove.to == piece.position) {
                    drawCircle(
                        color = Color(0xFF4CAF50),
                        radius = halfCell / 2,
                        center = Offset(cx, cy)
                    )
                }
            }
        }
    }
}

/**
 * 棋子组件
 */
@Composable
fun ChessPieceView(
    piece: ChessPiece,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (piece.color == PieceColor.RED) Color(0xFFFFCC80) else Color(0xFFE0E0E0)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = piece.getSymbol(),
            color = if (piece.color == PieceColor.RED) Color(0xFFD32F2F) else Color(0xFF212121),
            fontSize = (size.value * 0.55f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 走法提示小窗
 */
@Composable
fun MoveSuggestionView(
    move: Move?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF4CAF50)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "建议走法:",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (move != null) {
                Text(
                    text = "${move.piece.getShortSymbol()}${formatPosition(move.to)}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "计算中...",
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

private fun formatPosition(position: Position): String {
    val xStr = "一二三四五六七八九"[position.x]
    val yStr = when {
        position.y == 0 -> "一"
        position.y == 1 -> "二"
        position.y == 2 -> "三"
        position.y == 3 -> "四"
        position.y == 4 -> "五"
        position.y == 5 -> "六"
        position.y == 6 -> "七"
        position.y == 7 -> "八"
        position.y == 8 -> "九"
        position.y == 9 -> "十"
        else -> ""
    }
    return "$xStr$yStr"
}
