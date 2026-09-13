package com.qawse.openpage.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.qawse.openpage.R

/**
 * OpenPage type system — Inter, one small deliberate scale.
 *
 * Every style fixes size, weight, line height and letter spacing. Weight
 * carries hierarchy; body copy never goes bold. Screens must use these
 * roles (or MaterialTheme.typography) — never ad-hoc sizes.
 */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.W400),
    Font(R.font.inter_medium, FontWeight.W500),
    Font(R.font.inter_semibold, FontWeight.W600),
    Font(R.font.inter_bold, FontWeight.W700),
)

private val Display = TextStyle(          // brand moments only (About hero)
    fontFamily = Inter, fontWeight = FontWeight.W700,
    fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp,
)

private val Headline = TextStyle(          // screen titles
    fontFamily = Inter, fontWeight = FontWeight.W600,
    fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.25).sp,
)

private val TitleLarge = TextStyle(        // section titles, dialog titles
    fontFamily = Inter, fontWeight = FontWeight.W600,
    fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.15).sp,
)

private val TitleMedium = TextStyle(       // card and row titles
    fontFamily = Inter, fontWeight = FontWeight.W600,
    fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.1).sp,
)

private val BodyLarge = TextStyle(         // primary reading text
    fontFamily = Inter, fontWeight = FontWeight.W400,
    fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp,
)

private val BodyMedium = TextStyle(        // secondary reading text
    fontFamily = Inter, fontWeight = FontWeight.W400,
    fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
)

private val BodySmall = TextStyle(         // meta, captions, helper text
    fontFamily = Inter, fontWeight = FontWeight.W400,
    fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp,
)

private val LabelLarge = TextStyle(        // buttons, steppers
    fontFamily = Inter, fontWeight = FontWeight.W500,
    fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
)

private val LabelMedium = TextStyle(       // segmented items, chips, nav
    fontFamily = Inter, fontWeight = FontWeight.W500,
    fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp,
)

private val LabelSmall = TextStyle(        // section headers (uppercase)
    fontFamily = Inter, fontWeight = FontWeight.W500,
    fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp,
)

val OpenTypography = Typography(
    displaySmall = Display,
    headlineMedium = Headline,
    headlineSmall = Headline,
    titleLarge = TitleLarge,
    titleMedium = TitleMedium,
    titleSmall = TitleMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = BodyLarge,
    bodyMedium = BodyMedium,
    bodySmall = BodySmall,
    labelLarge = LabelLarge,
    labelMedium = LabelMedium,
    labelSmall = LabelSmall,
)
