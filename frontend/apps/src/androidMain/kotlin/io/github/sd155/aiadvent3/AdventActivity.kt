package io.github.sd155.aiadvent3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

internal class AdventActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FrontendAppUi()
        }
    }
}
