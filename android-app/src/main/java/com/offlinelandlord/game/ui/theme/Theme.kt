package com.offlinelandlord.game.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Cream = Color(0xFFFFFBF6)
val Ink = Color(0xFF3F4356)
val MutedInk = Color(0xFF777B8F)
val Peach = Color(0xFFFFAFA0)
val PeachDeep = Color(0xFFF27E78)
val Lavender = Color(0xFFC9BEFF)
val LavenderDeep = Color(0xFF7E73C8)
val Mint = Color(0xFFBDEBD7)
val MintDeep = Color(0xFF4F9B7E)
val Sky = Color(0xFFC9EBFF)
val Sunny = Color(0xFFFFD985)
val RoseRed = Color(0xFFD95563)

private val FreshColorScheme = lightColorScheme(
    primary = PeachDeep,
    onPrimary = Color.White,
    secondary = LavenderDeep,
    onSecondary = Color.White,
    tertiary = MintDeep,
    onTertiary = Color.White,
    background = Cream,
    onBackground = Ink,
    surface = Color(0xF7FFFFFF),
    onSurface = Ink,
    surfaceVariant = Color(0xFFF2EEFA),
    onSurfaceVariant = MutedInk,
    outline = Color(0xFFD9D3E4),
    error = RoseRed,
)

private val FreshTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 34.sp,
        letterSpacing = 1.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 25.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
    ),
)

@Composable
fun OfflineLandlordTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FreshColorScheme,
        typography = FreshTypography,
        content = content,
    )
}
