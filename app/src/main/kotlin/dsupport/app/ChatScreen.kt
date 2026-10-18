package dsupport.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dsupport.contract.AgentEvent
import dsupport.core.trace.TraceReport

private val ContentWidth = 740.dp
private val TraceWidth = 420.dp

@Composable
fun ChatScreen(
    state: ChatState,
    onSend: (String) -> Unit,
    onLanguage: (Language) -> Unit = {},
    dark: Boolean = isSystemInDarkTheme(),
) {
    val strings = Strings.of(state.language)
    var traceVisible by remember { mutableStateOf(true) }
    DSupportTheme(dark) {
        Surface(Modifier.fillMaxSize(), color = palette.background) {
            Row(Modifier.fillMaxSize()) {
                Conversation(state, strings, traceVisible, onSend, onLanguage, { traceVisible = !traceVisible }, Modifier.weight(1f).fillMaxHeight())
                if (traceVisible) {
                    Box(Modifier.width(1.dp).fillMaxHeight().background(palette.border))
                    TracePanel(state, strings, Modifier.width(TraceWidth).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun Conversation(
    state: ChatState,
    strings: Strings,
    traceVisible: Boolean,
    onSend: (String) -> Unit,
    onLanguage: (Language) -> Unit,
    onToggleTrace: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        Header(state.language, strings, traceVisible, onLanguage, onToggleTrace)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            if (state.messages.isEmpty() && !state.running) {
                EmptyState(strings, onSend, Modifier.widthIn(max = ContentWidth).padding(horizontal = 24.dp))
            } else {
                MessageList(state, strings, Modifier.widthIn(max = ContentWidth).fillMaxSize())
            }
        }
        Composer(state.running, strings, onSend, Modifier.widthIn(max = ContentWidth).padding(horizontal = 24.dp, vertical = 16.dp).align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun Header(language: Language, strings: Strings, traceVisible: Boolean, onLanguage: (Language) -> Unit, onToggleTrace: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(strings.title, style = MaterialTheme.typography.titleMedium, color = palette.text)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                if (traceVisible) strings.hideTrace else strings.showTrace,
                Modifier.clip(Radius).clickable(onClick = onToggleTrace).padding(horizontal = 8.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = palette.textMuted,
            )
            Segmented(language, onLanguage)
        }
    }
}

@Composable
private fun Segmented(selected: Language, onSelect: (Language) -> Unit) {
    Row(
        Modifier.clip(Radius).background(palette.surfaceRaised).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Language.entries.forEach { language ->
            val active = language == selected
            Text(
                language.label,
                Modifier.clip(Radius).background(if (active) palette.surface else palette.surfaceRaised)
                    .clickable { onSelect(language) }.padding(horizontal = 12.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium,
                color = if (active) palette.text else palette.textMuted,
            )
        }
    }
}

@Composable
private fun EmptyState(strings: Strings, onSend: (String) -> Unit, modifier: Modifier) {
    Column(modifier.padding(top = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(strings.greeting, style = MaterialTheme.typography.headlineSmall, color = palette.text)
        Text(strings.subtitle, style = MaterialTheme.typography.bodyLarge, color = palette.textMuted)
        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            strings.suggestions.forEach { suggestion ->
                Text(
                    suggestion,
                    Modifier.fillMaxWidth().clip(Radius).background(palette.surface)
                        .outline(palette.border).clickable { onSend(suggestion) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.text,
                )
            }
        }
    }
}

private fun Modifier.outline(color: androidx.compose.ui.graphics.Color): Modifier =
    border(BorderStroke(1.dp, color), Radius)

@Composable
private fun MessageList(state: ChatState, strings: Strings, modifier: Modifier) {
    val listState = rememberLazyListState()
    val lastIndex = state.messages.size + if (state.running) 1 else 0
    LaunchedEffect(lastIndex) {
        if (lastIndex > 0) listState.animateScrollToItem(lastIndex - 1)
    }
    LazyColumn(
        modifier,
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(state.messages) { MessageBubble(it, strings) }
        if (state.running) item { Thinking(strings) }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, strings: Strings) {
    when (message) {
        is ChatMessage.Customer -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                message.text,
                Modifier.widthIn(max = 520.dp).clip(Radius).background(palette.accent).padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = palette.onAccent,
            )
        }
        is ChatMessage.Problem -> Column(
            Modifier.fillMaxWidth().clip(Radius).background(palette.dangerSoft).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(strings.errorTitle, style = MaterialTheme.typography.titleMedium, color = palette.danger)
            Text(message.text, style = MaterialTheme.typography.bodyMedium, color = palette.danger)
        }
        is ChatMessage.Reply -> ReplyCard(message, strings)
    }
}

@Composable
private fun ReplyCard(reply: ChatMessage.Reply, strings: Strings) {
    val answer = reply.answer
    Column(
        Modifier.fillMaxWidth().clip(Radius).background(palette.surface).outline(palette.border).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(answer.answer, style = MaterialTheme.typography.bodyLarge, color = palette.text)
        if (answer.escalated) {
            Column(
                Modifier.fillMaxWidth().clip(Radius).background(palette.warningSoft).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    strings.escalated + (answer.ticketId?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.warning,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(strings.ticketOpened, style = MaterialTheme.typography.bodySmall, color = palette.warning)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip(strings.intent(answer.intent))
            Chip("${strings.confidence} ${(answer.confidence * 100).toInt()}%")
            answer.sources.forEach { Chip(it, accent = true) }
        }
    }
}

@Composable
private fun Chip(text: String, accent: Boolean = false) {
    Text(
        text,
        Modifier.clip(Radius).background(if (accent) palette.accentSoft else palette.surfaceRaised).padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = if (accent) palette.accent else palette.textMuted,
    )
}

@Composable
private fun Thinking(strings: Strings) {
    val transition = rememberInfiniteTransition()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(600, delayMillis = index * 150), RepeatMode.Reverse),
            )
            Box(Modifier.size(7.dp).alpha(alpha).clip(CircleShape).background(palette.accent))
        }
        Text(strings.thinking, style = MaterialTheme.typography.bodySmall, color = palette.textMuted, modifier = Modifier.padding(start = 6.dp))
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Composer(running: Boolean, strings: Strings, onSend: (String) -> Unit, modifier: Modifier) {
    var draft by remember { mutableStateOf("") }
    val send = {
        if (draft.isNotBlank() && !running) {
            onSend(draft)
            draft = ""
        }
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                val enter = event.key == Key.Enter && event.type == KeyEventType.KeyDown && !event.isShiftPressed
                if (enter) send()
                enter
            },
            placeholder = { Text(strings.placeholder, color = palette.textMuted) },
            singleLine = true,
            shape = Radius,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.text),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = palette.surface,
                unfocusedContainerColor = palette.surface,
                focusedBorderColor = palette.accent,
                unfocusedBorderColor = palette.border,
                cursorColor = palette.accent,
            ),
        )
        Button(
            onClick = { send() },
            enabled = draft.isNotBlank() && !running,
            shape = Radius,
            modifier = Modifier.height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                contentColor = palette.onAccent,
                disabledContainerColor = palette.surfaceRaised,
                disabledContentColor = palette.textMuted,
            ),
        ) { Text(strings.send, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun TracePanel(state: ChatState, strings: Strings, modifier: Modifier) {
    val report = remember { TraceReport() }
    val events: List<AgentEvent> = if (state.running || state.liveEvents.isNotEmpty()) {
        state.liveEvents
    } else {
        (state.messages.lastOrNull { it is ChatMessage.Reply } as? ChatMessage.Reply)?.events.orEmpty()
    }
    Column(modifier.background(palette.surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(if (state.running) strings.liveTrace else strings.lastTrace, style = MaterialTheme.typography.titleMedium, color = palette.text)
        if (events.isEmpty()) {
            Text(strings.traceHint, style = MaterialTheme.typography.bodyMedium, color = palette.textMuted)
        } else {
            val costs = report.byAgent(events)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("${costs.sumOf { it.inputTokens }}", strings.inLabel, Modifier.weight(1f))
                Stat("${costs.sumOf { it.outputTokens }}", strings.outLabel, Modifier.weight(1f))
                Stat("${costs.sumOf { it.calls }}", strings.modelCalls, Modifier.weight(1f))
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().clip(Radius).background(palette.background).outline(palette.border)
                    .verticalScroll(rememberScrollState()).padding(14.dp),
            ) {
                Text(report.tree(events), fontFamily = Mono, fontSize = 11.sp, lineHeight = 17.sp, color = palette.text)
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(Radius).background(palette.surfaceRaised).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = palette.text)
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
    }
}
