package dsupport.eval

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One question of the golden dataset and what is expected from the answer.
 * A null [shouldEscalate] means the case is not judged for escalation.
 */
@Serializable
data class GoldenCase(
    val id: String,
    val question: String,
    @SerialName("expected_intents") val expectedIntents: List<String>,
    @SerialName("should_escalate") val shouldEscalate: Boolean? = null,
    @SerialName("must_contain") val mustContain: List<String> = emptyList(),
    @SerialName("must_not_contain") val mustNotContain: List<String> = emptyList(),
    @SerialName("expected_sources") val expectedSources: List<String> = emptyList(),
    val note: String? = null,
)
