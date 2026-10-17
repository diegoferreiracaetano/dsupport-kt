package dsupport.core.domain

import kotlinx.datetime.LocalDate

enum class OrderStatus(val wire: String) {
    PROCESSING("processing"),
    IN_TRANSIT("in_transit"),
    DELIVERED("delivered"),
    CANCELED("canceled"),
    RETURN_IN_PROGRESS("return_in_progress"),
    ;

    companion object {
        fun fromWire(value: String): OrderStatus =
            entries.firstOrNull { it.wire == value } ?: throw IllegalArgumentException("unknown order status: $value")
    }
}

data class OrderItem(val sku: String, val name: String, val qty: Int)

data class Order(
    val orderId: String,
    val email: String,
    val status: OrderStatus,
    val eta: LocalDate,
    val carrier: String,
    val tracking: String,
    val total: Double,
    val items: List<OrderItem>,
)
