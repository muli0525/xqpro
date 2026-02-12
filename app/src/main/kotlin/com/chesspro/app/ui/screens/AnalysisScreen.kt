package com.chesspro.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesspro.app.core.chess.PieceColor
import com.chesspro.app.core.chess.PieceType
import com.chesspro.app.core.chess.Position
import com.chesspro.app.core.capture.RecognizedPiece
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// 颜色定义
private val BoardBg = Color(0xFFD4A843)
private val BoardLine = Color(0xFF4A3728)
private val RedPiece = Color(0xFFCC0000)
private val BlackPiece = Color(0xFF222222)
private val PieceBg = Color(0xFFFFF8DC)
private val ArrowColor = Color(0xCC26C6B0)
private val ToolbarBg = Color(0xFF2A2A2A)
private val AnalysisBg = Color(0xFF1E1E2E)
private val TealPrimary = Color(0xFF26C6B0)

/**
 * 分析数据
 */
data class AnalysisData(
    val pieces: List<RecognizedPiece> = emptyList(),
    val bestMove: String = "",
    val score: String = "0.00",
    val depth: Int = 0,
    val notation: String = "",
    val moveHistory: List<String> = emptyList(),
    val currentMoveIndex: Int = -1,
    val isAnalyzing: Boolean = false,
    val winRate: Float = 50f
)

/**
 * 棋盘分析界面 - 模仿Pro象棋图9/10
 * 顶部工具栏 + 棋盘(带箭头) + 分析信息栏 + 走法记录
 */
@Composable
fun AnalysisScreen(
    analysisData: AnalysisData,
    onBack: () -> Unit = {},
    onCapture: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    onNavigateForward: () -> Unit = {},
    onNavigateFirst: () -> Unit = {},
    onNavigateLast: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AnalysisBg)
    ) {
        // 顶部工具栏
        TopToolbar(onBack = onBack, onCapture = onCapture)

        // 棋盘
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            ChessBoardCanvas(
                pieces = analysisData.pieces,
                bestMove = analysisData.bestMove
            )
        }

        // 分析信息栏
        AnalysisInfoBar(analysisData)

        // 走法标签栏
        MoveTabBar(analysisData)

        // 走法记录 + 导航
        MoveHistoryBar(
            analysisData = analysisData,
            onNavigateBack = onNavigateBack,
            onNavigateForward = onNavigateForward,
            onNavigateFirst = onNavigateFirst,
            onNavigateLast = onNavigateLast
        )
    }
}

/**
 * 顶部工具栏 - 图标行
 */
@Composable
private fun TopToolbar(
    onBack: () -> Unit,
    onCapture: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ToolbarBg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolbarIcon(Icons.Default.ArrowBack, "返回", onBack)
        ToolbarIcon(Icons.Default.DesktopWindows, "连线")
        ToolbarIcon(Icons.Default.Fullscreen, "全屏")
        ToolbarIcon(Icons.Default.Search, "搜索")
        ToolbarIcon(Icons.Default.FlashOn, "闪电", onCapture)
        ToolbarIcon(Icons.Default.Tune, "设置")
        ToolbarIcon(Icons.Default.Timer, "计时")
    }
}

@Composable
private fun ToolbarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit = {}
) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = desc, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/**
 * 棋盘Canvas - 画棋盘+棋子+箭头
 */
@Composable
private fun ChessBoardCanvas(
    pieces: List<RecognizedPiece>,
    bestMove: String
) {
    val density = LocalDensity.current

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 10.5f)
            .padding(8.dp)
    ) {
        val canvasW = size.width
        val canvasH = size.height
        val margin = canvasW * 0.05f
        val boardW = canvasW - margin * 2
        val boardH = canvasH - margin * 2
        val cellW = boardW / 8f
        val cellH = boardH / 9f
        val pieceRadius = min(cellW, cellH) * 0.42f

        // 画棋盘背景
        drawRect(BoardBg, Offset.Zero, size)

        // 画网格线
        drawBoardLines(margin, cellW, cellH)

        // 画楚河汉界
        drawRiverText(margin, cellW, cellH)

        // 画棋子
        pieces.forEach { piece ->
            val cx = margin + piece.position.x * cellW
            val cy = margin + piece.position.y * cellH
            drawPiece(cx, cy, pieceRadius, piece.type, piece.color)
        }

        // 画箭头
        if (bestMove.length >= 4) {
            val fromCol = bestMove[0] - 'a'
            val fromRow = bestMove[1] - '0'
            val toCol = bestMove[2] - 'a'
            val toRow = bestMove[3] - '0'

            if (fromCol in 0..8 && fromRow in 0..9 && toCol in 0..8 && toRow in 0..9) {
                val fromX = margin + fromCol * cellW
                val fromY = margin + fromRow * cellH
                val toX = margin + toCol * cellW
                val toY = margin + toRow * cellH

                drawMoveArrow(fromX, fromY, toX, toY, pieceRadius * 0.7f)
            }
        }
    }
}

private fun DrawScope.drawBoardLines(margin: Float, cellW: Float, cellH: Float) {
    val lineColor = BoardLine
    val strokeWidth = 2f

    // 横线
    for (i in 0..9) {
        val y = margin + i * cellH
        drawLine(lineColor, Offset(margin, y), Offset(margin + 8 * cellW, y), strokeWidth)
    }

    // 竖线 - 上半部
    for (i in 0..8) {
        val x = margin + i * cellW
        drawLine(lineColor, Offset(x, margin), Offset(x, margin + 4 * cellH), strokeWidth)
    }
    // 竖线 - 下半部
    for (i in 0..8) {
        val x = margin + i * cellW
        drawLine(lineColor, Offset(x, margin + 5 * cellH), Offset(x, margin + 9 * cellH), strokeWidth)
    }
    // 边框竖线穿过河界
    drawLine(lineColor, Offset(margin, margin + 4 * cellH), Offset(margin, margin + 5 * cellH), strokeWidth)
    drawLine(lineColor, Offset(margin + 8 * cellW, margin + 4 * cellH), Offset(margin + 8 * cellW, margin + 5 * cellH), strokeWidth)

    // 九宫斜线
    val palaceLeft = margin + 3 * cellW
    val palaceRight = margin + 5 * cellW
    // 上方九宫
    drawLine(lineColor, Offset(palaceLeft, margin), Offset(palaceRight, margin + 2 * cellH), strokeWidth)
    drawLine(lineColor, Offset(palaceRight, margin), Offset(palaceLeft, margin + 2 * cellH), strokeWidth)
    // 下方九宫
    drawLine(lineColor, Offset(palaceLeft, margin + 7 * cellH), Offset(palaceRight, margin + 9 * cellH), strokeWidth)
    drawLine(lineColor, Offset(palaceRight, margin + 7 * cellH), Offset(palaceLeft, margin + 9 * cellH), strokeWidth)
}

private fun DrawScope.drawRiverText(margin: Float, cellW: Float, cellH: Float) {
    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(60, 74, 55, 40)
        textSize = cellH * 0.55f
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val riverY = margin + 4.5f * cellH + textPaint.textSize * 0.35f
    drawContext.canvas.nativeCanvas.drawText("楚 河", margin + 2 * cellW, riverY, textPaint)
    drawContext.canvas.nativeCanvas.drawText("漢 界", margin + 6 * cellW, riverY, textPaint)
}

private fun DrawScope.drawPiece(cx: Float, cy: Float, radius: Float, type: PieceType, color: PieceColor) {
    val isRed = color == PieceColor.RED

    // 阴影
    drawCircle(Color(0x40000000), radius, Offset(cx + 2f, cy + 2f))
    // 棋子底色
    drawCircle(PieceBg, radius, Offset(cx, cy))
    // 边框
    drawCircle(
        if (isRed) RedPiece else BlackPiece,
        radius, Offset(cx, cy),
        style = Stroke(width = radius * 0.08f)
    )
    // 内圈
    drawCircle(
        if (isRed) RedPiece else BlackPiece,
        radius * 0.82f, Offset(cx, cy),
        style = Stroke(width = radius * 0.04f)
    )

    // 文字
    val text = getPieceChar(type, color)
    val textPaint = android.graphics.Paint().apply {
        this.color = if (isRed) android.graphics.Color.rgb(204, 0, 0) else android.graphics.Color.rgb(34, 34, 34)
        textSize = radius * 1.1f
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(text, cx, cy + textPaint.textSize * 0.35f, textPaint)
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

private fun DrawScope.drawMoveArrow(
    fromX: Float, fromY: Float, toX: Float, toY: Float, headSize: Float
) {
    val angle = atan2((toY - fromY).toDouble(), (toX - fromX).toDouble())

    // 起点高亮圆
    drawCircle(ArrowColor, headSize * 1.2f, Offset(fromX, fromY))

    // 箭头线
    val lineStartX = fromX + headSize * cos(angle).toFloat()
    val lineStartY = fromY + headSize * sin(angle).toFloat()
    val lineEndX = toX - headSize * cos(angle).toFloat()
    val lineEndY = toY - headSize * sin(angle).toFloat()

    drawLine(ArrowColor, Offset(lineStartX, lineStartY), Offset(lineEndX, lineEndY), strokeWidth = 8f)

    // 箭头头部
    val arrowAngle = Math.toRadians(25.0)
    val path = Path().apply {
        moveTo(toX, toY)
        lineTo(
            toX - headSize * cos(angle - arrowAngle).toFloat(),
            toY - headSize * sin(angle - arrowAngle).toFloat()
        )
        lineTo(
            toX - headSize * cos(angle + arrowAngle).toFloat(),
            toY - headSize * sin(angle + arrowAngle).toFloat()
        )
        close()
    }
    drawPath(path, ArrowColor, style = Fill)
}

/**
 * 分析信息栏 - 分析/开局库/导航 + 标准/精简
 */
@Composable
private fun AnalysisInfoBar(data: AnalysisData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF333333))
    ) {
        // Tab行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TabText("分析", true)
                TabText("开局库", false)
                TabText("导航", false)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ModeChip("标准", true)
                ModeChip("精简", false)
            }
        }

        // 副标签
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SubTabText("招法", true)
            SubTabText("胜率", false)
            SubTabText("分数", false)
        }

        // 走法+分数行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = data.notation.ifEmpty { "等待分析..." },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )

            // 胜率条
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF555555))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(data.winRate / 100f)
                        .background(TealPrimary)
                )
                Text(
                    text = "${data.winRate.toInt()}%",
                    fontSize = 9.sp,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = data.score,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    data.score.startsWith("+") -> Color(0xFF4CAF50)
                    data.score.startsWith("-") -> Color(0xFFE53935)
                    else -> Color.White
                }
            )
        }
    }
}

@Composable
private fun TabText(text: String, selected: Boolean) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) Color.White else Color(0xFF999999)
    )
}

@Composable
private fun SubTabText(text: String, selected: Boolean) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = if (selected) TealPrimary else Color(0xFF999999)
    )
}

@Composable
private fun ModeChip(text: String, selected: Boolean) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (selected) TealPrimary else Color(0xFF555555)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/**
 * 走法标签栏
 */
@Composable
private fun MoveTabBar(data: AnalysisData) {
    if (data.moveHistory.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2A2A2A))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            data.moveHistory.forEachIndexed { index, move ->
                val isCurrent = index == data.currentMoveIndex
                Text(
                    text = "${index + 1}. $move",
                    fontSize = 12.sp,
                    color = if (isCurrent) TealPrimary else Color(0xFF999999),
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clickable { }
                )
            }
        }
    }
}

/**
 * 底部导航栏 - 前进/后退
 */
@Composable
private fun MoveHistoryBar(
    analysisData: AnalysisData,
    onNavigateBack: () -> Unit,
    onNavigateForward: () -> Unit,
    onNavigateFirst: () -> Unit,
    onNavigateLast: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF222222))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 第一步
        IconButton(onClick = onNavigateFirst, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "第一步", tint = Color.White, modifier = Modifier.size(24.dp))
        }
        // 后退
        IconButton(onClick = onNavigateBack, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "后退", tint = Color.White, modifier = Modifier.size(28.dp))
        }
        // 前进
        IconButton(onClick = onNavigateForward, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.ChevronRight, contentDescription = "前进", tint = Color.White, modifier = Modifier.size(28.dp))
        }
        // 最后一步
        IconButton(onClick = onNavigateLast, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.SkipNext, contentDescription = "最后", tint = Color.White, modifier = Modifier.size(24.dp))
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 引擎信息
        if (analysisData.depth > 0) {
            Text(
                text = "d${analysisData.depth}",
                fontSize = 11.sp,
                color = TealPrimary
            )
        }

        // 分享/保存按钮
        IconButton(onClick = { }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Save, contentDescription = "保存", tint = Color(0xFF999999), modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = { }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Share, contentDescription = "分享", tint = Color(0xFF999999), modifier = Modifier.size(20.dp))
        }
    }
}
