package io.github.sd155.aiadvent3

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import io.github.sd155.aiadvent3.build.GITHUB_API_KEY
import io.github.sd155.aiadvent3.build.LLM_API_KEY
import io.github.sd155.aiadvent3.chat.ui.ChatScreen

@Composable
internal fun FrontendAppUi() {
    MaterialTheme {
        ChatScreen(llmApiKey = LLM_API_KEY, githubApiKey = GITHUB_API_KEY)
    }
}
