package dsupport.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import dsupport.contract.AgentEvent
import dsupport.contract.SupportAnswer
import org.jetbrains.skia.EncodedImageFormat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class ChatScreenRenderTest {

    private val events = listOf(
        AgentEvent.AgentStarted("orchestrator", 0, "My order 1001 is late", "r1"),
        AgentEvent.ModelCalled("orchestrator", 900, 80, 700, "r1"),
        AgentEvent.ToolStarted("orchestrator", "orders", "{}", "r1"),
        AgentEvent.AgentStarted("orders", 1, "check 1001", "r2", "r1"),
        AgentEvent.ModelCalled("orders", 400, 40, 500, "r2"),
        AgentEvent.ToolFinished("orchestrator", "orders", "ok", false, 1300, "r1"),
        AgentEvent.Escalated("TCK-1001", "needs a human"),
    )

    private val reply = ChatMessage.Reply(
        SupportAnswer(
            answer = "I am sorry about the delay. I opened a ticket so a person can follow your order.",
            intent = "order",
            sources = listOf("shipping-delays.md"),
            confidence = 0.86,
            escalated = true,
            ticketId = "TCK-1001",
        ),
        events,
    )

    private fun render(state: ChatState, name: String, dark: Boolean = false): Path {
        val scene = ImageComposeScene(width = 1100, height = 720, density = Density(1f)) { ChatScreen(state, {}, {}, dark) }
        val bytes = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
        scene.close()
        val file = Path.of("build", "render", "$name.png")
        Files.createDirectories(file.parent)
        Files.write(file, bytes)
        return file
    }

    @Test
    fun `given a finished conversation when rendering the screen then should draw an image`() {
        val state = ChatState(messages = listOf(ChatMessage.Customer("Meu pedido 1001 atrasou"), reply))

        val file = render(state, "finished")

        assertTrue(Files.size(file) > 5_000)
    }

    @Test
    fun `given the English language when rendering the screen then should draw an image`() {
        val state = ChatState(messages = listOf(ChatMessage.Customer("My order 1001 is late"), reply), language = Language.ENGLISH)

        val file = render(state, "english")

        assertTrue(Files.size(file) > 5_000)
    }

    @Test
    fun `given a running question when rendering the screen then should draw an image`() {
        val state = ChatState(messages = listOf(ChatMessage.Customer("My order 1001 is late")), running = true, liveEvents = events.take(4))

        val file = render(state, "running")

        assertTrue(Files.size(file) > 5_000)
    }

    @Test
    fun `given the dark theme when rendering the screen then should draw an image`() {
        val state = ChatState(messages = listOf(ChatMessage.Customer("Meu pedido 1001 atrasou"), reply))

        assertTrue(Files.size(render(state, "dark", dark = true)) > 5_000)
    }

    @Test
    fun `given a new chat when rendering the screen then should draw the empty state`() {
        assertTrue(Files.size(render(ChatState(), "empty")) > 5_000)
    }
}
