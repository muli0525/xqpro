package com.chesspro.app.core.overlay

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkBg = Color(0xFF1E1E2E)
private val DarkCard = Color(0xFF2A2A3C)
private val AccentGold = Color(0xFFE6A817)
private val AccentGreen = Color(0xFF4CAF50)
private val AccentRed = Color(0xFFE53935)
private val TextWhite = Color(0xFFEEEEEE)
private val TextGray = Color(0xFF999999)

/**
 * 悬浮窗内容 - 紧凑型专业分析界面
 */
@Composable
fun OverlayContent(
    state: OverlayState,
    onClose: () -> Unit,
    onAnalyze: () -> Unit,
    onMoveClick: (SuggestedMove) -> Unit,
    onResize: (Int, Int) -> Unit,
    onDrag: (Int, Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部标题栏
            TitleBar(state = state, onClose = onClose, onAnalyze = onAnalyze)

            // 主内容
            when {
                state.isAnalyzing -> AnalyzingContent(state)
                state.bestMoves.isNotEmpty() -> ResultContent(state, onMoveClick, onAnalyze)
                else -> IdleContent(onAnalyze)
            }
        }
    }
}

/**
 * 标题栏 - 深色紧凑
 */
@Composable
private fun TitleBar(
    state: OverlayState,
    onClose: () -> Unit,
    onAnalyze: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkCard)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 状态指示灯
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = when {
                        state.isAnalyzing -> AccentGold
                        state.bestMoves.isNotEmpty() -> AccentGreen
                        else -> TextGray
                    },
                    shape = RoundedCornerShape(4.dp)
                )
        )
        Spacer(modifier = Modifier.width(6.dp))

        // 标题
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "象棋分析",
                color = TextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = state.analysisStatus,
                color = TextGray,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 识别按钮
        IconButton(
            onClick = onAnalyze,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = "识别",
                tint = AccentGold,
                modifier = Modifier.size(16.dp)
            )
        }

        // 关闭按钮
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "关闭",
                tint = TextGray,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * 分析中状态
 */
@Composable
private fun AnalyzingContent(state: OverlayState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = AccentGold,
            strokeWidth = 3.dp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = state.analysisStatus,
            fontSize = 13.sp,
            color = TextWhite,
            textAlign = TextAlign.Center
        )
        if (state.evaluation != "0.00") {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.evaluation,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AccentGold
            )
        }
    }
}

/**
 * 分析结果
 */
@Composable
private fun ResultContent(
    state: OverlayState,
    onMoveClick: (SuggestedMove) -> Unit,
    onReAnalyze: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        // 评分条
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkCard, RoundedCornerShape(8.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 评分
            Column {
                Text("评分", fontSize = 9.sp, color = TextGray)
                Text(
                    text = state.evaluation,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        state.evaluation.startsWith("+") -> AccentGreen
                        state.evaluation.startsWith("-") -> AccentRed
                        else -> TextWhite
                    }
                )
            }

            // 引擎信息
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = state.engineStatus.ifEmpty { "Pikafish" },
                    fontSize = 9.sp,
                    color = TextGray
                )
                Text(
                    text = state.analysisStatus,
                    fontSize = 9.sp,
                    color = AccentGold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 走法列表标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("推荐走法", fontSize = 11.sp, color = TextGray)
            TextButton(
                onClick = onReAnalyze,
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = AccentGold
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text("重新识别", fontSize = 10.sp, color = AccentGold)
            }
        }

        // 走法列表
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(state.bestMoves) { move ->
                val isFirst = state.bestMoves.indexOf(move) == 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isFirst) AccentGold.copy(alpha = 0.15f) else DarkCard,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onMoveClick(move) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isFirst) {
                            Text("★ ", fontSize = 14.sp, color = AccentGold)
                        }
                        Text(
                            text = move.notation,
                            fontSize = if (isFirst) 15.sp else 13.sp,
                            fontWeight = if (isFirst) FontWeight.Bold else FontWeight.Normal,
                            color = TextWhite
                        )
                    }
                    if (move.uciMove.isNotEmpty()) {
                        Text(
                            text = move.uciMove,
                            fontSize = 9.sp,
                            color = TextGray
                        )
                    }
                }
            }
        }
    }
}

/**
 * 空闲状态 - 等待识别
 */
@Composable
private fun IdleContent(onAnalyze: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CameraAlt,
            contentDescription = null,
            tint = AccentGold,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "点击识别棋盘",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TextWhite
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "请先打开象棋APP\n确保棋盘完整显示在屏幕上",
            fontSize = 11.sp,
            color = TextGray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onAnalyze,
            colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("识别棋盘", fontSize = 14.sp)
        }
    }
}
