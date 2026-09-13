package com.qawse.openpage.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.qawse.openpage.R
import com.qawse.openpage.ui.screens.AboutScreen
import com.qawse.openpage.ui.screens.HomeScreen
import com.qawse.openpage.ui.screens.PrintScreen
import com.qawse.openpage.ui.screens.PrintersScreen
import com.qawse.openpage.ui.screens.ScanScreen
import com.qawse.openpage.ui.screens.SettingsScreen
import com.qawse.openpage.ui.screens.diagnostics.DiagnosticsScreen
import com.qawse.openpage.ui.theme.LocalOpenColors
import com.qawse.openpage.ui.theme.Tokens
import com.qawse.openpage.ui.theme.rememberReducedMotion
import com.qawse.openpage.viewmodel.AppViewModel
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * Adaptive navigation width buckets — Material window size classes,
 * derived from the live window configuration (works in split-screen,
 * foldables and rotation, not from device labels).
 */
enum class WindowWidth { COMPACT, MEDIUM, EXPANDED }

/** The essential destinations — nothing more earns a top-level slot. */
internal data class Destination(val route: String, val labelRes: Int, val iconRes: Int)

internal val topLevel = listOf(
    Destination("home", R.string.nav_home, R.drawable.ic_blank_page),
    Destination("print", R.string.nav_print, R.drawable.ic_printer),
    Destination("scan", R.string.nav_scan, R.drawable.ic_scan),
    Destination("settings", R.string.nav_settings, R.drawable.ic_settings),
)

/**
 * The app shell: bottom navigation on compact windows, a rail on medium
 * and expanded ones. Navigation state survives rotation and process
 * recreation via saveState/restoreState.
 */
@Composable
fun AppShell(vm: AppViewModel, width: WindowWidth, nav: NavHostController) {
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: "home"
    val showNav = current in topLevel.map { it.route }
    val colors = LocalOpenColors.current

    val content: @Composable (Modifier) -> Unit = { modifier ->
        OpenNavHost(vm = vm, nav = nav, modifier = modifier)
    }

    if (width == WindowWidth.COMPACT) {
        Scaffold(
            containerColor = colors.background,
            bottomBar = {
                if (showNav) {
                    NavigationBar(
                        containerColor = colors.elevatedSurface,
                        tonalElevation = 0.dp,
                    ) {
                        topLevel.forEach { dest ->
                            val selected = current == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { nav.navigateTop(dest.route) },
                                icon = {
                                    Icon(
                                        painter = painterResource(dest.iconRes),
                                        contentDescription = stringResource(dest.labelRes),
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = colors.well,
                                    selectedIconColor = colors.onSurface,
                                    selectedTextColor = colors.onSurface,
                                    unselectedIconColor = colors.onSurfaceVariant,
                                    unselectedTextColor = colors.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { content(Modifier) }
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            if (showNav) {
                NavigationRail(
                    containerColor = colors.background,
                ) {
                    topLevel.forEach { dest ->
                        val selected = current == dest.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { nav.navigateTop(dest.route) },
                            icon = {
                                Icon(
                                    painter = painterResource(dest.iconRes),
                                    contentDescription = stringResource(dest.labelRes),
                                )
                            },
                            label = { Text(stringResource(dest.labelRes)) },
                            alwaysShowLabel = width == WindowWidth.EXPANDED,
                            colors = NavigationRailItemDefaults.colors(
                                indicatorColor = colors.well,
                                selectedIconColor = colors.onSurface,
                                selectedTextColor = colors.onSurface,
                                unselectedIconColor = colors.onSurfaceVariant,
                                unselectedTextColor = colors.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
            Box(Modifier.fillMaxSize().weight(1f)) { content(Modifier) }
        }
    }
}

private fun NavHostController.navigateTop(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun OpenNavHost(vm: AppViewModel, nav: NavHostController, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val durations = if (reduced) 0 else Tokens.MotionBase
    NavHost(
        navController = nav,
        startDestination = "home",
        modifier = modifier,
        enterTransition = {
            if (durations == 0) EnterTransition.None
            else fadeIn(tween(durations)) + slideInHorizontally(tween(durations)) { it / 24 }
        },
        exitTransition = {
            if (durations == 0) ExitTransition.None else fadeOut(tween(durations / 2))
        },
        popEnterTransition = {
            if (durations == 0) EnterTransition.None else fadeIn(tween(durations))
        },
        popExitTransition = {
            if (durations == 0) ExitTransition.None
            else fadeOut(tween(durations / 2)) + slideOutHorizontally(tween(durations)) { it / 24 }
        },
    ) {
        composable("home") {
            HomeScreen(
                vm = vm,
                onPrint = { nav.navigate("print") },
                onScan = { nav.navigate("scan") },
                onPrinters = { nav.navigate("printers") },
            )
        }
        composable("print") {
            PrintScreen(vm = vm, onPrinters = { nav.navigate("printers") })
        }
        composable("scan") { ScanScreen(vm = vm, onGoToPrint = { nav.navigate("print") }) }
        composable("printers") {
            PrintersScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(
                vm = vm,
                onAbout = { nav.navigate("about") },
                onDiagnostics = { nav.navigate("diagnostics") },
            )
        }
        composable("about") {
            AboutScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable("diagnostics") {
            DiagnosticsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
