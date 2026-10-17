package dsupport.adapters.llm

import dsupport.core.llm.LlmClient
import dsupport.core.llm.LlmReply
import dsupport.core.llm.LlmRequest
import dsupport.core.llm.OutputSchema
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

private const val API_VERSION = "2023-06-01"
private const val BASE_DELAY_MS = 500L

/**
 * [LlmClient] backed by the Anthropic Messages API over plain HTTP.
 * Thinking is always disabled because the neutral history does not carry provider reasoning blocks.
 * Rate limits (429) and server errors (5xx) are retried up to [maxRetries] times with a growing delay.
 */
class AnthropicLlmClient(
    private val apiKey: String,
    private val modelId: String = "claude-haiku-5-5",
    private val http: HttpClient,
    private val maxTokens: Int = 4096,
    private val maxRetries: Int = 2,
    private val baseUrl: String = "https://api.anthropic.com",
) : LlmClient {

    override suspend fun next(request: LlmRequest): LlmReply =
        parseReply(send(buildRequestBody(modelId, maxTokens, request)))

    override suspend fun structured(request: LlmRequest, schema: OutputSchema): JsonObject =
        parseStructured(send(buildRequestBody(modelId, maxTokens, request, schema)))

    private suspend fun send(body: JsonObject): JsonObject {
        var attempt = 0
        while (true) {
            val response = http.post("$baseUrl/v1/messages") {
                header("x-api-key", apiKey)
                header("anthropic-version", API_VERSION)
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
            val status = response.status.value
            val text = response.bodyAsText()
            if (status in 200..299) return parseBody(status, text)
            if (isRetryable(status) && attempt < maxRetries) {
                delay(BASE_DELAY_MS shl attempt)
                attempt++
                continue
            }
            throw LlmApiException(status, "Anthropic API responded $status: $text")
        }
    }

    private fun parseBody(status: Int, text: String): JsonObject = try {
        Json.parseToJsonElement(text).jsonObject
    } catch (e: SerializationException) {
        throw LlmApiException(status, "Anthropic API returned a body that is not JSON: ${e.message}")
    } catch (e: IllegalArgumentException) {
        throw LlmApiException(status, "Anthropic API returned a body that is not a JSON object: ${e.message}")
    }

    private fun isRetryable(status: Int) = status == 429 || status >= 500
}
