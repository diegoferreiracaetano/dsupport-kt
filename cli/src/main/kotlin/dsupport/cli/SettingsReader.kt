package dsupport.cli

import kotlinx.datetime.LocalDate
import java.nio.file.Path

class RuntimeSettings(
    val apiKey: String,
    val modelId: String,
    val dataDir: Path,
    val agentsFile: Path,
    val referenceDate: LocalDate?,
)

/** Reads the settings from environment variables. Optional ones that are blank count as not set. */
class SettingsReader {

    fun read(env: Map<String, String>, referenceDate: LocalDate? = null): RuntimeSettings {
        val apiKey = env.value("ANTHROPIC_API_KEY")
            ?: throw IllegalArgumentException("Set ANTHROPIC_API_KEY (see .env.example).")
        return RuntimeSettings(
            apiKey = apiKey,
            modelId = env.value("ANTHROPIC_MODEL") ?: DEFAULT_MODEL,
            dataDir = Path.of(env.value("DATA_DIR") ?: "data"),
            agentsFile = Path.of("config", "agents.json"),
            referenceDate = referenceDate ?: env.value("REFERENCE_DATE")?.let(::parseDate),
        )
    }

    private fun Map<String, String>.value(name: String): String? = this[name]?.takeIf { it.isNotBlank() }

    private fun parseDate(text: String): LocalDate = try {
        LocalDate.parse(text)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("REFERENCE_DATE must be an ISO date like 2026-10-15, got '$text'.", e)
    }

    private companion object {
        const val DEFAULT_MODEL = "claude-haiku-5-5"
    }
}
