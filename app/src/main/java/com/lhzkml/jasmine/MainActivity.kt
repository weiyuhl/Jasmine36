package com.lhzkml.jasmine

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.lhzkml.jasmine.ui.ChatViewModel
import com.lhzkml.jasmine.ui.navigation.AppNavigation
import com.lhzkml.jasmine.ui.theme.JasmineTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }

    private val viewModel: ChatViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            JasmineTheme {
                AppNavigation(viewModel = viewModel)
            }
        }

        viewModel.initialize(this, object : ChatViewModel.LifecycleCallbacks {
            override fun finishAndLaunch(intent: Intent) {
                startActivity(intent)
                finish()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.handleNewIntent(intent)
    }
}
