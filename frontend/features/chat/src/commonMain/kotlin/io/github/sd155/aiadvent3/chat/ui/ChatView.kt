package io.github.sd155.aiadvent3.chat.ui

import aiadvent3.frontend.features.chat.generated.resources.Res
import aiadvent3.frontend.features.chat.generated.resources.llm_progress
import aiadvent3.frontend.features.chat.generated.resources.prompt_hint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
                is ChatMessage.UserMessage -> UserBubble(message)
                ChatMessage.AgentProgress -> AgentProgress()
                is ChatMessage.AgentMessage -> AgentBubble(message)
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
private fun UserBubble(message: ChatMessage.UserMessage) =
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
                modifier = Modifier.padding(10.dp),
                text = message.content,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

@Composable
private fun AgentBubble(message: ChatMessage.AgentMessage) =
    Row(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(size = 16.dp),
                )
                .padding(16.dp)
                .weight(2f),
        ) {
            Text(
                text = message.agentTag,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                modifier = Modifier.padding(10.dp),
                text = message.content,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }

@Composable
private fun AgentProgress() =
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