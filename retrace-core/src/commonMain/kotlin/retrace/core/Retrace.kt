package retrace.core

public object Retrace {
    public fun retrace(
        mapping: String,
        stackTrace: String,
        config: RetraceConfig = RetraceConfig(),
    ): String {
        val lines = stackTrace.splitToLinesKeepingEmpty()
        return retraceLines(mapping, lines, config).joinToString("\n")
    }

    public fun retraceLines(
        mapping: String,
        stackTrace: List<String>,
        config: RetraceConfig = RetraceConfig(),
    ): List<String> = Retracer.fromMapping(mapping, config).retraceLines(stackTrace)
}

public class Retracer internal constructor(
    mappingIndex: MappingIndex,
    config: RetraceConfig,
) {
    private val engine = RetraceEngine(mappingIndex, config)

    public val diagnostics: List<RetraceDiagnostic> = mappingIndex.diagnostics

    public fun retraceLines(stackTrace: List<String>): List<String> = engine.retraceLines(stackTrace)

    public fun retrace(stackTrace: String): String =
        retraceLines(stackTrace.splitToLinesKeepingEmpty()).joinToString("\n")

    public companion object {
        public fun fromMapping(
            mapping: String,
            config: RetraceConfig = RetraceConfig(),
        ): Retracer {
            val mappingIndex = MappingParser().parse(mapping)
            val errors = mappingIndex.diagnostics.filter { it.severity == RetraceDiagnostic.Severity.ERROR }
            if (errors.isNotEmpty()) {
                throw RetraceException("Invalid mapping file", mappingIndex.diagnostics)
            }
            return Retracer(mappingIndex, config)
        }
    }
}

private fun String.splitToLinesKeepingEmpty(): List<String> =
    replace("\r\n", "\n").replace('\r', '\n').split('\n')
