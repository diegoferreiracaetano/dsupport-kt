package dsupport.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

private const val DEFAULT_SERVER = "http://localhost:8080"

fun main() = application {
    val serverUrl = System.getenv("DSUPPORT_SERVER")?.takeIf { it.isNotBlank() } ?: DEFAULT_SERVER
    Window(
        onCloseRequest = ::exitApplication,
        title = "DSupport",
        state = rememberWindowState(size = DpSize(1100.dp, 720.dp)),
    ) {
        App(serverUrl)
    }
}
