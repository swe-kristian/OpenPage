package com.qawse.openpage.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qawse.openpage.R
import com.qawse.openpage.data.ColorMode
import com.qawse.openpage.data.DuplexMode
import com.qawse.openpage.data.PaperSize
import com.qawse.openpage.data.PrintQuality
import com.qawse.openpage.ui.components.ConfirmDialog
import com.qawse.openpage.ui.components.DropdownField
import com.qawse.openpage.ui.components.InfoBanner
import com.qawse.openpage.ui.components.ListRow
import com.qawse.openpage.ui.components.SectionHeader
import com.qawse.openpage.ui.components.SettingSegment
import com.qawse.openpage.ui.components.SwitchRow
import com.qawse.openpage.ui.theme.Tokens
import com.qawse.openpage.viewmodel.AppViewModel

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onAbout: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    val theme by vm.settingsStore.themeMode.collectAsState()
    val paper by vm.settingsStore.defaultPaper.collectAsState()
    val color by vm.settingsStore.defaultColor.collectAsState()
    val quality by vm.settingsStore.defaultQuality.collectAsState()
    val duplex by vm.settingsStore.defaultDuplex.collectAsState()
    val keepAwake by vm.settingsStore.keepAwake.collectAsState()
    val jobs by vm.jobs.collectAsState()
    val diagnosticsUnlocked by vm.settingsStore.diagnosticsUnlocked.collectAsState()
    var clearConfirm by remember { mutableStateOf(false) }
    val width = rememberWidth()

    if (clearConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.settings_clear_confirm_title),
            body = stringResource(R.string.settings_clear_confirm_body),
            confirmLabel = stringResource(R.string.action_clear),
            onConfirm = { clearConfirm = false; vm.history.clear() },
            onDismiss = { clearConfirm = false },
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
        )
    }

    ScreenScaffold(title = stringResource(R.string.settings_title), width = width) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = Tokens.SpaceXXL),
            verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSM),
        ) {
            item { SectionHeader(stringResource(R.string.settings_section_appearance)) }
            item {
                SettingSegment(
                    title = stringResource(R.string.settings_theme),
                    options = listOf("system", "light", "dark"),
                    selected = theme,
                    label = {
                        when (it) {
                            "system" -> stringResource(R.string.settings_theme_system)
                            "light" -> stringResource(R.string.settings_theme_light)
                            else -> stringResource(R.string.settings_theme_dark)
                        }
                    },
                    onSelect = { vm.settingsStore.setThemeMode(it) },
                )
            }

            item { SectionHeader(stringResource(R.string.settings_section_print)) }
            item {
                DropdownField(
                    value = paper,
                    options = PaperSize.entries.toList(),
                    label = { it.label },
                    onSelect = { vm.settingsStore.setDefaultPaper(it) },
                    fieldLabel = stringResource(R.string.settings_default_paper),
                )
            }
            item {
                SettingSegment(
                    title = stringResource(R.string.settings_default_color),
                    options = listOf(ColorMode.GRAYSCALE, ColorMode.COLOR, ColorMode.MONO),
                    selected = color,
                    label = { colorModeLabel(it) },
                    onSelect = { vm.settingsStore.setDefaultColor(it) },
                )
            }
            item {
                SettingSegment(
                    title = stringResource(R.string.settings_default_quality),
                    options = PrintQuality.entries.toList(),
                    selected = quality,
                    label = { qualityLabel(it) },
                    onSelect = { vm.settingsStore.setDefaultQuality(it) },
                )
            }
            item {
                SettingSegment(
                    title = stringResource(R.string.settings_default_duplex),
                    options = listOf(DuplexMode.OFF, DuplexMode.LONG, DuplexMode.SHORT),
                    selected = duplex,
                    label = { duplexLabel(it) },
                    onSelect = { vm.settingsStore.setDefaultDuplex(it) },
                )
            }

            item { SectionHeader(stringResource(R.string.settings_section_behavior)) }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_keep_awake),
                    subtitle = stringResource(R.string.settings_keep_awake_hint),
                    checked = keepAwake,
                    onChange = { vm.settingsStore.setKeepAwake(it) },
                )
            }

            item { SectionHeader(stringResource(R.string.settings_section_data)) }
            item {
                InfoBanner(
                    text = stringResource(R.string.settings_privacy_note),
                    icon = R.drawable.ic_lock,
                )
            }
            item {
                ListRow(
                    icon = R.drawable.ic_history,
                    title = stringResource(R.string.settings_clear_recent),
                    subtitle = androidx.compose.ui.res.pluralStringResource(
                        R.plurals.settings_clear_recent_hint, jobs.size, jobs.size),
                    onClick = { clearConfirm = true },
                )
            }

            item { SectionHeader(stringResource(R.string.settings_section_about_block)) }
            item {
                ListRow(
                    icon = R.drawable.ic_info,
                    title = stringResource(R.string.nav_about),
                    subtitle = stringResource(R.string.app_tagline),
                    onClick = onAbout,
                )
            }
            if (diagnosticsUnlocked) {
                item {
                    ListRow(
                        icon = R.drawable.ic_bolt,
                        title = stringResource(R.string.settings_diagnostics),
                        subtitle = stringResource(R.string.settings_diagnostics_hint),
                        onClick = onDiagnostics,
                    )
                }
            }
        }
    }
}
