package dsupport.eval

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EvalArgumentsParserTest {
    private val parser = EvalArgumentsParser()

    @Test
    fun `given no arguments when parsing then should run every case once`() {
        val arguments = parser.parse(emptyArray())

        assertEquals(emptySet(), arguments.caseIds)
        assertEquals(1, arguments.repeat)
    }

    @Test
    fun `given case ids when parsing then should keep only those cases`() {
        assertEquals(setOf("g04", "g12"), parser.parse(arrayOf("g04", "g12")).caseIds)
    }

    @Test
    fun `given a repeat flag when parsing then should read the number of runs per case`() {
        assertEquals(3, parser.parse(arrayOf("--repeat=3")).repeat)
    }

    @Test
    fun `given ids and a repeat flag in any order when parsing then should read both`() {
        val arguments = parser.parse(arrayOf("--repeat=2", "g07"))

        assertEquals(setOf("g07"), arguments.caseIds)
        assertEquals(2, arguments.repeat)
    }

    @Test
    fun `given a repeat that is zero or not a number when parsing then should fail naming the flag`() {
        listOf("--repeat=0", "--repeat=many", "--repeat=").forEach { flag ->
            val failure = assertFailsWith<IllegalArgumentException> { parser.parse(arrayOf(flag)) }
            assertTrue("--repeat" in failure.message.orEmpty(), "message was ${failure.message}")
        }
    }

    @Test
    fun `given an unknown flag when parsing then should fail naming it`() {
        val failure = assertFailsWith<IllegalArgumentException> { parser.parse(arrayOf("--fast")) }

        assertTrue("--fast" in failure.message.orEmpty())
    }
}
