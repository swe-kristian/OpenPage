package com.qawse.openpage.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import com.qawse.openpage.core.AppLog
import com.qawse.openpage.core.Tags
import com.qawse.openpage.data.PrinterDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One USB interface, summarized for the diagnostics view. */
data class UsbInterfaceInfo(
    val index: Int,
    val interfaceClass: Int,
    val subclass: Int,
    val protocol: Int,
    val endpointCount: Int,
) {
    val classLabel: String
        get() = when (interfaceClass) {
            7 -> "Printer"
            0xFF -> "Vendor-specific"
            2 -> "CDC data"
            10 -> "CDC control"
            6 -> "Still image"
            else -> "Class $interfaceClass"
        }
}

/** Printer-shaped snapshot of an attached USB device. */
data class UsbPrinterInfo(
    val device: UsbDevice,
    val name: String,
    val vid: Int,
    val pid: Int,
    val serialNumber: String?,
    val productName: String?,
    val hasPermission: Boolean,
    val driverFamily: PrinterDatabase.DriverFamily,
    val brand: PrinterDatabase.Brand?,
    val isPrinterClass: Boolean,
    /** interface with a bulk OUT endpoint suitable for printing */
    val printInterfaceIndex: Int?,
    /** interface with protocol 4 (IPP/eSCL over USB) suitable for scanning */
    val ippInterfaceIndex: Int?,
    /** all interfaces, for diagnostics */
    val interfaces: List<UsbInterfaceInfo>,
    /** max bulk-out packet size on the print interface, for diagnostics */
    val bulkOutMaxPacket: Int?,
) {
    val key: String get() = "${vid}:${pid}:${serialNumber ?: device.deviceId}"

    /** True when the driver fell back because the model is unknown. */
    val usingGenericDriver: Boolean
        get() = driverFamily == PrinterDatabase.DriverFamily.GENERIC
}

sealed interface UsbEvent {
    data class Attached(val printer: UsbPrinterInfo) : UsbEvent
    data class Detached(val key: String) : UsbEvent
}

/**
 * Owns everything USB: discovery, the one-time permission dance, hot-plug
 * listening, and byte transfer to the printer's bulk endpoints.
 */
class UsbPrinterManager(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _printers = MutableStateFlow<List<UsbPrinterInfo>>(emptyList())
    val printers: StateFlow<List<UsbPrinterInfo>> = _printers.asStateFlow()

    private val _events = MutableStateFlow<UsbEvent?>(null)
    val events: StateFlow<UsbEvent?> = _events.asStateFlow()

    private var receiver: BroadcastReceiver? = null
    private var permissionReceiver: BroadcastReceiver? = null

    companion object {
        private const val ACTION_USB_PERMISSION = "com.qawse.openpage.USB_PERMISSION"
    }

    // ------------------------------------------------------------------ setup

    fun start() {
        if (receiver != null) return
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        AppLog.d(Tags.USB, "device attached")
                        refresh()
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        AppLog.d(Tags.USB, "device detached")
                        refresh()
                    }
                }
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context, r, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        receiver = r
        refresh()
    }

    fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        permissionReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
        permissionReceiver = null
    }

    // ------------------------------------------------------------ discovery

    fun refresh() {
        val found = usbManager.deviceList.values
            .filter { it.isPrinterLike() }
            .map { it.toInfo() }
        _printers.value = found
        AppLog.d(Tags.USB, "discovery: ${found.size} printer-shaped device(s)")
    }

    private fun UsbDevice.isPrinterLike(): Boolean {
        if (PrinterDatabase.brandFor(vendorId) != null) return true
        for (i in 0 until interfaceCount) {
            if (getInterface(i).interfaceClass == UsbConstants.USB_CLASS_PRINTER) return true
        }
        return false
    }

    private fun UsbDevice.toInfo(): UsbPrinterInfo {
        var printIdx: Int? = null
        var ippIdx: Int? = null
        var bulkOutMax: Int? = null
        val ifaces = mutableListOf<UsbInterfaceInfo>()
        for (i in 0 until interfaceCount) {
            val iface = getInterface(i)
            val cls = iface.interfaceClass
            val proto = iface.interfaceProtocol
            var hasBulkOut = false
            for (e in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(e)
                if (ep.direction == UsbConstants.USB_DIR_OUT &&
                    ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) hasBulkOut = true
            }
            ifaces.add(UsbInterfaceInfo(i, cls, iface.interfaceSubclass, proto, iface.endpointCount))
            if (cls == UsbConstants.USB_CLASS_PRINTER && hasBulkOut && printIdx == null) printIdx = i
            if (cls == UsbConstants.USB_CLASS_PRINTER && proto == 4 && ippIdx == null) ippIdx = i
            if (cls == 0xFF && proto == 4 && hasBulkOut && ippIdx == null) ippIdx = i // vendor-specific IPP-USB
        }
        if (printIdx == null) {
            // last resort: any bulk OUT endpoint
            outer@ for (i in 0 until interfaceCount) {
                val iface = getInterface(i)
                for (e in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(e)
                    if (ep.direction == UsbConstants.USB_DIR_OUT &&
                        ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        printIdx = i
                        bulkOutMax = ep.maxPacketSize.takeIf { it > 0 }
                        break@outer
                    }
                }
            }
        }
        if (bulkOutMax == null && printIdx != null) {
            val iface = getInterface(printIdx)
            for (e in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(e)
                if (ep.direction == UsbConstants.USB_DIR_OUT &&
                    ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    bulkOutMax = ep.maxPacketSize.takeIf { it > 0 }
                    break
                }
            }
        }
        val serial = runCatching { serialNumber }
            .onFailure { AppLog.d(Tags.USB, "serial unavailable (permission not granted yet)") }
            .getOrNull()
        val product = runCatching { productName }.getOrNull()
        val thermalLike = PrinterDatabase.brandFor(vendorId)?.deviceClass in setOf(
            PrinterDatabase.DeviceClass.THERMAL, PrinterDatabase.DeviceClass.LABEL
        )
        return UsbPrinterInfo(
            device = this,
            name = PrinterDatabase.displayName(vendorId, productId, product, printerClassGuess()),
            vid = vendorId,
            pid = productId,
            serialNumber = serial,
            productName = product,
            hasPermission = usbManager.hasPermission(this),
            driverFamily = PrinterDatabase.recommendedDriver(vendorId, printerClassGuess(), printerSubclassGuess(), thermalLike),
            brand = PrinterDatabase.brandFor(vendorId),
            isPrinterClass = printerClassGuess() == 7,
            printInterfaceIndex = printIdx,
            ippInterfaceIndex = ippIdx,
            interfaces = ifaces,
            bulkOutMaxPacket = bulkOutMax,
        )
    }

    private fun UsbDevice.printerClassGuess(): Int {
        for (i in 0 until interfaceCount) {
            if (getInterface(i).interfaceClass == UsbConstants.USB_CLASS_PRINTER) return 7
        }
        return -1
    }

    private fun UsbDevice.printerSubclassGuess(): Int? {
        for (i in 0 until interfaceCount) {
            val iface = getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_PRINTER) return iface.interfaceSubclass
        }
        return null
    }

    // ------------------------------------------------------------ permission

    fun requestPermission(printer: UsbPrinterInfo, onResult: (Boolean) -> Unit) {
        if (usbManager.hasPermission(printer.device)) { onResult(true); return }

        val flags = if (Build.VERSION.SDK_INT >= 31)
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else PendingIntent.FLAG_UPDATE_CURRENT

        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val pi = PendingIntent.getBroadcast(context, printer.device.deviceId, intent, flags)

        val pr = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i?.action != ACTION_USB_PERMISSION) return
                val granted = i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                runCatching { context.unregisterReceiver(this) }
                if (permissionReceiver === this) permissionReceiver = null
                refresh()
                AppLog.d(Tags.USB, "permission result: granted=$granted")
                onResult(granted)
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context, pr, IntentFilter(ACTION_USB_PERMISSION),
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        permissionReceiver = pr
        usbManager.requestPermission(printer.device, pi)
    }

    // ------------------------------------------------------------ connection

    /**
     * Opens a bulk connection to the printer's data interface.
     * Caller must [close] it; the interface stays claimed while open.
     */
    fun openConnection(printer: UsbPrinterInfo): UsbPrinterConnection? {
        val idx = printer.printInterfaceIndex ?: return null
        if (!usbManager.hasPermission(printer.device)) return null
        val connection = usbManager.openDevice(printer.device) ?: return null
        val iface: UsbInterface = printer.device.getInterface(idx)
        if (!connection.claimInterface(iface, true)) {
            connection.close()
            return null
        }
        var out: UsbEndpoint? = null
        var inEp: UsbEndpoint? = null
        for (e in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(e)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_OUT && out == null) out = ep
                if (ep.direction == UsbConstants.USB_DIR_IN && inEp == null) inEp = ep
            }
        }
        if (out == null) { connection.close(); return null }
        return UsbPrinterConnection(connection, iface, out, inEp)
    }

    /** Opens the IEEE-1284.4/IPP style byte-stream interface used by eSCL scanning. */
    fun openIppConnection(printer: UsbPrinterInfo): UsbPrinterConnection? {
        val idx = printer.ippInterfaceIndex ?: return null
        if (!usbManager.hasPermission(printer.device)) return null
        val connection = usbManager.openDevice(printer.device) ?: return null
        val iface = printer.device.getInterface(idx)
        if (!connection.claimInterface(iface, true)) {
            connection.close()
            return null
        }
        var out: UsbEndpoint? = null
        var inEp: UsbEndpoint? = null
        for (e in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(e)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_OUT && out == null) out = ep
                if (ep.direction == UsbConstants.USB_DIR_IN && inEp == null) inEp = ep
            }
        }
        if (out == null || inEp == null) { connection.close(); return null }
        return UsbPrinterConnection(connection, iface, out, inEp)
    }
}

/**
 * A claimed USB interface with bulk IN/OUT endpoints and chunked transfer.
 */
class UsbPrinterConnection(
    private val connection: UsbDeviceConnection,
    private val iface: UsbInterface,
    private val bulkOut: UsbEndpoint,
    private val bulkIn: UsbEndpoint?,
) : AutoCloseable {

    private var closed = false

    val maxOutPacket: Int get() = bulkOut.maxPacketSize.takeIf { it > 0 } ?: 64

    /** Writes a whole buffer in endpoint-sized chunks. Returns bytes sent or -1. */
    fun write(buffer: ByteArray, timeoutMs: Int = 8000): Int {
        if (closed) return -1
        var sent = 0
        val chunk = maxOutPacket.coerceAtLeast(64)
        var i = 0
        while (i < buffer.size) {
            val len = minOf(chunk, buffer.size - i)
            val n = connection.bulkTransfer(bulkOut, buffer, i, len, timeoutMs)
            if (n < 0) return if (sent > 0) sent else -1
            sent += n
            i += n
        }
        return sent
    }

    /** Reads up to [max] bytes with a timeout. Returns bytes read or -1. */
    fun read(max: Int, timeoutMs: Int = 8000): Int {
        if (closed || bulkIn == null) return -1
        val buf = ByteArray(max)
        val n = connection.bulkTransfer(bulkIn, buf, max, timeoutMs)
        if (n <= 0) return -1
        lastRead = buf.copyOf(n)
        return n
    }

    var lastRead: ByteArray? = null
        private set

    fun readBytes(max: Int, timeoutMs: Int = 8000): ByteArray? {
        val n = read(max, timeoutMs)
        return if (n > 0) lastRead else null
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { connection.releaseInterface(iface) }
        runCatching { connection.close() }
    }
}
