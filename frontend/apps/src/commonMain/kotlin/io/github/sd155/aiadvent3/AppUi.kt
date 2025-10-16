package io.github.sd155.aiadvent3

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import io.github.sd155.aiadvent3.build.API_KEY
import io.github.sd155.aiadvent3.chat.ui.ChatScreen

@Composable
internal fun FrontendAppUi() {
    MaterialTheme {
        ChatScreen(apiKey = API_KEY)
    }
}
