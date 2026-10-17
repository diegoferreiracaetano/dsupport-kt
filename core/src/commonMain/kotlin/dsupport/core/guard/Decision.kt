package dsupport.core.guard

import kotlinx.serialization.json.JsonObject

/** What a guard decides before a tool runs. */
sealed interface Decision {
    data object Allow : Decision
    data class Deny(val reason: String) : Decision
    data class Modify(val args: JsonObject) : Decision
}
