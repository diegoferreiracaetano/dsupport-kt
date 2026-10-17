package dsupport.core.usecase

import dsupport.contract.AgentEvent
import dsupport.contract.SupportAnswer
import dsupport.core.agent.EventSink
import dsupport.core.review.ReviewLoop
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class AnswerCustomerTest {
    private fun factoryAnswering(): (EventSink) -> ReviewLoop = { sink ->
        mockk<ReviewLoop>().also { loop ->
            coEvery { loop.answer(any()) } coAnswers {
                val question = firstArg<String>()
                sink.emit(AgentEvent.ReviewRound(1, true, emptyList()))
                SupportAnswer(answer = "answer to $question")
            }
        }
    }

    @Test
    fun `given a question when answering then should stream the events and finish with Completed`() = runTest {
        val events = AnswerCustomer(factoryAnswering())("Where is my order?").toList()

        assertEquals(2, events.size)
        assertIs<AgentEvent.ReviewRound>(events.first())
        assertEquals(AgentEvent.Completed(SupportAnswer(answer = "answer to Where is my order?")), events.last())
    }

    @Test
    fun `given two simultaneous questions when answering then should not mix their events`() = runTest {
        val useCase = AnswerCustomer(factoryAnswering())

        val first = async { useCase("first").toList() }
        val second = async { useCase("second").toList() }

        assertEquals("answer to first", (first.await().last() as AgentEvent.Completed).answer.answer)
        assertEquals("answer to second", (second.await().last() as AgentEvent.Completed).answer.answer)
        assertEquals(2, first.await().size)
        assertEquals(2, second.await().size)
    }

    @Test
    fun `given a loop that fails when answering then should propagate the failure to the collector`() = runTest {
        val failing: (EventSink) -> ReviewLoop = {
            mockk<ReviewLoop>().also { loop -> coEvery { loop.answer(any()) } throws IllegalStateException("boom") }
        }

        assertFailsWith<IllegalStateException> { AnswerCustomer(failing)("question").toList() }
    }
}
