package com.qawse.openpage.core

/**
 * Structured, user-explainable failure taxonomy.
 *
 * Every failure the user can see maps to one of these types, and the UI
 * renders message + recovery action from string resources. No screen ever
 * shows a raw exception or "Something went wrong".
 */

/** Print pipeline failures. */
enum class PrintProblem {
    /** No printer attached. Recovery: connect one, open Printers. */
    NO_PRINTER,

    /** USB permission denied. Recovery: grant access from Printers. */
    NO_PERMISSION,

    /** Device stopped accepting transfers mid-job. Recovery: re-plug, retry. */
    TRANSPORT,

    /** The document could not be opened or decoded. Recovery: pick again. */
    OPEN_FAILED,

    /** Rendering a page failed after the document opened. */
    RENDER,

    /** Page range is syntactically invalid or selects nothing. */
    BAD_RANGE,

    /** User cancelled. Not an error — pages sent so far are reported. */
    CANCELLED,
}

/** Scan pipeline failures with a precise cause. */
sealed interface ScanProblem {
    /** The device has no IPP/eSCL interface — glass scanning impossible. */
    data object NoScanner : ScanProblem

    /** USB permission denied for the device. */
    data object PermissionDenied : ScanProblem

    /** Transfer-level failure: timeout, stall, cable. */
    data class Transport(val detail: String) : ScanProblem

    /** Device answered, but the response could not be parsed. */
    data class InvalidResponse(val detail: String) : ScanProblem

    /** Device refused the scan job (unsupported mode/size). */
    data class Unsupported(val detail: String) : ScanProblem

    /** Decoding the returned image bytes failed. */
    data object UndecodableImage : ScanProblem

    /** No camera app is available on this phone. */
    data object NoCameraApp : ScanProblem

    /** The camera returned but the photo could not be decoded. */
    data object CameraCaptureFailed : ScanProblem

    /** User cancelled — partial pages are kept. */
    data object Cancelled : ScanProblem
}
