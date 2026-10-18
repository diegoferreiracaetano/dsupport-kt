package dsupport.core.agent

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Hands out the id of each agent run. One generator is shared by all the agents of a question. */
fun interface RunIds {
    fun next(): String
}

/** Ids `r1`, `r2`, ... Safe to call from concurrent coroutines. */
@OptIn(ExperimentalAtomicApi::class)
class SequentialRunIds : RunIds {
    private val counter = AtomicInt(0)

    override fun next(): String = "r${counter.incrementAndFetch()}"
}

/**
 * The run an agent is executing, carried in the coroutine context so that a sub-agent started from inside
 * a tool call can find the run that started it.
 */
class RunContext(val runId: String, val parentRunId: String?) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<RunContext>
}
