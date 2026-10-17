package dsupport.eval

import dsupport.contract.INTENTS
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoldenDatasetLoaderTest {
    private val loader = GoldenDatasetLoader()
    private val golden = Path.of("..", "data", "golden.json")

    @Test
    fun `given only the required fields when loading a case then should default the optional ones`() {
        val file = createTempFile(suffix = ".json")
        file.writeText("""[{"id":"x1","question":"Hi?","expected_intents":["other"]}]""")

        val case = loader.load(file).single()

        assertNull(case.shouldEscalate)
        assertEquals(emptyList(), case.mustContain)
        assertEquals(emptyList(), case.mustNotContain)
        assertEquals(emptyList(), case.expectedSources)
    }

    @Test
    fun `given all the snake case fields when loading a case then should map them`() {
        val file = createTempFile(suffix = ".json")
        file.writeText(
            """[{"id":"x1","question":"Q","expected_intents":["order"],"should_escalate":true,""" +
                """"must_contain":["a"],"must_not_contain":["b"],"expected_sources":["c"]}]""",
        )

        val case = loader.load(file).single()

        assertEquals(true, case.shouldEscalate)
        assertEquals(listOf("a"), case.mustContain)
        assertEquals(listOf("b"), case.mustNotContain)
        assertEquals(listOf("c"), case.expectedSources)
    }

    @Test
    fun `given the project golden dataset when loading it then should have 15 cases with unique ids`() {
        val cases = loader.load(golden)

        assertEquals(15, cases.size)
        assertEquals(15, cases.map { it.id }.toSet().size)
    }

    @Test
    fun `given the project golden dataset when checking intents then should only use the supported intents`() {
        val cases = loader.load(golden)

        assertTrue(cases.all { case -> case.expectedIntents.isNotEmpty() && case.expectedIntents.all { it in INTENTS } })
    }

    @Test
    fun `given the project golden dataset when checking order numbers then should only mention orders that exist`() {
        val orders = Files.readString(Path.of("..", "data", "orders.json"))

        val mentioned = loader.load(golden).flatMap { Regex("""\b1\d{3}\b""").findAll(it.question).map { m -> m.value }.toList() }

        assertTrue(mentioned.isNotEmpty())
        assertTrue(mentioned.all { "\"order_id\": \"$it\"" in orders }, "unknown order in $mentioned")
    }

    @Test
    fun `given the project golden dataset when checking expected sources then should only cite titles of real articles`() {
        val titles = Files.list(Path.of("..", "data", "kb")).use { files ->
            files.map { Files.readAllLines(it).first().removePrefix("# ") }.toList()
        }

        val expected = loader.load(golden).flatMap { it.expectedSources }

        assertTrue(expected.isNotEmpty())
        assertTrue(expected.all { it in titles }, "unknown source in $expected, known: $titles")
    }

    @Test
    fun `given the project golden dataset when looking for judgement calls then should leave the identity mismatch case unjudged for escalation`() {
        val identity = loader.load(golden).single { it.id == "g11" }

        assertNull(identity.shouldEscalate)
        assertTrue(identity.mustNotContain.isNotEmpty())
    }
}
