package dsupport.core.review

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ReviewSchemaTest {
    private val output = ReviewSchema.output

    @Test
    fun `given the schema when reading it then should be named review_verdict and require both fields`() {
        assertEquals("review_verdict", output.name)
        assertEquals(
            listOf("approved", "problems"),
            output.schema.getValue("required").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `given the schema when reading its properties then should forbid extra ones and type both fields`() {
        val properties = output.schema.getValue("properties").jsonObject

        assertFalse(output.schema.getValue("additionalProperties").jsonPrimitive.boolean)
        assertEquals("boolean", properties.getValue("approved").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("array", properties.getValue("problems").jsonObject.getValue("type").jsonPrimitive.content)
    }
}
