package com.qawse.openpage

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.qawse.openpage.ui.navigation.AppShell
import com.qawse.openpage.ui.navigation.WindowWidth
import com.qawse.openpage.ui.theme.OpenPageTheme
import com.qawse.openpage.ui.theme.OpenTheme
import com.qawse.openpage.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val app = application as OpenPageApp
            val vm: AppViewModel = viewModel { AppViewModel(app, createSavedStateHandle()) }
            val themeMode by app.settings.themeMode.collectAsState()
            val dark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            // Keep the screen on while a transfer is running, if the user
            // allows it — mid-job sleep is the classic OTG failure.
            val keepAwake by app.settings.keepAwake.collectAsState()
            val printUi by vm.printUi.collectAsState()
            val scanUi by vm.scanUi.collectAsState()
            val busy = printUi is AppViewModel.PrintUi.Sending ||
                scanUi is AppViewModel.ScanUi.GlassScanning ||
                scanUi is AppViewModel.ScanUi.Processing
            if (keepAwake && busy) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            OpenPageTheme(darkTheme = dark) {
                val width = rememberWindowWidth()
                val nav = rememberNavController()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = OpenTheme.colors.background,
                ) {
                    AppShell(vm = vm, width = width, nav = nav)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A printer was just plugged in — refresh the device list immediately.
        (application as OpenPageApp).usb.refresh()
    }
}

/**
 * Window width bucket from the live configuration — reacts to rotation,
 * split-screen and foldable postures, not device model labels.
 */
@Composable
private fun rememberWindowWidth(): WindowWidth {
    val w = LocalConfiguration.current.screenWidthDp
    return when {
        w >= Tokens_EXPANDED -> WindowWidth.EXPANDED
        w >= Tokens_MEDIUM -> WindowWidth.MEDIUM
        else -> WindowWidth.COMPACT
    }
}

private const val Tokens_MEDIUM = 600
private const val Tokens_EXPANDED = 840
