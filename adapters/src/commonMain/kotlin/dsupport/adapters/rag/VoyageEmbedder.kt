package dsupport.adapters.rag

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.math.sqrt

class EmbeddingApiException(val status: Int?, message: String) : Exception(message)

/** Semantic embeddings from the Voyage AI API. Texts are sent in batches and the vectors come back normalized. */
class VoyageEmbedder(
    private val apiKey: String,
    private val http: HttpClient,
    private val model: String = "voyage-3.5",
    private val batchSize: Int = 64,
    private val baseUrl: String = "https://api.voyageai.com",
) : Embedder {

    override suspend fun embedDocuments(texts: List<String>): List<DoubleArray> =
        texts.chunked(batchSize).flatMap { embed(it, "document") }

    override suspend fun embedQuery(text: String): DoubleArray = embed(listOf(text), "query").single()

    private suspend fun embed(texts: List<String>, inputType: String): List<DoubleArray> {
        val body = buildJsonObject {
            put("input", JsonArray(texts.map { JsonPrimitive(it) }))
            put("model", model)
            put("input_type", inputType)
        }
        val response = http.post("$baseUrl/v1/embeddings") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val status = response.status.value
        val text = response.bodyAsText()
        if (status !in 200..299) throw EmbeddingApiException(status, "Voyage API responded $status: $text")
        return try {
            Json.parseToJsonElement(text).jsonObject.getValue("data").jsonArray
                .map { it.jsonObject }
                .sortedBy { it.getValue("index").jsonPrimitive.int }
                .map { item -> normalize(item.getValue("embedding").jsonArray.map { it.jsonPrimitive.double }.toDoubleArray()) }
        } catch (e: SerializationException) {
            throw EmbeddingApiException(status, "Voyage API returned an unexpected body: ${e.message}")
        } catch (e: NoSuchElementException) {
            throw EmbeddingApiException(status, "Voyage API response has no data: ${e.message}")
        }
    }

    private fun normalize(vector: DoubleArray): DoubleArray {
        val norm = sqrt(vector.sumOf { it * it })
        return if (norm > 0) DoubleArray(vector.size) { vector[it] / norm } else vector
    }
}
