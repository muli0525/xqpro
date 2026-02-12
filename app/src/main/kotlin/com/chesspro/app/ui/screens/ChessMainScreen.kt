package com.chesspro.app.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesspro.app.core.data.ChessRecordRepository
import com.chesspro.app.core.data.RecordCategory
import com.chesspro.app.core.overlay.OverlayService

// Pro象棋风格颜色
private val TealPrimary = Color(0xFF26C6B0)
private val TealDark = Color(0xFF1BA898)
private val TealLight = Color(0xFFE0F7F3)
private val BgColor = Color(0xFFF5F0E8)
private val CardBg = Color.White
private val TextPrimary = Color(0xFF333333)
private val TextSecondary = Color(0xFF888888)

/**
 * Pro象棋主屏幕 - 模仿Pro象棋风格
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessMainScreen(
    onStartOverlay: () -> Unit = {}
) {
    val context = LocalContext.current
    var isOverlayRunning by remember { mutableStateOf(OverlayService.isRunning()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    val hasOverlayPermission = remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    Scaffold(
        topBar = {
            // 顶部标题栏 - Pro象棋风格
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Pro象棋",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "菜单",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = TealPrimary
                )
            )
        },
        bottomBar = {
            // 底部导航栏
            NavigationBar(
                containerColor = CardBg,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("主页", fontSize = 11.sp) },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TealPrimary,
                        selectedTextColor = TealPrimary,
                        indicatorColor = TealLight
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                    label = { Text("连线", fontSize = 11.sp) },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TealPrimary,
                        selectedTextColor = TealPrimary,
                        indicatorColor = TealLight
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("我的", fontSize = 11.sp) },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TealPrimary,
                        selectedTextColor = TealPrimary,
                        indicatorColor = TealLight
                    )
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgColor)
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            when (selectedTab) {
                0 -> MainPage(
                    isOverlayRunning = isOverlayRunning,
                    hasOverlayPermission = hasOverlayPermission.value,
                    onStartOverlay = {
                        onStartOverlay()
                        isOverlayRunning = !isOverlayRunning
                    },
                    context = context
                )
                1 -> ConnectionPage()
                2 -> SettingsPage()
            }
        }
    }

    // 设置对话框
    if (showSettings) {
        SettingsDialog(onDismiss = { showSettings = false })
    }
}

/**
 * 主页内容
 */
@Composable
private fun MainPage(
    isOverlayRunning: Boolean,
    hasOverlayPermission: Boolean,
    onStartOverlay: () -> Unit,
    context: Context
) {
    var selectedCategory by remember { mutableIntStateOf(0) }
    val categories = RecordCategory.entries.toList()

    Column(modifier = Modifier.padding(16.dp)) {
        // 分类标签栏 - 模仿Pro象棋顶部tab
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            categories.forEachIndexed { index, cat ->
                Text(
                    text = cat.label,
                    fontSize = 14.sp,
                    fontWeight = if (index == selectedCategory) FontWeight.Bold else FontWeight.Normal,
                    color = if (index == selectedCategory) TealDark else TextSecondary,
                    modifier = Modifier.clickable { selectedCategory = index }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 棋谱列表 (3列网格)
        val records = ChessRecordRepository.getRecordsByCategory(categories[selectedCategory])
        val rows = records.chunked(3)
        rows.forEach { rowRecords ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowRecords.forEach { record ->
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp)
                            .clickable { },
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = record.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = record.author,
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1
                            )
                            Text(
                                text = record.date,
                                fontSize = 9.sp,
                                color = TealPrimary
                            )
                        }
                    }
                }
                // 补足空列
                repeat(3 - rowRecords.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 功能入口横排
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FeatureEntry(icon = Icons.Default.Cloud, label = "云棋谱", color = TealPrimary)
            FeatureEntry(icon = Icons.Default.Link, label = "连线", color = TealPrimary)
            FeatureEntry(icon = Icons.Default.GridOn, label = "续盘", color = TealPrimary)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 权限设置卡片 - 模仿Pro象棋
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TealLight),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "使用前先点击下方两个权限按钮并开启",
                    fontSize = 13.sp,
                    color = TealDark,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        // 悬浮窗权限
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardBg)
                                .border(1.dp, Color(0xFFDDDDDD), RoundedCornerShape(8.dp))
                                .clickable {
                                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("1、悬浮窗权限", fontSize = 14.sp, color = TextPrimary)
                            Spacer(modifier = Modifier.weight(1f))
                            if (hasOverlayPermission) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = TealPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 无障碍模式
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardBg)
                                .border(1.dp, Color(0xFFDDDDDD), RoundedCornerShape(8.dp))
                                .clickable {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("2、无障碍模式", fontSize = 14.sp, color = TextPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 开启连线按钮
                    Button(
                        onClick = onStartOverlay,
                        modifier = Modifier
                            .width(72.dp)
                            .height(80.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOverlayRunning) Color(0xFFE53935) else TealPrimary
                        ),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (isOverlayRunning) "关闭\n连线" else "开启\n连线",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 引擎信息卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("引擎配置", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Pikafish", fontSize = 12.sp, color = TealPrimary)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    InfoItem(label = "引擎", value = "皮卡鱼110")
                    InfoItem(label = "层数", value = "16")
                    InfoItem(label = "时间", value = "2秒")
                    InfoItem(label = "棋规", value = "天天棋规")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 使用说明
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("使用说明", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(12.dp))
                UsageStep("1", "点击「开启连线」，授权悬浮窗和屏幕录制权限")
                UsageStep("2", "打开天天象棋/微信象棋等任意象棋应用")
                UsageStep("3", "点击屏幕上的金色圆形按钮，自动截屏识别棋盘")
                UsageStep("4", "Pikafish引擎分析后，箭头直接画在棋盘上")
                UsageStep("5", "长按按钮开启自动模式，对方走完自动分析")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 特点标签
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TagChip("本地引擎", Modifier.weight(1f))
            TagChip("无需ROOT", Modifier.weight(1f))
            TagChip("免费使用", Modifier.weight(1f))
            TagChip("自动识别", Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 连线页（占位）
 */
@Composable
private fun ConnectionPage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(80.dp))
        Icon(
            Icons.Default.Link,
            contentDescription = null,
            tint = TealPrimary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("连线模式", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "请在主页点击「开启连线」后\n切换到象棋APP使用",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 设置页
 */
@Composable
private fun SettingsPage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // 核心设置
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("核心设置", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(16.dp))

                Text("本地引擎", fontSize = 13.sp, color = TextSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("皮卡鱼110", fontSize = 15.sp, color = TextPrimary,
                        modifier = Modifier.weight(1f))
                    Icon(Icons.Default.CheckCircle, contentDescription = null,
                        tint = TealPrimary, modifier = Modifier.size(20.dp))
                }

                Divider(color = Color(0xFFEEEEEE))
                Spacer(modifier = Modifier.height(12.dp))

                Text("引擎配置", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))

                SettingRow("使用层数", "16")
                SettingRow("使用时间", "2秒")
                SettingRow("棋规设置", "天天棋规")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("关于", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Pro象棋 v1.0.0", fontSize = 13.sp, color = TextSecondary)
                Text("Powered by Pikafish Engine", fontSize = 13.sp, color = TextSecondary)
            }
        }
    }
}

// ====== 小组件 ======

@Composable
private fun FeatureEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { }
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = color)
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun UsageStep(num: String, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(TealPrimary),
            contentAlignment = Alignment.Center
        ) {
            Text(num, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TagChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = TealLight
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 11.sp,
            color = TealDark,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = TextPrimary)
        Text(value, fontSize = 14.sp, color = TealPrimary)
    }
}

@Composable
private fun SettingsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("引擎设置", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text("引擎: 皮卡鱼 110 (Pikafish)", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("搜索层数: 16", fontSize = 14.sp)
                Text("搜索时间: 2秒", fontSize = 14.sp)
                Text("棋规: 天天棋规", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Pro象棋 v1.0.0\nPowered by Pikafish",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("确定", color = TealPrimary) }
        }
    )
}
