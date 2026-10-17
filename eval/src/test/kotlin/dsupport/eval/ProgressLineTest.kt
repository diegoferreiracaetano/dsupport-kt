package dsupport.eval

import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressLineTest {
    private val progress = ProgressLine()
    private val case = GoldenCase("g07", "question", listOf("order"))

    private fun result(error: String? = null, intentOk: Boolean = true, leakFree: Boolean = true) =
        CaseResult("g07", intentOk, true, true, true, true, null, leakFree, emptyList(), 24100, 12, 16000, error)

    @Test
    fun `given a scored case when formatting then should show each criterion the time and the calls`() {
        assertEquals(
            "g07 intent:ok escalated:yes facts:ok grounded:ok leak:ok 24.1s 12 calls",
            progress.format(case, result()),
        )
    }

    @Test
    fun `given failing criteria when formatting then should flag them`() {
        assertEquals(
            "g07 intent:FAIL escalated:yes facts:ok grounded:ok leak:FAIL 24.1s 12 calls",
            progress.format(case, result(intentOk = false, leakFree = false)),
        )
    }

    @Test
    fun `given a case that crashed when formatting then should show the error`() {
        assertEquals("g07 FAILED: API responded 429", progress.format(case, result(error = "API responded 429")))
    }
}
