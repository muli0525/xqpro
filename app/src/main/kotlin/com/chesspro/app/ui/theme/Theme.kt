package com.chesspro.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 中国象棋主题颜色
val ChessRed = Color(0xFFE53935)
val ChessBlack = Color(0xFF212121)
val ChessBoardGreen = Color(0xFF81C784)
val ChessBoardLightGreen = Color(0xFFA5D6A7)
val ChessGold = Color(0xFFFFD700)

// 木纹色系
val WoodLight = Color(0xFFF5DEB3)
val WoodMedium = Color(0xFFE8D4A8)
val WoodDark = Color(0xFFDEB887)
val WoodBorder = Color(0xFF8B4513)
val WoodBorderLight = Color(0xFFA0522D)

// 棋子颜色
val PieceRed = Color(0xFFC41E3A)
val PieceRedDark = Color(0xFF8B0000)
val PieceRedHighlight = Color(0xFFE8D0D5)
val PieceBlack = Color(0xFF1A1A1A)
val PieceBlackHighlight = Color(0xFF4A4A4A)

// AI建议颜色
val SuggestionGreen = Color(0xFF4CAF51)
val SuggestionBlue = Color(0xFF2196F3)

private val DarkColorScheme = darkColorScheme(
    primary = ChessGold,
    secondary = ChessRed,
    tertiary = ChessBlack,
    background = Color(0xFF1B1B1B),
    surface = Color(0xFF2D2D2D),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = ChessBlack,
    secondary = ChessRed,
    tertiary = ChessGold,
    background = WoodLight,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color(0xFF1B1B1B),
    onSurface = Color(0xFF1B1B1B),
    primaryContainer = WoodMedium,
    onPrimaryContainer = ChessBlack,
    secondaryContainer = PieceRed.copy(alpha = 0.1f),
    onSecondaryContainer = ChessRed,
    tertiaryContainer = ChessGold.copy(alpha = 0.2f),
    onTertiaryContainer = ChessBlack
)

@Composable
fun ChineseChessProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // 关闭动态颜色，使用自定义主题
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                window.statusBarColor = colorScheme.primary.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
