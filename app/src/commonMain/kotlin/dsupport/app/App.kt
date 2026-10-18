package dsupport.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout

private const val REQUEST_TIMEOUT_MS = 180_000L

/** The whole app, shared by every platform. Each platform only supplies the server address. */
@Composable
fun App(serverUrl: String) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(serverUrl) {
        val http = HttpClient { install(HttpTimeout) { requestTimeoutMillis = REQUEST_TIMEOUT_MS } }
        ChatViewModel(ChatClient(http, serverUrl), scope)
    }
    val state by viewModel.state.collectAsState()
    ChatScreen(state, viewModel::send, viewModel::changeLanguage)
}
