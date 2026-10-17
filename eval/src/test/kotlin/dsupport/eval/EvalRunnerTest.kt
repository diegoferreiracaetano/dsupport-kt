package dsupport.eval

import dsupport.contract.SupportAnswer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvalRunnerTest {
    private val answerer = mockk<CaseAnswerer>()
    private val progress = mutableListOf<Pair<String, CaseResult>>()
    private val runner = EvalRunner(answerer, CaseScorer()) { case, result -> progress += case.id to result }

    private fun case(id: String, intents: List<String> = listOf("order")) =
        GoldenCase(id, "question $id", intents, shouldEscalate = null)

    private fun run(intent: String = "order") = CaseRun(
        answer = SupportAnswer(answer = "Your order is fine.", intent = intent, confidence = 0.9),
        evidence = emptyList(),
        events = emptyList(),
        elapsedMs = 100,
    )

    @Test
    fun `given several cases when running then should return one scored result per case in order`() = runTest {
        coEvery { answerer.answer(any()) } returns run()

        val results = runner.run(listOf(case("g1"), case("g2"), case("g3")))

        assertEquals(listOf("g1", "g2", "g3"), results.map { it.id })
        assertTrue(results.all { it.intentOk })
    }

    @Test
    fun `given an answer with the wrong intent when running then should score the routing as wrong`() = runTest {
        coEvery { answerer.answer(any()) } returns run(intent = "policy")

        assertFalse(runner.run(listOf(case("g1"))).single().intentOk)
    }

    @Test
    fun `given an answerer that throws for one case when running then should record it as failed and still run the others`() = runTest {
        coEvery { answerer.answer(match { it.id == "g2" }) } throws IllegalStateException("API responded 429")
        coEvery { answerer.answer(match { it.id != "g2" }) } returns run()

        val results = runner.run(listOf(case("g1"), case("g2"), case("g3")))

        assertEquals(3, results.size)
        assertEquals("API responded 429", results[1].error)
        assertFalse(results[1].intentOk)
        assertEquals(null, results[0].error)
        assertEquals(null, results[2].error)
    }

    @Test
    fun `given cases when running then should report each result as soon as it is ready`() = runTest {
        coEvery { answerer.answer(any()) } returns run()

        runner.run(listOf(case("g1"), case("g2")))

        assertEquals(listOf("g1", "g2"), progress.map { it.first })
    }

    @Test
    fun `given a list of case ids when selecting then should keep only those cases`() = runTest {
        coEvery { answerer.answer(any()) } returns run()

        val results = runner.run(listOf(case("g1"), case("g2"), case("g3")), only = setOf("g3", "g1"))

        assertEquals(listOf("g1", "g3"), results.map { it.id })
        coVerify(exactly = 2) { answerer.answer(any()) }
    }

    @Test
    fun `given no cases when running then should return no results`() = runTest {
        assertEquals(emptyList(), runner.run(emptyList()))
    }
}
