package dsupport.adapters.rag

/** Turns text into vectors. Documents and queries are embedded separately because some providers treat them differently. */
interface Embedder {
    suspend fun embedDocuments(texts: List<String>): List<DoubleArray>
    suspend fun embedQuery(text: String): DoubleArray
}
