package retrace.core

import kotlin.js.JsExport
import kotlin.js.JsName

private val exportedRetracers = hashMapOf<Int, Retracer>()

@JsExport
@JsName("retrace")
public fun retraceExport(
    mapping: String,
    stackTrace: String,
    regex: String,
    verbose: Boolean,
): String = Retrace.retrace(
    mapping = mapping,
    stackTrace = stackTrace,
    config = RetraceConfig(
        regex = regex.ifBlank { RetraceDefaults.DEFAULT_REGEX },
        verbose = verbose,
    ),
)

@JsExport
@JsName("retraceDefault")
public fun retraceDefaultExport(
    mapping: String,
    stackTrace: String,
): String = Retrace.retrace(mapping, stackTrace)

@JsExport
@JsName("createRetracer")
public fun createRetracerExport(
    mapping: String,
    regex: String,
    verbose: Boolean,
): Int {
    val retracer = Retracer.fromMapping(
        mapping = mapping,
        config = RetraceConfig(
            regex = regex.ifBlank { RetraceDefaults.DEFAULT_REGEX },
            verbose = verbose,
        ),
    )
    val id = allocateRetracerId()
    exportedRetracers[id] = retracer
    return id
}

@JsExport
@JsName("retraceWith")
public fun retraceWithExport(
    retracerId: Int,
    stackTrace: String,
): String {
    val retracer = exportedRetracers[retracerId] ?: throw IllegalArgumentException("Unknown retracer id: $retracerId")
    return retracer.retrace(stackTrace)
}

@JsExport
@JsName("disposeRetracer")
public fun disposeRetracerExport(retracerId: Int): Boolean = exportedRetracers.remove(retracerId) != null

@JsExport
@JsName("defaultRegex")
public fun defaultRegexExport(): String = RetraceDefaults.DEFAULT_REGEX

private var nextRetracerId: Int = 1
private fun allocateRetracerId(): Int = nextRetracerId++
