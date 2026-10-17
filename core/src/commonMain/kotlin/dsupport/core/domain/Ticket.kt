package dsupport.core.domain

enum class TicketPriority(val wire: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    ;

    companion object {
        fun fromWire(value: String): TicketPriority? = entries.firstOrNull { it.wire == value }
    }
}

enum class TicketStatus(val wire: String) {
    OPEN("open"),
    CLOSED("closed"),
    ;

    companion object {
        fun fromWire(value: String): TicketStatus =
            entries.firstOrNull { it.wire == value } ?: throw IllegalArgumentException("unknown ticket status: $value")
    }
}

data class Ticket(
    val ticketId: String,
    val createdAt: String,
    val status: TicketStatus,
    val summary: String,
    val priority: TicketPriority,
    val customerEmail: String,
    val reason: String,
)
