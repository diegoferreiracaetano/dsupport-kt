package dsupport.eval

import dsupport.cli.DSupportRuntime
import dsupport.contract.SupportAnswer
import dsupport.core.agent.EventSink
import dsupport.core.review.ReviewLoop
import dsupport.core.usecase.AnswerCustomer
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AssemblyCaseAnswererTest {
    private val runtime = mockk<DSupportRuntime>()
    private val workDir = createTempDirectory()
    private val answerer = AssemblyCaseAnswerer(runtime, workDir)
    private val assembledWith = mutableListOf<Path>()
    private val contentsSeen = mutableListOf<String>()

    private fun case(id: String) = GoldenCase(id, "question $id", listOf("order"))

    private fun stubRuntime(answerText: (String) -> String = { "answer to $it" }) {
        coEvery { runtime.assemble(any()) } answers {
            val file = firstArg<Path>()
            assembledWith.add(file)
            contentsSeen.add(file.readText())
            AnswerCustomer { _: EventSink ->
                mockk<ReviewLoop>().also { loop ->
                    coEvery { loop.answer(any()) } coAnswers { SupportAnswer(answer = answerText(firstArg())) }
                }
            }
        }
    }

    @Test
    fun `given a case when answering then should run the question and return the answer with its events and time`() = runTest {
        stubRuntime()

        val run = answerer.answer(case("g1"))

        assertEquals("answer to question g1", run.answer.answer)
        assertTrue(run.events.isNotEmpty())
        assertTrue(run.elapsedMs >= 0)
    }

    @Test
    fun `given two cases when answering then should give each one its own fresh empty tickets file`() = runTest {
        stubRuntime()

        answerer.answer(case("g1"))
        answerer.answer(case("g2"))

        assertEquals(2, assembledWith.toSet().size)
        assertNotEquals(assembledWith[0], assembledWith[1])
        assertEquals(listOf("[]", "[]"), contentsSeen)
        assertTrue(assembledWith.all { Files.exists(it) && it.startsWith(workDir) })
    }
}
