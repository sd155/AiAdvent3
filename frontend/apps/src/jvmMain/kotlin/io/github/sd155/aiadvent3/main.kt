package io.github.sd155.aiadvent3

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "AiAdvent3",
    ) {
        FrontendAppUi()
    }
}
