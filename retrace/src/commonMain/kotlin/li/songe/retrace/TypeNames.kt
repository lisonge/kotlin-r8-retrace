package li.songe.retrace

internal object TypeNames {
    private val primitiveDescriptors: Map<Char, String> =
        mapOf(
            'V' to "void",
            'Z' to "boolean",
            'B' to "byte",
            'S' to "short",
            'C' to "char",
            'I' to "int",
            'J' to "long",
            'F' to "float",
            'D' to "double",
        )

    private val primitiveNames: Set<String> =
        setOf("void", "boolean", "byte", "short", "char", "int", "long", "float", "double")

    fun descriptorToTypeName(descriptor: String): String? {
        if (descriptor.isEmpty()) return null
        var index = 0
        var dimensions = 0
        while (index < descriptor.length && descriptor[index] == '[') {
            dimensions += 1
            index += 1
        }
        if (index >= descriptor.length) return null
        val base =
            when (val marker = descriptor[index]) {
                'L' -> {
                    if (!descriptor.endsWith(";")) return null
                    descriptor.substring(index + 1, descriptor.length - 1).replace('/', '.')
                }
                in primitiveDescriptors.keys -> primitiveDescriptors[marker] ?: return null
                else -> return null
            }
        return base + "[]".repeat(dimensions)
    }

    fun parseMethodDescriptor(descriptor: String): Pair<List<String>, String>? {
        if (!descriptor.startsWith("(")) return null
        val endArguments = descriptor.indexOf(')')
        if (endArguments < 0) return null
        val parameters = mutableListOf<String>()
        var index = 1
        while (index < endArguments) {
            val parsed = parseDescriptorType(descriptor, index) ?: return null
            parameters += parsed.first
            index = parsed.second
        }
        val returnType = descriptorToTypeName(descriptor.substring(endArguments + 1)) ?: return null
        return parameters to returnType
    }

    private fun parseDescriptorType(descriptor: String, start: Int): Pair<String, Int>? {
        var index = start
        var dimensions = 0
        while (index < descriptor.length && descriptor[index] == '[') {
            dimensions += 1
            index += 1
        }
        if (index >= descriptor.length) return null
        val base: String
        val nextIndex: Int
        when (val marker = descriptor[index]) {
            'L' -> {
                val end = descriptor.indexOf(';', index)
                if (end < 0) return null
                base = descriptor.substring(index + 1, end).replace('/', '.')
                nextIndex = end + 1
            }
            in primitiveDescriptors.keys -> {
                base = primitiveDescriptors[marker] ?: return null
                nextIndex = index + 1
            }
            else -> return null
        }
        return base + "[]".repeat(dimensions) to nextIndex
    }

    fun retraceType(typeName: String, mapping: MappingIndex): String {
        if (typeName == "void") return typeName
        var base = typeName
        var dimensions = 0
        while (base.endsWith("[]")) {
            dimensions += 1
            base = base.dropLast(2)
        }
        if (base in primitiveNames) {
            return base + "[]".repeat(dimensions)
        }
        val retraced = mapping.classes[base]?.originalName ?: base
        return retraced + "[]".repeat(dimensions)
    }

    fun binaryToTypeName(binaryName: String): String = binaryName.replace('/', '.')

    fun typeToBinaryName(typeName: String): String = typeName.replace('.', '/')

    fun simpleOuterName(typeName: String): String {
        val packageEnd = typeName.lastIndexOf('.')
        val innerStart = typeName.indexOf('$', startIndex = packageEnd + 1)
        val end = if (innerStart >= 0) innerStart else typeName.length
        return typeName.substring(packageEnd + 1, end)
    }

    fun inferSourceFile(retracedClassName: String, originalSourceFile: String, hasRetraceResult: Boolean): String {
        if (!hasRetraceResult || originalSourceFile == "Native Method") return originalSourceFile
        val dot = originalSourceFile.lastIndexOf('.')
        var extension = if (dot >= 0) originalSourceFile.substring(dot + 1) else ""
        var fileName = simpleOuterName(retracedClassName)
        if (fileName.endsWith("Kt") && (extension.isEmpty() || extension == "kt")) {
            fileName = fileName.dropLast(2)
            extension = "kt"
        } else if (extension != "kt") {
            extension = "java"
        }
        return "$fileName.$extension"
    }
}

internal fun MappingInfo.ResidualSignature.asMethodSignature(renamedName: String): MethodSignature? {
    val parsed = TypeNames.parseMethodDescriptor(descriptor) ?: return null
    return MethodSignature(name = renamedName, type = parsed.second, parameters = parsed.first)
}

internal fun MappingInfo.ResidualSignature.asFieldSignature(renamedName: String): FieldSignature? {
    val typeName = TypeNames.descriptorToTypeName(descriptor) ?: return null
    if (typeName == "void") return null
    return FieldSignature(name = renamedName, type = typeName)
}
