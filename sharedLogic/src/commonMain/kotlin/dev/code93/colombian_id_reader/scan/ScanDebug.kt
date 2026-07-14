package dev.code93.colombian_id_reader.scan

import kotlin.concurrent.Volatile

/**
 * Opt-in scan diagnostics hook, for debugging documents that fail to
 * scan during development.
 *
 * **Off by default and must stay off in production**: the emitted
 * events include raw OCR lines and MRZ candidates, i.e. personal data
 * from the document (§7). Enabling it is an explicit decision by the
 * developer on their own device:
 *
 * ```kotlin
 * ScanDebug.listener = { Log.d("ColombianIdScan", it) }
 * ```
 */
public object ScanDebug {

    @Volatile
    public var listener: ((String) -> Unit)? = null

    internal inline fun log(message: () -> String) {
        listener?.invoke(message())
    }
}
