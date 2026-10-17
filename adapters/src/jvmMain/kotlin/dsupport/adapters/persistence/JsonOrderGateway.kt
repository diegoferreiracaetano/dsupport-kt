package dsupport.adapters.persistence

import dsupport.core.domain.Order
import dsupport.core.port.OrderGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

class JsonOrderGateway(private val file: Path) : OrderGateway {

    override suspend fun findOwned(orderId: String, email: String): Order? =
        all().firstOrNull { it.orderId == orderId && it.email.equals(email, ignoreCase = true) }

    suspend fun all(): List<Order> = withContext(Dispatchers.IO) {
        Json.decodeFromString(ListSerializer(OrderRecord.serializer()), Files.readString(file)).map { it.toDomain() }
    }
}
