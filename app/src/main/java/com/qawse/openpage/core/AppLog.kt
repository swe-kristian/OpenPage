package com.qawse.openpage.core

import android.util.Log

/**
 * Minimal logging facade.
 *
 * Rules:
 *  - Debug builds log to logcat; release builds log nothing.
 *  - NEVER log document content, private file names, USB serial numbers,
 *    or credentials.
 *  - Messages are short, structured sentences: what stage, what happened.
 */
object AppLog {
    private const val MAX_LEN = 160

    /** Set from the Application at startup: logs only in debuggable builds. */
    @Volatile
    var enabled: Boolean = false

    fun d(tag: String, message: String) {
        if (enabled) Log.d(tag, clip(message))
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        if (enabled) Log.w(tag, clip(message), error)
    }

    /** Failure that changes app behavior — logged even where d() is silent. */
    fun e(tag: String, message: String, error: Throwable? = null) {
        if (enabled) Log.e(tag, clip(message), error)
    }

    private fun clip(s: String): String =
        if (s.length <= MAX_LEN) s else s.take(MAX_LEN - 1) + "…"
}

/** Canonical log tags. */
object Tags {
    const val USB = "OpenPage.USB"
    const val PRINT = "OpenPage.Print"
    const val SCAN = "OpenPage.Scan"
    const val RENDER = "OpenPage.Render"
    const val UI = "OpenPage.UI"
}
