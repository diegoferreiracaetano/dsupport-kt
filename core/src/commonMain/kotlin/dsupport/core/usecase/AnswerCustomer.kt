package dsupport.core.usecase

import dsupport.contract.AgentEvent
import dsupport.core.agent.EventSink
import dsupport.core.review.ReviewLoop
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

/**
 * The entry point of the application. Each question gets its own [ReviewLoop], so histories and events of
 * simultaneous questions never mix. The flow ends with [AgentEvent.Completed].
 */
class AnswerCustomer(private val loops: (EventSink) -> ReviewLoop) {

    operator fun invoke(question: String): Flow<AgentEvent> = channelFlow {
        val loop = loops(EventSink { send(it) })
        send(AgentEvent.Completed(loop.answer(question)))
    }
}
