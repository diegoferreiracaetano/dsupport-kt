package dsupport.core.tools

import dsupport.core.tool.ToolResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun ok(vararg fields: Pair<String, JsonElement>): ToolResult = ToolResult(
    buildJsonObject {
        put("ok", true)
        fields.forEach { (name, value) -> put(name, value) }
    }.toString(),
)

internal fun fail(error: String): ToolResult = ToolResult(
    buildJsonObject {
        put("ok", false)
        put("error", error)
    }.toString(),
)

internal fun JsonObject.stringArg(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

internal fun stringSchema(vararg names: String): JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject { names.forEach { put(it, buildJsonObject { put("type", "string") }) } })
    put("required", JsonArray(names.map { JsonPrimitive(it) }))
    put("additionalProperties", false)
}
