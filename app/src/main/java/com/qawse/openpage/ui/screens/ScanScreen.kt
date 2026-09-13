package com.qawse.openpage.ui.screens

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qawse.openpage.R
import com.qawse.openpage.core.ScanProblem
import com.qawse.openpage.scan.ScanProcessor
import com.qawse.openpage.ui.components.ActionChip
import com.qawse.openpage.ui.components.ConfirmDialog
import com.qawse.openpage.ui.components.InfoBanner
import com.qawse.openpage.ui.components.ListRow
import com.qawse.openpage.ui.components.OpenCard
import com.qawse.openpage.ui.components.OpenIconButton
import com.qawse.openpage.ui.components.PillTone
import com.qawse.openpage.ui.components.PrimaryButton
import com.qawse.openpage.ui.components.ProgressPanel
import com.qawse.openpage.ui.components.SecondaryButton
import com.qawse.openpage.ui.components.SectionHeader
import com.qawse.openpage.ui.components.SegmentedControl
import com.qawse.openpage.ui.components.StatusPill
import com.qawse.openpage.ui.theme.LocalOpenColors
import com.qawse.openpage.ui.theme.Tokens
import com.qawse.openpage.viewmodel.AppViewModel
import com.qawse.openpage.viewmodel.AppViewModel.ScanUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ScanScreen(vm: AppViewModel, onGoToPrint: () -> Unit = {}) {
    val ui by vm.scanUi.collectAsState()
    val context = LocalContext.current

    // Full-resolution camera capture through the system camera app.
    val photoFile = remember {
        File(context.cacheDir, "scans").apply { mkdirs() }
            .let { File(it, "camera_scan.jpg") }
    }
    val photoUri = remember {
        androidx.core.content.FileProvider.getUriForFile(
            context, "com.qawse.openpage.fileprovider", photoFile)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) {
            val bmp = decodeScaled(photoFile.absolutePath, 2400)
            if (bmp != null) vm.acceptCameraCapture(bmp) else vm.cameraCaptureFailed()
        } // cancelled — quietly return to the method chooser
    }

    when (val state = ui) {
        ScanUi.Idle, ScanUi.Choosing -> ScanChooseScreen(
            onGlass = { vm.startGlassScan() },
            onCamera = {
                try { cameraLauncher.launch(photoUri) }
                catch (e: android.content.ActivityNotFoundException) {
                    vm.cameraUnavailable()
                }
            },
        )
        is ScanUi.GlassScanning -> ScanProgressScreen(vm, state)
        is ScanUi.Processing -> ScanProcessingScreen(state)
        is ScanUi.Cropping -> ScanCropScreen(vm, state)
        is ScanUi.Review -> ScanReviewScreen(vm, state, onGoToPrint)
        is ScanUi.Failed -> ScanFailedScreen(vm, state)
    }
}

private fun decodeScaled(path: String, maxDim: Int): Bitmap? {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    return android.graphics.BitmapFactory.decodeFile(path, opts)
}

// ------------------------------------------------------------- choosing

@Composable
private fun ScanChooseScreen(onGlass: () -> Unit, onCamera: () -> Unit) {
    val width = rememberWidth()
    ScreenScaffold(title = stringResource(R.string.scan_title), width = width) {
        LazyColumn(Modifier.fillMaxSize()) {
            item { Spacer(Modifier.height(Tokens.SpaceMD)) }
            item {
                Text(
                    stringResource(R.string.scan_choose_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalOpenColors.current.onSurface,
                    modifier = Modifier.padding(horizontal = Tokens.SpaceLG + Tokens.SpaceXS),
                )
            }
            item { Spacer(Modifier.height(Tokens.SpaceSM)) }
            item {
                ListRow(
                    icon = R.drawable.ic_scan,
                    title = stringResource(R.string.scan_method_glass),
                    subtitle = stringResource(R.string.scan_method_glass_hint),
                    onClick = onGlass,
                )
            }
            item {
                ListRow(
                    icon = R.drawable.ic_camera,
                    title = stringResource(R.string.scan_method_camera),
                    subtitle = stringResource(R.string.scan_method_camera_hint),
                    onClick = onCamera,
                )
            }
            item {
                Spacer(Modifier.height(Tokens.SpaceSM))
                InfoBanner(
                    text = stringResource(R.string.scan_camera_explainer),
                    icon = R.drawable.ic_camera,
                )
            }
            item { Spacer(Modifier.height(Tokens.SpaceXXL)) }
        }
    }
}

// ------------------------------------------------------------- progress

@Composable
private fun ScanProgressScreen(vm: AppViewModel, state: ScanUi.GlassScanning) {
    val width = rememberWidth()
    ScreenScaffold(title = stringResource(R.string.scan_title), width = width) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ProgressPanel(
                label = stringResource(R.string.scan_glass_scanning_title, state.printerName),
                detail = stringResource(R.string.scan_glass_scanning_hint),
                onCancel = { vm.cancelScan() },
                cancelLabel = stringResource(R.string.scan_cancel),
                modifier = Modifier.padding(Tokens.SpaceXL).width(360.dp),
            )
        }
    }
}

@Composable
private fun ScanProcessingScreen(state: ScanUi.Processing) {
    val width = rememberWidth()
    ScreenScaffold(title = stringResource(R.string.scan_title), width = width) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ProgressPanel(
                label = stringResource(R.string.scan_processing_title),
                detail = stringResource(R.string.scan_processing_hint),
                modifier = Modifier.padding(Tokens.SpaceXL).width(360.dp),
            )
        }
    }
}

// ------------------------------------------------------------- crop

@Composable
private fun ScanCropScreen(vm: AppViewModel, state: ScanUi.Cropping) {
    val bitmap = state.bitmap
    // Quad lives in the ViewModel: rotation and configuration changes
    // keep the user's corner placement.
    var quad by remember { mutableStateOf(vm.cropQuad ?: ScanProcessor.defaultQuad(
        bitmap.width.toFloat(), bitmap.height.toFloat())) }
    vm.cropQuad = quad
    var enhance by rememberSaveable { mutableStateOf(ScanProcessor.Enhance.DOCUMENT) }
    var rotate by rememberSaveable { mutableStateOf(false) }

    val width = rememberWidth()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val sidePanel = landscape || width != com.qawse.openpage.ui.navigation.WindowWidth.COMPACT

    val controls: @Composable () -> Unit = {
        Column(Modifier.padding(Tokens.SpaceLG)) {
            Text(
                stringResource(R.string.scan_crop_hint),
                style = MaterialTheme.typography.bodySmall,
                color = LocalOpenColors.current.onSurfaceVariant,
            )
            Spacer(Modifier.height(Tokens.SpaceMD))
            com.qawse.openpage.ui.components.FieldLabel(stringResource(R.string.scan_enhance))
            Spacer(Modifier.height(Tokens.SpaceXS))
            SegmentedControl(
                options = ScanProcessor.Enhance.entries.toList(),
                selected = enhance,
                onSelect = { enhance = it },
                label = { enhanceLabel(it) },
            )
            Spacer(Modifier.height(Tokens.SpaceMD))
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSM)) {
                OpenIconButton(
                    icon = R.drawable.ic_rotate,
                    label = stringResource(R.string.scan_rotate),
                    onClick = { rotate = !rotate },
                    prominent = rotate,
                )
                ActionChip(
                    text = stringResource(R.string.scan_crop_use_whole),
                    icon = R.drawable.ic_fit,
                    onClick = {
                        quad = ScanProcessor.Quad(
                            ScanProcessor.Corner(0f, 0f),
                            ScanProcessor.Corner(bitmap.width.toFloat(), 0f),
                            ScanProcessor.Corner(bitmap.width.toFloat(), bitmap.height.toFloat()),
                            ScanProcessor.Corner(0f, bitmap.height.toFloat()),
                        )
                    },
                )
                ActionChip(
                    text = stringResource(R.string.scan_crop_reset),
                    icon = R.drawable.ic_refresh,
                    onClick = {
                        quad = ScanProcessor.defaultQuad(
                            bitmap.width.toFloat(), bitmap.height.toFloat())
                    },
                )
            }
            Spacer(Modifier.height(Tokens.SpaceMD))
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSM)) {
                PrimaryButton(
                    text = stringResource(R.string.scan_done_crop),
                    icon = R.drawable.ic_check,
                    onClick = { vm.finishCrop(quad, enhance, rotate) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(Tokens.SpaceSM))
            SecondaryButton(
                text = stringResource(R.string.scan_retake),
                onClick = { vm.resetScanUi() },
            )
        }
    }

    ScreenScaffold(title = stringResource(R.string.scan_crop_title), width = width) {
        if (sidePanel) {
            Row(Modifier.fillMaxSize()) {
                CropCanvas(
                    bitmap = bitmap,
                    quad = quad,
                    onQuadChange = { quad = it },
                    modifier = Modifier.weight(1f).padding(Tokens.SpaceSM),
                )
                Column(
                    Modifier
                        .width(Tokens.DetailPaneWidth)
                        .fillMaxSize()
                        .verticalScrollIfNeeded(),
                ) { controls() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                CropCanvas(
                    bitmap = bitmap,
                    quad = quad,
                    onQuadChange = { quad = it },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = Tokens.SpaceSM),
                )
                controls()
            }
        }
    }
}

private fun Modifier.verticalScrollIfNeeded(): Modifier = this.then(
    Modifier.padding(bottom = Tokens.SpaceLG)
)

@Composable
private fun enhanceLabel(mode: ScanProcessor.Enhance): String = when (mode) {
    ScanProcessor.Enhance.ORIGINAL -> stringResource(R.string.scan_enhance_original)
    ScanProcessor.Enhance.DOCUMENT -> stringResource(R.string.scan_enhance_document)
    ScanProcessor.Enhance.GRAYSCALE -> stringResource(R.string.scan_enhance_gray)
}

@Composable
private fun CropCanvas(
    bitmap: Bitmap,
    quad: ScanProcessor.Quad,
    onQuadChange: (ScanProcessor.Quad) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalOpenColors.current
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val scale = minOf(boxW / bitmap.width, boxH / bitmap.height)
        val dispW = bitmap.width * scale
        val dispH = bitmap.height * scale
        val offX = (boxW - dispW) / 2f
        val offY = (boxH - dispH) / 2f

        val disp = remember(quad, scale, offX, offY) {
            listOf(
                Offset(quad.topLeft.x * scale + offX, quad.topLeft.y * scale + offY),
                Offset(quad.topRight.x * scale + offX, quad.topRight.y * scale + offY),
                Offset(quad.bottomRight.x * scale + offX, quad.bottomRight.y * scale + offY),
                Offset(quad.bottomLeft.x * scale + offX, quad.bottomLeft.y * scale + offY),
            )
        }
        var active by remember { mutableIntStateOf(-1) }

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.scan_crop_hint),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(bitmap) {
                detectDragGestures(
                    onDragStart = { pos ->
                        active = disp.indices.minByOrNull {
                            (disp[it] - pos).getDistanceSquared()
                        }?.takeIf { (disp[it] - pos).getDistance() < 80.dp.toPx() } ?: -1
                    },
                    onDrag = { change, _ ->
                        if (active >= 0) {
                            change.consume()
                            val p = change.position
                            val x = ((p.x - offX) / scale).coerceIn(0f, bitmap.width.toFloat())
                            val y = ((p.y - offY) / scale).coerceIn(0f, bitmap.height.toFloat())
                            val c = ScanProcessor.Corner(x, y)
                            onQuadChange(
                                when (active) {
                                    0 -> quad.copy(topLeft = c)
                                    1 -> quad.copy(topRight = c)
                                    2 -> quad.copy(bottomRight = c)
                                    else -> quad.copy(bottomLeft = c)
                                }
                            )
                        }
                    },
                    onDragEnd = { active = -1 },
                )
            }
        ) {
            val path = Path().apply {
                moveTo(disp[0].x, disp[0].y)
                lineTo(disp[1].x, disp[1].y)
                lineTo(disp[2].x, disp[2].y)
                lineTo(disp[3].x, disp[3].y)
                close()
            }
            val scrim = Path().apply {
                addRect(Rect(0f, 0f, size.width, size.height))
                op(this, path, PathOperation.Difference)
            }
            drawPath(scrim, Color(0x8D000000))
            drawPath(
                path,
                androidx.compose.ui.graphics.Color.White,
                style = Stroke(width = 2.dp.toPx()),
            )
            disp.forEach { p ->
                drawCircle(Color.White, radius = 18f, center = p)
                drawCircle(colors.primary, radius = 10f, center = p)
            }
        }
    }
}

// ------------------------------------------------------------- review/tray

@Composable
private fun ScanReviewScreen(vm: AppViewModel, state: ScanUi.Review, onGoToPrint: () -> Unit) {
    val width = rememberWidth()
    val saveUi by vm.scanSave.collectAsState()
    var discardConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(state.pages.size) {
        if (state.pages.isNotEmpty()) listState.animateScrollToItem(state.pages.lastIndex)
    }

    val sidePanel = width == com.qawse.openpage.ui.navigation.WindowWidth.EXPANDED

    if (discardConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.scan_discard_confirm_title),
            body = androidx.compose.ui.res.pluralStringResource(
                R.plurals.scan_discard_confirm_body, state.pages.size, state.pages.size),
            confirmLabel = stringResource(R.string.action_discard),
            onConfirm = { discardConfirm = false; vm.discardScanSession() },
            onDismiss = { discardConfirm = false },
            dismissLabel = stringResource(R.string.action_keep),
            destructive = true,
        )
    }

    ScreenScaffold(title = stringResource(R.string.scan_title), width = width) {
        Column(Modifier.fillMaxSize()) {
            if (sidePanel) {
                Row(Modifier.fillMaxSize()) {
                    PageTrayList(vm = vm, state = state, modifier = Modifier.weight(1f))
                    Column(
                        Modifier
                            .width(Tokens.DetailPaneWidth)
                            .padding(Tokens.SpaceLG),
                    ) { ReviewActions(vm, state, { discardConfirm = true }, onGoToPrint) }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                ) {
                    item {
                        Text(
                            stringResource(R.string.scan_tray_title) + " · ${state.pages.size}",
                            style = MaterialTheme.typography.titleMedium,
                            color = LocalOpenColors.current.onSurface,
                            modifier = Modifier.padding(
                                start = Tokens.SpaceLG + Tokens.SpaceXS,
                                top = Tokens.SpaceSM, bottom = Tokens.SpaceSM),
                        )
                    }
                    item {
                        PageTrayStrip(vm = vm, state = state)
                        Spacer(Modifier.height(Tokens.SpaceSM))
                    }
                    item {
                        Text(
                            stringResource(R.string.scan_tray_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalOpenColors.current.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Tokens.SpaceLG + Tokens.SpaceXS),
                        )
                    }
                }
                Surface(color = LocalOpenColors.current.background, modifier = Modifier.fillMaxWidth()) {
                    ReviewActions(vm, state, { discardConfirm = true }, onGoToPrint)
                }
            }
        }
    }
}

/** Bottom thumbnail strip on phones. */
@Composable
private fun PageTrayStrip(vm: AppViewModel, state: ScanUi.Review) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSM),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = Tokens.SpaceLG + Tokens.SpaceXS),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(state.pages, key = { it.id }) { page ->
            PageThumb(vm = vm, page = page, index = state.pages.indexOf(page), count = state.pages.size)
        }
        item {
            AddPageThumb(onClick = { vm.addAnotherPage() })
        }
    }
}

/** Side list on expanded windows. */
@Composable
private fun PageTrayList(vm: AppViewModel, state: ScanUi.Review, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.padding(Tokens.SpaceMD)) {
        items(state.pages, key = { it.id }) { page ->
            PageRow(vm = vm, page = page, index = state.pages.indexOf(page), count = state.pages.size)
            Spacer(Modifier.height(Tokens.SpaceSM))
        }
        item {
            ActionChip(
                text = stringResource(R.string.scan_add_page),
                icon = R.drawable.ic_add,
                onClick = { vm.addAnotherPage() },
                modifier = Modifier.padding(vertical = Tokens.SpaceSM),
            )
        }
    }
}

@Composable
private fun PageThumb(vm: AppViewModel, page: AppViewModel.ScanPage, index: Int, count: Int) {
    val colors = LocalOpenColors.current
    var bmp by remember(page.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(page.id) {
        bmp = withContext(Dispatchers.IO) {
            val b = android.graphics.BitmapFactory.decodeFile(page.file.absolutePath)
            b?.let { ScanProcessor.fit(it, 256) }
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = colors.well,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.divider),
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp!!.asImageBitmap(),
                        contentDescription = stringResource(R.string.scan_tray_page, index + 1),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(88.dp),
                    )
                } else {
                    Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(Tokens.IconMD),
                            strokeWidth = 2.dp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = colors.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onPrimary,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(Tokens.SpaceXS))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            OpenIconButton(
                icon = R.drawable.ic_chevron_left,
                label = stringResource(R.string.scan_page_move_earlier, index + 1),
                onClick = { vm.moveScanPage(page, -1) },
                enabled = index > 0,
            )
            OpenIconButton(
                icon = R.drawable.ic_delete,
                label = stringResource(R.string.scan_page_delete, index + 1),
                onClick = { vm.deleteScanPage(page) },
            )
            OpenIconButton(
                icon = R.drawable.ic_chevron_right,
                label = stringResource(R.string.scan_page_move_later, index + 1),
                onClick = { vm.moveScanPage(page, +1) },
                enabled = index < count - 1,
            )
        }
    }
}

@Composable
private fun AddPageThumb(onClick: () -> Unit) {
    val colors = LocalOpenColors.current
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = colors.well,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.divider),
        modifier = Modifier.size(88.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_add),
                contentDescription = stringResource(R.string.scan_add_page),
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(Tokens.IconLG),
            )
        }
    }
}

@Composable
private fun PageRow(vm: AppViewModel, page: AppViewModel.ScanPage, index: Int, count: Int) {
    val colors = LocalOpenColors.current
    var bmp by remember(page.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(page.id) {
        bmp = withContext(Dispatchers.IO) {
            val b = android.graphics.BitmapFactory.decodeFile(page.file.absolutePath)
            b?.let { ScanProcessor.fit(it, 512) }
        }
    }
    OpenCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = colors.well,
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp!!.asImageBitmap(),
                        contentDescription = stringResource(R.string.scan_tray_page, index + 1),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(72.dp),
                    )
                } else {
                    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(Tokens.IconMD), strokeWidth = 2.dp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.width(Tokens.SpaceMD))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.scan_tray_page, index + 1),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Text(
                    "${page.file.name.takeLast(18)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            OpenIconButton(
                icon = R.drawable.ic_rotate,
                label = stringResource(R.string.scan_page_rotate, index + 1),
                onClick = { vm.rotateScanPage(page) },
            )
            OpenIconButton(
                icon = R.drawable.ic_chevron_up,
                label = stringResource(R.string.scan_page_move_earlier, index + 1),
                onClick = { vm.moveScanPage(page, -1) },
                enabled = index > 0,
            )
            OpenIconButton(
                icon = R.drawable.ic_chevron_down,
                label = stringResource(R.string.scan_page_move_later, index + 1),
                onClick = { vm.moveScanPage(page, +1) },
                enabled = index < count - 1,
            )
            OpenIconButton(
                icon = R.drawable.ic_delete,
                label = stringResource(R.string.scan_page_delete, index + 1),
                onClick = { vm.deleteScanPage(page) },
            )
        }
    }
}

@Composable
private fun ReviewActions(vm: AppViewModel, state: ScanUi.Review, onDiscard: () -> Unit, onGoToPrint: () -> Unit) {
    val context = LocalContext.current
    val saveUi by vm.scanSave.collectAsState()
    Column(Modifier.padding(Tokens.SpaceLG)) {
        when (val s = saveUi) {
            is AppViewModel.ScanSaveUi.Done -> {
                InfoBanner(
                    text = androidx.compose.ui.res.pluralStringResource(
                        R.plurals.scan_save_done, s.count, s.count),
                    icon = R.drawable.ic_check,
                    tone = com.qawse.openpage.ui.components.BannerTone.SUCCESS,
                )
                Spacer(Modifier.height(Tokens.SpaceSM))
            }
            AppViewModel.ScanSaveUi.Failed -> {
                InfoBanner(
                    text = stringResource(R.string.scan_save_failed),
                    icon = R.drawable.ic_alert,
                    tone = com.qawse.openpage.ui.components.BannerTone.ERROR,
                )
                Spacer(Modifier.height(Tokens.SpaceSM))
            }
            AppViewModel.ScanSaveUi.Saving -> {
                InfoBanner(
                    text = stringResource(R.string.scan_save_saving),
                    icon = R.drawable.ic_info,
                )
                Spacer(Modifier.height(Tokens.SpaceSM))
            }
            AppViewModel.ScanSaveUi.Idle -> {}
        }
        PrimaryButton(
            text = androidx.compose.ui.res.pluralStringResource(
                R.plurals.scan_save, state.pages.size, state.pages.size),
            icon = R.drawable.ic_save,
            onClick = { vm.saveScanPages() },
            loading = saveUi is AppViewModel.ScanSaveUi.Saving,
        )
        Spacer(Modifier.height(Tokens.SpaceSM))
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSM)) {
            SecondaryButton(
                text = stringResource(R.string.scan_share),
                icon = R.drawable.ic_share,
                onClick = {
                    val uris = ArrayList<android.net.Uri>()
                    state.pages.forEach { p ->
                        uris.add(androidx.core.content.FileProvider.getUriForFile(
                            context, "com.qawse.openpage.fileprovider", p.file))
                    }
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "image/jpeg"
                        putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(send, null))
                },
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = stringResource(R.string.scan_print),
                icon = R.drawable.ic_printer,
                onClick = {
                    vm.printScanPages()
                    onGoToPrint()
                },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(Tokens.SpaceSM))
        SecondaryButton(
            text = stringResource(R.string.scan_discard),
            onClick = onDiscard,
        )
    }
}

// ------------------------------------------------------------- failed

@Composable
private fun ScanFailedScreen(vm: AppViewModel, state: ScanUi.Failed) {
    val width = rememberWidth()
    val message = scanProblemMessage(state.problem)
    ScreenScaffold(title = stringResource(R.string.scan_title), width = width) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(Tokens.SpaceXL).width(380.dp),
            ) {
                StatusPill(
                    text = stringResource(R.string.scan_failed_title),
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
                PrimaryButton(
                    text = stringResource(R.string.scan_retry),
                    icon = R.drawable.ic_refresh,
                    onClick = { vm.resetScanUi() },
                )
                Spacer(Modifier.height(Tokens.SpaceSM))
                SecondaryButton(
                    text = stringResource(R.string.action_close),
                    onClick = { vm.closeScanResult() },
                )
            }
        }
    }
}

@Composable
private fun scanProblemMessage(problem: ScanProblem): String = when (problem) {
    ScanProblem.NoScanner -> stringResource(R.string.scan_failed_no_scanner)
    ScanProblem.PermissionDenied -> stringResource(R.string.scan_failed_permission)
    is ScanProblem.Transport -> stringResource(R.string.scan_failed_transport)
    is ScanProblem.InvalidResponse -> stringResource(R.string.scan_failed_invalid)
    is ScanProblem.Unsupported -> stringResource(R.string.scan_failed_unsupported)
    ScanProblem.UndecodableImage -> stringResource(R.string.scan_failed_undecodable)
    ScanProblem.NoCameraApp -> stringResource(R.string.scan_failed_no_camera)
    ScanProblem.CameraCaptureFailed -> stringResource(R.string.scan_camera_error)
    ScanProblem.Cancelled -> stringResource(R.string.scan_failed_cancelled)
}
