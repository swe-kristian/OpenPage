package com.qawse.openpage.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * OpenPage palette — warm paper, legible ink, one cobalt accent.
 *
 * Raw values live ONLY here. Everything else consumes semantic roles
 * (see [OpenColors] in Theme.kt): background, surface, elevatedSurface,
 * primary, secondary, accent, success, warning, error, divider, disabled.
 */

// Ink ramp (foreground / brand dark)
val ink900 = Color(0xFF101114) // launcher background, true ink
val ink800 = Color(0xFF17191E)
val ink700 = Color(0xFF23252B)
val ink600 = Color(0xFF33363E)
val ink500 = Color(0xFF4A4E57)
val ink400 = Color(0xFF6E727C)
val ink300 = Color(0xFF9A9EA7)
val ink200 = Color(0xFFC6C8CE)
val ink100 = Color(0xFFE7E8EB)
val ink50 = Color(0xFFF2F3F5)

// Paper ramp (backgrounds)
val paperWhite = Color(0xFFFBFBF9) // warm off-white, not clinical white
val paperCard = Color(0xFFFFFFFF)
val paperWell = Color(0xFFF4F4F1) // recessed wells (preview mats, code)

// The single brand accent: a calm cobalt. Not default Android blue.
val cobalt = Color(0xFF3B62D6)
val cobaltDeep = Color(0xFF2F4FB5)
val cobaltContainer = Color(0xFFE4EAF9)
val cobaltOnContainer = Color(0xFF27407F)
val cobaltLight = Color(0xFF8AA3FF)
val cobaltContainerDark = Color(0xFF2B3862)
val cobaltOnContainerDark = Color(0xFFD9E2FC)

// Status inks — controlled, desaturated, never neon.
val successInk = Color(0xFF2E7D5B)
val successInkDark = Color(0xFF8FD6B8)
val successContainer = Color(0xFFE2F1EA)
val successContainerDark = Color(0xFF1C3A2E)
val warnInk = Color(0xFF9A5A16)
val warnInkDark = Color(0xFFE5B078)
val warnContainer = Color(0xFFF7EBDD)
val warnContainerDark = Color(0xFF3D2E19)

// Error rides the Material scheme; these are its container companions.
val errorContainerDark = Color(0xFF8C1D18)

// Night ramp (dark theme surfaces)
val nightBg = Color(0xFF101114)
val nightSurface = Color(0xFF17181C)
val nightSurfaceHigh = Color(0xFF1E2025)
val nightWell = Color(0xFF0B0C0E)
val nightOutline = Color(0xFF2A2B31)
val nightOutlineVariant = Color(0xFF232529)
val nightText = Color(0xFFEDEDEF)
val nightTextDim = Color(0xFFA6A9B0)
