# OpenPage

**Print and scan, straight from USB.**

OpenPage turns an Android phone into a direct printing and scanning station
for USB printers — no accounts, no cloud, no internet permission. It renders
documents on the phone and speaks the printer's own command language over a
USB-C / OTG connection.

- **Author and publisher:** Kristian Espedido
- **Organization:** The QAWSE Institute / QAWSE Corp
- **Role:** Founder and current CEO
- **Version:** 2.0.0 (versionCode 2)
- **Attribution line:** *OpenPage — The QAWSE Institute — Developed by Kristian Espedido*

---

## Requirements

| Item | Requirement |
| --- | --- |
| Android | 8.0 (API 26) or newer; targets Android 15 (API 35) |
| Hardware | USB host mode (USB-C or Micro-USB OTG adapter). Most phones from 2017 onward support this |
| Permissions | USB device access (requested once per device). No CAMERA, no INTERNET, no storage permissions |
| Documents | PDF, PNG, JPG, WebP, BMP, TXT / CSV / MD / LOG |

## Features

- **Print** PDFs, images and text with full page setup: paper size (A4, Letter,
  Legal, A3, A5, A6, B4, B5, Executive, Statement, Tabloid, Folio, Oficio,
  photo sizes, 58/80 mm rolls), page ranges (`1-3, 5, 8-`), copies with
  collation, orientation, color mode, quality, scaling (fit / fill / actual),
  margins (presets + per-side custom), duplex, reverse order, mirror.
- **Scan** from a printer's flatbed (eSCL over IPP-USB, when the device
  exposes it) or with the phone camera: crop with draggable corners,
  perspective flattening, document enhancement, grayscale.
- **Multi-page sessions**: a page tray collects scans; pages survive
  rotation and process death (cached to disk), can be reordered, rotated,
  removed, saved together, shared together, or printed as one job.
- **Local driver engine** with a generic fallback for unknown models.
- **Job history** with repeat-action for completed jobs.
- **Diagnostics** (support view): transport, VID/PID, interfaces, chosen
  driver and why, feature expectations, last-scan stats, test page.
- **Light and dark themes**, adaptive layout for phones and tablets
  (bottom bar → navigation rail → two-pane print setup).

## Printer support

| Driver family | Typical hardware | Status |
| --- | --- | --- |
| HP PCL raster | HP, Brother, Samsung, Xerox, Kyocera, Lexmark, Ricoh, Sharp, Toshiba, OKI, Dell, Konica Minolta lasers | Best-effort: implemented and unit-tested at the command-framing level; broad real-world PCL compatibility, not certified per model |
| Epson ESC/P-R | Epson Stylus / EcoTank / WorkForce / Expression; closest cousin for Canon inkjets | Best-effort: follows the publicly documented command structure |
| ESC/POS raster | Thermal/label: Epson TM, Zebra, Bixolon, Citizen, Star and clones | Best-effort |
| PostScript Level 2 | Office lasers with a PS interpreter | Best-effort |
| Generic raster (PCL form) | Any unknown printer-class USB device | Conservative fallback — 300 dpi monochrome |
| Plain text | Impact printers, stubborn legacy hardware | Best-effort |
| eSCL scanning (IPP-USB) | MFPs that expose AirPrint-style scanning over USB | Best-effort |

Brand routing covers Epson, HP, Canon, Brother, Samsung, Xerox, Dell,
Konica Minolta, Kyocera, Lexmark, Ricoh, Sharp, Toshiba, OKI, plus thermal
vendors. Paper, ink and readiness levels are **not reported as known** when
the connection cannot provide them — the UI states "unknown" instead of
fabricating data.

> **Honesty note:** every protocol above is implemented from public
> documentation and unit-tested for command correctness. None of them has
> been certified against exhaustive real-hardware matrices; use the built-in
> test page and diagnostics view to verify a specific printer.

## Building

Requirements: JDK 17+ (a full JDK with `jlink` is only needed if you enable
Java sources / BuildConfig), Android SDK 35, Gradle wrapper included.

```bash
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release APK (see Signing)
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew lintDebug              # static analysis (0 errors gate)
./gradlew connectedDebugAndroidTest   # UI smoke tests — needs a device/emulator
```

## Signing

Release signing reads `keystore.properties` (git-ignored) at the repo root:

```properties
storeFile=openpage.jks
storePassword=…
keyAlias=openpage
keyPassword=…
```

Generate your own keystore and never commit it:

```bash
keytool -genkeypair -keystore openpage.jks -alias openpage \
  -keyalg RSA -keysize 2048 -validity 10950
```

The historical v1.0.0 signing material was rotated in v2.0.0 and removed
from version control and the source archive.

## Project layout

```
app/src/main/java/com/qawse/openpage/
  core/        AppLog, structured error taxonomy (PrintProblem, ScanProblem)
  data/        Models (paper, settings, page ranges), PrinterDatabase
  drivers/     PclDriver, EscPRDriver, EscPosDriver, PostScriptDriver,
               RawTextDriver, GenericDriver, Raster halftoning
  print/       PageGeometry (pure math), PageRenderer, PrintJobEngine, JobHistory
  scan/        EsclScanner (HTTP over IPP-USB), ScanProcessor
  usb/         UsbPrinterManager (discovery, permission, bulk transfer)
  viewmodel/   AppViewModel (state, SavedStateHandle persistence)
  ui/          theme/ (tokens, palette, type), components/, navigation/,
               screens/ (+ screens/diagnostics/)
app/src/test/  JVM unit tests (no device needed)
app/src/androidTest/  UI smoke tests (device needed)
```

## Testing

- **90 JVM test methods** cover page-range parsing (including typographic
  dashes), live range validation, driver routing per brand VID, PCL command
  framing (regression: v1.0.0 sent several commands without the ESC prefix),
  page geometry and dpi capping, copy/collate ordering, scan quad math, and
  the eSCL HTTP response parser (regression: header values losing their last
  byte; chunked terminal-chunk detection).
- **Lint**: The project is configured to fail on lint errors. A clean lint
  result should be verified in your Android SDK environment before release.
- **Instrumentation**: navigation smoke tests exist but require a device;
  they were not executed in the v2.0.0 build environment (no emulator/KVM).

## Known limitations

- Paper/ink/readyness levels are unknowable over the supported transports —
  shown as unknown, never guessed.
- eSCL scanning requires the printer to expose IPP-USB (protocol 4)
  interfaces; many consumer inkjets do not.
- Scan crop corner-drag has no alternative keyboard/TalkBack path; the
  "Use whole photo" button is the accessible fallback.
- PostScript output is capped at 400 dpi to bound job size.
- Camera capture relies on a system camera app being installed.
- No print-preview for duplex flipping (preview shows single faces).

## License and attribution

OpenPage's original app code is released under the **MIT License**. See
[`LICENSE`](LICENSE). The MIT license permits use, copying, modification,
merging, publication, distribution, sublicensing, and sale of copies, subject
to retaining the copyright and permission notices.

Copyright © 2026 The QAWSE Institute. Developed and published by Kristian
Espedido, Founder and current CEO of The QAWSE Institute / QAWSE Corp.

This copyright notice is **not** a restriction against GitHub users using,
modifying, or redistributing the code. Retaining the notice is one of the
conditions of the MIT License.

The Inter typeface is bundled under the SIL Open Font License 1.1 and is
located in `app/src/main/res/font/`; its license is preserved at
`app/src/main/assets/inter_ofl_license.txt`. See
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for dependency and asset
licensing information.

## Contributing and reporting security issues

Contributions are welcome. Please read [`CONTRIBUTING.md`](CONTRIBUTING.md)
before opening a pull request. Do not report unpatched security issues in a
public issue; use the process in [`SECURITY.md`](SECURITY.md).
