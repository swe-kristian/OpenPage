package com.qawse.openpage.data

/**
 * Local printer knowledge base.
 *
 * Maps USB vendor IDs of the supported manufacturers to a brand identity and
 * a recommended driver family, plus heuristics for classifying unknown
 * devices. Everything is resolved on-device; no lookups ever leave the phone.
 */
object PrinterDatabase {

    enum class DriverFamily { PCL, ESCPOS, ESCPR, POSTSCRIPT, GENERIC, RAW }

    enum class DeviceClass(val label: String) {
        INKJET("Inkjet"),
        LASER("Laser"),
        LED("LED"),
        THERMAL("Thermal"),
        LABEL("Label"),
        DOTMATRIX("Dot matrix"),
        MULTIFUNCTION("Multifunction"),
        UNKNOWN("Unknown");
    }

    data class Brand(
        val name: String,
        val vid: Int,
        val preferredDriver: DriverFamily,
        val alternateDriver: DriverFamily?,
        val deviceClass: DeviceClass,
        val duplexCapable: Boolean,
        val scanLikely: Boolean,
    )

    val brands: List<Brand> = listOf(
        Brand("Epson", 0x04B8, DriverFamily.ESCPR, DriverFamily.PCL, DeviceClass.INKJET, duplexCapable = true, scanLikely = true),
        Brand("HP", 0x03F0, DriverFamily.PCL, DriverFamily.POSTSCRIPT, DeviceClass.MULTIFUNCTION, duplexCapable = true, scanLikely = true),
        Brand("Canon", 0x04A9, DriverFamily.ESCPR, DriverFamily.PCL, DeviceClass.INKJET, duplexCapable = true, scanLikely = true),
        Brand("Brother", 0x04F9, DriverFamily.PCL, DriverFamily.POSTSCRIPT, DeviceClass.MULTIFUNCTION, duplexCapable = true, scanLikely = true),
        Brand("Samsung", 0x04E8, DriverFamily.PCL, DriverFamily.POSTSCRIPT, DeviceClass.LASER, duplexCapable = true, scanLikely = true),
        Brand("Xerox", 0x06E8, DriverFamily.PCL, DriverFamily.POSTSCRIPT, DeviceClass.LASER, duplexCapable = true, scanLikely = true),
        Brand("Dell", 0x413C, DriverFamily.PCL, DriverFamily.POSTSCRIPT, DeviceClass.LASER, duplexCapable = true, scanLikely = false),
        Brand("Konica Minolta", 0x132B, DriverFamily.PCL, DriverFamily.POSTSCRIPT, DeviceClass.LASER, duplexCapable = true, scanLikely = true),
        Brand("Kyocera", 0x0482, DriverFamily.PCL, DriverFamily.POSTSCRIPT, DeviceClass.LASER, duplexCapable = true, scanLikely = true),
        Brand("Lexmark", 0x043D, DriverFamily.PCL, DriverFamily.POSTSCRIPT, DeviceClass.LASER, duplexCapable = true, scanLikely = true),
        Brand("Ricoh", 0x05CA, DriverFamily.PCL, DriverFamily.POSTSCRIPT, DeviceClass.LASER, duplexCapable = true, scanLikely = true),
        Brand("Sharp", 0x04DD, DriverFamily.PCL, DriverFamily.POSTSCRIPT, DeviceClass.LASER, duplexCapable = true, scanLikely = true),
        Brand("Toshiba", 0x0934, DriverFamily.PCL, DriverFamily.POSTSCRIPT, DeviceClass.LASER, duplexCapable = true, scanLikely = true),
        Brand("OKI", 0x0622, DriverFamily.PCL, DriverFamily.POSTSCRIPT, DeviceClass.LED, duplexCapable = true, scanLikely = false),
        // Thermal / label printers that speak ESC/POS-style raster
        Brand("Zebra", 0x0A5F, DriverFamily.ESCPOS, null, DeviceClass.THERMAL, duplexCapable = false, scanLikely = false),
        Brand("DYMO", 0x0922, DriverFamily.ESCPOS, null, DeviceClass.LABEL, duplexCapable = false, scanLikely = false),
        Brand("Citizen", 0x1D90, DriverFamily.ESCPOS, null, DeviceClass.THERMAL, duplexCapable = false, scanLikely = false),
        Brand("Bixolon", 0x1504, DriverFamily.ESCPOS, null, DeviceClass.THERMAL, duplexCapable = false, scanLikely = false),
        Brand("Star Micronics", 0x0545, DriverFamily.ESCPOS, null, DeviceClass.THERMAL, duplexCapable = false, scanLikely = false),
    )

    private val byVid: Map<Int, Brand> = brands.associateBy { it.vid }

    fun brandFor(vid: Int): Brand? = byVid[vid]

    /** True when the device is a printer-class (7) USB device, whatever the vendor. */
    fun isPrinterClass(usbClass: Int, subclass: Int?): Boolean =
        usbClass == 7 && (subclass == null || subclass == 1)

    /**
     * Driver routing: known brand → preferred family with an alternate;
     * unknown printer-class device → generic PCL-compatible raster;
     * anything else → generic.
     */
    fun recommendedDriver(vid: Int, usbClass: Int, subclass: Int?, isThermalLike: Boolean): DriverFamily {
        brandFor(vid)?.let { return it.preferredDriver }
        if (isThermalLike) return DriverFamily.ESCPOS
        if (isPrinterClass(usbClass, subclass)) return DriverFamily.GENERIC
        return DriverFamily.GENERIC
    }

    fun driverLabel(family: DriverFamily): String = when (family) {
        DriverFamily.PCL -> "HP PCL raster"
        DriverFamily.ESCPOS -> "ESC/POS thermal"
        DriverFamily.ESCPR -> "Epson ESC/P-R"
        DriverFamily.POSTSCRIPT -> "PostScript Level 2"
        DriverFamily.GENERIC -> "Generic raster"
        DriverFamily.RAW -> "Plain text"
    }

    fun driverDescription(family: DriverFamily): String = when (family) {
        DriverFamily.PCL -> "HP Printer Command Language with raster graphics — the lingua franca of laser printing. Also spoken by most Brother, Samsung, Xerox, Kyocera, Lexmark, Ricoh, Sharp, Toshiba, OKI, Dell and Konica Minolta engines."
        DriverFamily.ESCPOS -> "Epson's thermal control set (ESC/POS raster bit-image). Drives receipt and label printers from Epson, Zebra, Bixolon, Citizen, Star and compatible clones."
        DriverFamily.ESCPR -> "Epson's modern inkjet raster language (ESC/P-R) used on Stylus, EcoTank, WorkForce and Expression series, and the basis for Canon inkjets."
        DriverFamily.POSTSCRIPT -> "Adobe PostScript Level 2 with ASCII-hex imagemask. Accepted by office lasers with a PS interpreter."
        DriverFamily.GENERIC -> "Conservative 300 dpi monochrome raster in PCL form — the safest bet for unlisted models."
        DriverFamily.RAW -> "Line-mode plain text with form feeds for impact and basic receipt printing."
    }

    /**
     * Human display name for a USB device: brand + product string when the
     * device reports one, otherwise brand + class, otherwise "Generic printer".
     */
    fun displayName(vid: Int, pid: Int, productString: String?, usbClass: Int): String {
        val brand = brandFor(vid)
        val raw = productString?.trim().orEmpty()
        if (raw.isNotEmpty()) return if (brand != null && !raw.contains(brand.name, ignoreCase = true))
            "${brand.name} $raw" else raw
        if (brand != null) return "${brand.name} printer (${String.format("USB %04X:%04X", vid, pid)})"
        return if (usbClass == 7) "USB printer (${String.format("%04X:%04X", vid, pid)})"
        else "USB device (${String.format("%04X:%04X", vid, pid)})"
    }
}
