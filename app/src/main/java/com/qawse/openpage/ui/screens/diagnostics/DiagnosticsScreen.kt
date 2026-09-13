package com.qawse.openpage.ui.screens.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qawse.openpage.R
import com.qawse.openpage.data.PrinterDatabase
import com.qawse.openpage.ui.components.ActionChip
import com.qawse.openpage.ui.components.DetailRow
import com.qawse.openpage.ui.components.EmptyState
import com.qawse.openpage.ui.components.InfoBanner
import com.qawse.openpage.ui.components.OpenCard
import com.qawse.openpage.ui.components.SectionHeader
import com.qawse.openpage.ui.theme.Tokens
import com.qawse.openpage.ui.screens.ScreenScaffold
import com.qawse.openpage.ui.screens.driverLabelRes
import com.qawse.openpage.ui.screens.rememberWidth
import com.qawse.openpage.usb.UsbPrinterInfo
import com.qawse.openpage.viewmodel.AppViewModel

/**
 * Development-only diagnostics: honest reports of what the app detected,
 * what it chose, and why — without leaking document content.
 */
@Composable
fun DiagnosticsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val printers by vm.printers.collectAsState()
    val width = rememberWidth()
    val target by vm.targetPrinter.collectAsState()

    ScreenScaffold(title = stringResource(R.string.diag_title), width = width, onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = Tokens.SpaceXXL),
            verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSM),
        ) {
            item { SectionHeader(stringResource(R.string.diag_printer_section)) }
            val tgt = target
            if (tgt == null) {
                item {
                    EmptyState(
                        icon = R.drawable.ic_usb,
                        title = stringResource(R.string.diag_no_printer),
                        hint = stringResource(R.string.printers_none_hint),
                    )
                }
            } else {
                item { PrinterDiagnostics(vm = vm, printer = tgt) }
            }

            item { SectionHeader(stringResource(R.string.diag_scan_section)) }
            item { ScanDiagnostics(vm = vm) }

            item {
                InfoBanner(
                    text = stringResource(R.string.diag_honesty_note),
                    icon = R.drawable.ic_info,
                )
            }
        }
    }
}

@Composable
private fun PrinterDiagnostics(vm: AppViewModel, printer: UsbPrinterInfo) {
    val overridden = vm.driverOverridden(printer)
    OpenCard {
        Column {
            androidx.compose.material3.Text(
                printer.name,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Tokens.SpaceMD))

            DetailRow(
                stringResource(R.string.printers_detail_transport),
                stringResource(R.string.printers_detail_transport_value, printer.bulkOutMaxPacket ?: 64),
            )
            DetailRow(stringResource(R.string.printers_detail_vid), "0x%04X".format(printer.vid))
            DetailRow(stringResource(R.string.printers_detail_pid), "0x%04X".format(printer.pid))
            DetailRow(
                stringResource(R.string.printers_detail_serial),
                printer.serialNumber ?: stringResource(R.string.printers_detail_value_unknown),
            )
            DetailRow(
                stringResource(R.string.printers_detail_product),
                printer.productName ?: stringResource(R.string.printers_detail_value_unknown),
            )
            DetailRow(
                stringResource(R.string.printers_detail_class),
                if (printer.isPrinterClass) stringResource(R.string.printers_class_printer)
                else stringResource(R.string.printers_class_vendor),
            )

            Spacer(Modifier.height(Tokens.SpaceMD))
            androidx.compose.material3.Text(
                stringResource(R.string.diag_driver_family),
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Tokens.SpaceXS))
            androidx.compose.material3.Text(
                driverLabelRes(vm.driverFor(printer)),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Tokens.SpaceXS))
            androidx.compose.material3.Text(
                when {
                    overridden -> stringResource(R.string.diag_driver_reason_override)
                    printer.brand != null -> stringResource(R.string.diag_driver_reason_brand)
                    else -> stringResource(R.string.diag_driver_reason_fallback)
                },
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Tokens.SpaceLG))
            androidx.compose.material3.Text(
                stringResource(R.string.diag_features),
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Tokens.SpaceXS))
            val family = vm.driverFor(printer)
            val colorCapable = family in setOf(
                PrinterDatabase.DriverFamily.PCL,
                PrinterDatabase.DriverFamily.ESCPR,
                PrinterDatabase.DriverFamily.POSTSCRIPT,
            )
            DetailRow(
                stringResource(R.string.diag_feature_color),
                if (colorCapable) stringResource(R.string.diag_feature_yes)
                else stringResource(R.string.diag_feature_unknown),
            )
            DetailRow(
                stringResource(R.string.diag_feature_duplex),
                if (printer.brand?.duplexCapable == true)
                    stringResource(R.string.diag_feature_yes)
                else stringResource(R.string.diag_feature_unknown),
            )
            DetailRow(
                stringResource(R.string.diag_feature_scan),
                if (printer.ippInterfaceIndex != null)
                    stringResource(R.string.diag_feature_yes)
                else stringResource(R.string.diag_feature_no),
            )

            Spacer(Modifier.height(Tokens.SpaceLG))
            androidx.compose.material3.Text(
                stringResource(R.string.diag_interfaces),
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Tokens.SpaceXS))
            printer.interfaces.forEach { iface ->
                androidx.compose.material3.Text(
                    stringResource(
                        R.string.diag_interface_row, iface.index, iface.classLabel, iface.endpointCount),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(Tokens.SpaceLG))
            ActionChip(
                text = stringResource(R.string.diag_test_page),
                icon = R.drawable.ic_document,
                onClick = { vm.printTestPage(printer) },
            )
        }
    }
}

@Composable
private fun ScanDiagnostics(vm: AppViewModel) {
    val stats = vm.lastScanStats
    OpenCard {
        if (stats == null) {
            androidx.compose.material3.Text(
                stringResource(R.string.diag_scan_none),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            DetailRow(
                stringResource(R.string.diag_scan_dimensions),
                "${stats.widthPx} × ${stats.heightPx} px",
            )
            DetailRow(stringResource(R.string.diag_scan_resolution), "${stats.dpi} dpi")
            DetailRow(stringResource(R.string.diag_scan_color), stats.colorMode)
            DetailRow(stringResource(R.string.diag_scan_size),
                stringResource(R.string.diag_bytes, stats.bytes / 1024))
            DetailRow(stringResource(R.string.diag_scan_time),
                stringResource(R.string.diag_ms, stats.elapsedMs.toInt()))
        }
    }
}
