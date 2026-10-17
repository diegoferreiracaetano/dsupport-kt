package dsupport.adapters.rag

import dsupport.core.port.Hit
import dsupport.core.port.KnowledgeSearch
import kotlin.math.min
import kotlin.math.round

/** An in-memory vector store. Vectors are unit length, so the dot product is the cosine similarity. */
class VectorKnowledgeBase private constructor(
    private val embedder: Embedder,
    private val entries: List<Pair<Chunk, DoubleArray>>,
) : KnowledgeSearch {

    override suspend fun search(query: String, k: Int): List<Hit> {
        val queryVector = embedder.embedQuery(query)
        return entries
            .map { (chunk, vector) -> chunk to similarity(queryVector, vector) }
            .sortedByDescending { it.second }
            .take(k)
            .map { (chunk, score) -> Hit(chunk.docId, chunk.title, chunk.text, round(score * 1000) / 1000.0) }
    }

    private fun similarity(a: DoubleArray, b: DoubleArray): Double =
        (0 until min(a.size, b.size)).sumOf { a[it] * b[it] }

    companion object {
        suspend fun create(embedder: Embedder, chunks: List<Chunk>): VectorKnowledgeBase {
            val vectors = embedder.embedDocuments(chunks.map { it.text })
            return VectorKnowledgeBase(embedder, chunks.zip(vectors))
        }
    }
}
