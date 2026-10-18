package dsupport.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout

private const val REQUEST_TIMEOUT_MS = 180_000L
private const val CONNECT_TIMEOUT_MS = 8_000L

/** The whole app, shared by every platform. Each platform only supplies the server address. */
@Composable
fun App(serverUrl: String) {
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(serverUrl) }
    val viewModel = remember {
        val http = HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
            }
        }
        ChatViewModel({ question: String -> ChatClient(http, address).ask(question) }, scope)
    }
    val state by viewModel.state.collectAsState()
    ChatScreen(state, viewModel::send, viewModel::changeLanguage, serverAddress = address, onServerAddress = { address = it.trim().trimEnd('/') }, onNewChat = viewModel::reset)
}
