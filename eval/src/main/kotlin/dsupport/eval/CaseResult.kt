package dsupport.eval

/** The outcome of one case, with every criterion kept apart so the report shows where the system fails. */
data class CaseResult(
    val id: String,
    val intentOk: Boolean,
    val escalated: Boolean,
    val shouldEscalate: Boolean?,
    val grounded: Boolean,
    val factsOk: Boolean,
    val sourcesOk: Boolean?,
    val leakFree: Boolean,
    val problems: List<String>,
    val ms: Long,
    val modelCalls: Int,
    val tokens: Int,
    val error: String? = null,
)
