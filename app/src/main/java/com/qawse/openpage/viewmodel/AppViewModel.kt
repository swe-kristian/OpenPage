package com.qawse.openpage.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.qawse.openpage.AppSettings
import com.qawse.openpage.OpenPageApp
import com.qawse.openpage.core.AppLog
import com.qawse.openpage.core.PrintProblem
import com.qawse.openpage.core.ScanProblem
import com.qawse.openpage.core.Tags
import com.qawse.openpage.data.Margins
import com.qawse.openpage.data.PrintSettings
import com.qawse.openpage.data.PrinterDatabase
import com.qawse.openpage.print.PrintJobEngine
import com.qawse.openpage.print.PrintJobRequest
import com.qawse.openpage.print.PrintOutcome
import com.qawse.openpage.print.PrintProgress
import com.qawse.openpage.print.PageRenderer
import com.qawse.openpage.scan.EsclScanner
import com.qawse.openpage.scan.ScanProcessor
import com.qawse.openpage.usb.UsbPrinterInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppViewModel(app: Application, private val savedState: SavedStateHandle) : AndroidViewModel(app) {

    private val appCtx = app as OpenPageApp
    val usb get() = appCtx.usb
    val settingsStore: AppSettings get() = appCtx.settings
    val engine: PrintJobEngine get() = appCtx.engine
    val scanner: EsclScanner get() = appCtx.scanner
    val history get() = appCtx.history

    val printers: StateFlow<List<UsbPrinterInfo>> = usb.printers
    val jobs = history.jobs

    // ============================================================== printing

    sealed interface PrintUi {
        data object Idle : PrintUi
        data class Opening(val fileName: String) : PrintUi
        data class FailedOpen(val fileName: String) : PrintUi
        data class Ready(
            val fileName: String,
            val pageCount: Int,
            val settings: PrintSettings,
            val preview: Bitmap?,
            val previewPage: Int,
            val isText: Boolean,
            val previewDirty: Boolean,
        ) : PrintUi
        data class Sending(
            val fileName: String,
            val printerName: String,
            val page: Int,
            val totalPages: Int,
            val copy: Int,
            val copies: Int,
            val phaseRendering: Boolean,
        ) : PrintUi
        data class Done(
            val fileName: String,
            val printerName: String,
            val pages: Int,
            val copies: Int,
        ) : PrintUi
        data class Failed(
            val fileName: String,
            val problem: PrintProblem,
            val pagesSent: Int,
        ) : PrintUi
    }

    private val _printUi = MutableStateFlow<PrintUi>(PrintUi.Idle)
    val printUi: StateFlow<PrintUi> = _printUi.asStateFlow()

    private var printJob: Job? = null
    private var currentSource: PageRenderer.Source? = null
    private var currentRawText: String? = null
    private var currentUri: Uri? = null
    private var previewGeneration = 0

    init { restorePrintState() }

    private val _targetPrinter = MutableStateFlow<UsbPrinterInfo?>(null)

    /** Default printer: the saved default if attached, else the first
     *  attached. A StateFlow so composition stays reactive (and lint-clean). */
    val targetPrinter: StateFlow<UsbPrinterInfo?> = _targetPrinter.asStateFlow()

    private fun recomputeTarget() {
        val list = printers.value
        val key = settingsStore.defaultPrinterKey
        _targetPrinter.value = list.firstOrNull { it.key == key } ?: list.firstOrNull()
    }

    init {
        viewModelScope.launch {
            printers.collect { recomputeTarget() }
        }
    }

    fun driverFor(printer: UsbPrinterInfo): PrinterDatabase.DriverFamily =
        settingsStore.driverOverride(printer.key) ?: printer.driverFamily

    /** True when the effective driver differs from the family's preference. */
    fun driverOverridden(printer: UsbPrinterInfo): Boolean =
        settingsStore.driverOverride(printer.key) != null

    fun requestPermission(printer: UsbPrinterInfo, onResult: (Boolean) -> Unit) {
        usb.requestPermission(printer, onResult)
    }

    fun refreshUsb() = usb.refresh()

    fun openDocument(uri: Uri, displayName: String) {
        _printUi.value = PrintUi.Opening(displayName)
        viewModelScope.launch {
            val source = withContext(Dispatchers.IO) { PageRenderer.open(getApplication(), uri) }
            if (source == null) {
                AppLog.w(Tags.PRINT, "document open failed")
                _printUi.value = PrintUi.FailedOpen(displayName)
                return@launch
            }
            currentSource = source
            currentUri = uri
            currentRawText = (source as? PageRenderer.Source.Text)?.text
            val count = engine.pageCount(source)
            val defaults = settingsStore
            val base = PrintSettings(
                paper = defaults.defaultPaper.value,
                colorMode = defaults.defaultColor.value,
                quality = defaults.defaultQuality.value,
                duplex = defaults.defaultDuplex.value,
                margins = if (defaults.defaultPaper.value.thermalRoll)
                    Margins(0f, 0f, 0f, 0f) else Margins(),
            )
            val preview = engine.preview(source, 0, base)
            val isText = source is PageRenderer.Source.Text
            val pages = if (isText) 1 else count
            _printUi.value = PrintUi.Ready(
                fileName = displayName,
                pageCount = pages,
                settings = base,
                preview = preview,
                previewPage = 0,
                isText = isText,
                previewDirty = false,
            )
        }
    }

    /**
     * Live settings update with race-free preview regeneration: each change
     * bumps a generation counter; stale async renders are discarded.
     */
    fun updateSettings(transform: (PrintSettings) -> PrintSettings) {
        val cur = _printUi.value as? PrintUi.Ready ?: return
        val next = transform(cur.settings)
        if (next == cur.settings) return
        _printUi.value = cur.copy(settings = next, previewDirty = true)
        val gen = ++previewGeneration
        viewModelScope.launch {
            val preview = engine.preview(currentSource ?: return@launch, cur.previewPage, next)
            if (preview == null) return@launch
            val now = _printUi.value as? PrintUi.Ready ?: return@launch
            if (gen != previewGeneration) return@launch
            if (now.settings != next) return@launch
            _printUi.value = now.copy(preview = preview, previewDirty = false)
        }
    }

    fun movePreview(delta: Int) {
        val cur = _printUi.value as? PrintUi.Ready ?: return
        if (cur.isText) return
        val next = (cur.previewPage + delta).coerceIn(0, (cur.pageCount - 1).coerceAtLeast(0))
        if (next == cur.previewPage) return
        _printUi.value = cur.copy(previewPage = next, previewDirty = true)
        val gen = ++previewGeneration
        viewModelScope.launch {
            val preview = engine.preview(currentSource ?: return@launch, next, cur.settings)
            if (preview == null) return@launch
            val now = _printUi.value as? PrintUi.Ready ?: return@launch
            if (gen != previewGeneration) return@launch
            if (now.previewPage != next) return@launch
            _printUi.value = now.copy(preview = preview, previewDirty = false)
        }
    }

    fun startPrint() {
        val ready = _printUi.value as? PrintUi.Ready ?: return
        val printer = targetPrinter.value ?: run {
            _printUi.value = PrintUi.Failed(ready.fileName, PrintProblem.NO_PRINTER, 0)
            return
        }
        val source = currentSource ?: return
        val family = driverFor(printer)
        val request = PrintJobRequest(
            printer = printer,
            driverFamily = family,
            source = source,
            fileName = ready.fileName,
            settings = ready.settings,
            rawText = currentRawText.takeIf { family == PrinterDatabase.DriverFamily.RAW },
            sourceUri = currentUri,
        )
        printJob = viewModelScope.launch {
            _printUi.value = PrintUi.Sending(ready.fileName, printer.name, 0, 0, 1, 1, false)
            val outcome = engine.print(request) { p ->
                val running = when (p) {
                    is PrintProgress.Rendering -> PrintUi.Sending(ready.fileName, printer.name,
                        p.pageIndex + 1, p.total, 1, ready.settings.copies, true)
                    is PrintProgress.Transferring -> PrintUi.Sending(ready.fileName, printer.name,
                        p.pageIndex, p.total, p.copy, p.copies, false)
                    PrintProgress.Finished -> null
                }
                if (running != null) _printUi.value = running
            }
            _printUi.value = when (outcome) {
                is PrintOutcome.Success -> PrintUi.Done(
                    ready.fileName, printer.name, outcome.pages, outcome.copies)
                is PrintOutcome.Failure -> PrintUi.Failed(
                    ready.fileName, outcome.problem, outcome.pagesSent)
            }
        }
    }

    fun cancelPrint() {
        printJob?.cancel()
        printJob = null
        val state = _printUi.value
        val name = (state as? PrintUi.Sending)?.fileName
            ?: (state as? PrintUi.Ready)?.fileName ?: ""
        val sent = (state as? PrintUi.Sending)?.let { it.page - 1 } ?: 0
        // The engine's cancellation handler writes the final state; this
        // immediate write covers the visual gap until the coroutine unwinds.
        _printUi.value = PrintUi.Failed(name, PrintProblem.CANCELLED, sent.coerceAtLeast(0))
    }

    fun resetPrintUi() {
        printJob?.cancel()
        printJob = null
        currentSource = null
        currentRawText = null
        currentUri = null
        _printUi.value = PrintUi.Idle
    }

    /** Re-prints from a history record when the source URI is still valid. */
    fun repeatJob(uriString: String) {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return
        openDocument(uri, uri.lastPathSegment?.substringAfterLast('/') ?: "Document")
    }

    // ------------------------------------------------------------ test page

    sealed interface TestPageUi {
        data object Idle : TestPageUi
        data object Sending : TestPageUi
        data class Done(val printerName: String) : TestPageUi
        data class Failed(val problem: PrintProblem) : TestPageUi
    }

    private val _testPageUi = MutableStateFlow<TestPageUi>(TestPageUi.Idle)
    val testPageUi: StateFlow<TestPageUi> = _testPageUi.asStateFlow()

    fun printTestPage(printer: UsbPrinterInfo) {
        viewModelScope.launch {
            _testPageUi.value = TestPageUi.Sending
            val family = driverFor(printer)
            val outcome = engine.printTestPage(printer, family, PrintSettings())
            _testPageUi.value = when (outcome) {
                is PrintOutcome.Success -> TestPageUi.Done(printer.name)
                is PrintOutcome.Failure -> TestPageUi.Failed(outcome.problem)
            }
        }
    }

    fun clearTestPageFeedback() { _testPageUi.value = TestPageUi.Idle }

    // ============================================================== scanning

    /** One captured scan page, persisted to cache so rotation and process
     *  death never lose work. */
    data class ScanPage(val file: File) {
        val id: Int get() = file.absolutePath.hashCode()
    }

    sealed interface ScanUi {
        data object Idle : ScanUi
        data object Choosing : ScanUi
        data class GlassScanning(val printerName: String) : ScanUi
        data class Cropping(val bitmap: Bitmap) : ScanUi
        data class Processing(val pageCount: Int) : ScanUi
        data class Review(val pages: List<ScanPage>) : ScanUi
        data class Failed(val problem: ScanProblem) : ScanUi
    }

    private val _scanUi = MutableStateFlow<ScanUi>(ScanUi.Idle)
    val scanUi: StateFlow<ScanUi> = _scanUi.asStateFlow()

    /** Crop corners survive rotation by living here, not in the composable. */
    var cropQuad: ScanProcessor.Quad? = null

    /** Non-content stats of the last glass scan, for the diagnostics view. */
    var lastScanStats: EsclScanner.ScanStats? = null
        private set

    private var scanJob: Job? = null
    private var trayPages: List<ScanPage> = emptyList()
    private val scanDir: File get() = File(getApplication<Application>().cacheDir, "scans")
        .apply { mkdirs() }

    init {
        restoreScanState()
    }

    private fun scanPagesKey() = "openpage.scan.pages"

    private fun persistScanPages(pages: List<ScanPage>) {
        savedState[scanPagesKey()] = pages.map { it.file.absolutePath }
    }

    private fun restoreScanState() {
        val paths: List<String> = savedState[scanPagesKey()] ?: emptyList()
        val pages = paths.mapNotNull { p ->
            val f = File(p)
            if (f.exists()) ScanPage(f) else null
        }
        trayPages = pages
        if (pages.isNotEmpty()) _scanUi.value = ScanUi.Review(pages)
    }

    private fun restorePrintState() {
        // Settings survive process death via SavedStateHandle; the open
        // document itself (fd/uri) cannot be re-opened reliably, so we keep
        // only what was persisted and let the user re-pick with settings intact.
    }

    fun startGlassScan() {
        val printer = targetPrinter.value
        if (printer == null || !scanner.isSupported(printer)) {
            _scanUi.value = ScanUi.Failed(ScanProblem.NoScanner)
            return
        }
        if (!printer.hasPermission) {
            requestPermission(printer) { granted ->
                if (granted) startGlassScan() else
                    _scanUi.value = ScanUi.Failed(ScanProblem.PermissionDenied)
            }
            return
        }
        _scanUi.value = ScanUi.GlassScanning(printer.name)
        scanJob = viewModelScope.launch {
            val result = scanner.scan(printer, EsclScanner.ScanOptions())
            when (result) {
                is EsclScanner.ScanResult.Success -> {
                    lastScanStats = result.stats
                    val fitted = withContext(Dispatchers.Default) { ScanProcessor.fit(result.bitmap, 2200) }
                    _scanUi.value = ScanUi.Cropping(fitted)
                }
                is EsclScanner.ScanResult.Error ->
                    _scanUi.value = ScanUi.Failed(result.problem)
            }
        }
    }

    /** Receives a camera capture (already decoded, EXIF applied by caller). */
    fun acceptCameraCapture(bitmap: Bitmap) {
        _scanUi.value = ScanUi.Cropping(bitmap)
    }

    fun cameraUnavailable() {
        _scanUi.value = ScanUi.Failed(ScanProblem.NoCameraApp)
    }

    fun cameraCaptureFailed() {
        _scanUi.value = ScanUi.Failed(ScanProblem.CameraCaptureFailed)
    }

    /** Crop confirmed: flatten, enhance, rotate, persist to the page tray. */
    fun finishCrop(quad: ScanProcessor.Quad, enhance: ScanProcessor.Enhance, rotate: Boolean) {
        val state = _scanUi.value as? ScanUi.Cropping ?: return
        cropQuad = null
        _scanUi.value = ScanUi.Processing(trayPages.size)
        viewModelScope.launch {
            val page = withContext(Dispatchers.IO) {
                var out = ScanProcessor.flatten(state.bitmap, quad, 1600)
                out = ScanProcessor.enhance(out, enhance)
                if (rotate) out = ScanProcessor.rotate90(out)
                val f = File(scanDir, "page_${System.currentTimeMillis()}.jpg")
                f.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                ScanPage(f)
            }
            trayPages = trayPages + page
            persistScanPages(trayPages)
            _scanUi.value = ScanUi.Review(trayPages)
        }
    }

    /** Adds another page without discarding the tray. */
    fun addAnotherPage() {
        _scanUi.value = ScanUi.Choosing
    }

    fun deleteScanPage(page: ScanPage) {
        trayPages = trayPages.filterNot { it.id == page.id }
        runCatching { page.file.delete() }
        persistScanPages(trayPages)
        _scanUi.value = if (trayPages.isEmpty()) ScanUi.Choosing else ScanUi.Review(trayPages)
    }

    fun moveScanPage(page: ScanPage, delta: Int) {
        val pages = trayPages.toMutableList()
        val idx = pages.indexOfFirst { it.id == page.id }
        if (idx < 0) return
        val newIdx = (idx + delta).coerceIn(0, pages.lastIndex)
        if (newIdx == idx) return
        pages.removeAt(idx)
        pages.add(newIdx, page)
        trayPages = pages
        persistScanPages(pages)
        _scanUi.value = ScanUi.Review(pages)
    }

    fun rotateScanPage(page: ScanPage) {
        viewModelScope.launch {
            _scanUi.value = ScanUi.Processing(trayPages.size)
            withContext(Dispatchers.IO) {
                val bmp = android.graphics.BitmapFactory.decodeFile(page.file.absolutePath) ?: return@withContext
                val rotated = ScanProcessor.rotate90(bmp)
                if (bmp !== rotated) bmp.recycle()
                page.file.outputStream().use { rotated.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                rotated.recycle()
            }
            _scanUi.value = ScanUi.Review(trayPages)
        }
    }

    private fun currentScanPages(): List<ScanPage> = trayPages

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        if (currentScanPages().isNotEmpty()) {
            _scanUi.value = ScanUi.Review(currentScanPages())
        } else {
            _scanUi.value = ScanUi.Choosing
        }
    }

    fun resetScanUi() {
        scanJob?.cancel()
        scanJob = null
        cropQuad = null
        _scanUi.value = if (currentScanPages().isNotEmpty()) ScanUi.Review(currentScanPages())
        else ScanUi.Choosing
    }

    fun closeScanResult() {
        scanJob?.cancel()
        scanJob = null
        _scanUi.value = ScanUi.Idle
    }

    fun discardScanSession() {
        scanJob?.cancel()
        scanJob = null
        cropQuad = null
        currentScanPages().forEach { runCatching { it.file.delete() } }
        persistScanPages(emptyList())
        _scanUi.value = ScanUi.Choosing
    }

    // ------------------------------------------------------------- save/share

    sealed interface ScanSaveUi {
        data object Idle : ScanSaveUi
        data object Saving : ScanSaveUi
        data class Done(val count: Int, val folder: String) : ScanSaveUi
        data object Failed : ScanSaveUi
    }

    private val _scanSave = MutableStateFlow<ScanSaveUi>(ScanSaveUi.Idle)
    val scanSave: StateFlow<ScanSaveUi> = _scanSave.asStateFlow()

    /** Saves every tray page into MediaStore on Q+, app files elsewhere. */
    fun saveScanPages() {
        val pages = currentScanPages()
        if (pages.isEmpty()) return
        _scanSave.value = ScanSaveUi.Saving
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { savePagesInternal(pages) }
            _scanSave.value = result
        }
    }

    private fun savePagesInternal(pages: List<ScanPage>): ScanSaveUi {
        val resolver = getApplication<Application>().contentResolver
        val ts = System.currentTimeMillis()
        return try {
            var saved = 0
            pages.forEachIndexed { i, page ->
                val bmp = android.graphics.BitmapFactory.decodeFile(page.file.absolutePath) ?: return@forEachIndexed
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                            "OpenPage_scan_${ts}_${i + 1}.jpg")
                        put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenPage")
                    }
                    val uri = resolver.insert(
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use {
                            bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                        saved++
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val dir = android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_PICTURES)
                    val folder = File(dir, "OpenPage").apply { mkdirs() }
                    val f = File(folder, "OpenPage_scan_${ts}_${i + 1}.jpg")
                    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                    saved++
                }
                bmp.recycle()
            }
            if (saved == 0) ScanSaveUi.Failed else ScanSaveUi.Done(saved, "Pictures/OpenPage")
        } catch (e: Exception) {
            AppLog.w(Tags.SCAN, "save failed", e)
            ScanSaveUi.Failed
        }
    }

    fun clearSaveFeedback() { _scanSave.value = ScanSaveUi.Idle }

    /** Sends the whole tray into the print flow as one multi-page job. */
    fun printScanPages() {
        val pages = currentScanPages()
        if (pages.isEmpty()) return
        val uris = pages.map { Uri.fromFile(it.file) }
        _printUi.value = PrintUi.Opening("Scanned pages")
        viewModelScope.launch {
            val source = PageRenderer.Source.Images(uris)
            currentSource = source
            currentUri = null
            currentRawText = null
            val defaults = settingsStore
            val base = PrintSettings(
                paper = defaults.defaultPaper.value,
                colorMode = defaults.defaultColor.value,
                quality = defaults.defaultQuality.value,
                duplex = defaults.defaultDuplex.value,
                margins = if (defaults.defaultPaper.value.thermalRoll)
                    Margins(0f, 0f, 0f, 0f) else Margins(),
            )
            val preview = engine.preview(source, 0, base)
            _printUi.value = PrintUi.Ready(
                fileName = "Scanned pages (${pages.size})",
                pageCount = pages.size,
                settings = base,
                preview = preview,
                previewPage = 0,
                isText = false,
                previewDirty = false,
            )
        }
    }
}
