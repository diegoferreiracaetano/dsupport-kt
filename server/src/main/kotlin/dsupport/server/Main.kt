package dsupport.server

import dsupport.cli.DSupportRuntime
import dsupport.cli.SettingsReader
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

private const val REQUEST_TIMEOUT_MS = 120_000L
private const val DEFAULT_PORT = 8080

fun main() {
    val settings = try {
        SettingsReader().read(System.getenv())
    } catch (e: IllegalArgumentException) {
        System.err.println(e.message)
        exitProcess(1)
    }
    val http = HttpClient(CIO) { install(HttpTimeout) { requestTimeoutMillis = REQUEST_TIMEOUT_MS } }
    val answerCustomer = runBlocking { DSupportRuntime(settings, http).assemble(settings.dataDir.resolve("tickets.json")) }
    val port = System.getenv("PORT")?.toIntOrNull() ?: DEFAULT_PORT
    embeddedServer(Netty, port = port) { chatModule(answerCustomer) }.start(wait = true)
}
