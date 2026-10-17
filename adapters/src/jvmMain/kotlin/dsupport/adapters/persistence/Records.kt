package dsupport.adapters.persistence

import dsupport.core.domain.Order
import dsupport.core.domain.OrderItem
import dsupport.core.domain.OrderStatus
import dsupport.core.domain.Ticket
import dsupport.core.domain.TicketPriority
import dsupport.core.domain.TicketStatus
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class OrderItemRecord(val sku: String, val name: String, val qty: Int)

@Serializable
internal data class OrderRecord(
    @SerialName("order_id") val orderId: String,
    val email: String,
    val status: String,
    val eta: String,
    val carrier: String,
    val tracking: String,
    val total: Double,
    val items: List<OrderItemRecord>,
) {
    fun toDomain() = Order(
        orderId = orderId,
        email = email,
        status = OrderStatus.fromWire(status),
        eta = LocalDate.parse(eta),
        carrier = carrier,
        tracking = tracking,
        total = total,
        items = items.map { OrderItem(it.sku, it.name, it.qty) },
    )
}

@Serializable
internal data class TicketRecord(
    @SerialName("ticket_id") val ticketId: String,
    @SerialName("created_at") val createdAt: String,
    val status: String,
    val summary: String,
    val priority: String,
    @SerialName("customer_email") val customerEmail: String,
    val reason: String,
) {
    fun toDomain() = Ticket(
        ticketId = ticketId,
        createdAt = createdAt,
        status = TicketStatus.fromWire(status),
        summary = summary,
        priority = TicketPriority.fromWire(priority) ?: throw IllegalArgumentException("unknown ticket priority: $priority"),
        customerEmail = customerEmail,
        reason = reason,
    )
}
