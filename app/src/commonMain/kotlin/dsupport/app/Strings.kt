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
    val greeting: String,
    val suggestions: List<String>,
    val ticketOpened: String,
    val errorTitle: String,
    val hideTrace: String,
    val showTrace: String,
    val traceHint: String,
    val server: String,
    val serverHint: String,
    val save: String,
    val cancel: String,
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
            modelCalls = "chamadas",
            language = "Idioma",
            greeting = "Como posso ajudar?",
            suggestions = listOf("Onde está o meu pedido 1001? Meu e-mail é ana@example.com", "Como faço uma devolução?", "Qual é a política de reembolso?"),
            ticketOpened = "Ticket aberto. Uma pessoa vai acompanhar o seu caso.",
            errorTitle = "Algo deu errado",
            hideTrace = "Ocultar rastro",
            showTrace = "Mostrar rastro",
            traceHint = "Faça uma pergunta para ver, passo a passo, o que o assistente fez.",
            server = "Servidor",
            serverHint = "Endereço do servidor, por exemplo http://192.168.0.10:8080",
            save = "Salvar",
            cancel = "Cancelar",
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
            modelCalls = "calls",
            language = "Language",
            greeting = "How can I help?",
            suggestions = listOf("Where is my order 1001? My email is ana@example.com", "How do I return an item?", "What is the refund policy?"),
            ticketOpened = "Ticket opened. A person will follow your case.",
            errorTitle = "Something went wrong",
            hideTrace = "Hide trace",
            showTrace = "Show trace",
            traceHint = "Ask a question to see, step by step, what the assistant did.",
            server = "Server",
            serverHint = "Server address, for example http://192.168.0.10:8080",
            save = "Save",
            cancel = "Cancel",
            intents = emptyMap(),
        )

        fun of(language: Language): Strings = when (language) {
            Language.PORTUGUESE -> portuguese
            Language.ENGLISH -> english
        }
    }
}
