package com.example.decisionjournal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
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
    val pageVertical = 20.dp
    val sectionSpacing = 32.dp
    val cardSpacing = 16.dp
    val cardPadding = 20.dp
    val buttonHeight = 56.dp
    val smallRadius = 12.dp
    val cardRadius = 18.dp
    val heroRadius = 28.dp
}

@Composable
fun DecisionJournalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        shapes = Shapes(
            small = RoundedCornerShape(JournalDimens.smallRadius),
            medium = RoundedCornerShape(JournalDimens.cardRadius),
            large = RoundedCornerShape(JournalDimens.heroRadius),
        ),
        typography = androidx.compose.material3.Typography(
            displaySmall = TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                fontSize = 36.sp,
                lineHeight = 46.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            headlineSmall = TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                fontSize = 28.sp,
                lineHeight = 37.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 25.sp),
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 26.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 22.sp),
            bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 18.sp),
            labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 18.sp),
        ),
        content = content,
    )
}
