package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.GeminiVoicePreviewPlayer
import com.example.ai.MemoryAnalyzerService
import com.example.ai.RitLiveVoiceEngine
import com.example.data.local.RitDatabase
import com.example.data.remote.CloudSyncRepository
import com.example.data.repository.RitRepository
import com.example.navigation.RitNavGraph
import com.example.security.configureAppCheck
import com.example.service.RitVoiceCallService
import com.example.ui.theme.RitAiTheme
import com.example.ui.viewmodel.RitViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // MUST be initialized before any Firebase AI Logic or Firestore calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val appContext = context.applicationContext

            val ritViewModel: RitViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        val db = RitDatabase.getInstance(appContext)
                        val cloudSync = CloudSyncRepository(appContext)
                        val repository = RitRepository(appContext, db, cloudSync)
                        val memoryAnalyzer = MemoryAnalyzerService(repository)
                        val voiceEngine = RitLiveVoiceEngine(appContext, repository, memoryAnalyzer)
                        val previewPlayer = GeminiVoicePreviewPlayer(appContext)
                        RitViewModel(
                            repository = repository,
                            voiceEngine = voiceEngine,
                            previewPlayer = previewPlayer
                        )
                    }
                }
            )

            val voiceSession by ritViewModel.voiceSession.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                ritViewModel.attemptSilentSignIn(context)
            }

            LaunchedEffect(voiceSession.isCallActive) {
                if (voiceSession.isCallActive) {
                    RitVoiceCallService.start(appContext)
                } else {
                    RitVoiceCallService.stop(appContext)
                }
            }

            RitAiTheme {
                RitNavGraph(viewModel = ritViewModel)
            }
        }
    }
}
