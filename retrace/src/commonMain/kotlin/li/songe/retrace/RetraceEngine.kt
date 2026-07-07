package li.songe.retrace

internal class RetraceEngine(
    private val mappingIndex: MappingIndex,
    private val config: RetraceConfig,
) {
    private val stackLineParser = StackLineParser(config.regex)

    fun retraceLines(lines: List<String>): List<String> {
        var context = RetraceContext.EMPTY
        val output = mutableListOf<String>()
        lines.forEach { line ->
            val result = retraceParsed(stackLineParser.parse(line), context)
            context = result.context
            output += joinAmbiguousLines(result.alternatives)
        }
        return output
    }

    private fun retraceParsed(parsed: ParsedStackLine, context: RetraceContext): RetracedLineResult {
        val obfuscatedClass = parsed.classTypeNameValue()
        if (obfuscatedClass == null) {
            return RetracedLineResult.single(parsed.line, context)
        }
        val classMapping = mappingIndex.classes[obfuscatedClass]
        val methodName = parsed.methodNameValue()
        if (methodName != null) {
            return retraceMethodLine(parsed, classMapping, obfuscatedClass, methodName, context)
        }
        val fieldName = parsed.fieldName?.value(parsed.line)
        if (fieldName != null) {
            return retraceFieldLine(parsed, classMapping, obfuscatedClass, fieldName, context)
        }
        return retraceClassOnlyLine(parsed, classMapping, obfuscatedClass)
    }

    private fun retraceClassOnlyLine(
        parsed: ParsedStackLine,
        classMapping: ClassMapping?,
        obfuscatedClass: String,
    ): RetracedLineResult {
        val retracedClass = classMapping?.originalName ?: obfuscatedClass
        val frame =
            RenderFrame(
                className = retracedClass,
                hasClassRetraceResult = classMapping != null,
                methodSignature = null,
                sourceFile = null,
                lineNumber = null,
                ambiguous = false,
            )
        return RetracedLineResult(
            alternatives = listOf(listOf(render(parsed, frame))),
            context = RetraceContext(thrownException = retracedClass),
        )
    }

    private fun retraceFieldLine(
        parsed: ParsedStackLine,
        classMapping: ClassMapping?,
        obfuscatedClass: String,
        fieldName: String,
        context: RetraceContext,
    ): RetracedLineResult {
        if (classMapping == null) {
            return RetracedLineResult.single(parsed.line, context)
        }
        val fields = classMapping.fieldsByObfuscatedName[fieldName].orEmpty()
        if (fields.isEmpty()) {
            val frame =
                RenderFrame(
                    className = classMapping.originalName,
                    hasClassRetraceResult = true,
                    methodSignature = null,
                    fieldSignature = null,
                    sourceFile = sourceFileFor(classMapping.originalName, classMapping, parsed),
                    lineNumber = parsed.lineNumberValue(),
                    ambiguous = false,
                )
            return RetracedLineResult(listOf(listOf(render(parsed, frame))), context)
        }
        val alternatives =
            fields.map { field ->
                val holder = classMapping.originalName
                val frame =
                    RenderFrame(
                        className = holder,
                        hasClassRetraceResult = true,
                        methodSignature = null,
                        fieldSignature = field.originalSignature,
                        sourceFile = sourceFileFor(holder, classMapping, parsed),
                        lineNumber = parsed.lineNumberValue(),
                        ambiguous = fields.size > 1,
                    )
                listOf(render(parsed, frame))
            }
        return RetracedLineResult(alternatives.sortedBy { it.joinToString("\n") }, context)
    }

    private fun retraceMethodLine(
        parsed: ParsedStackLine,
        classMapping: ClassMapping?,
        obfuscatedClass: String,
        methodName: String,
        context: RetraceContext,
    ): RetracedLineResult {
        if (classMapping == null) {
            return RetracedLineResult.single(parsed.line, context)
        }
        val lineNumber = parsed.lineNumberValue()
        val methods = classMapping.methodsByObfuscatedName[methodName].orEmpty()
        if (methods.isEmpty()) {
            val frame =
                RenderFrame(
                    className = classMapping.originalName,
                    hasClassRetraceResult = true,
                    methodSignature = MethodSignature(methodName, "void", emptyList()),
                    sourceFile = sourceFileFor(classMapping.originalName, classMapping, parsed),
                    lineNumber = lineNumber,
                    ambiguous = false,
                )
            return RetracedLineResult(listOf(listOf(render(parsed, frame))), context)
        }
        val selected = selectGroups(methods, lineNumber, context)
        if (selected.groups.isEmpty()) {
            val frame =
                RenderFrame(
                    className = classMapping.originalName,
                    hasClassRetraceResult = true,
                    methodSignature = MethodSignature(methodName, "void", emptyList()),
                    sourceFile = sourceFileFor(classMapping.originalName, classMapping, parsed),
                    lineNumber = lineNumber,
                    ambiguous = false,
                )
            return RetracedLineResult(listOf(listOf(render(parsed, frame))), RetraceContext.EMPTY)
        }
        val allAlternatives =
            selected.groups.flatMap { group ->
                expandAmbiguousOriginalLines(group, selected.position).map { forcedLine ->
                    framesFromGroup(classMapping, parsed, group, selected.position, forcedLine, context)
                }
            }

        val ambiguous = allAlternatives.size > 1
        val alternatives =
            allAlternatives.map { alternative ->
                alternative.frames.map { frame ->
                    render(parsed, frame.copy(ambiguous = ambiguous))
                }
            }.distinct().sortedBy { it.joinToString("\n") }

        val resultContext =
            allAlternatives.map { it.context }.distinct().singleOrNull() ?: RetraceContext.EMPTY
        return RetracedLineResult(alternatives, resultContext)
    }

    private fun selectGroups(
        methods: List<MethodMapping>,
        lineNumber: Int?,
        context: RetraceContext,
    ): SelectedGroups {
        val groups = groupInlineFrames(methods)
        if (lineNumber != null && lineNumber > 0) {
            val direct = groups.filter { group -> group.first().minifiedRange?.contains(lineNumber) == true }
            if (direct.isNotEmpty()) {
                if (context.rewritePosition != null) {
                    direct.forEach { group ->
                        val outlineCallsite = group.last().outlineCallsite
                        if (outlineCallsite != null) {
                            val rewritten = outlineCallsite.rewritePosition(context.rewritePosition)
                            val rewrittenSelection = selectGroups(methods, rewritten, context.copy(rewritePosition = null))
                            if (rewrittenSelection.groups.isNotEmpty()) return rewrittenSelection
                        }
                    }
                }
                return SelectedGroups(direct, lineNumber)
            }
            val noRange = groups.filter { it.first().minifiedRange == null }
            if (noRange.isNotEmpty()) return SelectedGroups(noRange, lineNumber)
        } else {
            val noRange = groups.filter { it.first().minifiedRange == null }
            if (noRange.isNotEmpty()) return SelectedGroups(noRange, lineNumber)
        }
        return SelectedGroups(groups.take(1), lineNumber)
    }

    private fun groupInlineFrames(methods: List<MethodMapping>): List<List<MethodMapping>> {
        if (methods.isEmpty()) return emptyList()
        val groups = mutableListOf<List<MethodMapping>>()
        var index = 0
        while (index < methods.size) {
            val first = methods[index]
            val group = mutableListOf(first)
            index += 1
            while (index < methods.size && methods[index].minifiedRange == first.minifiedRange) {
                group += methods[index]
                index += 1
            }
            groups += group
        }
        return groups
    }

    private fun expandAmbiguousOriginalLines(group: List<MethodMapping>, lineNumber: Int?): List<Int?> {
        val top = group.firstOrNull() ?: return listOf(null)
        val originalRange = top.originalRange ?: return listOf(null)
        val minifiedRange = top.minifiedRange
        val isAmbiguous =
            originalRange.span() > 1 && (minifiedRange == null || minifiedRange.span() != originalRange.span())
        if (!isAmbiguous) return listOf(null)
        return (originalRange.from..originalRange.to).toList()
    }

    private fun framesFromGroup(
        classMapping: ClassMapping,
        parsed: ParsedStackLine,
        group: List<MethodMapping>,
        lineNumber: Int?,
        forcedTopOriginalLine: Int?,
        context: RetraceContext,
    ): FramesWithContext {
        val rewriteState =
            group.fold(RewriteState()) { state, mapping ->
                val rewriteFrame = mapping.rewriteFrame
                if (rewriteFrame != null && rewriteFrame.matches(context)) {
                    rewriteFrame.actions.fold(state) { current, action -> action.apply(current) }
                } else {
                    state
                }
            }
        val candidates =
            group.mapIndexed { index, mapping ->
                val holder = holderFor(mapping.originalSignature, classMapping)
                val sourceFile = sourceFileFor(holder, classMapping, parsed)
                val originalLine =
                    if (index == 0 && forcedTopOriginalLine != null) {
                        forcedTopOriginalLine
                    } else {
                        originalLineNumber(mapping, lineNumber)
                    }
                RenderFrame(
                    className = holder,
                    hasClassRetraceResult = true,
                    methodSignature = unqualified(mapping.originalSignature),
                    sourceFile = sourceFile,
                    lineNumber = originalLine,
                    ambiguous = false,
                )
            }

        val rewritten = mutableListOf<RenderFrame>()
        var remove = rewriteState.removeInnerFramesCount
        var previous = candidates.first()
        candidates.drop(1).forEach { next ->
            if (remove-- <= 0) {
                rewritten += previous
            }
            previous = next
        }
        if (remove <= 0 && !group.last().synthesized) {
            rewritten += previous
        }
        val resultContext =
            if (lineNumber != null && group.last().outline) {
                RetraceContext(rewritePosition = lineNumber)
            } else {
                RetraceContext.EMPTY
            }
        return FramesWithContext(rewritten, resultContext)
    }

    private fun holderFor(signature: MethodSignature, classMapping: ClassMapping): String {
        val holderSeparator = signature.name.lastIndexOf('.')
        return if (holderSeparator >= 0) signature.name.substring(0, holderSeparator) else classMapping.originalName
    }

    private fun unqualified(signature: MethodSignature): MethodSignature {
        val holderSeparator = signature.name.lastIndexOf('.')
        return if (holderSeparator >= 0) signature.copy(name = signature.name.substring(holderSeparator + 1)) else signature
    }

    private fun originalLineNumber(mapping: MethodMapping, obfuscatedLine: Int?): Int? {
        if (obfuscatedLine == null || obfuscatedLine <= 0) return null
        val minifiedRange = mapping.minifiedRange ?: return obfuscatedLine
        val originalRange = mapping.originalRange ?: return obfuscatedLine
        if (originalRange.from == originalRange.to) return originalRange.from
        return originalRange.from + obfuscatedLine - minifiedRange.from
    }

    private fun sourceFileFor(holder: String, currentClass: ClassMapping, parsed: ParsedStackLine): String? {
        val mappedSource =
            mappingIndex.classes.values.firstOrNull { it.originalName == holder }?.sourceFile
                ?: currentClass.sourceFile
        if (mappedSource != null) return mappedSource
        val originalSource = parsed.sourceFileValue() ?: return null
        return TypeNames.inferSourceFile(holder, originalSource, hasRetraceResult = true)
    }

    private fun render(parsed: ParsedStackLine, frame: RenderFrame): String {
        val replacements =
            parsed.orderedTokens.associateWith { token ->
                replacementFor(parsed, token, frame)
            }
        val builder = StringBuilder()
        var last = 0
        parsed.orderedTokens.forEach { token ->
            builder.append(parsed.line, last, token.start)
            builder.append(replacements[token] ?: token.value(parsed.line))
            last = token.end
        }
        builder.append(parsed.line, last, parsed.line.length)
        return builder.toString()
    }

    private fun replacementFor(parsed: ParsedStackLine, token: LineToken, frame: RenderFrame): String =
        when (token) {
            parsed.className ->
                frame.className?.let {
                    if (parsed.className.classNameType == ClassNameType.BINARY) {
                        TypeNames.typeToBinaryName(it)
                    } else {
                        it
                    }
                } ?: token.value(parsed.line)
            parsed.methodName -> frame.methodSignature?.let { methodDescription(it) } ?: token.value(parsed.line)
            parsed.fieldName -> frame.fieldSignature?.let { fieldDescription(it) } ?: token.value(parsed.line)
            parsed.sourceFile -> frame.sourceFile ?: token.value(parsed.line)
            parsed.lineNumber -> lineNumberReplacement(parsed, token as LineNumberToken, frame)
            parsed.fieldOrReturnType -> TypeNames.retraceType(token.value(parsed.line), mappingIndex)
            parsed.methodArguments -> parsed.methodArgumentsValue()?.joinToString(",") {
                TypeNames.retraceType(it, mappingIndex)
            } ?: token.value(parsed.line)
            else -> token.value(parsed.line)
        }

    private fun methodDescription(signature: MethodSignature): String {
        if (!config.verbose) return signature.name
        val returnType = TypeNames.retraceType(signature.type, mappingIndex)
        val parameters = signature.parameters.joinToString(",") { TypeNames.retraceType(it, mappingIndex) }
        return "$returnType ${signature.name}($parameters)"
    }

    private fun fieldDescription(signature: FieldSignature): String {
        if (!config.verbose) return signature.name
        return "${TypeNames.retraceType(signature.type, mappingIndex)} ${signature.name}"
    }

    private fun lineNumberReplacement(
        parsed: ParsedStackLine,
        token: LineNumberToken,
        frame: RenderFrame,
    ): String {
        val lineNumber = frame.lineNumber
        val originalLine = parsed.lineNumberValue()
        if (lineNumber != null && (originalLine != null || !frame.ambiguous || config.verbose)) {
            return if (lineNumber <= 0) "" else (if (token.insertSeparator) ":" else "") + lineNumber
        }
        return token.value(parsed.line)
    }

    private fun joinAmbiguousLines(alternatives: List<List<String>>): List<String> {
        if (alternatives.isEmpty()) return emptyList()
        if (alternatives.size == 1) return alternatives.first()
        val result = mutableListOf<String>()
        var lineIndex = 0
        while (true) {
            var added = false
            val reported = mutableSetOf<String>()
            alternatives.forEachIndexed { alternativeIndex, lines ->
                if (lineIndex < lines.size) {
                    added = true
                    val line = lines[lineIndex]
                    if (reported.add(line)) {
                        result += if (alternativeIndex == 0) line else insertOr(line)
                    }
                }
            }
            if (!added) return result
            lineIndex += 1
        }
    }

    private fun insertOr(line: String): String {
        var index = line.indexOf("at ")
        if (index < 0) index = line.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: 0
        return line.substring(0, index) + "<OR> " + line.substring(index)
    }

    private data class SelectedGroups(
        val groups: List<List<MethodMapping>>,
        val position: Int?,
    )

    private data class FramesWithContext(
        val frames: List<RenderFrame>,
        val context: RetraceContext,
    )

    private data class RenderFrame(
        val className: String?,
        val hasClassRetraceResult: Boolean,
        val methodSignature: MethodSignature? = null,
        val fieldSignature: FieldSignature? = null,
        val sourceFile: String? = null,
        val lineNumber: Int? = null,
        val ambiguous: Boolean,
    )

    private data class RetracedLineResult(
        val alternatives: List<List<String>>,
        val context: RetraceContext,
    ) {
        companion object {
            fun single(line: String, context: RetraceContext): RetracedLineResult =
                RetracedLineResult(listOf(listOf(line)), context)
        }
    }
}
