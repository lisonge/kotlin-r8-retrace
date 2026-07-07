package li.songe.retrace

/**
 * Configuration for string retracing.
 */
public data class RetraceConfig(
    public val regex: String = RetraceDefaults.DEFAULT_REGEX,
    public val verbose: Boolean = false,
)

public object RetraceDefaults {
    public const val DEFAULT_REGEX: String =
        "(?:.*?\\bat\\s+%c\\.%m\\s*\\(%S\\)\\p{Z}*(?:~\\[.*\\])?)" +
            "|(?:(?:(?:%c|.*)?[:\"]\\s+)?%c(?:(:|]).*)?)"
}

public data class RetraceDiagnostic(
    public val severity: Severity,
    public val lineNumber: Int,
    public val message: String,
) {
    public enum class Severity {
        INFO,
        WARNING,
        ERROR,
    }
}

public class RetraceException public constructor(
    message: String,
    public val diagnostics: List<RetraceDiagnostic> = emptyList(),
) : IllegalArgumentException(message)

internal data class Range(
    val from: Int,
    val to: Int,
    val isCardinal: Boolean = from == to,
) {
    init {
        require(from <= to) { "Invalid range: $from:$to" }
    }

    fun contains(value: Int): Boolean = value in from..to

    fun span(): Int = if (isCardinal) 1 else to - from + 1

    fun isCatchAll(): Boolean = from == 0 && to == Int.MAX_VALUE
}

internal sealed interface Signature {
    val name: String
    val type: String
    fun renamed(renamedName: String): Signature
}

internal data class MethodSignature(
    override val name: String,
    override val type: String,
    val parameters: List<String>,
) : Signature {
    override fun renamed(renamedName: String): MethodSignature = copy(name = renamedName)
}

internal data class FieldSignature(
    override val name: String,
    override val type: String,
) : Signature {
    override fun renamed(renamedName: String): FieldSignature = copy(name = renamedName)
}

internal data class MappingIndex(
    val classes: Map<String, ClassMapping>,
    val mapVersions: List<String>,
    val diagnostics: List<RetraceDiagnostic>,
)

internal data class ClassMapping(
    val originalName: String,
    val obfuscatedName: String,
    val sourceFile: String?,
    val synthesized: Boolean,
    val methodsByObfuscatedName: Map<String, List<MethodMapping>>,
    val fieldsByObfuscatedName: Map<String, List<FieldMapping>>,
)

internal data class MutableClassMapping(
    val originalName: String,
    val obfuscatedName: String,
    var sourceFile: String? = null,
    var synthesized: Boolean = false,
    val methods: MutableList<MethodMapping> = mutableListOf(),
    val fields: MutableList<FieldMapping> = mutableListOf(),
) {
    fun freeze(): ClassMapping =
        ClassMapping(
            originalName = originalName,
            obfuscatedName = obfuscatedName,
            sourceFile = sourceFile,
            synthesized = synthesized,
            methodsByObfuscatedName = methods.groupBy { it.obfuscatedName },
            fieldsByObfuscatedName = fields.groupBy { it.obfuscatedName },
        )
}

internal data class MethodMapping(
    val originalSignature: MethodSignature,
    val residualSignature: MethodSignature,
    val obfuscatedName: String,
    val minifiedRange: Range?,
    val originalRange: Range?,
    val sourceLine: Int,
    val synthesized: Boolean,
    val outline: Boolean,
    val outlineCallsite: OutlineCallsiteInfo?,
    val rewriteFrame: RewriteFrameInfo?,
) {
    fun withInfo(info: MappingInfo): MethodMapping =
        when (info) {
            is MappingInfo.CompilerSynthesized -> copy(synthesized = true)
            is MappingInfo.Outline -> copy(outline = true, synthesized = true)
            is MappingInfo.OutlineCallsite -> copy(outlineCallsite = info.info)
            is MappingInfo.RewriteFrame -> copy(rewriteFrame = info.info)
            is MappingInfo.ResidualSignature -> {
                val signature = info.asMethodSignature(obfuscatedName)
                if (signature == null) this else copy(residualSignature = signature)
            }
            else -> this
        }
}

internal data class FieldMapping(
    val originalSignature: FieldSignature,
    val residualSignature: FieldSignature,
    val obfuscatedName: String,
    val sourceLine: Int,
    val synthesized: Boolean,
) {
    fun withInfo(info: MappingInfo): FieldMapping =
        when (info) {
            is MappingInfo.CompilerSynthesized -> copy(synthesized = true)
            is MappingInfo.ResidualSignature -> {
                val signature = info.asFieldSignature(obfuscatedName)
                if (signature == null) this else copy(residualSignature = signature)
            }
            else -> this
        }
}

internal data class OutlineCallsiteInfo(
    val positions: Map<Int, Int>,
    val outline: String?,
) {
    fun rewritePosition(position: Int): Int = positions[position] ?: position
}

internal data class RewriteFrameInfo(
    val conditions: List<RewriteCondition>,
    val actions: List<RewriteAction>,
) {
    fun matches(context: RetraceContext): Boolean = conditions.all { it.matches(context) }
}

internal sealed interface RewriteCondition {
    fun matches(context: RetraceContext): Boolean

    data class Throws(val className: String) : RewriteCondition {
        override fun matches(context: RetraceContext): Boolean = context.thrownException == className
    }
}

internal sealed interface RewriteAction {
    fun apply(state: RewriteState): RewriteState

    data class RemoveInnerFrames(val count: Int) : RewriteAction {
        override fun apply(state: RewriteState): RewriteState =
            state.copy(removeInnerFramesCount = state.removeInnerFramesCount + count)
    }
}

internal data class RewriteState(
    val removeInnerFramesCount: Int = 0,
)

internal data class RetraceContext(
    val thrownException: String? = null,
    val rewritePosition: Int? = null,
) {
    companion object {
        val EMPTY: RetraceContext = RetraceContext()
    }
}

internal sealed interface MappingInfo {
    data class MapVersion(val version: String) : MappingInfo
    data class SourceFile(val fileName: String) : MappingInfo
    data object CompilerSynthesized : MappingInfo
    data object Outline : MappingInfo
    data class OutlineCallsite(val info: OutlineCallsiteInfo) : MappingInfo
    data class RewriteFrame(val info: RewriteFrameInfo) : MappingInfo
    data class ResidualSignature(val descriptor: String) : MappingInfo
    data class Unknown(val id: String) : MappingInfo
}
