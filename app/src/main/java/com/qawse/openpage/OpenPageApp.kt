package com.qawse.openpage

import android.app.Application
import com.qawse.openpage.core.AppLog
import com.qawse.openpage.data.ColorMode
import com.qawse.openpage.data.DuplexMode
import com.qawse.openpage.data.PaperSize
import com.qawse.openpage.data.PrintQuality
import com.qawse.openpage.data.PrinterDatabase
import com.qawse.openpage.print.JobHistory
import com.qawse.openpage.print.PrintJobEngine
import com.qawse.openpage.scan.EsclScanner
import com.qawse.openpage.usb.UsbPrinterManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Composition root. Everything USB/print/scan lives for the process
 * lifetime and is shared by all screens.
 */
class OpenPageApp : Application() {

    lateinit var usb: UsbPrinterManager
        private set
    lateinit var history: JobHistory
        private set
    lateinit var settings: AppSettings
        private set
    lateinit var scanner: EsclScanner
        private set
    lateinit var engine: PrintJobEngine
        private set

    override fun onCreate() {
        super.onCreate()
        AppLog.enabled = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        settings = AppSettings(this)
        usb = UsbPrinterManager(this)
        history = JobHistory(this)
        scanner = EsclScanner(usb)
        engine = PrintJobEngine(this, usb, history)
        usb.start()
    }
}

/**
 * User preferences + per-printer driver overrides, persisted in shared
 * preferences as JSON-lite records.
 */
class AppSettings(context: android.content.Context) {

    private val prefs = context.getSharedPreferences("openpage_settings", android.content.Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadTheme())
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _defaultPaper = MutableStateFlow(loadPaper())
    val defaultPaper: StateFlow<PaperSize> = _defaultPaper.asStateFlow()

    private val _defaultColor = MutableStateFlow(loadColor())
    val defaultColor: StateFlow<ColorMode> = _defaultColor.asStateFlow()

    private val _defaultQuality = MutableStateFlow(loadQuality())
    val defaultQuality: StateFlow<PrintQuality> = _defaultQuality.asStateFlow()

    private val _defaultDuplex = MutableStateFlow(loadDuplex())
    val defaultDuplex: StateFlow<DuplexMode> = _defaultDuplex.asStateFlow()

    private val _keepAwake = MutableStateFlow(prefs.getBoolean("keepAwake", true))
    val keepAwake: StateFlow<Boolean> = _keepAwake.asStateFlow()

    /** Diagnostics entry unlocked (About version tapped 5 times in release). */
    private val _diagnosticsUnlocked = MutableStateFlow(prefs.getBoolean("diagUnlocked", false))
    val diagnosticsUnlocked: StateFlow<Boolean> = _diagnosticsUnlocked.asStateFlow()

    var defaultPrinterKey: String?
        get() = prefs.getString("defaultPrinter", null)
        set(value) { prefs.edit().putString("defaultPrinter", value).apply() }

    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme", mode).apply()
        _themeMode.value = mode
    }

    fun setDefaultPaper(paper: PaperSize) {
        prefs.edit().putString("paper", paper.name).apply()
        _defaultPaper.value = paper
    }

    fun setDefaultColor(mode: ColorMode) {
        prefs.edit().putString("color", mode.name).apply()
        _defaultColor.value = mode
    }

    fun setDefaultQuality(q: PrintQuality) {
        prefs.edit().putString("quality", q.name).apply()
        _defaultQuality.value = q
    }

    fun setDefaultDuplex(d: DuplexMode) {
        prefs.edit().putString("duplex", d.name).apply()
        _defaultDuplex.value = d
    }

    fun setKeepAwake(on: Boolean) {
        prefs.edit().putBoolean("keepAwake", on).apply()
        _keepAwake.value = on
    }

    fun setDiagnosticsUnlocked(on: Boolean) {
        prefs.edit().putBoolean("diagUnlocked", on).apply()
        _diagnosticsUnlocked.value = on
    }

    private fun loadTheme(): String = prefs.getString("theme", "system") ?: "system"
    private fun loadPaper(): PaperSize =
        prefs.getString("paper", null)?.let { runCatching { PaperSize.valueOf(it) }.getOrNull() } ?: PaperSize.A4
    private fun loadColor(): ColorMode =
        prefs.getString("color", null)?.let { runCatching { ColorMode.valueOf(it) }.getOrNull() } ?: ColorMode.GRAYSCALE
    private fun loadQuality(): PrintQuality =
        prefs.getString("quality", null)?.let { runCatching { PrintQuality.valueOf(it) }.getOrNull() } ?: PrintQuality.NORMAL
    private fun loadDuplex(): DuplexMode =
        prefs.getString("duplex", null)?.let { runCatching { DuplexMode.valueOf(it) }.getOrNull() } ?: DuplexMode.OFF

    // ---------------------------------------------------- driver overrides

    fun driverOverride(key: String): PrinterDatabase.DriverFamily? {
        val name = prefs.getString("driver.$key", null) ?: return null
        return runCatching { PrinterDatabase.DriverFamily.valueOf(name) }.getOrNull()
    }

    fun setDriverOverride(key: String, family: PrinterDatabase.DriverFamily?) {
        val e = prefs.edit()
        if (family == null) e.remove("driver.$key") else e.putString("driver.$key", family.name)
        e.apply()
    }
}
