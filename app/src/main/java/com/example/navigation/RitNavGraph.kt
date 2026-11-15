package com.example.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.ChatHistoryScreen
import com.example.ui.screens.ConversationDetailScreen
import com.example.ui.screens.MemoryBrainScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VoiceHomeScreen
import com.example.ui.screens.VoiceSelectionScreen
import com.example.ui.viewmodel.RitViewModel

object RitRoutes {
    const val ROUTE_VOICE_HOME = "voice_home"
    const val ROUTE_HISTORY = "history"
    const val ROUTE_CONVERSATION_DETAIL = "conversation_detail/{conversationId}"
    const val ROUTE_VOICES = "voices"
    const val ROUTE_BRAIN = "brain"
    const val ROUTE_SETTINGS = "settings"

    fun conversationDetail(conversationId: String): String =
        "conversation_detail/${Uri.encode(conversationId)}"
}

@Composable
fun RitNavGraph(viewModel: RitViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = RitRoutes.ROUTE_VOICE_HOME
    ) {
        composable(RitRoutes.ROUTE_VOICE_HOME) {
            VoiceHomeScreen(
                viewModel = viewModel,
                onOpenHistory = { navController.navigate(RitRoutes.ROUTE_HISTORY) },
                onOpenBrain = { navController.navigate(RitRoutes.ROUTE_BRAIN) },
                onOpenVoices = { navController.navigate(RitRoutes.ROUTE_VOICES) },
                onOpenSettings = { navController.navigate(RitRoutes.ROUTE_SETTINGS) }
            )
        }

        composable(RitRoutes.ROUTE_HISTORY) {
            ChatHistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenConversation = { convId ->
                    navController.navigate(RitRoutes.conversationDetail(convId))
                }
            )
        }

        composable(
            route = RitRoutes.ROUTE_CONVERSATION_DETAIL,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val convId = Uri.decode(backStackEntry.arguments?.getString("conversationId").orEmpty())
            ConversationDetailScreen(
                conversationId = convId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onContinueInVoiceCall = { id ->
                    viewModel.startVoiceCall(continueFromConversationId = id)
                    navController.popBackStack(RitRoutes.ROUTE_VOICE_HOME, inclusive = false)
                }
            )
        }

        composable(RitRoutes.ROUTE_VOICES) {
            VoiceSelectionScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(RitRoutes.ROUTE_BRAIN) {
            MemoryBrainScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(RitRoutes.ROUTE_SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenVoices = { navController.navigate(RitRoutes.ROUTE_VOICES) },
                onOpenBrain = { navController.navigate(RitRoutes.ROUTE_BRAIN) },
                onOpenHistory = { navController.navigate(RitRoutes.ROUTE_HISTORY) }
            )
        }
    }
}
