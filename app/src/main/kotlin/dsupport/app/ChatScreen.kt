package dsupport.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dsupport.contract.AgentEvent
import dsupport.core.trace.TraceReport

private val CustomerColor = Color(0xFFDCEBFF)
private val ReplyColor = Color(0xFFF1F3F5)
private val ProblemColor = Color(0xFFFFE3E0)
private val EscalatedColor = Color(0xFFB45309)

@Composable
fun ChatScreen(state: ChatState, onSend: (String) -> Unit, onLanguage: (Language) -> Unit = {}) {
    val strings = Strings.of(state.language)
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                Conversation(state, strings, onSend, onLanguage, Modifier.weight(0.58f).fillMaxHeight())
                TracePanel(state, strings, Modifier.weight(0.42f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun Conversation(state: ChatState, strings: Strings, onSend: (String) -> Unit, onLanguage: (Language) -> Unit, modifier: Modifier) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    val send = {
        if (draft.isNotBlank() && !state.running) {
            onSend(draft)
            draft = ""
        }
    }
    Column(modifier.padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(strings.title, style = MaterialTheme.typography.headlineSmall)
            LanguageSelector(state.language, strings, onLanguage)
        }
        Text(strings.subtitle, style = MaterialTheme.typography.bodySmall)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.messages) { MessageBubble(it, strings) }
            if (state.running) item { Text(strings.thinking, style = MaterialTheme.typography.bodySmall) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(strings.placeholder) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
            )
            Button(onClick = { send() }, enabled = draft.isNotBlank() && !state.running) { Text(strings.send) }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, strings: Strings) {
    when (message) {
        is ChatMessage.Customer -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Bubble(CustomerColor) { Text(message.text) }
        }
        is ChatMessage.Problem -> Bubble(ProblemColor) { Text(message.text) }
        is ChatMessage.Reply -> Bubble(ReplyColor) {
            Text(message.answer.answer)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(strings.intent(message.answer.intent), style = MaterialTheme.typography.labelSmall)
                Text("${strings.confidence} ${(message.answer.confidence * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                if (message.answer.escalated) {
                    Text(
                        strings.escalated + (message.answer.ticketId?.let { " · $it" } ?: ""),
                        Modifier.background(EscalatedColor, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (message.answer.sources.isNotEmpty()) {
                Text("${strings.sources}: " + message.answer.sources.joinToString(", "), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun Bubble(color: Color, content: @Composable () -> Unit) {
    Column(Modifier.widthIn(max = 520.dp).background(color, RoundedCornerShape(12.dp)).padding(12.dp)) { content() }
}

@Composable
private fun TracePanel(state: ChatState, strings: Strings, modifier: Modifier) {
    val report = remember { TraceReport() }
    val events: List<AgentEvent> = if (state.running || state.liveEvents.isNotEmpty()) {
        state.liveEvents
    } else {
        (state.messages.lastOrNull { it is ChatMessage.Reply } as? ChatMessage.Reply)?.events.orEmpty()
    }
    Column(modifier.border(1.dp, Color(0xFFD0D5DB)).padding(16.dp)) {
        Text(if (state.running) strings.liveTrace else strings.lastTrace, style = MaterialTheme.typography.titleMedium)
        Column(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp).verticalScroll(rememberScrollState())) {
            if (events.isEmpty()) {
                Text(strings.nothingYet, style = MaterialTheme.typography.bodySmall)
            } else {
                Text(report.tree(events), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
        if (events.isNotEmpty()) {
            val costs = report.byAgent(events)
            Text(
                "${strings.tokens}: ${costs.sumOf { it.inputTokens }} ${strings.inLabel} / ${costs.sumOf { it.outputTokens }} ${strings.outLabel} · ${costs.sumOf { it.calls }} ${strings.modelCalls}",
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun LanguageSelector(selected: Language, strings: Strings, onLanguage: (Language) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(strings.language, style = MaterialTheme.typography.labelSmall)
        Language.entries.forEach { language ->
            FilterChip(selected = language == selected, onClick = { onLanguage(language) }, label = { Text(language.label) })
        }
    }
}
