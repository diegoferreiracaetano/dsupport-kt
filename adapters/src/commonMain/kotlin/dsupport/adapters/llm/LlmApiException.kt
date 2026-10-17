package dsupport.adapters.llm

/** A failure talking to an LLM API. [status] is the HTTP status, or null when the failure is not HTTP related. */
class LlmApiException(val status: Int?, message: String) : Exception(message)
