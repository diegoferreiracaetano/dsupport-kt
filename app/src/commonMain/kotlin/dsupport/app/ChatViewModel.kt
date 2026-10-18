package dsupport.app

import dsupport.contract.AgentEvent
import dsupport.contract.StreamItem
import dsupport.contract.SupportAnswer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ChatMessage {
    data class Customer(val text: String) : ChatMessage
    data class Reply(val answer: SupportAnswer, val events: List<AgentEvent>) : ChatMessage
    data class Problem(val text: String) : ChatMessage
}

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val running: Boolean = false,
    val liveEvents: List<AgentEvent> = emptyList(),
    val language: Language = Language.PORTUGUESE,
)

/** Holds what the screen shows. One question runs at a time. */
class ChatViewModel(private val assistant: Assistant, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = mutableState.asStateFlow()

    fun reset() {
        if (mutableState.value.running) return
        mutableState.update { it.copy(messages = emptyList(), liveEvents = emptyList()) }
    }

    fun changeLanguage(language: Language) {
        mutableState.update { it.copy(language = language) }
    }

    fun send(question: String) {
        val text = question.trim()
        if (text.isEmpty() || mutableState.value.running) return
        mutableState.update { it.copy(messages = it.messages + ChatMessage.Customer(text), running = true, liveEvents = emptyList()) }
        scope.launch {
            val events = mutableListOf<AgentEvent>()
            var outcome: ChatMessage = ChatMessage.Problem("the assistant finished without an answer")
            try {
                assistant.ask(text).collect { item ->
                    when (item) {
                        is StreamItem.Event -> {
                            val event = item.event
                            events += event
                            mutableState.update { it.copy(liveEvents = events.toList()) }
                            if (event is AgentEvent.Completed) outcome = ChatMessage.Reply(event.answer, events.toList())
                        }
                        is StreamItem.Failure -> outcome = ChatMessage.Problem(item.message)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                outcome = ChatMessage.Problem(e.message ?: e::class.simpleName.orEmpty())
            }
            mutableState.update { it.copy(messages = it.messages + outcome, running = false, liveEvents = emptyList()) }
        }
    }
}
