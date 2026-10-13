package com.abdullah.lockapp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.abdullah.lockapp.appContainer
import com.abdullah.lockapp.ui.theme.LockTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LockTheme { LockAppRoot(appContainer) }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { appContainer.rules.applyDuePending() }
    }
}
