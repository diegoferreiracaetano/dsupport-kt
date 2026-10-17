package dsupport.cli

import kotlinx.datetime.LocalDate
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsReaderTest {
    private val reader = SettingsReader()
    private val minimal = mapOf("ANTHROPIC_API_KEY" to "test-key")

    @Test
    fun `given only the api key when reading then should use the defaults for the rest`() {
        val settings = reader.read(minimal)

        assertEquals("test-key", settings.apiKey)
        assertEquals("claude-haiku-5-5", settings.modelId)
        assertEquals(Path.of("data"), settings.dataDir)
        assertEquals(Path.of("config", "agents.json"), settings.agentsFile)
        assertNull(settings.referenceDate)
    }

    @Test
    fun `given every variable when reading then should use them`() {
        val settings = reader.read(
            minimal + mapOf("ANTHROPIC_MODEL" to "claude-sonnet-5-5", "DATA_DIR" to "somewhere", "REFERENCE_DATE" to "2026-10-15"),
        )

        assertEquals("claude-sonnet-5-5", settings.modelId)
        assertEquals(Path.of("somewhere"), settings.dataDir)
        assertEquals(LocalDate(2026, 10, 15), settings.referenceDate)
    }

    @Test
    fun `given a missing api key when reading then should fail telling which variable to set`() {
        val failure = assertFailsWith<IllegalArgumentException> { reader.read(emptyMap()) }

        assertTrue("ANTHROPIC_API_KEY" in failure.message.orEmpty())
    }

    @Test
    fun `given a blank api key when reading then should fail like a missing one`() {
        assertFailsWith<IllegalArgumentException> { reader.read(mapOf("ANTHROPIC_API_KEY" to "  ")) }
    }

    @Test
    fun `given blank optional variables when reading then should ignore them`() {
        val settings = reader.read(minimal + mapOf("ANTHROPIC_MODEL" to "", "REFERENCE_DATE" to " "))

        assertEquals("claude-haiku-5-5", settings.modelId)
        assertNull(settings.referenceDate)
    }

    @Test
    fun `given an invalid reference date when reading then should fail naming the variable`() {
        val failure = assertFailsWith<IllegalArgumentException> { reader.read(minimal + ("REFERENCE_DATE" to "tomorrow")) }

        assertTrue("REFERENCE_DATE" in failure.message.orEmpty())
    }

    @Test
    fun `given a reference date override when reading then should prefer it over the environment`() {
        val settings = reader.read(minimal + ("REFERENCE_DATE" to "2026-01-01"), referenceDate = LocalDate(2026, 10, 15))

        assertEquals(LocalDate(2026, 10, 15), settings.referenceDate)
    }
}
