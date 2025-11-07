package io.github.sd155.aiadvent3.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ChatScreen(llmApiKey: String, githubApiKey: String) {
    val viewModel: ChatViewModel = viewModel { ChatViewModel(llmApiKey = llmApiKey, githubApiKey = githubApiKey) }
    val state by viewModel.state.collectAsState()

    ChatView(
        state = state,
        onPrompt = { viewModel.onViewIntent(ChatViewIntent.UserPrompted(prompt = it)) },
    )
}