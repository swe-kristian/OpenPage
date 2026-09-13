package com.qawse.openpage.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qawse.openpage.R
import com.qawse.openpage.core.PrintProblem
import com.qawse.openpage.data.PrinterDatabase
import com.qawse.openpage.ui.components.ActionChip
import com.qawse.openpage.ui.components.BannerTone
import com.qawse.openpage.ui.components.DetailRow
import com.qawse.openpage.ui.components.DropdownField
import com.qawse.openpage.ui.components.EmptyState
import com.qawse.openpage.ui.components.InfoBanner
import com.qawse.openpage.ui.components.IconBadge
import com.qawse.openpage.ui.components.OpenCard
import com.qawse.openpage.ui.components.PillTone
import com.qawse.openpage.ui.components.SectionHeader
import com.qawse.openpage.ui.components.StatusPill
import com.qawse.openpage.ui.components.ToolbarIconButton
import com.qawse.openpage.ui.theme.LocalOpenColors
import com.qawse.openpage.ui.theme.Tokens
import com.qawse.openpage.usb.UsbPrinterInfo
import com.qawse.openpage.viewmodel.AppViewModel

@Composable
fun PrintersScreen(vm: AppViewModel, onBack: () -> Unit) {
    val printers by vm.printers.collectAsState()
    val defaultKey = vm.settingsStore.defaultPrinterKey
    val testPageUi by vm.testPageUi.collectAsState()
    val width = rememberWidth()

    ScreenScaffold(
        title = stringResource(R.string.printers_title),
        width = width,
        onBack = onBack,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Tokens.SpaceXXL),
        ) {
            when (val t = testPageUi) {
                is AppViewModel.TestPageUi.Done -> item {
                    InfoBanner(
                        text = stringResource(R.string.printers_test_sent, t.printerName),
                        icon = R.drawable.ic_check,
                        tone = BannerTone.SUCCESS,
                    )
                }
                is AppViewModel.TestPageUi.Failed -> item {
                    InfoBanner(
                        text = stringResource(R.string.printers_test_failed),
                        icon = R.drawable.ic_alert,
                        tone = BannerTone.ERROR,
                    )
                }
                AppViewModel.TestPageUi.Sending -> item {
                    InfoBanner(
                        text = stringResource(R.string.printers_test_sending),
                        icon = R.drawable.ic_info,
                    )
                }
                AppViewModel.TestPageUi.Idle -> {}
            }

            if (printers.isEmpty()) {
                item {
                    Spacer(Modifier.height(Tokens.SpaceLG))
                    EmptyState(
                        icon = R.drawable.ic_usb,
                        title = stringResource(R.string.printers_none_attached),
                        hint = stringResource(R.string.printers_none_hint),
                    )
                }
            } else {
                item { SectionHeader(stringResource(R.string.printers_section_attached)) }
                items(printers.size) { i ->
                    val p = printers[i]
                    PrinterCard(
                        vm = vm,
                        printer = p,
                        isDefault = p.key == defaultKey ||
                            (defaultKey == null && printers.size == 1),
                    )
                    Spacer(Modifier.height(Tokens.SpaceSM))
                }
                item {
                    InfoBanner(
                        text = stringResource(R.string.printers_driver_generic_reason),
                        icon = R.drawable.ic_info,
                    )
                    Spacer(Modifier.height(Tokens.SpaceSM))
                    InfoBanner(
                        text = stringResource(R.string.settings_privacy_note),
                        icon = R.drawable.ic_lock,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrinterCard(vm: AppViewModel, printer: UsbPrinterInfo, isDefault: Boolean) {
    var expanded by rememberSaveable(printer.key) { mutableStateOf(false) }
    val colors = LocalOpenColors.current
    val overridden = vm.driverOverridden(printer)

    OpenCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon = R.drawable.ic_printer,
                    tint = colors.onSurface,
                    bg = colors.well,
                )
                Spacer(Modifier.width(Tokens.SpaceMD + 2.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        printer.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(
                            R.string.printers_driver,
                            PrinterDatabase.driverLabel(vm.driverFor(printer)),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(Tokens.SpaceSM))
                Column(horizontalAlignment = Alignment.End) {
                    if (printer.hasPermission) {
                        StatusPill(
                            text = stringResource(R.string.printers_status_connected),
                            tone = PillTone.SUCCESS,
                            icon = R.drawable.ic_check,
                        )
                    } else {
                        StatusPill(
                            text = stringResource(R.string.printers_status_permission),
                            tone = PillTone.WARNING,
                            icon = R.drawable.ic_lock,
                        )
                    }
                    if (isDefault) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.printers_default),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Tokens.SpaceMD))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSM),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (!printer.hasPermission) {
                    ActionChip(
                        text = stringResource(R.string.printers_grant),
                        icon = R.drawable.ic_lock,
                        prominent = true,
                        onClick = { vm.requestPermission(printer) { } },
                    )
                } else {
                    ActionChip(
                        text = stringResource(R.string.printers_test_page),
                        icon = R.drawable.ic_document,
                        onClick = { vm.printTestPage(printer) },
                    )
                }
                if (!isDefault) {
                    ActionChip(
                        text = stringResource(R.string.printers_set_default),
                        icon = R.drawable.ic_check,
                        onClick = {
                            vm.settingsStore.defaultPrinterKey = printer.key
                            vm.refreshUsb()
                        },
                    )
                }
                ActionChip(
                    text = if (expanded) stringResource(R.string.printers_details_hide)
                    else stringResource(R.string.printers_details),
                    icon = R.drawable.ic_chevron_down,
                    onClick = { expanded = !expanded },
                )
            }

            if (overridden) {
                Spacer(Modifier.height(Tokens.SpaceSM))
                Text(
                    stringResource(R.string.printers_driver_overridden),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(Tokens.SpaceLG))
                    Text(
                        stringResource(R.string.printers_driver_override),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Tokens.SpaceXS + 2.dp))
                    DropdownField(
                        value = vm.settingsStore.driverOverride(printer.key) ?: printer.driverFamily,
                        options = PrinterDatabase.DriverFamily.entries.toList(),
                        label = { driverLabelRes(it) },
                        onSelect = { family ->
                            vm.settingsStore.setDriverOverride(
                                printer.key,
                                if (family == printer.driverFamily) null else family,
                            )
                        },
                    )
                    Spacer(Modifier.height(Tokens.SpaceXS))
                    Text(
                        stringResource(R.string.printers_driver_override_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Tokens.SpaceMD))
                    Text(
                        PrinterDatabase.driverDescription(vm.driverFor(printer)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Tokens.SpaceMD))

                    DetailRow(stringResource(R.string.printers_detail_product),
                        printer.productName ?: stringResource(R.string.printers_detail_value_unknown))
                    DetailRow(stringResource(R.string.printers_detail_vid), "0x%04X".format(printer.vid))
                    DetailRow(stringResource(R.string.printers_detail_pid), "0x%04X".format(printer.pid))
                    DetailRow(
                        stringResource(R.string.printers_detail_serial),
                        printer.serialNumber ?: stringResource(R.string.printers_detail_value_unknown),
                    )
                    DetailRow(
                        stringResource(R.string.printers_detail_class),
                        if (printer.isPrinterClass) stringResource(R.string.printers_class_printer)
                        else stringResource(R.string.printers_class_vendor),
                    )
                    DetailRow(
                        stringResource(R.string.printers_detail_transport),
                        stringResource(
                            R.string.printers_detail_transport_value,
                            printer.bulkOutMaxPacket ?: 64,
                        ),
                    )
                    DetailRow(
                        stringResource(R.string.printers_detail_scanner),
                        if (printer.ippInterfaceIndex != null)
                            stringResource(R.string.printers_detail_scanner_yes)
                        else stringResource(R.string.printers_detail_scanner_no),
                    )
                }
            }
        }
    }
}

@Composable
fun driverLabelRes(family: PrinterDatabase.DriverFamily): String = when (family) {
    PrinterDatabase.DriverFamily.PCL -> stringResource(R.string.driver_pcl)
    PrinterDatabase.DriverFamily.ESCPOS -> stringResource(R.string.driver_escpos)
    PrinterDatabase.DriverFamily.ESCPR -> stringResource(R.string.driver_escpr)
    PrinterDatabase.DriverFamily.POSTSCRIPT -> stringResource(R.string.driver_ps)
    PrinterDatabase.DriverFamily.GENERIC -> stringResource(R.string.driver_generic)
    PrinterDatabase.DriverFamily.RAW -> stringResource(R.string.driver_raw)
}
