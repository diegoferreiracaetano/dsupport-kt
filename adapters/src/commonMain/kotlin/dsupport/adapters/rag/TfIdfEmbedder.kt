package dsupport.adapters.rag

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * A local embedder with no model and no key: every word of the corpus is a dimension, weighted by how often it
 * appears in the text (TF) and how rare it is in the corpus (IDF). It only matches similar words, not synonyms.
 * The vocabulary is learned by [embedDocuments], so queries embedded before that produce an empty vector.
 */
class TfIdfEmbedder(private val normalizer: TextNormalizer = TextNormalizer()) : Embedder {
    private var vocabulary: Map<String, Int> = emptyMap()
    private var idf = DoubleArray(0)

    override suspend fun embedDocuments(texts: List<String>): List<DoubleArray> {
        val tokenized = texts.map { normalizer.tokens(it) }
        val documentSets = tokenized.map { it.toSet() }
        vocabulary = tokenized.flatten().distinct().sorted().withIndex().associate { (index, term) -> term to index }
        idf = DoubleArray(vocabulary.size)
        vocabulary.forEach { (term, index) ->
            val documentsWithTerm = documentSets.count { term in it }
            idf[index] = ln((1.0 + texts.size) / (1.0 + documentsWithTerm)) + 1.0
        }
        return tokenized.map(::vectorize)
    }

    override suspend fun embedQuery(text: String): DoubleArray = vectorize(normalizer.tokens(text))

    private fun vectorize(tokens: List<String>): DoubleArray {
        val vector = DoubleArray(vocabulary.size)
        tokens.forEach { token -> vocabulary[token]?.let { vector[it] += idf[it] } }
        val norm = sqrt(vector.sumOf { it * it })
        if (norm > 0) for (index in vector.indices) vector[index] /= norm
        return vector
    }
}
