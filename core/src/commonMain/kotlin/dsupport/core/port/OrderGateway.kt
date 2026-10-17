package dsupport.core.port

import dsupport.core.domain.Order

interface OrderGateway {
    /** Returns the order only when [email] owns it. An unknown order and a wrong email are indistinguishable. */
    suspend fun findOwned(orderId: String, email: String): Order?
}
