package dsupport.core.port

data class Hit(val docId: String, val title: String, val snippet: String, val score: Double)

interface KnowledgeSearch {
    /** Returns the [k] most relevant chunks for [query], best first. Scores go from 0 (unrelated) to 1 (identical). */
    suspend fun search(query: String, k: Int = 3): List<Hit>
}
