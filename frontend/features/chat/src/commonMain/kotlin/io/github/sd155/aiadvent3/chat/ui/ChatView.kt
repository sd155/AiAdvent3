package io.github.sd155.aiadvent3.chat.ui

import aiadvent3.frontend.features.chat.generated.resources.Res
import aiadvent3.frontend.features.chat.generated.resources.creativity_value
import aiadvent3.frontend.features.chat.generated.resources.failed_label
import aiadvent3.frontend.features.chat.generated.resources.llm_progress
import aiadvent3.frontend.features.chat.generated.resources.prompt_hint
import aiadvent3.frontend.features.chat.generated.resources.question_label
import aiadvent3.frontend.features.chat.generated.resources.reasoning_label
import aiadvent3.frontend.features.chat.generated.resources.succeed_label
import aiadvent3.frontend.features.chat.generated.resources.summary_label
import aiadvent3.frontend.features.chat.generated.resources.used_tokens_value
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ChatView(
    state: ChatViewState,
    onPrompt: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer)
            .fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
    ) {
        MessageList(
            modifier = Modifier.weight(1f),
            messages = state.messages,
        )
        ChatPrompt(
            onPrompt = onPrompt
        )
    }
}

@Composable
private fun MessageList(
    modifier: Modifier = Modifier,
    messages: List<ChatMessage>,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .background(color = MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        itemsIndexed(messages) { index, message ->
            when (message) {
                is ChatMessage.UserMessage -> LocalBubble(message.content)
                is ChatMessage.LlmError -> RemoteError(message.content)
                is ChatMessage.LlmSuccess -> RemoteBubble { RemoteSuccess(message) }
                is ChatMessage.LlmQuery -> RemoteBubble { RemoteQuery(message) }
                is ChatMessage.LlmFailure -> RemoteBubble { RemoteFailure(message) }
                ChatMessage.LlmProgress -> RemoteLoading()
            }
            if (index < messages.size - 1)
                Spacer(modifier = Modifier.height(8.dp))
        }
    }
    
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(
                    index = messages.size - 1,
                )
            }
        }
    }
}

@Composable
private fun LocalBubble(content: String) =
    Row(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(size = 16.dp),
                )
                .padding(16.dp)
                .weight(2f),
        ) {
            Text(
                text = content,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

@Composable
private fun RemoteBubble(content: @Composable ColumnScope.() -> Unit) =
    Row(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(size = 16.dp),
                )
                .padding(16.dp)
                .weight(2f),
        ) { Column { content() } }
        Spacer(modifier = Modifier.weight(1f))
    }

@Composable
private fun RemoteSuccess(state: ChatMessage.LlmSuccess) {
    Text(
        text = stringResource(Res.string.succeed_label),
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.labelMedium,
    )
    Text(
        text = stringResource(Res.string.creativity_value, state.creativity),
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.labelMedium,
    )
    Text(
        text = stringResource(Res.string.used_tokens_value, state.usedTokens),
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.labelMedium,
    )
    Text(
        modifier = Modifier
            .padding(10.dp)
            .fillMaxWidth(),
        text = state.header,
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
    )
    state.reasoning?.let { reasoning ->
        Text(
            modifier = Modifier.padding(horizontal = 25.dp, vertical = 10.dp),
            text = stringResource(Res.string.reasoning_label),
            color = MaterialTheme.colorScheme.outline,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            modifier = Modifier.padding(horizontal = 25.dp, vertical = 10.dp),
            text = reasoning,
            color = MaterialTheme.colorScheme.outline,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    state.details.forEach { detail ->
        Text(
            modifier = Modifier.padding(10.dp),
            text = detail,
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Text(
        modifier = Modifier.padding(10.dp),
        text = stringResource(Res.string.summary_label),
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.labelLarge,
    )
    Text(
        modifier = Modifier.padding(10.dp),
        text = state.summary,
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun RemoteQuery(state: ChatMessage.LlmQuery) {
    Text(
        text = stringResource(Res.string.question_label),
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.labelMedium,
    )
    Text(
        text = stringResource(Res.string.creativity_value, state.creativity),
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.labelMedium,
    )
    Text(
        text = stringResource(Res.string.used_tokens_value, state.usedTokens),
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.labelMedium,
    )
    state.reasoning?.let { reasoning ->
        Text(
            modifier = Modifier.padding(horizontal = 25.dp, vertical = 10.dp),
            text = stringResource(Res.string.reasoning_label),
            color = MaterialTheme.colorScheme.outline,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            modifier = Modifier.padding(horizontal = 25.dp, vertical = 10.dp),
            text = reasoning,
            color = MaterialTheme.colorScheme.outline,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Text(
        modifier = Modifier.padding(10.dp),
        text = state.question,
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun RemoteFailure(state: ChatMessage.LlmFailure) {
    Text(
        text = stringResource(Res.string.failed_label),
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.labelMedium,
    )
    Text(
        text = stringResource(Res.string.creativity_value, state.creativity),
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.labelMedium,
    )
    Text(
        text = stringResource(Res.string.used_tokens_value, state.usedTokens),
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.labelMedium,
    )
    state.reasoning?.let { reasoning ->
        Text(
            modifier = Modifier.padding(horizontal = 25.dp, vertical = 10.dp),
            text = stringResource(Res.string.reasoning_label),
            color = MaterialTheme.colorScheme.outline,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            modifier = Modifier.padding(horizontal = 25.dp, vertical = 10.dp),
            text = reasoning,
            color = MaterialTheme.colorScheme.outline,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Text(
        modifier = Modifier.padding(10.dp),
        text = state.reason,
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun RemoteLoading() =
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(32.dp),
            strokeWidth = 8.dp,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stringResource(Res.string.llm_progress),
            color = MaterialTheme.colorScheme.secondary,
        )
    }

@Composable
private fun RemoteError(content: String) =
    Row(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .background(color = MaterialTheme.colorScheme.errorContainer)
                .padding(16.dp)
                .weight(2f),
        ) {
            Text(
                text = content,
                color = MaterialTheme.colorScheme.onError,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }

@Composable
private fun ChatPrompt(
    onPrompt: (String) -> Unit,
) {
    var prompt by rememberSaveable { mutableStateOf("") }
    TextField(
        modifier = Modifier
            .fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(text = stringResource(Res.string.prompt_hint)) },
        value = prompt,
        onValueChange = { prompt = it },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(
            onSend = {
                onPrompt(prompt)
                prompt = ""
            }
        )
    )
}