package li.songe.retrace

internal class StackLineParser(
    regex: String = RetraceDefaults.DEFAULT_REGEX,
) {
    private val compiledRegex: Regex
    private val handlers: List<RegexGroupHandler>

    init {
        val translated = RegexTranslator.translate(regex)
        compiledRegex = Regex(translated.pattern)
        handlers = translated.handlers
    }

    fun parse(line: String): ParsedStackLine {
        val builder = ParsedStackLineBuilder(line)
        val match = compiledRegex.matchEntire(line) ?: return builder.build()
        var seenMatchedClass = false
        handlers.forEach { handler ->
            if (seenMatchedClass && handler.isClassHandler) return@forEach
            val group = match.groups[handler.groupIndex] ?: return@forEach
            if (handler.apply(builder, group.range.first, group.range.last + 1)) {
                seenMatchedClass = seenMatchedClass || handler.isClassHandler
            }
        }
        return builder.build()
    }

    private companion object RegexTranslator {
        private const val NOT_ALLOWED_CHARACTERS: String = "\\s\\[\\];:()<>"
        private const val IDENTIFIER_PREFIX: String = "[^\\d$NOT_ALLOWED_CHARACTERS]"
        private const val IDENTIFIER_SUFFIX: String = "[^$NOT_ALLOWED_CHARACTERS]*"
        private const val IDENTIFIER_SEGMENT: String = "$IDENTIFIER_PREFIX$IDENTIFIER_SUFFIX"
        private const val METHOD_NAME_REGEX: String = "(?:$IDENTIFIER_SEGMENT|<init>|<clinit>)"
        private const val JAVA_TYPE_REGEX: String =
            "(?:$IDENTIFIER_SEGMENT\\.)*$IDENTIFIER_SEGMENT[\\[\\]]*"

        fun translate(regex: String): TranslatedRegex {
            val pattern = StringBuilder()
            val handlers = mutableListOf<RegexGroupHandler>()
            var captureGroupIndex = 1
            var lastCommittedIndex = 0
            var seenPercentage = false
            var escaped = false
            regex.forEachIndexed { index, ch ->
                if (seenPercentage) {
                    val literal = regex.substring(lastCommittedIndex, index - 1)
                    pattern.append(literal)
                    captureGroupIndex += countCapturingGroups(literal)
                    val placeholder = placeholderFor(ch)
                    pattern.append('(').append(placeholder.pattern).append(')')
                    handlers += placeholder.createHandler(captureGroupIndex)
                    captureGroupIndex += 1
                    lastCommittedIndex = index + 1
                    seenPercentage = false
                    escaped = false
                } else {
                    seenPercentage = !escaped && ch == '%'
                    escaped = !escaped && ch == '\\'
                }
            }
            val tail = regex.substring(lastCommittedIndex)
            pattern.append(tail)
            return TranslatedRegex(pattern.toString(), handlers)
        }

        private fun placeholderFor(ch: Char): RegexPlaceholder =
            when (ch) {
                'c' -> RegexPlaceholder(TYPE_NAME_REGEX) { groupIndex ->
                    ClassGroupHandler(groupIndex, ClassNameType.TYPENAME)
                }
                'C' -> RegexPlaceholder(BINARY_NAME_REGEX) { groupIndex ->
                    ClassGroupHandler(groupIndex, ClassNameType.BINARY)
                }
                'm' -> RegexPlaceholder(METHOD_NAME_REGEX) { MethodNameGroupHandler(it) }
                'f' -> RegexPlaceholder(IDENTIFIER_SEGMENT) { FieldNameGroupHandler(it) }
                's' -> RegexPlaceholder(SOURCE_FILE_REGEX) { SourceFileGroupHandler(it) }
                'l' -> RegexPlaceholder("\\d*") { LineNumberGroupHandler(it) }
                'S' -> RegexPlaceholder(".*") { SourceFileLineNumberGroupHandler(it) }
                't' -> RegexPlaceholder(JAVA_TYPE_REGEX) { FieldOrReturnTypeGroupHandler(it) }
                'a' -> RegexPlaceholder("(?:(?:$JAVA_TYPE_REGEX,\\s*)*$JAVA_TYPE_REGEX)?") {
                    MethodArgumentsGroupHandler(it)
                }
                else -> throw IllegalArgumentException("Unexpected regex placeholder: %$ch")
            }

        private const val TYPE_NAME_REGEX: String = "(?:$IDENTIFIER_SEGMENT\\.)*$IDENTIFIER_SEGMENT"
        private const val BINARY_NAME_REGEX: String = "(?:$IDENTIFIER_SEGMENT/)*$IDENTIFIER_SEGMENT"
        private const val SOURCE_FILE_REGEX: String = "(?:(?::+[^\\d:\\s])|[^:])*"

        private fun countCapturingGroups(pattern: String): Int {
            var count = 0
            var escaped = false
            var inCharacterClass = false
            var index = 0
            while (index < pattern.length) {
                val ch = pattern[index]
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '[' -> inCharacterClass = true
                    ch == ']' -> inCharacterClass = false
                    ch == '(' && !inCharacterClass -> {
                        val next = pattern.getOrNull(index + 1)
                        if (next != '?') count += 1
                        if (next == '?' && pattern.getOrNull(index + 2) == '<') {
                            val lookBehind = pattern.getOrNull(index + 3)
                            if (lookBehind != '=' && lookBehind != '!') count += 1
                        }
                    }
                }
                index += 1
            }
            return count
        }
    }
}

private data class TranslatedRegex(
    val pattern: String,
    val handlers: List<RegexGroupHandler>,
)

private data class RegexPlaceholder(
    val pattern: String,
    val createHandler: (Int) -> RegexGroupHandler,
)

private sealed class RegexGroupHandler(
    val groupIndex: Int,
    val isClassHandler: Boolean = false,
) {
    abstract fun apply(builder: ParsedStackLineBuilder, start: Int, end: Int): Boolean
}

private class ClassGroupHandler(
    groupIndex: Int,
    private val classNameType: ClassNameType,
) : RegexGroupHandler(groupIndex, isClassHandler = true) {
    override fun apply(builder: ParsedStackLineBuilder, start: Int, end: Int): Boolean {
        val value = builder.line.substring(start, end)
        if (value == "Suppressed") return false
        val classStart = if (classNameType == ClassNameType.TYPENAME) start + value.lastIndexOf('/') + 1 else start
        builder.registerClassName(classStart, end, classNameType)
        return true
    }
}

private class MethodNameGroupHandler(groupIndex: Int) : RegexGroupHandler(groupIndex) {
    override fun apply(builder: ParsedStackLineBuilder, start: Int, end: Int): Boolean {
        builder.registerMethodName(start, end)
        return true
    }
}

private class FieldNameGroupHandler(groupIndex: Int) : RegexGroupHandler(groupIndex) {
    override fun apply(builder: ParsedStackLineBuilder, start: Int, end: Int): Boolean {
        builder.registerFieldName(start, end)
        return true
    }
}

private class SourceFileGroupHandler(groupIndex: Int) : RegexGroupHandler(groupIndex) {
    override fun apply(builder: ParsedStackLineBuilder, start: Int, end: Int): Boolean {
        builder.registerSourceFile(start, end)
        return true
    }
}

private class LineNumberGroupHandler(groupIndex: Int) : RegexGroupHandler(groupIndex) {
    override fun apply(builder: ParsedStackLineBuilder, start: Int, end: Int): Boolean {
        var lineStart = start
        var insertSeparator = false
        if (lineStart > 0 && builder.line[lineStart - 1] == ':') {
            lineStart -= 1
            insertSeparator = true
        }
        builder.registerLineNumber(lineStart, end, insertSeparator)
        return true
    }
}

private class SourceFileLineNumberGroupHandler(groupIndex: Int) : RegexGroupHandler(groupIndex) {
    override fun apply(builder: ParsedStackLineBuilder, start: Int, end: Int): Boolean {
        val value = builder.line.substring(start, end)
        val sourceEnd = start + findEndOfSourceFile(value)
        builder.registerSourceFile(start, sourceEnd)
        builder.registerLineNumber(minOf(sourceEnd, end), end, insertSeparator = true)
        return true
    }

    private fun findEndOfSourceFile(group: String): Int {
        var index = group.length
        while (index > 0) {
            val current = group[index - 1]
            if (current == ':' && index < group.length) return index - 1
            if (!current.isDigit()) return group.length
            index -= 1
        }
        return group.length
    }
}

private class FieldOrReturnTypeGroupHandler(groupIndex: Int) : RegexGroupHandler(groupIndex) {
    override fun apply(builder: ParsedStackLineBuilder, start: Int, end: Int): Boolean {
        builder.registerFieldOrReturnType(start, end)
        return true
    }
}

private class MethodArgumentsGroupHandler(groupIndex: Int) : RegexGroupHandler(groupIndex) {
    override fun apply(builder: ParsedStackLineBuilder, start: Int, end: Int): Boolean {
        builder.registerMethodArguments(start, end)
        return true
    }
}

internal data class ParsedStackLine(
    val line: String,
    val orderedTokens: List<LineToken>,
    val className: ClassLineToken?,
    val methodName: LineToken?,
    val sourceFile: LineToken?,
    val lineNumber: LineNumberToken?,
    val fieldName: LineToken?,
    val fieldOrReturnType: LineToken?,
    val methodArguments: LineToken?,
) {
    fun classTypeNameValue(): String? =
        className?.let {
            val value = it.value(line)
            if (it.classNameType == ClassNameType.BINARY) TypeNames.binaryToTypeName(value) else value
        }

    fun methodNameValue(): String? = methodName?.value(line)
    fun sourceFileValue(): String? = sourceFile?.value(line)
    fun lineNumberValue(): Int? {
        val raw = lineNumber?.value(line)?.removePrefix(":") ?: return null
        return raw.toIntOrNull()
    }

    fun methodArgumentsValue(): List<String>? =
        methodArguments?.value(line)
            ?.takeIf { it.isNotBlank() }
            ?.split(',')
            ?.map { it.trim() }
            ?: methodArguments?.let { emptyList() }
}

internal class ParsedStackLineBuilder(val line: String) {
    private val orderedTokens = mutableListOf<LineToken>()
    private var className: ClassLineToken? = null
    private var methodName: LineToken? = null
    private var sourceFile: LineToken? = null
    private var lineNumber: LineNumberToken? = null
    private var fieldName: LineToken? = null
    private var fieldOrReturnType: LineToken? = null
    private var methodArguments: LineToken? = null

    fun registerClassName(start: Int, end: Int, classNameType: ClassNameType) {
        className = ClassLineToken(start, end, classNameType).also(orderedTokens::add)
    }

    fun registerMethodName(start: Int, end: Int) {
        methodName = LineToken(start, end).also(orderedTokens::add)
    }

    fun registerSourceFile(start: Int, end: Int) {
        sourceFile = LineToken(start, end).also(orderedTokens::add)
    }

    fun registerLineNumber(start: Int, end: Int, insertSeparator: Boolean) {
        lineNumber = LineNumberToken(start, end, insertSeparator).also(orderedTokens::add)
    }

    fun registerFieldName(start: Int, end: Int) {
        fieldName = LineToken(start, end).also(orderedTokens::add)
    }

    fun registerFieldOrReturnType(start: Int, end: Int) {
        fieldOrReturnType = LineToken(start, end).also(orderedTokens::add)
    }

    fun registerMethodArguments(start: Int, end: Int) {
        methodArguments = LineToken(start, end).also(orderedTokens::add)
    }

    fun build(): ParsedStackLine =
        ParsedStackLine(
            line = line,
            orderedTokens = orderedTokens.sortedBy { it.start },
            className = className,
            methodName = methodName,
            sourceFile = sourceFile,
            lineNumber = lineNumber,
            fieldName = fieldName,
            fieldOrReturnType = fieldOrReturnType,
            methodArguments = methodArguments,
        )
}

internal open class LineToken(
    val start: Int,
    val end: Int,
) {
    fun value(line: String): String = line.substring(start, end)
}

internal class ClassLineToken(
    start: Int,
    end: Int,
    val classNameType: ClassNameType,
) : LineToken(start, end)

internal class LineNumberToken(
    start: Int,
    end: Int,
    val insertSeparator: Boolean,
) : LineToken(start, end)

internal enum class ClassNameType {
    BINARY,
    TYPENAME,
}
