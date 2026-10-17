package dsupport.eval

class ProgressLine {

    fun format(case: GoldenCase, result: CaseResult): String {
        if (result.error != null) return "${case.id} FAILED: ${result.error}"
        val escalated = if (result.escalated) "yes" else "no"
        return "${case.id} intent:${flag(result.intentOk)} escalated:$escalated facts:${flag(result.factsOk)} " +
            "grounded:${flag(result.grounded)} leak:${flag(result.leakFree)} ${result.ms / 1000.0}s ${result.modelCalls} calls"
    }

    private fun flag(value: Boolean): String = if (value) "ok" else "FAIL"
}
