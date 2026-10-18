package dsupport.app

enum class Language(val label: String) {
    PORTUGUESE("PT"),
    ENGLISH("EN"),
}

/** Every text the screen shows, in one language. */
data class Strings(
    val title: String,
    val subtitle: String,
    val placeholder: String,
    val send: String,
    val thinking: String,
    val liveTrace: String,
    val lastTrace: String,
    val nothingYet: String,
    val escalated: String,
    val confidence: String,
    val sources: String,
    val tokens: String,
    val inLabel: String,
    val outLabel: String,
    val modelCalls: String,
    val language: String,
    private val intents: Map<String, String>,
) {
    fun intent(name: String): String = intents[name] ?: name

    companion object {
        private val portuguese = Strings(
            title = "DSupport",
            subtitle = "Pergunte sobre um pedido, uma devolução ou uma política.",
            placeholder = "Digite a pergunta do cliente",
            send = "Enviar",
            thinking = "Pensando...",
            liveTrace = "Rastro ao vivo",
            lastTrace = "Rastro da última resposta",
            nothingYet = "Nada ainda.",
            escalated = "Escalado",
            confidence = "confiança",
            sources = "Fontes",
            tokens = "Tokens",
            inLabel = "entrada",
            outLabel = "saída",
            modelCalls = "chamadas ao modelo",
            language = "Idioma",
            intents = mapOf("policy" to "política", "order" to "pedido", "return" to "devolução", "complaint" to "reclamação", "other" to "outro"),
        )

        private val english = Strings(
            title = "DSupport",
            subtitle = "Ask about an order, a return or a policy.",
            placeholder = "Type a customer question",
            send = "Send",
            thinking = "Thinking...",
            liveTrace = "Live trace",
            lastTrace = "Trace of the last answer",
            nothingYet = "Nothing yet.",
            escalated = "Escalated",
            confidence = "confidence",
            sources = "Sources",
            tokens = "Tokens",
            inLabel = "in",
            outLabel = "out",
            modelCalls = "model calls",
            language = "Language",
            intents = emptyMap(),
        )

        fun of(language: Language): Strings = when (language) {
            Language.PORTUGUESE -> portuguese
            Language.ENGLISH -> english
        }
    }
}
