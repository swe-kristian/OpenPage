package com.qawse.openpage.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * OpenPage design tokens — the single source of truth for spacing, shape,
 * elevation, motion, icon sizing and touch targets. Screens and components
 * must reference these (or the theme roles in [OpenColors]) instead of
 * inventing raw values.
 *
 * Two corner-radius families only:
 *  - [RADIUS_SM] for fields, chips and controls
 *  - [RADIUS_MD] for cards and primary surfaces
 *  - [RADIUS_LG] reserved for prominent containers, sheets and dialogs
 */
object Tokens {
    // ------------------------------------------------------------ spacing
    // 4 dp base grid. Named by role, not by number.
    val SpaceXS = 4.dp    // inside chips, pill padding
    val SpaceSM = 8.dp    // tight element gaps
    val SpaceMD = 12.dp   // standard element gap
    val SpaceLG = 16.dp   // screen horizontal padding, card gaps
    val SpaceXL = 20.dp   // section spacing
    val SpaceXXL = 28.dp  // between sections
    val SpaceXXXL = 40.dp // hero spacing

    // -------------------------------------------------------------- shape
    val RadiusSM = 10.dp  // text fields, dropdowns, banners, segmented items
    val RadiusMD = 16.dp  // cards, list rows, primary surfaces
    val RadiusLG = 24.dp  // dialogs, sheets, hero containers

    // ---------------------------------------------------------- elevation
    // Prefer tonal contrast + hairline borders; shadow only where the
    // surface genuinely floats above content (dialogs, bottom bars).
    val ElevSheet = 6.dp

    // --------------------------------------------------------------- icons
    val IconSM = 16.dp    // inline icons inside chips / banners
    val IconMD = 20.dp    // standard inline icon
    val IconLG = 24.dp    // navigation & toolbar icons

    // -------------------------------------------------------- touch target
    val TouchMin = 48.dp  // minimum interactive extent, both axes

    // --------------------------------------------------------------- motion
    // Milliseconds. All animation goes through [rememberMotionDuration] so
    // reduced-motion preferences collapse these to zero.
    val MotionFast = 150
    val MotionBase = 220
    val MotionSlow = 320

    // ------------------------------------------------------------- layout
    // Maximum readable column for centered tablet layouts.
    val ContentMaxWidth = 640.dp
    // Width of the detail pane in two-pane (expanded) layouts.
    val DetailPaneWidth = 420.dp
    // Breakpoints follow the Material window size classes.
    const val WIDTH_MEDIUM = 600   // dp — compact -> medium
    const val WIDTH_EXPANDED = 840 // dp — medium -> expanded
}

/** Letter-spacing token used by section headers (em-style tracking). */
val SectionTracking = 1.4.sp
