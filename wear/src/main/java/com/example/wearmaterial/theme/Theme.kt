package com.example.wearmaterial.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import com.example.wearmaterial.ui.theme.AppColorScheme
import com.example.wearmaterial.ui.theme.AppColors

// 当前配色方案
val LocalAppColorScheme = staticCompositionLocalOf { AppColors.Nextcloud }

@Composable
fun WearMaterialTheme(
    colorScheme: AppColorScheme = AppColors.Nextcloud,
    content: @Composable () -> Unit
) {
    // 创建 Wear Material Colors
    val colors = Colors(
        primary = colorScheme.primary,
        primaryVariant = colorScheme.primaryVariant,
        secondary = colorScheme.secondary,
        secondaryVariant = colorScheme.secondary,
        background = colorScheme.background,
        surface = colorScheme.surface,
        error = Color(0xFFCF6679),
        onPrimary = colorScheme.onPrimary,
        onSecondary = colorScheme.onSecondary,
        onBackground = colorScheme.onBackground,
        onSurface = colorScheme.onSurface,
        onError = Color.Black
    )
    
    CompositionLocalProvider(LocalAppColorScheme provides colorScheme) {
        MaterialTheme(
            colors = colors,
            content = content
        )
    }
}

// 扩展属性方便访问
object AppTheme {
    val colors: AppColorScheme
        @Composable
        get() = LocalAppColorScheme.current
}
