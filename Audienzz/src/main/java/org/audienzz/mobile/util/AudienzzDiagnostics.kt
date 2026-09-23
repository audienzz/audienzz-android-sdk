package org.audienzz.mobile.util

import android.util.Log

/**
 * One greppable line per decision the SDK makes about a slot.
 *
 * This exists so a run on a device can be captured with `adb logcat -s AUDZ`, sent to someone who
 * was not holding the phone, and read back as a sequence: which page became current, which slot
 * belongs to it, when an auction actually started, and — the part that is otherwise invisible —
 * *why* one did not.
 *
 * Deliberately separate from the existing per-class `Log.d(TAG, …)` traces. Those are prose, one
 * format per class, and spread over a dozen tags; this is one stable format under one tag, and the
 * iOS, Flutter and React Native SDKs emit the identical shape so a flow can be compared across
 * platforms.
 *
 * Off by default — a publisher's logcat is not ours to fill. Turn it on before initializing with
 * `AudienzzPrebidMobile.diagnosticsEnabled = true`.
 *
 * Format: `AUDZ <subsystem> <event> key=value key=value`
 * Keys are stable; new keys may be added, so parse by key rather than by position.
 */
object AudienzzDiagnostics {

    const val TAG = "AUDZ"

    @JvmStatic
    var isEnabled: Boolean = false

    /**
     * Where a line goes. Replaceable so a host can route diagnostics into its own file — logcat is
     * fine at a desk, but not something a tester in the field can email back.
     */
    @JvmStatic
    var sink: (String) -> Unit = { Log.i(TAG, it) }

    @JvmStatic
    fun log(subsystem: String, event: String, vararg fields: Pair<String, Any?>) {
        if (!isEnabled) return
        val line = StringBuilder("AUDZ ").append(subsystem).append(' ').append(event)
        for ((key, value) in fields) {
            if (value == null) continue
            val text = value.toString()
            line.append(' ').append(key).append('=')
            // Bare unless it contains a space, which would break key=value parsing.
            if (text.contains(' ')) line.append('"').append(text).append('"') else line.append(text)
        }
        sink(line.toString())
    }
}
