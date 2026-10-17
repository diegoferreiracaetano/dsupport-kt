package dsupport.core.tools

import dsupport.core.port.KnowledgeSearch
import dsupport.core.tool.Tool
import dsupport.core.tool.ToolResult
import dsupport.core.tool.ToolSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SearchKbTool(
    private val kb: KnowledgeSearch,
    private val minScore: Double = 0.25,
) : Tool {

    override val spec = ToolSpec(
        name = "search_kb",
        description = "Searches the help center articles. Use it for questions about policies, deadlines, returns, " +
            "refunds, shipping, payments, warranty, cancellation, privacy and the customer account.",
        inputSchema = stringSchema("query"),
    )

    override suspend fun run(args: JsonObject): ToolResult {
        val query = args.stringArg("query") ?: return fail("missing_argument:query")
        val relevant = kb.search(query).filter { it.score >= minScore }
        if (relevant.isEmpty()) return fail("nothing_relevant_found")
        val results = relevant.map {
            buildJsonObject {
                put("doc_id", it.docId)
                put("title", it.title)
                put("snippet", it.snippet)
                put("score", it.score)
            }
        }
        return ok("results" to JsonArray(results))
    }
}
