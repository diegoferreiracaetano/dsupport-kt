package dsupport.eval

private const val CHEAP_MODEL = "claude-haiku-5-5"
private const val TENTHS_OF_A_CENT_PER_EXECUTION = 2

/** A rough price tag shown before an evaluation starts, measured on the cheap model (about 9.5k tokens per execution). */
class CostEstimate {

    fun describe(executions: Int, modelId: String): String {
        val noun = if (executions == 1) "execution" else "executions"
        if (modelId != CHEAP_MODEL) return "$executions $noun; the price depends on $modelId (no estimate available)"
        val cents = (executions * TENTHS_OF_A_CENT_PER_EXECUTION + 9) / 10
        val unit = if (cents == 1) "cent" else "cents"
        return "$executions $noun, about $cents $unit (estimate for $modelId)"
    }
}
