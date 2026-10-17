package dsupport.adapters.rag

private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")

private val ACCENTS: Map<Char, Char> = buildMap {
    mapOf(
        "àáâãäå" to 'a', "ç" to 'c', "èéêë" to 'e', "ìíîï" to 'i', "ñ" to 'n',
        "òóôõö" to 'o', "ùúûü" to 'u', "ýÿ" to 'y',
    ).forEach { (accented, plain) -> accented.forEach { put(it, plain) } }
}

private val STOP_WORDS = setOf(
    "the", "an", "and", "or", "of", "to", "in", "on", "for", "with", "is", "are", "was", "were", "be", "been",
    "it", "its", "this", "that", "these", "those", "my", "your", "our", "their", "you", "we", "they", "he", "she",
    "do", "does", "did", "can", "could", "will", "would", "should", "how", "what", "when", "where", "which", "who",
    "why", "if", "at", "by", "from", "as", "about", "than", "then", "there", "here", "not", "no", "yes", "have",
    "has", "had", "please", "any", "all", "some", "me", "am", "so", "up", "out", "get", "after", "before",
    "order", "orders", "product", "products", "item", "items", "purchase", "customer",
)

/** Folds text to lowercase ASCII and turns it into stemmed tokens without stop words. */
class TextNormalizer {

    fun fold(text: String): String = buildString {
        for (character in text.lowercase()) append(ACCENTS[character] ?: character)
    }

    fun tokens(text: String): List<String> =
        fold(text).split(NON_ALPHANUMERIC).filter { it.length >= 2 && it !in STOP_WORDS }.map(::stem)

    private fun stem(word: String): String {
        var token = when {
            word.endsWith("ies") && word.length > 4 -> word.dropLast(3) + "y"
            word.endsWith("sses") -> word.dropLast(2)
            word.endsWith("s") && !word.endsWith("ss") && word.length > 3 -> word.dropLast(1)
            else -> word
        }
        token = when {
            token.endsWith("ment") && token.length > 6 -> token.dropLast(4)
            token.endsWith("ing") && token.length > 5 -> undouble(token.dropLast(3))
            token.endsWith("ed") && token.length > 4 -> undouble(token.dropLast(2))
            else -> token
        }
        return if (token.endsWith("y") && token.length > 4) token.dropLast(1) else token
    }

    private fun undouble(token: String): String =
        if (token.length > 2 && token.last() == token[token.lastIndex - 1] && token.last() !in "lsz") token.dropLast(1) else token
}
