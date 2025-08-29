package com.jacksen168.syncclipboard.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 基础颜色定义，与XML中的颜色保持一致
val Primary50 = Color(0xFFE8F5E8)
val Primary100 = Color(0xFFC8E6C9)
val Primary200 = Color(0xFFA5D6A7)
val Primary300 = Color(0xFF81C784)
val Primary400 = Color(0xFF66BB6A)
val Primary500 = Color(0xFF4CAF50)
val Primary600 = Color(0xFF43A047)
val Primary700 = Color(0xFF388E3C)
val Primary800 = Color(0xFF2E7D32)
val Primary900 = Color(0xFF1B5E20)

val Secondary50 = Color(0xFFE3F2FD)
val Secondary100 = Color(0xFFBBDEFB)
val Secondary200 = Color(0xFF90CAF9)
val Secondary300 = Color(0xFF64B5F6)
val Secondary400 = Color(0xFF42A5F5)
val Secondary500 = Color(0xFF2196F3)
val Secondary600 = Color(0xFF1E88E5)
val Secondary700 = Color(0xFF1976D2)
val Secondary800 = Color(0xFF1565C0)
val Secondary900 = Color(0xFF0D47A1)

val Accent500 = Color(0xFFFF9800)
val Accent300 = Color(0xFFFFB74D)

val Neutral50 = Color(0xFFFEFBFF)
val Neutral800 = Color(0xFF1E1E1E)
val Neutral900 = Color(0xFF121316)

val Error500 = Color(0xFFBA1A1A)
val Error300 = Color(0xFFFFB4AB)

// 兼容的颜色方案，避免使用新的Material 3属性
private val LightColorScheme = lightColorScheme(
    primary = Primary500,
    onPrimary = Color(0xFFFFFFFF),
    secondary = Secondary500,
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Accent500,
    onTertiary = Color(0xFFFFFFFF),
    background = Neutral50,
    onBackground = Color(0xFF1C1B1F),
    surface = Neutral50,
    onSurface = Color(0xFF1C1B1F),
    error = Error500,
    onError = Color(0xFFFFFFFF)
)

// 深色主题配色
private val DarkColorScheme = darkColorScheme(
    primary = Primary300,
    onPrimary = Color(0xFF003909),
    secondary = Secondary300,
    onSecondary = Color(0xFF003258),
    tertiary = Accent300,
    onTertiary = Color(0xFF000000),
    background = Neutral900,
    onBackground = Color(0xFFE6E1E5),
    surface = Neutral800,
    onSurface = Color(0xFFE6E1E5),
    error = Error300,
    onError = Color(0xFF690005)
)

@Composable
fun SyncClipboardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}