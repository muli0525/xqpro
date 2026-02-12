package com.chesspro.app.core.overlay

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesspro.app.ui.theme.ChessGold
import com.chesspro.app.ui.theme.ChessRed
import com.chesspro.app.ui.theme.SuggestionGreen

/**
 * 悬浮窗内容
 * 可调整大小、显示分析结果
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
    // 动画效果
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxSize()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color.Black.copy(alpha = 0.3f),
                spotColor = Color.Black.copy(alpha = 0.4f)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        // 双击放大/缩小
                        if (state.width > 350) {
                            onResize(300, 360)
                        } else {
                            onResize(400, 480)
                        }
                    }
                )
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF5F5F5).copy(alpha = pulseAlpha)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 标题栏
            OverlayTitleBar(
                state = state,
                onClose = onClose,
                onAnalyze = onAnalyze,
                onDrag = onDrag
            )

            // 内容区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    state.isAnalyzing -> {
                        AnalyzingView()
                    }
                    state.bestMoves.isNotEmpty() -> {
                        AnalysisResultView(
                            state = state,
                            onMoveClick = onMoveClick,
                            onReAnalyze = onAnalyze
                        )
                    }
                    else -> {
                        ReadyView(onAnalyze = onAnalyze)
                    }
                }
            }

            // 底部信息栏
            BottomInfoBar(state = state)
        }
    }
}

/**
 * 标题栏
 */
@Composable
private fun OverlayTitleBar(
    state: OverlayState,
    onClose: () -> Unit,
    onAnalyze: () -> Unit,
    onDrag: (Int, Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        // 长按显示操作菜单
                    }
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 左侧：标题和状态
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "象棋分析",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = state.analysisStatus,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }
        }

        // 右侧：操作按钮
        Row {
            IconButton(
                onClick = onAnalyze,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "重新分析",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 分析中视图
 */
@Composable
private fun AnalyzingView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = ChessGold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "AI正在分析...",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "请稍候",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

/**
 * 分析结果视图
 */
@Composable
private fun AnalysisResultView(
    state: OverlayState,
    onMoveClick: (SuggestedMove) -> Unit,
    onReAnalyze: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // 评估值
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "局面评估",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = state.evaluation,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.evaluation.startsWith("+"))
                            SuggestionGreen else if (state.evaluation.startsWith("-"))
                            ChessRed else Color.Gray
                    )
                }

                // 引擎状态
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = state.engineStatus.ifEmpty { "Pikafish" },
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = state.analysisStatus,
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        // 重新识别按钮 + 最佳走法列表
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "推荐走法",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            TextButton(onClick = onReAnalyze) {
                Text("重新识别", fontSize = 11.sp)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(state.bestMoves) { move ->
                MoveItem(
                    move = move,
                    isFirst = state.bestMoves.indexOf(move) == 0,
                    onClick = { onMoveClick(move) }
                )
            }
        }
    }
}

/**
 * 走法项
 */
@Composable
private fun MoveItem(
    move: SuggestedMove,
    isFirst: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isFirst) SuggestionGreen.copy(alpha = 0.15f)
            else Color(0xFFF0F0F0)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (isFirst) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = ChessGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = move.notation,
                    fontSize = if (isFirst) 18.sp else 14.sp,
                    fontWeight = if (isFirst) FontWeight.Bold else FontWeight.Normal
                )
            }

            if (move.uciMove.isNotEmpty()) {
                Text(
                    text = move.uciMove,
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

/**
 * 就绪视图
 */
@Composable
private fun ReadyView(onAnalyze: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lightbulb,
            contentDescription = null,
            tint = ChessGold,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "象棋AI助手",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "截取当前屏幕并识别棋盘",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onAnalyze,
            colors = ButtonDefaults.buttonColors(
                containerColor = ChessGold
            )
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("识别棋盘")
        }
    }
}

/**
 * 底部信息栏
 */
@Composable
private fun BottomInfoBar(state: OverlayState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFFE0E0E0),
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 最后一步
        state.lastMove?.let { move ->
            Text(
                text = "最后: $move",
                fontSize = 11.sp,
                color = Color.Gray
            )
        } ?: Spacer(modifier = Modifier.width(1.dp))

        // 提示
        Text(
            text = "双击放大/缩小",
            fontSize = 10.sp,
            color = Color.Gray
        )
    }
}
