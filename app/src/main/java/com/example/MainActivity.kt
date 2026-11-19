package com.example

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.SonicBloomApp
import com.example.ui.theme.SonicBloomTheme
import com.example.ui.theme.resolveIsDarkTheme
import com.example.ui.viewmodel.SonicBloomViewModel

class MainActivity : ComponentActivity() {

    private var incomingAudioUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        extractAudioUri(intent)?.let { incomingAudioUri = it }

        val container = (application as SonicBloomApplication).container
        setContent {
            val vm: SonicBloomViewModel = viewModel(
                factory = SonicBloomViewModel.provideFactory(container)
            )
            val libraryState by vm.libraryState.collectAsStateWithLifecycle()
            val isDarkTheme = resolveIsDarkTheme(libraryState.themeMode)

            LaunchedEffect(isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                )
            }

            SonicBloomTheme(darkTheme = isDarkTheme) {
                SonicBloomApp(
                    viewModel = vm,
                    incomingAudioUri = incomingAudioUri,
                    onConsumedIncomingUri = { incomingAudioUri = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractAudioUri(intent)?.let { incomingAudioUri = it }
    }

    private fun extractAudioUri(intent: Intent?): Uri? {
        if (intent?.action == Intent.ACTION_VIEW) {
            return intent.data
        }
        return null
    }
}
