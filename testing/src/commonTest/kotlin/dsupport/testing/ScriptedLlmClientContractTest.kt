package dsupport.testing

import dsupport.core.llm.LlmClient

class ScriptedLlmClientContractTest : LlmClientContract() {
    override fun clientFor(scenario: Scenario): LlmClient = ScriptedLlmClient.from(scenario)
}
