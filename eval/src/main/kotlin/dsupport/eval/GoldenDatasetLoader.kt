package dsupport.eval

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

class GoldenDatasetLoader {
    fun load(file: Path): List<GoldenCase> =
        Json.decodeFromString(ListSerializer(GoldenCase.serializer()), Files.readString(file))
}
