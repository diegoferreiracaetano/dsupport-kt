package dsupport.adapters.persistence

import dsupport.core.domain.Ticket
import dsupport.core.domain.TicketPriority
import dsupport.core.domain.TicketStatus
import dsupport.core.port.TicketGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.time.Clock

private const val FIRST_TICKET_NUMBER = 1001

class JsonTicketGateway(
    private val file: Path,
    private val now: () -> String = { Clock.System.now().toString() },
) : TicketGateway {
    private val json = Json { prettyPrint = true }
    private val serializer = ListSerializer(TicketRecord.serializer())
    private val writeLock = Mutex()

    override suspend fun create(summary: String, priority: TicketPriority, customerEmail: String, reason: String): String =
        writeLock.withLock {
            withContext(Dispatchers.IO) {
                val existing = load()
                val id = "TCK-${FIRST_TICKET_NUMBER + existing.size}"
                val record = TicketRecord(
                    ticketId = id,
                    createdAt = now(),
                    status = TicketStatus.OPEN.wire,
                    summary = summary,
                    priority = priority.wire,
                    customerEmail = customerEmail,
                    reason = reason,
                )
                store(existing + record)
                id
            }
        }

    override suspend fun findOpenByEmail(email: String): Ticket? = withContext(Dispatchers.IO) {
        load().lastOrNull { it.status == TicketStatus.OPEN.wire && it.customerEmail.equals(email, ignoreCase = true) }
            ?.toDomain()
    }

    private fun load(): List<TicketRecord> =
        if (Files.notExists(file)) emptyList() else json.decodeFromString(serializer, Files.readString(file))

    private fun store(records: List<TicketRecord>) {
        val temporary = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(temporary, json.encodeToString(serializer, records))
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
