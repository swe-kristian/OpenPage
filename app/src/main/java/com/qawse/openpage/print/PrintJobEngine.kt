package com.qawse.openpage.print

import android.content.Context
import android.graphics.Bitmap
import com.qawse.openpage.core.AppLog
import com.qawse.openpage.core.PrintProblem
import com.qawse.openpage.core.Tags
import com.qawse.openpage.data.PageRange
import com.qawse.openpage.data.PrintJobRecord
import com.qawse.openpage.data.PrintSettings
import com.qawse.openpage.data.PrinterDatabase
import com.qawse.openpage.drivers.DriverFactory
import com.qawse.openpage.drivers.PrintTransferException
import com.qawse.openpage.drivers.RawTextDriver
import com.qawse.openpage.usb.UsbPrinterConnection
import com.qawse.openpage.usb.UsbPrinterInfo
import com.qawse.openpage.usb.UsbPrinterManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything the engine needs to run one job. */
data class PrintJobRequest(
    val printer: UsbPrinterInfo,
    val driverFamily: PrinterDatabase.DriverFamily,
    val source: PageRenderer.Source,
    val fileName: String,
    val settings: PrintSettings,
    val rawText: String? = null,
    val sourceUri: android.net.Uri? = null,
)

sealed interface PrintProgress {
    data class Rendering(val pageIndex: Int, val total: Int) : PrintProgress
    data class Transferring(val pageIndex: Int, val total: Int, val copy: Int, val copies: Int) : PrintProgress
    data object Finished : PrintProgress
}

sealed interface PrintOutcome {
    data class Success(val pages: Int, val copies: Int) : PrintOutcome
    /** [problem] explains the failure; [pagesSent] counts pages already
     *  transferred before a cancel or transport error. */
    data class Failure(val problem: PrintProblem, val pagesSent: Int = 0) : PrintOutcome
}

/**
 * Page emission order: copies × (collated or uncollated), optional reverse.
 * Pure function — unit-tested in PageOrderTest.
 */
fun buildPageOrder(effectivePages: List<Int>, settings: PrintSettings): List<Pair<Int, Int>> {
    val ordered: List<Pair<Int, Int>> = buildList {
        if (settings.collate) {
            for (copy in 1..settings.copies)
                for (page in effectivePages) add(page to copy)
        } else {
            for (page in effectivePages)
                for (copy in 1..settings.copies) add(page to copy)
        }
    }
    return if (settings.reverseOrder) ordered.asReversed() else ordered
}

/**
 * The conductor: resolves the connection, renders lazily, streams pages
 * through the selected driver, handles copies/collation/reverse order and
 * appends to the job history — including failures, so the Recent list can
 * offer recovery.
 */
class PrintJobEngine(
    private val context: Context,
    private val usbManager: UsbPrinterManager,
    private val history: JobHistory,
) {
    private val renderer = PageRenderer(context)

    suspend fun print(
        request: PrintJobRequest,
        onProgress: (PrintProgress) -> Unit,
    ): PrintOutcome = withContext(Dispatchers.IO) {
        val settings = request.settings

        // 1. Connection -----------------------------------------------------
        if (!request.printer.hasPermission) {
            return@withContext PrintOutcome.Failure(PrintProblem.NO_PERMISSION)
        }
        val connection: UsbPrinterConnection = usbManager.openConnection(request.printer)
            ?: return@withContext PrintOutcome.Failure(PrintProblem.NO_PRINTER)

        var pagesSent = 0
        try {
            val driver = DriverFactory.create(request.driverFamily)

            // 2. Page selection ---------------------------------------------
            val docPages = renderer.pageCount(request.source)
            if (docPages <= 0) {
                return@withContext PrintOutcome.Failure(PrintProblem.RENDER)
            }
            val effectivePages: List<Int> = when {
                request.source is PageRenderer.Source.Text ->
                    (0 until renderer.textPageCount(
                        (request.source as PageRenderer.Source.Text).text, settings,
                        driver.renderDpi(settings))).map { it }
                else -> {
                    val sel = PageRange.parse(settings.pageRange, docPages)
                        ?: return@withContext PrintOutcome.Failure(PrintProblem.BAD_RANGE)
                    sel.pages.map { it - 1 }
                }
            }
            if (effectivePages.isEmpty()) {
                return@withContext PrintOutcome.Failure(PrintProblem.BAD_RANGE)
            }

            // 3. Raw text fast path -----------------------------------------
            if (driver is RawTextDriver && request.rawText != null) {
                try {
                    driver.sendText(connection, request.rawText)
                } catch (e: PrintTransferException) {
                    recordFailure(request, PrintProblem.TRANSPORT)
                    return@withContext PrintOutcome.Failure(PrintProblem.TRANSPORT)
                }
                history.add(PrintJobRecord(
                    fileName = request.fileName,
                    printerName = request.printer.name,
                    pages = 1, copies = 1,
                    timestamp = System.currentTimeMillis(), ok = true,
                    sourceUri = request.sourceUri?.toString()))
                onProgress(PrintProgress.Finished)
                return@withContext PrintOutcome.Success(1, 1)
            }

            // 4. Page plan ----------------------------------------------------
            val dpi = driver.renderDpi(settings)
            val finalOrder = buildPageOrder(effectivePages, settings)

            val renderPage: suspend (index: Int) -> Bitmap = { index ->
                val (page, copy) = finalOrder[index]
                onProgress(PrintProgress.Rendering(page, effectivePages.size))
                renderer.render(request.source, page, settings, dpi)
            }

            // 5. Stream to hardware ------------------------------------------
            try {
                driver.print(
                    connection = connection,
                    pageCount = finalOrder.size,
                    settings = settings,
                    renderPage = renderPage,
                    onPageStarted = { index, total ->
                        val (page, copy) = finalOrder.getOrElse(index) { 0 to 1 }
                        onProgress(PrintProgress.Transferring(page + 1, effectivePages.size, copy, settings.copies))
                        pagesSent = index + 1
                    },
                )
            } catch (e: PrintTransferException) {
                AppLog.w(Tags.PRINT, "transfer failed after $pagesSent pages", e)
                recordFailure(request, PrintProblem.TRANSPORT)
                return@withContext PrintOutcome.Failure(PrintProblem.TRANSPORT, pagesSent)
            }

            history.add(PrintJobRecord(
                fileName = request.fileName,
                printerName = request.printer.name,
                pages = effectivePages.size,
                copies = settings.copies,
                timestamp = System.currentTimeMillis(), ok = true,
                sourceUri = request.sourceUri?.toString()))
            onProgress(PrintProgress.Finished)
            PrintOutcome.Success(effectivePages.size, settings.copies)
        } catch (e: kotlinx.coroutines.CancellationException) {
            AppLog.d(Tags.PRINT, "job cancelled after $pagesSent pages")
            // Cancelled jobs keep their partial record for honest history.
            if (pagesSent > 0) recordCancelled(request, pagesSent)
            PrintOutcome.Failure(PrintProblem.CANCELLED, pagesSent)
        } catch (e: Exception) {
            AppLog.e(Tags.PRINT, "render/plan failure", e)
            recordFailure(request, PrintProblem.RENDER)
            PrintOutcome.Failure(PrintProblem.RENDER, pagesSent)
        } finally {
            connection.close()
        }
    }

    private fun recordFailure(request: PrintJobRequest, problem: PrintProblem) {
        history.add(PrintJobRecord(
            fileName = request.fileName,
            printerName = request.printer.name,
            pages = 0, copies = request.settings.copies,
            timestamp = System.currentTimeMillis(), ok = false,
            problem = problem.name,
            sourceUri = request.sourceUri?.toString()))
    }

    private fun recordCancelled(request: PrintJobRequest, pagesSent: Int) {
        history.add(PrintJobRecord(
            fileName = request.fileName,
            printerName = request.printer.name,
            pages = pagesSent, copies = request.settings.copies,
            timestamp = System.currentTimeMillis(), ok = false,
            problem = PrintProblem.CANCELLED.name,
            sourceUri = request.sourceUri?.toString()))
    }

    /** Renders a low-dpi preview of one page for the Print screen. */
    suspend fun preview(source: PageRenderer.Source, pageIndex: Int, settings: PrintSettings): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                renderer.render(source, pageIndex, settings, PREVIEW_DPI)
            }.onFailure { AppLog.w(Tags.RENDER, "preview render failed", it) }
                .getOrNull()
        }

    /** Deterministic test page through the chosen driver. */
    suspend fun printTestPage(
        printer: UsbPrinterInfo,
        driverFamily: PrinterDatabase.DriverFamily,
        settings: PrintSettings,
    ): PrintOutcome = withContext(Dispatchers.IO) {
        val driver = DriverFactory.create(driverFamily)
        val connection = usbManager.openConnection(printer)
            ?: return@withContext PrintOutcome.Failure(PrintProblem.NO_PRINTER)
        try {
            val dpi = driver.renderDpi(settings)
            val bmp = renderer.renderTestPage(settings, dpi, PrinterDatabase.driverLabel(driverFamily))
            try {
                driver.print(
                    connection = connection,
                    pageCount = 1,
                    settings = settings,
                    renderPage = { bmp },
                    onPageStarted = { _, _ -> },
                )
            } finally {
                bmp.recycle()
            }
            history.add(PrintJobRecord(
                fileName = TEST_PAGE_NAME,
                printerName = printer.name,
                pages = 1, copies = 1,
                timestamp = System.currentTimeMillis(), ok = true))
            PrintOutcome.Success(1, 1)
        } catch (e: PrintTransferException) {
            AppLog.w(Tags.PRINT, "test page transfer failed", e)
            PrintOutcome.Failure(PrintProblem.TRANSPORT)
        } catch (e: kotlinx.coroutines.CancellationException) {
            PrintOutcome.Failure(PrintProblem.CANCELLED)
        } catch (e: Exception) {
            AppLog.e(Tags.PRINT, "test page failed", e)
            PrintOutcome.Failure(PrintProblem.RENDER)
        } finally {
            connection.close()
        }
    }

    suspend fun pageCount(source: PageRenderer.Source): Int = renderer.pageCount(source)

    companion object {
        const val PREVIEW_DPI = 96
        const val TEST_PAGE_NAME = "Test page"
    }
}
