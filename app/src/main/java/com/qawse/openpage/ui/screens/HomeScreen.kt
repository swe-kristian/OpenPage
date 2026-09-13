package com.qawse.openpage.ui.screens

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qawse.openpage.R
import com.qawse.openpage.data.PrintJobRecord
import com.qawse.openpage.data.PrinterDatabase
import com.qawse.openpage.ui.components.ActionChip
import com.qawse.openpage.ui.components.EmptyState
import com.qawse.openpage.ui.components.IconBadge
import com.qawse.openpage.ui.components.InfoBanner
import com.qawse.openpage.ui.components.ListRow
import com.qawse.openpage.ui.components.OpenCard
import com.qawse.openpage.ui.components.PillTone
import com.qawse.openpage.ui.components.SectionHeader
import com.qawse.openpage.ui.components.StatusPill
import com.qawse.openpage.ui.navigation.WindowWidth
import com.qawse.openpage.ui.theme.LocalOpenColors
import com.qawse.openpage.ui.theme.Tokens
import com.qawse.openpage.viewmodel.AppViewModel

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onPrint: () -> Unit,
    onScan: () -> Unit,
    onPrinters: () -> Unit,
) {
    val printers by vm.printers.collectAsState()
    val jobs by vm.jobs.collectAsState()
    val target by vm.targetPrinter.collectAsState()
    val width = rememberWidth()

    Column(Modifier.fillMaxSize()) {
        ScreenScaffold(title = stringResource(R.string.home_greeting), width = width) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Printer status -----------------------------------------------
                item {
                    val tgt = target
                    if (tgt != null) PrinterStatusCard(vm, tgt, onPrint, onScan)
                    else NoPrinterCard(onPrinters)
                }

                // Start ----------------------------------------------------------
                item { SectionHeader(stringResource(R.string.home_section_actions)) }
                item {
                    ListRow(
                        icon = R.drawable.ic_document,
                        title = stringResource(R.string.home_action_print),
                        subtitle = stringResource(R.string.home_action_print_hint),
                        onClick = onPrint,
                        trailing = { Chevron() },
                    )
                }
                item {
                    ListRow(
                        icon = R.drawable.ic_scan,
                        title = stringResource(R.string.home_action_scan),
                        subtitle = stringResource(R.string.home_action_scan_hint),
                        onClick = onScan,
                        trailing = { Chevron() },
                    )
                }
                item {
                    ListRow(
                        icon = R.drawable.ic_printer,
                        title = stringResource(R.string.home_action_printers),
                        subtitle = stringResource(R.string.home_action_printers_hint),
                        onClick = onPrinters,
                        trailing = { Chevron() },
                    )
                }

                // Recent jobs ---------------------------------------------------
                item { SectionHeader(stringResource(R.string.home_section_recent)) }
                if (jobs.isEmpty()) {
                    item {
                        EmptyState(
                            icon = R.drawable.ic_history,
                            title = stringResource(R.string.home_recent_empty),
                            hint = stringResource(R.string.home_recent_empty_hint),
                        )
                    }
                } else {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSM)) {
                            jobs.take(5).forEach { job ->
                                JobRow(job = job, onRepeat = { uri -> vm.repeatJob(uri) })
                            }
                        }
                    }
                    item { Spacer(Modifier.height(Tokens.SpaceXS)) }
                    item {
                        InfoBanner(
                            text = stringResource(R.string.home_tips_privacy),
                            icon = R.drawable.ic_lock,
                        )
                    }
                }
                item { Spacer(Modifier.height(Tokens.SpaceXXL)) }
            }
        }
    }
}

@Composable
private fun NoPrinterCard(onPrinters: () -> Unit) {
    val colors = LocalOpenColors.current
    OpenCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = R.drawable.ic_usb,
                tint = colors.onSurfaceVariant,
                bg = colors.well,
            )
            Spacer(Modifier.width(Tokens.SpaceMD + 2.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_no_printer),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Text(
                    stringResource(R.string.home_no_printer_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Tokens.SpaceMD))
        ActionChip(
            text = stringResource(R.string.home_no_printer_action),
            onClick = onPrinters,
            icon = R.drawable.ic_search,
            prominent = true,
        )
    }
}

@Composable
private fun PrinterStatusCard(
    vm: AppViewModel,
    printer: com.qawse.openpage.usb.UsbPrinterInfo,
    onPrint: () -> Unit,
    onScan: () -> Unit,
) {
    val colors = LocalOpenColors.current
    OpenCard {
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
                        R.string.home_printer_details,
                        stringResource(R.string.printers_status_connected),
                        PrinterDatabase.driverLabel(vm.driverFor(printer)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Tokens.SpaceSM))
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
        }
        if (!printer.hasPermission) {
            Spacer(Modifier.height(Tokens.SpaceMD))
            ActionChip(
                text = stringResource(R.string.printers_grant),
                onClick = { vm.requestPermission(printer) { } },
                icon = R.drawable.ic_lock,
                prominent = true,
            )
        } else {
            Spacer(Modifier.height(Tokens.SpaceMD))
            Text(
                stringResource(R.string.home_readiness_unknown),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(Tokens.SpaceMD))
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSM)) {
                ActionChip(
                    text = stringResource(R.string.home_action_print),
                    onClick = onPrint,
                    icon = R.drawable.ic_document,
                )
                ActionChip(
                    text = stringResource(R.string.home_action_scan),
                    onClick = onScan,
                    icon = R.drawable.ic_scan,
                )
            }
        }
    }
}

@Composable
private fun JobRow(job: PrintJobRecord, onRepeat: (String) -> Unit) {
    val colors = LocalOpenColors.current
    val time = TimeText.forTimestamp(System.currentTimeMillis(), job.timestamp)
    val statusDesc = when {
        job.ok -> stringResource(R.string.a11y_job_status_ok)
        job.problem == "CANCELLED" -> stringResource(R.string.a11y_job_status_cancelled)
        else -> stringResource(R.string.a11y_job_status_failed)
    }
    ListRow(
        icon = when {
            job.ok -> R.drawable.ic_document
            job.problem == "CANCELLED" -> R.drawable.ic_close
            else -> R.drawable.ic_close
        },
        title = job.fileName,
        subtitle = "${job.printerName} · " +
            androidx.compose.ui.res.pluralStringResource(
                R.plurals.home_job_pages, job.pages, job.pages, job.copies) +
            " · $time",
        onClick = null,
        trailing = {
            if (job.ok && job.sourceUri != null) {
                ActionChip(
                    text = stringResource(R.string.home_job_repeat),
                    onClick = { onRepeat(job.sourceUri!!) },
                    icon = R.drawable.ic_refresh,
                )
            } else if (!job.ok) {
                Text(
                    if (job.problem == "CANCELLED")
                        androidx.compose.ui.res.pluralStringResource(R.plurals.home_job_cancelled, job.pages, job.pages)
                    else stringResource(R.string.home_job_failed_retry),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun Chevron() {
    val colors = LocalOpenColors.current
    androidx.compose.material3.Icon(
        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_arrow_forward),
        contentDescription = null,
        tint = colors.onSurfaceVariant,
        modifier = Modifier.size(Tokens.IconMD),
    )
}

@Composable
internal fun rememberWidth(): WindowWidth {
    val w = LocalConfiguration.current.screenWidthDp
    return when {
        w >= Tokens.WIDTH_EXPANDED -> WindowWidth.EXPANDED
        w >= Tokens.WIDTH_MEDIUM -> WindowWidth.MEDIUM
        else -> WindowWidth.COMPACT
    }
}
