package dsupport.app

import dsupport.contract.AgentEvent
import dsupport.contract.StreamItem
import dsupport.contract.SupportAnswer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val started = AgentEvent.AgentStarted("orchestrator", 0, "hi", "r1")
    private val answer = SupportAnswer(answer = "Hello!", intent = "other", confidence = 0.8)
    private val completed = AgentEvent.Completed(answer)

    private fun assistantStreaming(vararg items: StreamItem): Assistant = mockk<Assistant>().also {
        every { it.ask(any()) } returns flowOf(*items)
    }

    @Test
    fun `given a question when sending then should show the customer message and stay running until the stream ends`() = runTest {
        val gate = MutableSharedFlow<StreamItem>()
        val assistant = mockk<Assistant>().also { every { it.ask("hi") } returns gate }
        val viewModel = ChatViewModel(assistant, backgroundScope)

        viewModel.send("  hi  ")
        runCurrent()

        assertEquals(listOf<ChatMessage>(ChatMessage.Customer("hi")), viewModel.state.value.messages)
        assertTrue(viewModel.state.value.running)
    }

    @Test
    fun `given events while streaming when they arrive then should expose them as the live trace`() = runTest {
        val gate = MutableSharedFlow<StreamItem>()
        val assistant = mockk<Assistant>().also { every { it.ask(any()) } returns gate }
        val viewModel = ChatViewModel(assistant, backgroundScope)
        viewModel.send("hi")
        runCurrent()

        gate.emit(StreamItem.Event(started))
        runCurrent()

        assertEquals(listOf<AgentEvent>(started), viewModel.state.value.liveEvents)
    }

    @Test
    fun `given a completed stream when it ends then should add the assistant message with its trace and stop running`() = runTest {
        val viewModel = ChatViewModel(assistantStreaming(StreamItem.Event(started), StreamItem.Event(completed)), this)

        viewModel.send("hi")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(ChatMessage.Reply(answer, listOf(started, completed)), state.messages.last())
        assertTrue(!state.running)
        assertEquals(emptyList(), state.liveEvents)
    }

    @Test
    fun `given a failure item when the stream ends then should add a problem message and stop running`() = runTest {
        val viewModel = ChatViewModel(assistantStreaming(StreamItem.Failure("model unavailable")), this)

        viewModel.send("hi")
        advanceUntilIdle()

        assertEquals(ChatMessage.Problem("model unavailable"), viewModel.state.value.messages.last())
        assertTrue(!viewModel.state.value.running)
    }

    @Test
    fun `given a stream that throws when sending then should add a problem message and stop running`() = runTest {
        val assistant = mockk<Assistant>().also { every { it.ask(any()) } returns flow { throw IllegalStateException("boom") } }
        val viewModel = ChatViewModel(assistant, this)

        viewModel.send("hi")
        advanceUntilIdle()

        assertEquals(ChatMessage.Problem("boom"), viewModel.state.value.messages.last())
        assertTrue(!viewModel.state.value.running)
    }

    @Test
    fun `given a stream that ends without an answer when sending then should add a problem message`() = runTest {
        val viewModel = ChatViewModel(assistantStreaming(StreamItem.Event(started)), this)

        viewModel.send("hi")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.messages.last() is ChatMessage.Problem)
    }

    @Test
    fun `given a blank question when sending then should do nothing`() = runTest {
        val assistant = mockk<Assistant>(relaxed = true)
        val viewModel = ChatViewModel(assistant, this)

        viewModel.send("   ")
        advanceUntilIdle()

        assertEquals(emptyList(), viewModel.state.value.messages)
        verify(exactly = 0) { assistant.ask(any()) }
    }

    @Test
    fun `given a question still running when sending another then should ignore the second`() = runTest {
        val gate = MutableSharedFlow<StreamItem>()
        val assistant = mockk<Assistant>().also { every { it.ask(any()) } returns gate }
        val viewModel = ChatViewModel(assistant, backgroundScope)
        viewModel.send("first")
        runCurrent()

        viewModel.send("second")
        runCurrent()

        verify(exactly = 1) { assistant.ask(any()) }
    }

    @Test
    fun `given a new chat when reading the state then should start in Portuguese`() = runTest {
        val viewModel = ChatViewModel(mockk<Assistant>(relaxed = true), this)

        assertEquals(Language.PORTUGUESE, viewModel.state.value.language)
    }

    @Test
    fun `given a chosen language when changing it then should expose it and keep the messages`() = runTest {
        val viewModel = ChatViewModel(assistantStreaming(StreamItem.Event(completed)), this)
        viewModel.send("oi")
        advanceUntilIdle()

        viewModel.changeLanguage(Language.ENGLISH)

        assertEquals(Language.ENGLISH, viewModel.state.value.language)
        assertEquals(2, viewModel.state.value.messages.size)
    }
}
