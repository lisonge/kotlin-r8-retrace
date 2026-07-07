package li.songe.retrace

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import li.songe.json5.Json5

internal object MappingInfoParser {
    fun parse(line: String): MappingInfo? {
        val jsonStart = line.indexOf('{')
        if (jsonStart < 0) return null
        val payload = line.substring(jsonStart)
        val root =
            runCatching { Json5.parseToJson5Element(payload).jsonObject }
                .getOrNull()
                ?: return null
        val id = root.string("id") ?: return null
        return when (id) {
            "com.android.tools.r8.mapping" ->
                root.string("version")?.let(MappingInfo::MapVersion) ?: MappingInfo.Unknown(id)
            "sourceFile" ->
                root.string("fileName")?.let(MappingInfo::SourceFile) ?: MappingInfo.Unknown(id)
            "com.android.tools.r8.synthesized" -> MappingInfo.CompilerSynthesized
            "com.android.tools.r8.outline" -> MappingInfo.Outline
            "com.android.tools.r8.outlineCallsite" -> parseOutlineCallsite(root) ?: MappingInfo.Unknown(id)
            "com.android.tools.r8.rewriteFrame" -> parseRewriteFrame(root) ?: MappingInfo.Unknown(id)
            "com.android.tools.r8.residualsignature" ->
                root.string("signature")?.let(MappingInfo::ResidualSignature) ?: MappingInfo.Unknown(id)
            else -> MappingInfo.Unknown(id)
        }
    }

    private fun parseOutlineCallsite(root: JsonObject): MappingInfo.OutlineCallsite? {
        val positionsObject = root["positions"] as? JsonObject ?: return null
        val positions =
            positionsObject.mapNotNull { (key, value) ->
                val from = key.toIntOrNull()
                val to = value.primitiveOrNull()?.intOrNull
                if (from == null || to == null) null else from to to
            }.toMap()
        return MappingInfo.OutlineCallsite(
            OutlineCallsiteInfo(
                positions = positions,
                outline = root.string("outline"),
            ),
        )
    }

    private fun parseRewriteFrame(root: JsonObject): MappingInfo.RewriteFrame? {
        val conditions = root.array("conditions")?.mapNotNull(::parseCondition) ?: emptyList()
        val actions = root.array("actions")?.mapNotNull(::parseAction) ?: emptyList()
        return MappingInfo.RewriteFrame(RewriteFrameInfo(conditions, actions))
    }

    private fun parseCondition(element: JsonElement): RewriteCondition? {
        val value = element.primitiveOrNull()?.contentOrNull ?: return null
        val content = value.functionContent("throws") ?: return null
        val typeName = TypeNames.descriptorToTypeName(content) ?: return null
        return RewriteCondition.Throws(typeName)
    }

    private fun parseAction(element: JsonElement): RewriteAction? {
        val value = element.primitiveOrNull()?.contentOrNull ?: return null
        val removeCount = value.functionContent("removeInnerFrames")?.toIntOrNull()
        return removeCount?.let(RewriteAction::RemoveInnerFrames)
    }

    private fun String.functionContent(name: String): String? {
        val prefix = "$name("
        if (!startsWith(prefix) || !endsWith(")")) return null
        return substring(prefix.length, length - 1)
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.primitiveOrNull()?.contentOrNull

    private fun JsonObject.array(key: String): JsonArray? =
        runCatching { this[key]?.jsonArray }.getOrNull()

    private fun JsonElement.primitiveOrNull(): JsonPrimitive? =
        runCatching { jsonPrimitive }.getOrNull()
}
