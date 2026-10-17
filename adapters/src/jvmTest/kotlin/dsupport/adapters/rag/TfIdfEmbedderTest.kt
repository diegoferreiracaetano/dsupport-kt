package dsupport.adapters.rag

import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TfIdfEmbedderTest {
    private val embedder = TfIdfEmbedder()

    private fun DoubleArray.norm() = sqrt(sumOf { it * it })

    private fun dot(a: DoubleArray, b: DoubleArray) = a.indices.sumOf { a[it] * b[it] }

    @Test
    fun `given documents when embedding them then should produce unit vectors of the same size`() = runTest {
        val vectors = embedder.embedDocuments(listOf("refund policy for returns", "shipping costs and speeds"))

        assertEquals(1, vectors.map { it.size }.toSet().size)
        assertTrue(vectors.all { abs(it.norm() - 1.0) < 1e-9 })
    }

    @Test
    fun `given a rare term and a common term when embedding a query then should score the rare term document higher`() = runTest {
        val documents = embedder.embedDocuments(
            listOf("refund policy details", "shipping policy details", "warranty policy details"),
        )

        val query = embedder.embedQuery("refund")

        assertTrue(dot(query, documents[0]) > dot(query, documents[1]))
        assertTrue(dot(query, documents[0]) > dot(query, documents[2]))
    }

    @Test
    fun `given a query with only unknown words when embedding it then should produce a zero vector`() = runTest {
        embedder.embedDocuments(listOf("refund policy"))

        val query = embedder.embedQuery("xylophone")

        assertEquals(0.0, query.norm())
    }

    @Test
    fun `given no documents were embedded when embedding a query then should produce an empty vector`() = runTest {
        assertEquals(0, embedder.embedQuery("refund").size)
    }
}
