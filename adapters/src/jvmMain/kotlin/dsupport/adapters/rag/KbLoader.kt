package dsupport.adapters.rag

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

class KbLoader(private val chunker: MarkdownChunker = MarkdownChunker()) {

    fun load(dir: Path): List<Chunk> {
        val files = Files.list(dir).use { stream -> stream.filter { it.extension == "md" }.sorted().toList() }
        return files.flatMap { chunker.chunk(it.nameWithoutExtension, Files.readString(it)) }
    }
}
