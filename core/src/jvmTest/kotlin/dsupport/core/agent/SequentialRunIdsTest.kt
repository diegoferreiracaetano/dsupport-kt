package dsupport.core.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SequentialRunIdsTest {
    @Test
    fun `given a fresh generator when asking for ids then should return r1 r2 r3 in order`() {
        val ids = SequentialRunIds()

        assertEquals(listOf("r1", "r2", "r3"), List(3) { ids.next() })
    }

    @Test
    fun `given two generators when asking for ids then should count independently`() {
        assertEquals("r1", SequentialRunIds().next())
        assertEquals("r1", SequentialRunIds().next())
    }

    @Test
    fun `given many concurrent callers when asking for ids then should never repeat an id`() = runTest {
        val ids = SequentialRunIds()

        val all = coroutineScope {
            (1..2000).map { async(Dispatchers.Default) { ids.next() } }.awaitAll()
        }

        assertEquals(2000, all.toSet().size)
    }
}
