package dsupport.core.review

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ReviewTest {
    private fun parse(raw: String) = Review.fromJson(Json.parseToJsonElement(raw) as JsonObject)

    @Test
    fun `given an approval when decoding then should have no problems`() {
        assertEquals(Review(approved = true, problems = emptyList()), parse("""{"approved":true,"problems":[]}"""))
    }

    @Test
    fun `given a rejection with problems when decoding then should keep them in order`() {
        val review = parse("""{"approved":false,"problems":["too long","missing the delay"]}""")

        assertFalse(review.approved)
        assertEquals(listOf("too long", "missing the delay"), review.problems)
    }

    @Test
    fun `given an empty object when decoding then should default to a rejection`() {
        assertEquals(Review(approved = false, problems = emptyList()), parse("{}"))
    }
}
