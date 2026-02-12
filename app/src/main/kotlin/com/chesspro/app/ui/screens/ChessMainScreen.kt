package com.chesspro.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chesspro.app.core.chess.*
import com.chesspro.app.ui.ChessViewModel
import com.chesspro.app.ui.GameMode
import com.chesspro.app.ui.components.*
import com.chesspro.app.ui.theme.ChessGold
import com.chesspro.app.ui.theme.ChessRed

/**
 * 象棋APP主屏幕
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
fun ChessMainScreen(
    viewModel: ChessViewModel = viewModel(),
    onStartOverlay: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedPosition by viewModel.selectedPosition.collectAsState()
    val suggestedMove by viewModel.suggestedMove.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()
    val editPieceType by viewModel.editPieceType.collectAsState()
    val editPieceColor by viewModel.editPieceColor.collectAsState()
    val engineResult by viewModel.engineResult.collectAsState()

    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("象棋 Pro", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = if (currentMode == GameMode.BOARD_EDIT)
                                MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = if (currentMode == GameMode.BOARD_EDIT) "摆棋" else "走棋",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // 引擎状态
                        Surface(
                            color = if (uiState.engineReady)
                                Color(0xFF4CAF50).copy(alpha = 0.2f)
                            else Color.Gray.copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = uiState.engineStatus,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onStartOverlay) {
                        Icon(Icons.Default.Layers, contentDescription = "悬浮窗",
                            tint = ChessGold)
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 状态栏
            GameStatusBar(
                currentPlayer = uiState.currentPlayer,
                moveCount = uiState.moveCount,
                evaluation = uiState.evaluation,
                depth = uiState.analysisDepth,
                isThinking = uiState.isThinking
            )

            // 棋盘
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                ChessBoardView(
                    board = viewModel.chessBoard,
                    selectedPosition = selectedPosition,
                    suggestedMove = suggestedMove,
                    onPositionClick = { viewModel.onPositionClick(it) },
                    onPieceDrag = { from, to -> viewModel.onPieceDrag(from, to) },
                    boardSize = 380.dp
                )
            }

            // 分析提示
            AnimatedVisibility(
                visible = suggestedMove != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                suggestedMove?.let { move ->
                    MoveSuggestionCard(
                        move = move,
                        evaluation = uiState.evaluation,
                        depth = uiState.analysisDepth,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // AI思考指示
            AnimatedVisibility(
                visible = uiState.isThinking && suggestedMove == null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                AIThinkingView(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }

            // 摆棋模式 - 棋子选择器
            if (currentMode == GameMode.BOARD_EDIT) {
                PieceSelector(
                    selectedType = editPieceType,
                    selectedColor = editPieceColor,
                    onTypeSelect = { viewModel.setEditPieceType(it) },
                    onColorToggle = {
                        viewModel.setEditPieceColor(
                            if (editPieceColor == PieceColor.RED) PieceColor.BLACK else PieceColor.RED
                        )
                    }
                )
            }

            // 操作按钮区
            ActionBar(
                currentMode = currentMode,
                isThinking = uiState.isThinking,
                hasMoves = viewModel.chessBoard.getMoveCount() > 0,
                onModeToggle = {
                    viewModel.setMode(
                        if (currentMode == GameMode.BOARD_EDIT) GameMode.PLAY else GameMode.BOARD_EDIT
                    )
                },
                onAnalyze = { viewModel.analyzeCurrentPosition() },
                onUndo = { viewModel.undoMove() },
                onRestart = { viewModel.restart() },
                onClear = { viewModel.clearBoard() },
                onTogglePlayer = { viewModel.toggleCurrentPlayer() }
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // 设置对话框
    if (showSettings) {
        SettingsDialog(
            onDismiss = { showSettings = false },
            onDepthChange = { viewModel.setEngineDepth(it) }
        )
    }

    // 棋子选择器对话框
    if (uiState.showPiecePicker && uiState.pickedPosition != null) {
        PiecePickerDialog(
            position = uiState.pickedPosition!!,
            onPieceSelected = { type, color ->
                viewModel.addPiece(type, color, uiState.pickedPosition!!)
            },
            onDismiss = { }
        )
    }
}

/**
 * 游戏状态栏
 */
@Composable
fun GameStatusBar(
    currentPlayer: PieceColor,
    moveCount: Int,
    evaluation: String,
    depth: Int,
    isThinking: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        if (currentPlayer == PieceColor.RED) ChessRed else Color.Black
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (currentPlayer == PieceColor.RED) "红方" else "黑方",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (depth > 0) {
            Text(
                text = "评估: $evaluation (d$depth)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "第${moveCount / 2 + 1}手",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * 走法建议卡片
 */
@Composable
fun MoveSuggestionCard(
    move: Move,
    evaluation: String,
    depth: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = ChessGold,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "最佳走法",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = move.moveNotation.ifEmpty { "${move.from} → ${move.to}" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = evaluation,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (evaluation.startsWith("+")) Color(0xFF4CAF50)
                    else if (evaluation.startsWith("-")) ChessRed
                    else Color.Gray
                )
                if (depth > 0) {
                    Text(
                        text = "深度 $depth",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

/**
 * 棋子选择器（摆棋模式）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PieceSelector(
    selectedType: PieceType?,
    selectedColor: PieceColor,
    onTypeSelect: (PieceType?) -> Unit,
    onColorToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("选择棋子:", style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = onColorToggle) {
                Text(
                    text = if (selectedColor == PieceColor.RED) "红方" else "黑方",
                    color = if (selectedColor == PieceColor.RED) ChessRed else Color.Black
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val pieces = PieceType.entries.toTypedArray()
            // 取消选择按钮
            FilterChip(
                selected = selectedType == null,
                onClick = { onTypeSelect(null) },
                label = { Text("清除", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )
            pieces.forEach { type ->
                val name = ChessPiece(type, selectedColor, Position(0, 0)).getSymbol()
                FilterChip(
                    selected = selectedType == type,
                    onClick = { onTypeSelect(type) },
                    label = { Text(name, fontSize = 14.sp) }
                )
            }
        }
    }
}

/**
 * 操作按钮栏
 */
@Composable
fun ActionBar(
    currentMode: GameMode,
    isThinking: Boolean,
    hasMoves: Boolean,
    onModeToggle: () -> Unit,
    onAnalyze: () -> Unit,
    onUndo: () -> Unit,
    onRestart: () -> Unit,
    onClear: () -> Unit,
    onTogglePlayer: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 模式切换
        FilledTonalButton(onClick = onModeToggle) {
            Icon(
                if (currentMode == GameMode.BOARD_EDIT) Icons.Default.PlayArrow else Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (currentMode == GameMode.BOARD_EDIT) "走棋" else "摆棋", fontSize = 13.sp)
        }

        // 分析
        Button(
            onClick = onAnalyze,
            enabled = !isThinking,
            colors = ButtonDefaults.buttonColors(containerColor = ChessGold)
        ) {
            Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("分析", fontSize = 13.sp)
        }

        // 切换执子方
        OutlinedButton(onClick = onTogglePlayer) {
            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("换手", fontSize = 13.sp)
        }

        if (currentMode == GameMode.PLAY && hasMoves) {
            OutlinedButton(onClick = onUndo, enabled = !isThinking) {
                Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("撤销", fontSize = 13.sp)
            }
        }

        // 重置/清空
        OutlinedButton(
            onClick = if (currentMode == GameMode.BOARD_EDIT) onClear else onRestart
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                if (currentMode == GameMode.BOARD_EDIT) "清空" else "重置",
                fontSize = 13.sp
            )
        }
    }
}

/**
 * 棋子选择器对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PiecePickerDialog(
    position: Position,
    onPieceSelected: (PieceType, PieceColor) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedColor by remember { mutableStateOf(PieceColor.RED) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择棋子") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilterChip(
                        selected = selectedColor == PieceColor.RED,
                        onClick = { selectedColor = PieceColor.RED },
                        label = { Text("红方") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = selectedColor == PieceColor.BLACK,
                        onClick = { selectedColor = PieceColor.BLACK },
                        label = { Text("黑方") }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                PieceType.entries.forEach { type ->
                    val name = ChessPiece(type, selectedColor, Position(0, 0)).getSymbol()
                    TextButton(
                        onClick = { onPieceSelected(type, selectedColor) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(name, fontSize = 20.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 设置对话框
 */
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onDepthChange: (Int) -> Unit = {}
) {
    var searchDepth by remember { mutableIntStateOf(18) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("设置")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 搜索深度
                Text("Pikafish搜索深度", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = searchDepth.toFloat(),
                    onValueChange = {
                        searchDepth = it.toInt()
                        onDepthChange(it.toInt())
                    },
                    valueRange = 1f..30f,
                    steps = 28
                )
                Text(
                    text = "深度: $searchDepth",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                Divider()

                Text(
                    text = "象棋 Pro v1.0.0\nPowered by Pikafish",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("确定") }
        }
    )
}
