package com.example.wearmaterial.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Material You 配色方案
 */
data class AppColorScheme(
    val name: String,
    val primary: Color,
    val primaryVariant: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val onPrimary: Color,
    val onSecondary: Color,
    val onBackground: Color,
    val onSurface: Color
)

object AppColors {
    // Nextcloud 蓝（默认）
    val Nextcloud = AppColorScheme(
        name = "Nextcloud",
        primary = Color(0xFF0082C9),
        primaryVariant = Color(0xFF0066A0),
        secondary = Color(0xFF0082C9),
        background = Color(0xFF1A1A1A),
        surface = Color(0xFF2A2A2A),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White
    )
    
    // 薄荷绿
    val Mint = AppColorScheme(
        name = "薄荷绿",
        primary = Color(0xFF00BFA5),
        primaryVariant = Color(0xFF00897B),
        secondary = Color(0xFF00BFA5),
        background = Color(0xFF1A1A1A),
        surface = Color(0xFF2A2A2A),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White
    )
    
    // 珊瑚橙
    val Coral = AppColorScheme(
        name = "珊瑚橙",
        primary = Color(0xFFFF6D3A),
        primaryVariant = Color(0xFFE64A19),
        secondary = Color(0xFFFF6D3A),
        background = Color(0xFF1A1A1A),
        surface = Color(0xFF2A2A2A),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White
    )
    
    // 紫罗兰
    val Violet = AppColorScheme(
        name = "紫罗兰",
        primary = Color(0xFF7C4DFF),
        primaryVariant = Color(0xFF651FFF),
        secondary = Color(0xFF7C4DFF),
        background = Color(0xFF1A1A1A),
        surface = Color(0xFF2A2A2A),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White
    )
    
    // 玫瑰粉
    val Rose = AppColorScheme(
        name = "玫瑰粉",
        primary = Color(0xFFFF4081),
        primaryVariant = Color(0xFFC51162),
        secondary = Color(0xFFFF4081),
        background = Color(0xFF1A1A1A),
        surface = Color(0xFF2A2A2A),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White
    )
    
    // 天空蓝
    val Sky = AppColorScheme(
        name = "天空蓝",
        primary = Color(0xFF40C4FF),
        primaryVariant = Color(0xFF0091EA),
        secondary = Color(0xFF40C4FF),
        background = Color(0xFF1A1A1A),
        surface = Color(0xFF2A2A2A),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White
    )
    
    // 琥珀黄
    val Amber = AppColorScheme(
        name = "琥珀黄",
        primary = Color(0xFFFFAB00),
        primaryVariant = Color(0xFFFF6D00),
        secondary = Color(0xFFFFAB00),
        background = Color(0xFF1A1A1A),
        surface = Color(0xFF2A2A2A),
        onPrimary = Color.Black,
        onSecondary = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White
    )
    
    val allSchemes = listOf(
        Nextcloud,
        Mint,
        Coral,
        Violet,
        Rose,
        Sky,
        Amber
    )
    
    fun getByName(name: String): AppColorScheme {
        return allSchemes.find { it.name == name } ?: Nextcloud
    }
}
