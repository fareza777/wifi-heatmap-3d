package com.sinyal.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sinyal.app.R

/**
 * Plus Jakarta Sans, bundled rather than taken from the system.
 *
 * The stock sans is what makes an Android app read as unstyled — it is the same
 * face every other app uses. A distinct, well-fitted typeface is the single
 * largest visual change available, and costs about 640 KB.
 */
private val Sans = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semi_bold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extra_bold, FontWeight.ExtraBold),
)

val SinyalTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 62.sp,
        lineHeight = 66.sp,
        letterSpacing = (-2.2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 27.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.7).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.2).sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        letterSpacing = 1.2.sp,
    ),
)
