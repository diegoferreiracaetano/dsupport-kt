package dsupport.eval

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CostEstimateTest {
    private val estimate = CostEstimate()

    @Test
    fun `given forty five executions with the cheap model when describing then should announce the count and about nine cents`() {
        assertEquals("45 executions, about 9 cents (estimate for claude-haiku-5-5)", estimate.describe(45, "claude-haiku-5-5"))
    }

    @Test
    fun `given a single execution when describing then should use the singular and round the cost up to one cent`() {
        assertEquals("1 execution, about 1 cent (estimate for claude-haiku-5-5)", estimate.describe(1, "claude-haiku-5-5"))
    }

    @Test
    fun `given another model when describing then should not invent a price`() {
        val text = estimate.describe(45, "claude-opus-5-5")

        assertTrue("45 executions" in text && "claude-opus-5-5" in text && "cents" !in text)
    }
}
