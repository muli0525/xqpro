package com.chesspro.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesspro.app.core.overlay.OverlayService

/**
 * 象棋Pro 主屏幕 - 简洁启动页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessMainScreen(
    onStartOverlay: () -> Unit = {}
) {
    val isOverlayRunning = remember { mutableStateOf(OverlayService.isRunning()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F3460)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            // Logo区域
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE6A817)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "棋",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "象棋 Pro",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Powered by Pikafish",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // 主按钮
            Button(
                onClick = {
                    onStartOverlay()
                    isOverlayRunning.value = !isOverlayRunning.value
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOverlayRunning.value) Color(0xFFE53935) else Color(0xFFE6A817)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    if (isOverlayRunning.value) Icons.Default.Close else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isOverlayRunning.value) "关闭悬浮窗" else "启动悬浮窗",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 使用说明
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "使用方法",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    StepItem(
                        step = "1",
                        title = "启动悬浮窗",
                        desc = "点击上方按钮，授权悬浮窗和屏幕录制权限",
                        icon = Icons.Default.Layers
                    )
                    StepItem(
                        step = "2",
                        title = "打开象棋APP",
                        desc = "切换到天天象棋、微信象棋等任意象棋应用",
                        icon = Icons.Default.Apps
                    )
                    StepItem(
                        step = "3",
                        title = "点击识别",
                        desc = "在悬浮窗中点击识别按钮，自动截屏识别棋盘",
                        icon = Icons.Default.CameraAlt
                    )
                    StepItem(
                        step = "4",
                        title = "查看分析",
                        desc = "Pikafish引擎自动分析，显示最佳走法和评分",
                        icon = Icons.Default.Psychology
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 特点
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FeatureChip(
                    text = "本地运行",
                    icon = Icons.Default.PhoneAndroid,
                    modifier = Modifier.weight(1f)
                )
                FeatureChip(
                    text = "无需联网",
                    icon = Icons.Default.WifiOff,
                    modifier = Modifier.weight(1f)
                )
                FeatureChip(
                    text = "实时分析",
                    icon = Icons.Default.Speed,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "v1.0.0",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun StepItem(
    step: String,
    title: String,
    desc: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFFE6A817)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Text(
                text = desc,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun FeatureChip(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color(0xFFE6A817),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}
