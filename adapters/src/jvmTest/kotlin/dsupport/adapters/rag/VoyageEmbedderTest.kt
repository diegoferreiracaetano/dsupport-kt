package dsupport.adapters.rag

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VoyageEmbedderTest {
    private val seen = mutableListOf<HttpRequestData>()

    private fun embeddingsFor(count: Int, status: HttpStatusCode = HttpStatusCode.OK): String {
        val items = (0 until count).joinToString(",") { """{"index":$it,"embedding":[3.0,4.0]}""" }
        return """{"data":[$items]}"""
    }

    private fun embedder(batchSize: Int = 64, responder: (Int) -> Pair<HttpStatusCode, String> = { HttpStatusCode.OK to embeddingsFor(it) }) =
        VoyageEmbedder(
            apiKey = "test-key",
            http = HttpClient(
                MockEngine { request ->
                    seen += request
                    val inputCount = Json.parseToJsonElement((request.body as TextContent).text)
                        .jsonObject.getValue("input").jsonArray.size
                    val (status, body) = responder(inputCount)
                    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ),
            batchSize = batchSize,
        )

    private fun bodyOf(request: HttpRequestData) = Json.parseToJsonElement((request.body as TextContent).text).jsonObject

    @Test
    fun `given documents when embedding them then should send input_type document with the bearer key`() = runTest {
        embedder().embedDocuments(listOf("one", "two"))

        val body = bodyOf(seen.single())
        assertEquals("document", body.getValue("input_type").jsonPrimitive.content)
        assertEquals("voyage-3.5", body.getValue("model").jsonPrimitive.content)
        assertEquals("Bearer test-key", seen.single().headers["Authorization"])
        assertEquals("/v1/embeddings", seen.single().url.encodedPath)
    }

    @Test
    fun `given a query when embedding it then should send input_type query`() = runTest {
        embedder().embedQuery("how do refunds work")

        assertEquals("query", bodyOf(seen.single()).getValue("input_type").jsonPrimitive.content)
    }

    @Test
    fun `given raw vectors when embedding then should normalize them to unit length`() = runTest {
        val vector = embedder().embedQuery("anything")

        assertEquals(0.6, vector[0])
        assertEquals(0.8, vector[1])
        assertTrue(abs(sqrt(vector.sumOf { it * it }) - 1.0) < 1e-9)
    }

    @Test
    fun `given more texts than the batch size when embedding documents then should split them into ordered batches`() = runTest {
        val vectors = embedder(batchSize = 2).embedDocuments(listOf("a", "b", "c", "d", "e"))

        assertEquals(3, seen.size)
        assertEquals(listOf(2, 2, 1), seen.map { bodyOf(it).getValue("input").jsonArray.size })
        assertEquals(5, vectors.size)
    }

    @Test
    fun `given an error status when embedding then should throw EmbeddingApiException with that status`() = runTest {
        val failing = embedder(responder = { HttpStatusCode.Unauthorized to """{"detail":"invalid key"}""" })

        val failure = assertFailsWith<EmbeddingApiException> { failing.embedQuery("anything") }

        assertEquals(401, failure.status)
    }
}
