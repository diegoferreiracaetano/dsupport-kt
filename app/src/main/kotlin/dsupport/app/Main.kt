package dsupport.app

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout

private const val DEFAULT_SERVER = "http://localhost:8080"
private const val REQUEST_TIMEOUT_MS = 180_000L

fun main() = application {
    val baseUrl = System.getenv("DSUPPORT_SERVER")?.takeIf { it.isNotBlank() } ?: DEFAULT_SERVER
    Window(
        onCloseRequest = ::exitApplication,
        title = "DSupport",
        state = rememberWindowState(size = DpSize(1100.dp, 720.dp)),
    ) {
        val scope = rememberCoroutineScope()
        val viewModel = remember {
            val http = HttpClient(CIO) { install(HttpTimeout) { requestTimeoutMillis = REQUEST_TIMEOUT_MS } }
            ChatViewModel(ChatClient(http, baseUrl), scope)
        }
        val state by viewModel.state.collectAsState()
        ChatScreen(state, viewModel::send)
    }
}
