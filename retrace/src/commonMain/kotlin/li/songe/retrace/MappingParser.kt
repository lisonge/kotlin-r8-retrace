package li.songe.retrace

internal class MappingParser(
    private val config: RetraceConfig,
) {
    private val diagnostics = mutableListOf<RetraceDiagnostic>()
    private val classes = linkedMapOf<String, MutableClassMapping>()
    private val mapVersions = mutableListOf<String>()

    private var currentClass: MutableClassMapping? = null
    private var lastMemberKind: LastMemberKind? = null

    fun parse(mapping: String): MappingIndex {
        mapping.lineSequence().forEachIndexed { index, line ->
            parseLine(line.trimEnd('\r'), index + 1)
        }
        return MappingIndex(
            classes = classes.mapValues { it.value.freeze() },
            mapVersions = mapVersions.toList(),
            diagnostics = diagnostics.toList(),
        )
    }

    private fun parseLine(line: String, lineNumber: Int) {
        if (line.isBlank()) return
        val trimmedStart = line.trimStart()
        if (trimmedStart.startsWith("#")) {
            parseComment(line, trimmedStart, lineNumber)
            return
        }
        if (!line.first().isWhitespace()) {
            parseClassLine(line, lineNumber)
            return
        }
        parseMemberLine(trimmedStart, lineNumber)
    }

    private fun parseComment(rawLine: String, trimmedStart: String, lineNumber: Int) {
        val info = MappingInfoParser.parse(trimmedStart) ?: return
        when (info) {
            is MappingInfo.MapVersion -> mapVersions += info.version
            is MappingInfo.Unknown ->
                diagnostics += RetraceDiagnostic(
                    RetraceDiagnostic.Severity.INFO,
                    lineNumber,
                    "Unknown mapping information '${info.id}'",
                )
            else -> applyScopedInfo(rawLine, info)
        }
    }

    private fun applyScopedInfo(rawLine: String, info: MappingInfo) {
        val clazz = currentClass ?: return
        val isIndented = rawLine.firstOrNull()?.isWhitespace() == true
        val memberKind = lastMemberKind
        if (isIndented && memberKind != null) {
            when (memberKind) {
                LastMemberKind.METHOD -> {
                    val lastIndex = clazz.methods.lastIndex
                    if (lastIndex >= 0) {
                        clazz.methods[lastIndex] = clazz.methods[lastIndex].withInfo(info)
                    }
                }
                LastMemberKind.FIELD -> {
                    val lastIndex = clazz.fields.lastIndex
                    if (lastIndex >= 0) {
                        clazz.fields[lastIndex] = clazz.fields[lastIndex].withInfo(info)
                    }
                }
            }
            return
        }
        when (info) {
            is MappingInfo.SourceFile -> clazz.sourceFile = info.fileName
            is MappingInfo.CompilerSynthesized -> clazz.synthesized = true
            else -> Unit
        }
    }

    private fun parseClassLine(line: String, lineNumber: Int) {
        val arrow = line.indexOf("->")
        if (arrow < 0 || !line.trimEnd().endsWith(":")) {
            addError(lineNumber, "Expected class mapping line")
            return
        }
        val original = line.substring(0, arrow).trim()
        val obfuscated = line.substring(arrow + 2, line.lastIndexOf(':')).trim()
        if (original.isEmpty() || obfuscated.isEmpty()) {
            addError(lineNumber, "Invalid class mapping line")
            return
        }
        currentClass =
            classes.getOrPut(obfuscated) {
                MutableClassMapping(originalName = original, obfuscatedName = obfuscated)
            }
        lastMemberKind = null
    }

    private fun parseMemberLine(line: String, lineNumber: Int) {
        val clazz = currentClass
        if (clazz == null) {
            addError(lineNumber, "Member mapping without class mapping")
            return
        }
        val arrow = line.indexOf("->")
        if (arrow < 0) {
            addError(lineNumber, "Expected member mapping arrow")
            return
        }
        val left = line.substring(0, arrow).trim()
        val obfuscatedName = line.substring(arrow + 2).trim().takeWhile { !it.isWhitespace() }
        if (left.isEmpty() || obfuscatedName.isEmpty()) {
            addError(lineNumber, "Invalid member mapping line")
            return
        }
        val rangeAndRemainder = consumeMinifiedRange(left)
        val minifiedRange = rangeAndRemainder.first
        val signaturePart = rangeAndRemainder.second.trim()
        if ('(' in signaturePart) {
            val parsed = parseMethodSignature(signaturePart)
            if (parsed == null) {
                addError(lineNumber, "Invalid method signature")
                return
            }
            val method =
                MethodMapping(
                    originalSignature = parsed.signature,
                    residualSignature = parsed.signature.renamed(obfuscatedName),
                    obfuscatedName = obfuscatedName,
                    minifiedRange = minifiedRange,
                    originalRange = parsed.originalRange,
                    sourceLine = lineNumber,
                    synthesized = false,
                    outline = false,
                    outlineCallsite = null,
                    rewriteFrame = null,
                )
            clazz.methods += method
            lastMemberKind = LastMemberKind.METHOD
        } else {
            if (minifiedRange != null) {
                addError(lineNumber, "Field mapping cannot have a minified range")
                return
            }
            val signature = parseFieldSignature(signaturePart)
            if (signature == null) {
                addError(lineNumber, "Invalid field signature")
                return
            }
            clazz.fields +=
                FieldMapping(
                    originalSignature = signature,
                    residualSignature = signature.renamed(obfuscatedName),
                    obfuscatedName = obfuscatedName,
                    sourceLine = lineNumber,
                    synthesized = false,
                )
            lastMemberKind = LastMemberKind.FIELD
        }
    }

    private fun consumeMinifiedRange(left: String): Pair<Range?, String> {
        var index = 0
        val first = readNumber(left, index) ?: return null to left
        index = first.nextIndex
        index = left.skipSpaces(index)
        if (index >= left.length || left[index] != ':') return null to left
        index += 1
        index = left.skipSpaces(index)
        val second = readNumber(left, index) ?: return null to left
        index = second.nextIndex
        index = left.skipSpaces(index)
        if (index >= left.length || left[index] != ':') return null to left
        index += 1
        val from = minOf(first.value, second.value)
        val to = maxOf(first.value, second.value)
        return Range(from, to, isCardinal = false) to left.substring(index)
    }

    private fun parseMethodSignature(text: String): ParsedMethodSignature? {
        val open = text.indexOf('(')
        val close = text.indexOf(')', startIndex = open + 1)
        if (open < 0 || close < 0) return null
        val beforeOpen = text.substring(0, open).trimEnd()
        val split = beforeOpen.lastIndexOfAny(charArrayOf(' ', '\t'))
        if (split < 0) return null
        val returnType = beforeOpen.substring(0, split).trim()
        val methodName = beforeOpen.substring(split + 1).trim()
        val parameters =
            text.substring(open + 1, close)
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.split(',')
                ?.map { it.trim() }
                ?: emptyList()
        val afterClose = text.substring(close + 1).trim()
        val originalRange =
            if (afterClose.isEmpty()) {
                null
            } else {
                if (!afterClose.startsWith(":")) return null
                parseRange(afterClose.substring(1).trim()) ?: return null
            }
        return ParsedMethodSignature(MethodSignature(methodName, returnType, parameters), originalRange)
    }

    private fun parseFieldSignature(text: String): FieldSignature? {
        val split = text.trimEnd().lastIndexOfAny(charArrayOf(' ', '\t'))
        if (split < 0) return null
        val type = text.substring(0, split).trim()
        val name = text.substring(split + 1).trim()
        if (type.isEmpty() || name.isEmpty()) return null
        return FieldSignature(name, type)
    }

    private fun parseRange(text: String): Range? {
        var index = 0
        val first = readNumber(text, index) ?: return null
        index = text.skipSpaces(first.nextIndex)
        if (index >= text.length) return Range(first.value, first.value, isCardinal = true)
        if (text[index] != ':') return null
        index += 1
        index = text.skipSpaces(index)
        val second = readNumber(text, index) ?: return null
        index = text.skipSpaces(second.nextIndex)
        if (index != text.length) return null
        val from = minOf(first.value, second.value)
        val to = maxOf(first.value, second.value)
        return Range(from, to, isCardinal = first.value == second.value)
    }

    private fun readNumber(text: String, start: Int): ParsedNumber? {
        var index = start
        if (index >= text.length || !text[index].isDigit()) return null
        var value = 0
        while (index < text.length && text[index].isDigit()) {
            value = value * 10 + (text[index] - '0')
            index += 1
        }
        return ParsedNumber(value, index)
    }

    private fun String.skipSpaces(start: Int): Int {
        var index = start
        while (index < length && this[index].isWhitespace()) index += 1
        return index
    }

    private fun addError(lineNumber: Int, message: String) {
        diagnostics += RetraceDiagnostic(RetraceDiagnostic.Severity.ERROR, lineNumber, message)
    }

    private enum class LastMemberKind {
        METHOD,
        FIELD,
    }

    private data class ParsedNumber(val value: Int, val nextIndex: Int)

    private data class ParsedMethodSignature(
        val signature: MethodSignature,
        val originalRange: Range?,
    )
}
