package dsupport.core.guard

internal const val NATIONAL_ID_PLACEHOLDER = "[ID REMOVED]"
internal const val CARD_PLACEHOLDER = "[CARD REMOVED]"

/** The patterns shared by the guards. National ids cover the Brazilian CPF and the US SSN formats. */
object Patterns {
    val nationalId = Regex("""\b(?:\d{3}\.?\d{3}\.?\d{3}-?\d{2}|\d{3}-\d{2}-\d{4})\b""")
    val card = Regex("""\b(?:\d[ -]?){13,16}\b""")
    val email = Regex("""[\w.+-]+@[\w-]+\.[\w.]+""")
    val ticketId = Regex("""TCK-\d+""")

    fun redact(text: String): String =
        text.replace(nationalId, NATIONAL_ID_PLACEHOLDER).replace(card, CARD_PLACEHOLDER)
}
