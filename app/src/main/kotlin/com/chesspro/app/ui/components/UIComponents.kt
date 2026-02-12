package com.chesspro.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesspro.app.core.chess.Move
import com.chesspro.app.core.chess.PieceColor

/**
 * 走法建议视图
 */
@Composable
fun MoveSuggestionView(
    move: Move,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Card(
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(12.dp),
                ambientColor = Color(0xFF4CAF50).copy(alpha = glowAlpha),
                spotColor = Color(0xFF4CAF50).copy(alpha = glowAlpha)
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "AI建议走法",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = getMoveNotation(move),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // 置信度指示
            ConfidenceIndicator(confidence = 0.85f)
        }
    }
}

/**
 * 置信度指示器
 */
@Composable
private fun ConfidenceIndicator(
    confidence: Float,
    modifier: Modifier = Modifier
) {
    val color = when {
        confidence >= 0.7f -> Color(0xFF4CAF50)
        confidence >= 0.5f -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${(confidence * 100).toInt()}%",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = "置信度",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * AI思考状态视图
 */
@Composable
fun AIThinkingView(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thinkingAlpha"
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = alpha)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "AI正在思考...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * 游戏结果视图
 */
@Composable
fun GameResultView(
    winner: PieceColor?,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val message = when (winner) {
        PieceColor.RED -> "红方获胜！"
        PieceColor.BLACK -> "黑方获胜！"
        null -> "平局"
    }

    val icon = when (winner) {
        PieceColor.RED -> Icons.Default.EmojiEvents
        PieceColor.BLACK -> Icons.Default.EmojiEvents
        null -> Icons.Default.Handshake
    }

    val iconColor = when (winner) {
        PieceColor.RED -> Color(0xFFE53935)
        PieceColor.BLACK -> Color(0xFF212121)
        null -> Color(0xFF757575)
    }

    AlertDialog(
        onDismissRequest = { },
        modifier = modifier,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = "游戏结束",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        confirmButton = {
            Button(onClick = onRestart) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("再来一局")
            }
        },
        dismissButton = {
            TextButton(onClick = { }) {
                Text("关闭")
            }
        }
    )
}

/**
 * 玩家切换提示
 */
@Composable
fun PlayerTurnHint(
    currentPlayer: PieceColor,
    modifier: Modifier = Modifier
) {
    val playerColor = if (currentPlayer == PieceColor.RED) {
        Color(0xFFE53935)
    } else {
        Color(0xFF212121)
    }

    Row(
        modifier = modifier
            .background(
                color = playerColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.AccessTime,
            contentDescription = null,
            tint = playerColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (currentPlayer == PieceColor.RED) "红方走棋" else "黑方走棋",
            color = playerColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 获取走法表示
 */
private fun getMoveNotation(move: Move): String {
    val fromNotation = "${'a' + move.from.x}${10 - move.from.y}"
    val toNotation = "${'a' + move.to.x}${10 - move.to.y}"
    return "$fromNotation → $toNotation"
}
