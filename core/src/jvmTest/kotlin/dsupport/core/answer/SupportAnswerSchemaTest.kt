package dsupport.core.answer

import dsupport.contract.INTENTS
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupportAnswerSchemaTest {
    private val output = SupportAnswerSchema.output
    private val schema = output.schema
    private val properties = schema.getValue("properties").jsonObject

    @Test
    fun `given the schema when reading its name then should be answer_customer`() {
        assertEquals("answer_customer", output.name)
    }

    @Test
    fun `given the schema when reading its required fields then should require every field of the answer`() {
        val required = schema.getValue("required").jsonArray.map { it.jsonPrimitive.content }

        assertEquals(listOf("answer", "intent", "sources", "confidence", "escalated", "ticket_id"), required)
        assertEquals(required.toSet(), properties.keys)
    }

    @Test
    fun `given the schema when reading additional properties then should forbid them as the API requires`() {
        assertFalse(schema.getValue("additionalProperties").jsonPrimitive.boolean)
    }

    @Test
    fun `given the intent property when reading it then should be an enum with the supported intents`() {
        val values = properties.getValue("intent").jsonObject.getValue("enum").jsonArray.map { it.jsonPrimitive.content }

        assertEquals(INTENTS, values)
    }

    @Test
    fun `given the ticket_id property when reading it then should accept a string or null`() {
        val types = properties.getValue("ticket_id").jsonObject.getValue("anyOf").jsonArray
            .map { it.jsonObject.getValue("type").jsonPrimitive.content }

        assertEquals(setOf("string", "null"), types.toSet())
    }

    @Test
    fun `given the confidence property when reading its description then should say that asking the customer for a detail is a confident answer`() {
        val description = properties.getValue("confidence").jsonObject.getValue("description").jsonPrimitive.content

        assertTrue("asking the customer" in description.lowercase() && "confident" in description.lowercase())
    }

    @Test
    fun `given the intent property when reading its description then should define other as outside the help center topics`() {
        val description = properties.getValue("intent").jsonObject.getValue("description").jsonPrimitive.content

        assertTrue("other" in description && "outside" in description.lowercase())
    }

    @Test
    fun `given the confidence property when reading it then should not use numeric constraints the API rejects`() {
        val confidence = properties.getValue("confidence").jsonObject

        assertFalse("minimum" in confidence || "maximum" in confidence)
        assertTrue("description" in confidence)
    }
}
