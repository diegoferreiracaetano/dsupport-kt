package dsupport.adapters.rag

/** Splits an article into one chunk per `## ` section, each prefixed with the article title. */
class MarkdownChunker {

    fun chunk(docId: String, text: String): List<Chunk> {
        val lines = text.lines()
        val title = lines.firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim() ?: docId
        val sections = mutableListOf(mutableListOf<String>())
        for (line in lines) {
            if (line.startsWith("## ")) sections += mutableListOf(line.removePrefix("## ").trim()) else sections.last() += line
        }
        return sections.mapIndexedNotNull { index, section ->
            val content = if (index == 0) introOf(section) else sectionOf(title, section)
            content?.let { Chunk("$docId#$index", docId, title, it) }
        }
    }

    private fun introOf(lines: List<String>): String? {
        val body = lines.filterNot { it.startsWith("# ") }.joinToString("\n").trim()
        return if (body.isEmpty()) null else lines.joinToString("\n").trim()
    }

    private fun sectionOf(title: String, lines: List<String>): String? {
        val body = lines.drop(1).joinToString("\n").trim()
        return if (body.isEmpty()) null else "$title — ${lines.first()}\n$body"
    }
}
