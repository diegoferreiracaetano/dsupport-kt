package dsupport.adapters.rag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextNormalizerTest {
    private val normalizer = TextNormalizer()

    @Test
    fun `given accented mixed case text when folding then should lowercase it and remove the accents`() {
        assertEquals("cafe nandu", normalizer.fold("Café Ñandú"))
    }

    @Test
    fun `given inflected forms of return when tokenizing then should reduce them to the same token`() {
        val forms = listOf("return", "returns", "returned", "returning").map { normalizer.tokens(it).single() }

        assertEquals(1, forms.toSet().size)
    }

    @Test
    fun `given ship shipping shipped and shipment when tokenizing then should reduce them to the same token`() {
        val forms = listOf("ship", "shipping", "shipped", "shipment", "ships").map { normalizer.tokens(it).single() }

        assertEquals(1, forms.toSet().size)
    }

    @Test
    fun `given delivery and deliveries when tokenizing then should reduce them to the same token`() {
        val forms = listOf("delivery", "deliveries", "delivered").map { normalizer.tokens(it).single() }

        assertEquals(1, forms.toSet().size)
    }

    @Test
    fun `given a sentence with stop words and punctuation when tokenizing then should keep only meaningful tokens`() {
        val tokens = normalizer.tokens("How long does the refund take, please?")

        assertFalse("the" in tokens)
        assertFalse("how" in tokens)
        assertTrue(normalizer.tokens("refund").single() in tokens)
        assertTrue(normalizer.tokens("long").single() in tokens)
    }

    @Test
    fun `given numbers when tokenizing then should keep them as tokens`() {
        assertTrue("30" in normalizer.tokens("Returns within 30 days"))
    }
}
