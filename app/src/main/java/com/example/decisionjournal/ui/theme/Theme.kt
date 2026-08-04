package com.example.decisionjournal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

private val Colors = lightColorScheme(
    primary = BlueAccent,
    onPrimary = CardWhite,
    background = AppBackground,
    onBackground = Ink,
    surface = CardWhite,
    onSurface = Ink,
    secondary = SoftSage,
    primaryContainer = MistBlue,
    onPrimaryContainer = Ink,
    secondaryContainer = MistGreen,
    onSecondaryContainer = Ink,
    tertiaryContainer = MistSand,
    onTertiaryContainer = Ink,
)

object JournalDimens {
    val pageHorizontal = 20.dp
    val pageVertical = 18.dp
    val sectionSpacing = 24.dp
    val cardSpacing = 12.dp
    val cardPadding = 18.dp
    val buttonHeight = 54.dp
    val smallRadius = 12.dp
    val cardRadius = 18.dp
    val heroRadius = 24.dp
}

@Composable
fun DecisionJournalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
        ),
        typography = androidx.compose.material3.Typography(
            displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 39.sp),
            headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 25.sp, lineHeight = 32.sp),
            titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
            labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp),
        ),
        content = content,
    )
}
