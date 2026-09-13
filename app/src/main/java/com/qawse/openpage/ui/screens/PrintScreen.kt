package com.qawse.openpage.ui.screens

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qawse.openpage.R
import com.qawse.openpage.core.PrintProblem
import com.qawse.openpage.data.ColorMode
import com.qawse.openpage.data.DuplexMode
import com.qawse.openpage.data.MarginPreset
import com.qawse.openpage.data.Margins
import com.qawse.openpage.data.OrientationMode
import com.qawse.openpage.data.PageRange
import com.qawse.openpage.data.PaperSize
import com.qawse.openpage.data.PrintQuality
import com.qawse.openpage.data.PrintSettings
import com.qawse.openpage.data.PrinterDatabase
import com.qawse.openpage.data.RangeValidation
import com.qawse.openpage.data.ScalingMode
import com.qawse.openpage.ui.components.ActionChip
import com.qawse.openpage.ui.components.BannerTone
import com.qawse.openpage.ui.components.ConfirmDialog
import com.qawse.openpage.ui.components.EmptyState
import com.qawse.openpage.ui.components.FieldLabel
import com.qawse.openpage.ui.components.FieldSupport
import com.qawse.openpage.ui.components.InfoBanner
import com.qawse.openpage.ui.components.OpenCard
import com.qawse.openpage.ui.components.OpenIconButton
import com.qawse.openpage.ui.components.PillTone
import com.qawse.openpage.ui.components.PrimaryButton
import com.qawse.openpage.ui.components.ProgressPanel
import com.qawse.openpage.ui.components.SecondaryButton
import com.qawse.openpage.ui.components.SectionHeader
import com.qawse.openpage.ui.components.SettingSegment
import com.qawse.openpage.ui.components.StatusPill
import com.qawse.openpage.ui.components.Stepper
import com.qawse.openpage.ui.components.SwitchRow
import com.qawse.openpage.ui.theme.LocalOpenColors
import com.qawse.openpage.ui.theme.Tokens
import com.qawse.openpage.viewmodel.AppViewModel
import com.qawse.openpage.viewmodel.AppViewModel.PrintUi

@Composable
fun PrintScreen(vm: AppViewModel, onPrinters: () -> Unit) {
    val ui by vm.printUi.collectAsState()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = queryDisplayName(vm, uri) ?: "Document"
            vm.openDocument(uri, name)
        }
    }

    when (val state = ui) {
        PrintUi.Idle -> PrintPickScreen(onPick = {
            picker.launch(arrayOf("application/pdf", "image/*", "text/*"))
        })
        is PrintUi.Opening -> PrintOpeningScreen(state.fileName)
        is PrintUi.FailedOpen -> PrintOpenFailedScreen(state.fileName, onPick = {
            picker.launch(arrayOf("application/pdf", "image/*", "text/*"))
        })
        is PrintUi.Ready -> PrintSetupScreen(vm, state, onPrinters)
        is PrintUi.Sending -> PrintSendingScreen(vm, state)
        is PrintUi.Done -> PrintDoneScreen(vm, state)
        is PrintUi.Failed -> PrintFailedScreen(vm, state, onPrinters)
    }
}

private fun queryDisplayName(vm: AppViewModel, uri: android.net.Uri): String? = try {
    val app = vm.getApplication<android.app.Application>()
    app.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
    }
} catch (e: Exception) { null }

// --------------------------------------------------------------- pick screen

@Composable
private fun PrintPickScreen(onPick: () -> Unit) {
    val width = rememberWidth()
    ScreenScaffold(title = stringResource(R.string.print_title), width = width) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Spacer(Modifier.height(Tokens.SpaceXXL))
            EmptyState(
                icon = R.drawable.ic_blank_page,
                title = stringResource(R.string.print_pick_title),
                hint = stringResource(R.string.print_pick_hint),
                action = {
                    PrimaryButton(
                        text = stringResource(R.string.print_pick_action),
                        icon = R.drawable.ic_document,
                        onClick = onPick,
                    )
                },
            )
        }
    }
}

@Composable
private fun PrintOpeningScreen(name: String) {
    val width = rememberWidth()
    ScreenScaffold(title = stringResource(R.string.print_title), width = width) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(Tokens.IconLG + 24.dp),
                    strokeWidth = 3.5.dp,
                    color = LocalOpenColors.current.primary,
                )
                Spacer(Modifier.height(Tokens.SpaceLG))
                Text(
                    stringResource(R.string.print_opening, name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalOpenColors.current.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Tokens.SpaceLG),
                )
            }
        }
    }
}

@Composable
private fun PrintOpenFailedScreen(name: String, onPick: () -> Unit) {
    val colors = LocalOpenColors.current
    val width = rememberWidth()
    ScreenScaffold(title = stringResource(R.string.print_title), width = width) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(Tokens.SpaceLG),
            ) {
                StatusPill(
                    text = stringResource(R.string.print_open_failed_title),
                    tone = PillTone.ERROR,
                    icon = R.drawable.ic_close,
                )
                Spacer(Modifier.height(Tokens.SpaceLG))
                Text(
                    stringResource(R.string.print_open_failed_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Tokens.SpaceXL))
                PrimaryButton(
                    text = stringResource(R.string.print_open_failed_action),
                    icon = R.drawable.ic_document,
                    onClick = onPick,
                    modifier = Modifier.width(280.dp),
                )
            }
        }
    }
}

// ------------------------------------------------------------- setup screen

@Composable
private fun PrintSetupScreen(
    vm: AppViewModel,
    state: PrintUi.Ready,
    onPrinters: () -> Unit,
) {
    val printers by vm.printers.collectAsState()
    val target by vm.targetPrinter.collectAsState()
    var showConfirm by remember { mutableStateOf(false) }
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    val width = rememberWidth()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val twoPane = width != com.qawse.openpage.ui.navigation.WindowWidth.COMPACT || landscape

    val validation = if (state.isText) null
    else PageRange.validate(state.settings.pageRange, state.pageCount)
    val selectedCount = when (validation) {
        is RangeValidation.Ok -> validation.selection.pages.size
        is RangeValidation.OutOfRange -> validation.selection.pages.size
        is RangeValidation.AllPages -> state.pageCount
        else -> 0
    }
    val canPrint = target != null && selectedCount > 0

    val actionLabel = when {
        selectedCount <= 1 && state.settings.copies == 1 ->
            stringResource(R.string.print_action_one)
        state.settings.copies == 1 ->
            androidx.compose.ui.res.pluralStringResource(
                R.plurals.print_action_simple, selectedCount, selectedCount)
        else ->
            androidx.compose.ui.res.pluralStringResource(
                R.plurals.print_action_pages, selectedCount, selectedCount, state.settings.copies)
    }

    val firePrint = {
        val heavy = selectedCount * state.settings.copies >= 4 ||
            state.settings.duplex != DuplexMode.OFF
        if (heavy) showConfirm = true else vm.startPrint()
    }

    val previewPane: @Composable (Modifier) -> Unit = { modifier ->
        PreviewPane(vm = vm, state = state, modifier = modifier)
    }
    val settingsPane: @Composable (Modifier) -> Unit = { modifier ->
        SettingsPane(
            vm = vm,
            state = state,
            advancedOpen = advancedOpen,
            onAdvancedToggle = { advancedOpen = it },
            modifier = modifier,
        )
    }
    val actionBar: @Composable () -> Unit = {
        Column {
            Spacer(Modifier.height(Tokens.SpaceMD))
            com.qawse.openpage.ui.components.SectionHeader(
                stringResource(R.string.print_destination))
            DestinationBanner(target = target, onPrinters = onPrinters)
            Spacer(Modifier.height(Tokens.SpaceSM))
            PrimaryButton(
                text = actionLabel,
                icon = R.drawable.ic_printer,
                enabled = canPrint,
                onClick = { firePrint() },
            )
            Spacer(Modifier.height(Tokens.SpaceLG))
        }
    }

    if (showConfirm && target != null) {
        ConfirmDialog(
            title = stringResource(R.string.print_confirm_title),
            body = stringResource(
                R.string.print_confirm_body,
                state.settings.paper.label,
                colorModeLabel(state.settings.colorMode),
                if (state.settings.duplex == DuplexMode.OFF)
                    stringResource(R.string.print_duplex_off)
                else stringResource(R.string.print_confirm_duplex),
            ) + " · " + androidx.compose.ui.res.pluralStringResource(
                R.plurals.print_action_simple,
                selectedCount * state.settings.copies,
                selectedCount * state.settings.copies).removePrefix("Print ") +
                "\n" + stringResource(R.string.print_confirm_note),
            confirmLabel = stringResource(R.string.print_action_one),
            onConfirm = { showConfirm = false; vm.startPrint() },
            onDismiss = { showConfirm = false },
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }

    if (twoPane) {
        ScreenScaffold(title = stringResource(R.string.print_title), width = width) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).padding(end = Tokens.SpaceMD)) {
                    previewPane(Modifier.fillMaxSize())
                }
                Column(
                    Modifier
                        .width(Tokens.DetailPaneWidth)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    settingsPane(Modifier)
                    actionBar()
                }
            }
        }
    } else {
        ScreenScaffold(title = stringResource(R.string.print_title), width = width) {
            Column(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.weight(1f)) {
                    item { previewPane(Modifier) }
                    item { settingsPane(Modifier) }
                }
                Surface(
                    color = LocalOpenColors.current.background,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    actionBar()
                }
            }
        }
    }
}

// ------------------------------------------------------------- preview pane

@Composable
private fun PreviewPane(vm: AppViewModel, state: PrintUi.Ready, modifier: Modifier = Modifier) {
    val colors = LocalOpenColors.current
    val settings = state.settings
    OpenCard(modifier = modifier.padding(Tokens.SpaceSM)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val paperLandscape = when (settings.orientation) {
                OrientationMode.PORTRAIT -> false
                OrientationMode.LANDSCAPE -> true
                OrientationMode.AUTO -> state.preview?.let { it.width > it.height } ?: false
            }
            val ratio = if (settings.paper.thermalRoll) 0.55f
            else (if (paperLandscape) settings.paper.heightMm / settings.paper.widthMm
            else settings.paper.widthMm / settings.paper.heightMm)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(Tokens.RadiusSM))
                    .background(colors.well),
                contentAlignment = Alignment.Center,
            ) {
                if (state.preview != null) {
                    Image(
                        bitmap = state.preview.asImageBitmap(),
                        contentDescription = stringResource(
                            R.string.print_preview_note),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(Tokens.SpaceSM),
                    )
                } else {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(Tokens.IconLG + 8.dp),
                        strokeWidth = 3.dp,
                        color = colors.onSurfaceVariant,
                    )
                }
                // Margin guides reflect the real margins.
                if (!settings.paper.thermalRoll) {
                    MarginGuides(settings = settings, showCustom = settings.marginPreset == MarginPreset.CUSTOM)
                }
                if (state.previewDirty) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = colors.background.copy(alpha = 0.84f),
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = Tokens.SpaceSM),
                    ) {
                        Text(
                            stringResource(R.string.print_preview_generating),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Tokens.SpaceMD, vertical = Tokens.SpaceXS + 2.dp),
                        )
                    }
                }
            }
            if (!state.isText && state.pageCount > 1) {
                Spacer(Modifier.height(Tokens.SpaceMD))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceMD),
                ) {
                    OpenIconButton(
                        icon = R.drawable.ic_chevron_left,
                        label = stringResource(R.string.print_preview_previous),
                        onClick = { vm.movePreview(-1) },
                        enabled = state.previewPage > 0,
                    )
                    Text(
                        stringResource(R.string.print_preview_page, state.previewPage + 1, state.pageCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                    OpenIconButton(
                        icon = R.drawable.ic_chevron_right,
                        label = stringResource(R.string.print_preview_next),
                        onClick = { vm.movePreview(1) },
                        enabled = state.previewPage < state.pageCount - 1,
                    )
                }
            }
            Spacer(Modifier.height(Tokens.SpaceMD))
            Text(
                stringResource(R.string.print_preview_note),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (state.isText) {
                Spacer(Modifier.height(Tokens.SpaceXS))
                Text(
                    stringResource(R.string.print_txt_renders),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun MarginGuides(settings: PrintSettings, showCustom: Boolean) {
    val colors = LocalOpenColors.current
    Canvas(Modifier.fillMaxSize().padding(Tokens.SpaceSM)) {
        val w = size.width
        val h = size.height
        val paper = settings.paper
        // Fractions of the visible page occupied by each margin.
        val lf = (settings.margins.leftMm / paper.widthMm).coerceIn(0f, 0.5f)
        val rf = (settings.margins.rightMm / paper.widthMm).coerceIn(0f, 0.5f)
        val tf = (settings.margins.topMm / paper.heightMm).coerceIn(0f, 0.5f)
        val bf = (settings.margins.bottomMm / paper.heightMm).coerceIn(0f, 0.5f)
        val l = w * lf
        val r = w * rf
        val t = h * tf
        val b = h * bf
        val stroke = colors.onSurfaceVariant.copy(alpha = 0.9f)
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        drawRect(
            color = stroke,
            topLeft = Offset(l, t),
            size = Size(w - l - r, h - t - b),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx(), pathEffect = dash),
        )
    }
}

// ------------------------------------------------------------ settings pane

@Composable
private fun SettingsPane(
    vm: AppViewModel,
    state: PrintUi.Ready,
    advancedOpen: Boolean,
    onAdvancedToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    val validation = if (state.isText) null
    else PageRange.validate(settings.pageRange, state.pageCount)

    Column(modifier = modifier) {
        SectionHeader(stringResource(R.string.print_section_document))
        if (!state.isText) {
            FieldLabel(stringResource(R.string.print_page_range))
            OpenTextFieldLocal(
                value = settings.pageRange,
                onValueChange = { v -> vm.updateSettings { it.copy(pageRange = v) } },
                placeholder = stringResource(R.string.print_page_range_hint),
                validation = validation,
                total = state.pageCount,
            )
        } else {
            FieldSupport(stringResource(R.string.print_page_range_all))
        }
        Spacer(Modifier.height(Tokens.SpaceSM))
        FieldLabel(stringResource(R.string.print_copies))
        Stepper(
            value = settings.copies,
            onChange = { n -> vm.updateSettings { it.copy(copies = n) } },
            decreaseLabel = stringResource(R.string.print_copies_decrease),
            increaseLabel = stringResource(R.string.print_copies_increase),
            valueLabel = stringResource(R.string.print_copies),
        )

        SectionHeader(stringResource(R.string.print_section_paper))
        DropdownFieldLocal(
            value = settings.paper,
            options = PaperSize.entries.toList(),
            fieldLabel = stringResource(R.string.print_paper_size),
            label = { it.label },
            onSelect = { p ->
                vm.updateSettings { s ->
                    s.copy(
                        paper = p,
                        margins = if (p.thermalRoll) Margins(0f, 0f, 0f, 0f) else s.margins,
                    )
                }
            },
        )
        Spacer(Modifier.height(Tokens.SpaceSM))
        SettingSegment(
            title = stringResource(R.string.print_orientation),
            options = OrientationMode.entries.toList(),
            selected = settings.orientation,
            label = { orientationLabel(it) },
            onSelect = { v -> vm.updateSettings { it.copy(orientation = v) } },
        )

        SectionHeader(stringResource(R.string.print_section_layout))
        SettingSegment(
            title = stringResource(R.string.print_scaling),
            options = ScalingMode.entries.toList(),
            selected = settings.scaling,
            label = { scalingLabel(it) },
            onSelect = { v -> vm.updateSettings { it.copy(scaling = v) } },
        )
        Spacer(Modifier.height(Tokens.SpaceMD))
        MarginsBlock(vm = vm, settings = settings)

        SectionHeader(stringResource(R.string.print_section_output))
        SettingSegment(
            title = stringResource(R.string.print_color_mode),
            options = listOf(ColorMode.GRAYSCALE, ColorMode.COLOR, ColorMode.MONO),
            selected = settings.colorMode,
            label = { colorModeLabel(it) },
            onSelect = { v -> vm.updateSettings { it.copy(colorMode = v) } },
        )
        Spacer(Modifier.height(Tokens.SpaceMD))
        SettingSegment(
            title = stringResource(R.string.print_quality),
            options = PrintQuality.entries.toList(),
            selected = settings.quality,
            label = { qualityLabel(it) },
            onSelect = { v -> vm.updateSettings { it.copy(quality = v) } },
        )
        Spacer(Modifier.height(Tokens.SpaceMD))
        SettingSegment(
            title = stringResource(R.string.print_duplex),
            options = listOf(DuplexMode.OFF, DuplexMode.LONG, DuplexMode.SHORT),
            selected = settings.duplex,
            label = { duplexLabel(it) },
            onSelect = { v -> vm.updateSettings { it.copy(duplex = v) } },
        )

        SectionHeader(stringResource(R.string.print_section_advanced))
        OpenCard(onClick = { onAdvancedToggle(!advancedOpen) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (advancedOpen) stringResource(R.string.print_advanced_hide)
                    else stringResource(R.string.print_advanced_show),
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalOpenColors.current.onSurface,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.Icon(
                    painter = androidx.compose.ui.res.painterResource(
                        if (advancedOpen) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = LocalOpenColors.current.onSurfaceVariant,
                    modifier = Modifier.size(Tokens.IconMD),
                )
            }
        }
        AnimatedVisibility(visible = advancedOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSM)) {
                Spacer(Modifier.height(Tokens.SpaceXS))
                SwitchRow(
                    title = stringResource(R.string.print_collate),
                    subtitle = stringResource(R.string.print_collate_hint),
                    checked = settings.collate,
                    onChange = { b -> vm.updateSettings { it.copy(collate = b) } },
                )
                SwitchRow(
                    title = stringResource(R.string.print_reverse_order),
                    subtitle = stringResource(R.string.print_reverse_order_hint),
                    checked = settings.reverseOrder,
                    onChange = { b -> vm.updateSettings { it.copy(reverseOrder = b) } },
                )
                SwitchRow(
                    title = stringResource(R.string.print_mirror),
                    subtitle = stringResource(R.string.print_mirror_hint),
                    checked = settings.mirror,
                    onChange = { b -> vm.updateSettings { it.copy(mirror = b) } },
                )
            }
        }
        Spacer(Modifier.height(Tokens.SpaceSM))
    }
}

@Composable
private fun OpenTextFieldLocal(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    validation: RangeValidation?,
    total: Int,
) {
    val (support, error) = when (validation) {
        is RangeValidation.AllPages -> stringResource(R.string.print_page_range_all) to false
        is RangeValidation.Ok ->
            androidx.compose.ui.res.pluralStringResource(
                R.plurals.print_page_range_ok,
                validation.selection.pages.size,
                validation.selection.pages.size) to false
        is RangeValidation.OutOfRange ->
            androidx.compose.ui.res.pluralStringResource(
                R.plurals.print_page_range_out_of_range, total, total) to true
        RangeValidation.SyntaxError -> stringResource(R.string.print_page_range_syntax) to true
        null -> stringResource(R.string.print_page_range_all) to false
    }
    Column(Modifier.padding(horizontal = Tokens.SpaceLG + Tokens.SpaceXS)) {
        com.qawse.openpage.ui.components.OpenTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            supportingText = support,
            isError = error,
        )
    }
}

@Composable
private fun DropdownFieldLocal(
    value: PaperSize,
    options: List<PaperSize>,
    fieldLabel: String,
    label: (PaperSize) -> String,
    onSelect: (PaperSize) -> Unit,
) {
    com.qawse.openpage.ui.components.DropdownField(
        value = value,
        options = options,
        label = label,
        onSelect = onSelect,
        fieldLabel = fieldLabel,
    )
}

@Composable
private fun MarginsBlock(vm: AppViewModel, settings: PrintSettings) {
    FieldLabel(stringResource(R.string.print_margins))
    if (settings.paper.thermalRoll) {
        FieldSupport(stringResource(R.string.print_margins_thermal))
        return
    }
    val presets = listOf(MarginPreset.NONE, MarginPreset.NARROW, MarginPreset.NORMAL, MarginPreset.WIDE)
    Column(Modifier.padding(horizontal = Tokens.SpaceLG + Tokens.SpaceXS)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSM)) {
            presets.forEach { preset ->
                val active = settings.marginPreset == preset
                MarginChip(
                    label = marginPresetLabel(preset),
                    active = active,
                    modifier = Modifier.weight(1f),
                ) {
                    vm.updateSettings {
                        it.copy(
                            marginPreset = preset,
                            margins = Margins(preset.mm, preset.mm, preset.mm, preset.mm),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Tokens.SpaceSM))
        CustomMarginFields(vm = vm, margins = settings.margins)
    }
}

@Composable
private fun MarginChip(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalOpenColors.current
    val bg = if (active) colors.primary else colors.well
    val fg = if (active) colors.onPrimary else colors.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = bg,
        contentColor = fg,
        modifier = modifier.heightIn(min = Tokens.TouchMin),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = Tokens.SpaceSM)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CustomMarginFields(vm: AppViewModel, margins: Margins) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    MarginChip(
        label = stringResource(R.string.print_margin_custom),
        active = expanded,
        modifier = Modifier.fillMaxWidth(0.4f),
    ) { expanded = !expanded }
    if (expanded) {
        Spacer(Modifier.height(Tokens.SpaceSM))
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSM)) {
            MarginField(stringResource(R.string.print_margin_left), margins.leftMm) { v ->
                vm.updateSettings { it.copy(margins = it.margins.copy(leftMm = v), marginPreset = MarginPreset.CUSTOM) }
            }
            MarginField(stringResource(R.string.print_margin_right), margins.rightMm) { v ->
                vm.updateSettings { it.copy(margins = it.margins.copy(rightMm = v), marginPreset = MarginPreset.CUSTOM) }
            }
        }
        Spacer(Modifier.height(Tokens.SpaceSM))
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSM)) {
            MarginField(stringResource(R.string.print_margin_top), margins.topMm) { v ->
                vm.updateSettings { it.copy(margins = it.margins.copy(topMm = v), marginPreset = MarginPreset.CUSTOM) }
            }
            MarginField(stringResource(R.string.print_margin_bottom), margins.bottomMm) { v ->
                vm.updateSettings { it.copy(margins = it.margins.copy(bottomMm = v), marginPreset = MarginPreset.CUSTOM) }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.MarginField(
    label: String,
    valueMm: Float,
    onChange: (Float) -> Unit,
) {
    var text by remember(valueMm) {
        mutableStateOf(if (valueMm % 1f == 0f) valueMm.toInt().toString() else valueMm.toString())
    }
    Column(Modifier.weight(1f)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = LocalOpenColors.current.onSurfaceVariant,
            modifier = Modifier.padding(start = Tokens.SpaceXS, bottom = Tokens.SpaceXS + 2.dp),
        )
        com.qawse.openpage.ui.components.OpenTextField(
            value = text,
            onValueChange = { v ->
                text = v.filter { it.isDigit() || it == '.' }.take(6)
                text.toFloatOrNull()?.let { onChange(it.coerceIn(0f, 50f)) }
            },
            placeholder = "mm",
            numeric = true,
        )
    }
}

@Composable
private fun DestinationBanner(
    target: com.qawse.openpage.usb.UsbPrinterInfo?,
    onPrinters: () -> Unit,
) {
    if (target != null) {
        InfoBanner(
            text = target.name,
            icon = R.drawable.ic_printer,
            tone = BannerTone.SUCCESS,
        )
    } else {
        InfoBanner(
            text = stringResource(R.string.print_destination_none_hint),
            icon = R.drawable.ic_usb,
            tone = BannerTone.ERROR,
            action = {
                ActionChip(
                    text = stringResource(R.string.print_destination_choose),
                    onClick = onPrinters,
                )
            },
        )
    }
}

// ---------------------------------------------------------- progress screens

@Composable
private fun PrintSendingScreen(vm: AppViewModel, state: PrintUi.Sending) {
    val width = rememberWidth()
    ScreenScaffold(title = stringResource(R.string.print_title), width = width) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(Tokens.SpaceXL).width(360.dp),
            ) {
                ProgressPanel(
                    label = stringResource(R.string.print_sending_title, state.printerName),
                    detail = if (state.phaseRendering)
                        androidx.compose.ui.res.pluralStringResource(
                            R.plurals.print_sending_render, state.page, state.page, state.totalPages)
                    else androidx.compose.ui.res.pluralStringResource(
                            R.plurals.print_sending_transfer, state.page, state.page, state.totalPages) +
                        if (state.copies > 1)
                            " · " + stringResource(R.string.print_sending_copy, state.copy, state.copies)
                        else "",
                    progress = if (state.totalPages > 0) state.page.toFloat() / state.totalPages else null,
                    onCancel = { vm.cancelPrint() },
                    cancelLabel = stringResource(R.string.print_sending_cancel),
                )
                Spacer(Modifier.height(Tokens.SpaceMD))
                Text(
                    stringResource(R.string.print_sending_cancel_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalOpenColors.current.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PrintDoneScreen(vm: AppViewModel, state: PrintUi.Done) {
    val width = rememberWidth()
    ScreenScaffold(title = stringResource(R.string.print_title), width = width) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(Tokens.SpaceXL).width(360.dp),
            ) {
                StatusPill(
                    text = stringResource(R.string.print_done_title),
                    tone = PillTone.SUCCESS,
                    icon = R.drawable.ic_check,
                )
                Spacer(Modifier.height(Tokens.SpaceLG))
                Text(
                    if (state.pages * state.copies == 1)
                        stringResource(R.string.print_done_body_single, state.fileName, state.printerName)
                    else androidx.compose.ui.res.pluralStringResource(
                        R.plurals.print_done_body, state.pages,
                        state.fileName, state.pages, state.copies, state.printerName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalOpenColors.current.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Tokens.SpaceXS))
                Text(
                    stringResource(R.string.print_done_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalOpenColors.current.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Tokens.SpaceXXL))
                PrimaryButton(
                    text = stringResource(R.string.print_another),
                    icon = R.drawable.ic_document,
                    onClick = { vm.resetPrintUi() },
                )
            }
        }
    }
}

@Composable
private fun PrintFailedScreen(
    vm: AppViewModel,
    state: PrintUi.Failed,
    onPrinters: () -> Unit,
) {
    val width = rememberWidth()
    val message = printProblemMessage(state.problem, state.pagesSent)
    ScreenScaffold(title = stringResource(R.string.print_title), width = width) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(Tokens.SpaceXL).width(380.dp),
            ) {
                StatusPill(
                    text = stringResource(R.string.print_failed_title),
                    tone = PillTone.ERROR,
                    icon = R.drawable.ic_close,
                )
                Spacer(Modifier.height(Tokens.SpaceLG))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalOpenColors.current.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Tokens.SpaceXXL))
                when (state.problem) {
                    PrintProblem.NO_PRINTER, PrintProblem.NO_PERMISSION ->
                        PrimaryButton(
                            text = stringResource(R.string.print_go_printers),
                            icon = R.drawable.ic_printer,
                            onClick = onPrinters,
                        )
                    PrintProblem.CANCELLED ->
                        PrimaryButton(
                            text = stringResource(R.string.print_another),
                            icon = R.drawable.ic_document,
                            onClick = { vm.resetPrintUi() },
                        )
                    else ->
                        PrimaryButton(
                            text = stringResource(R.string.print_retry),
                            icon = R.drawable.ic_refresh,
                            onClick = { vm.startPrint() },
                        )
                }
                Spacer(Modifier.height(Tokens.SpaceSM))
                SecondaryButton(
                    text = stringResource(R.string.action_close),
                    onClick = { vm.resetPrintUi() },
                )
            }
        }
    }
}

@Composable
private fun printProblemMessage(problem: PrintProblem, pagesSent: Int): String = when (problem) {
    PrintProblem.NO_PRINTER -> stringResource(R.string.print_failed_no_printer)
    PrintProblem.NO_PERMISSION -> stringResource(R.string.print_failed_permission)
    PrintProblem.TRANSPORT -> if (pagesSent > 0)
        stringResource(R.string.print_failed_transport, pagesSent)
    else stringResource(R.string.print_failed_transport_none)
    PrintProblem.OPEN_FAILED -> stringResource(R.string.print_failed_open)
    PrintProblem.RENDER -> stringResource(R.string.print_failed_render)
    PrintProblem.BAD_RANGE -> stringResource(R.string.print_failed_range)
    PrintProblem.CANCELLED -> if (pagesSent > 0)
        androidx.compose.ui.res.pluralStringResource(
            R.plurals.print_failed_cancelled, pagesSent, pagesSent)
    else stringResource(R.string.print_failed_cancelled_none)
}

// ------------------------------------------------------------- enum labels

@Composable
fun orientationLabel(mode: OrientationMode): String = when (mode) {
    OrientationMode.AUTO -> stringResource(R.string.print_orientation_auto)
    OrientationMode.PORTRAIT -> stringResource(R.string.print_orientation_portrait)
    OrientationMode.LANDSCAPE -> stringResource(R.string.print_orientation_landscape)
}

@Composable
fun colorModeLabel(mode: ColorMode): String = when (mode) {
    ColorMode.COLOR -> stringResource(R.string.print_color)
    ColorMode.GRAYSCALE -> stringResource(R.string.print_grayscale)
    ColorMode.MONO -> stringResource(R.string.print_black_white)
}

@Composable
fun qualityLabel(q: PrintQuality): String = when (q) {
    PrintQuality.DRAFT -> stringResource(R.string.print_quality_draft)
    PrintQuality.NORMAL -> stringResource(R.string.print_quality_normal)
    PrintQuality.HIGH -> stringResource(R.string.print_quality_high)
}

@Composable
fun scalingLabel(mode: ScalingMode): String = when (mode) {
    ScalingMode.FIT -> stringResource(R.string.print_scaling_fit)
    ScalingMode.FILL -> stringResource(R.string.print_scaling_fill)
    ScalingMode.ACTUAL -> stringResource(R.string.print_scaling_actual)
}

@Composable
fun duplexLabel(mode: DuplexMode): String = when (mode) {
    DuplexMode.OFF -> stringResource(R.string.print_duplex_off)
    DuplexMode.LONG -> stringResource(R.string.print_duplex_long)
    DuplexMode.SHORT -> stringResource(R.string.print_duplex_short)
}

@Composable
fun marginPresetLabel(preset: MarginPreset): String = when (preset) {
    MarginPreset.NONE -> stringResource(R.string.print_margin_none)
    MarginPreset.NARROW -> stringResource(R.string.print_margin_narrow)
    MarginPreset.NORMAL -> stringResource(R.string.print_margin_normal)
    MarginPreset.WIDE -> stringResource(R.string.print_margin_wide)
    MarginPreset.CUSTOM -> stringResource(R.string.print_margin_custom)
}
