package dsupport.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class StringsTest {

    @Test
    fun `given the Portuguese language when picking the strings then should return the Portuguese texts`() {
        assertEquals("Enviar", Strings.of(Language.PORTUGUESE).send)
    }

    @Test
    fun `given the English language when picking the strings then should return the English texts`() {
        assertEquals("Send", Strings.of(Language.ENGLISH).send)
    }

    @Test
    fun `given a known intent when labelling it then should translate it to each language`() {
        assertEquals("pedido", Strings.of(Language.PORTUGUESE).intent("order"))
        assertEquals("order", Strings.of(Language.ENGLISH).intent("order"))
    }

    @Test
    fun `given an unknown intent when labelling it then should keep the original name`() {
        assertEquals("surprise", Strings.of(Language.PORTUGUESE).intent("surprise"))
    }

    @Test
    fun `given both languages when comparing their placeholders then should differ`() {
        assertNotEquals(Strings.of(Language.PORTUGUESE).placeholder, Strings.of(Language.ENGLISH).placeholder)
    }
}
