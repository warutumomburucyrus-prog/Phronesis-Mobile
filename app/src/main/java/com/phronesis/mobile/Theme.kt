package com.phronesis.mobile

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Terracotta = Color(0xFFE07A5F)
val Amber = Color(0xFFF2A65A)
val Clay = Color(0xFFB5654A)
val Cream = Color(0xFFFFF4E6)       // main background
val WarmSurface = Color(0xFFFBE8D3) // cards/surfaces — warm instead of stark white
val InkBrown = Color(0xFF3D2B1F)

private val PhronesisColors = lightColorScheme(
    primary = Terracotta,
    secondary = Amber,
    tertiary = Clay,
    background = Cream,
    surface = WarmSurface,
    onBackground = InkBrown,
    onSurface = InkBrown
)

@Composable
fun PhronesisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PhronesisColors,
        content = content
    )
}