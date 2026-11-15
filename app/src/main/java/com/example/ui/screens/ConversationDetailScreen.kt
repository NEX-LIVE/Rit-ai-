package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.MessageEntity
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTopBar
import com.example.ui.components.RitAmbientBackdrop
import com.example.ui.theme.CyanListening
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SoftPinkAccent
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletPrimary
import com.example.ui.viewmodel.RitViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConversationDetailScreen(
    conversationId: String,
    viewModel: RitViewModel,
    onBack: () -> Unit,
    onContinueInVoiceCall: (String) -> Unit
) {
    val context = LocalContext.current
    val convWithSummary by viewModel.observeConversation(conversationId)
        .collectAsStateWithLifecycle(initialValue = null)
    val messages by viewModel.observeMessages(conversationId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var showDeleteDialog by remember { mutableStateOf(false) }
    var micDeniedBanner by remember { mutableStateOf(false) }
    var playingMessageId by remember { mutableStateOf<String?>(null) }
    val mediaPlayer = remember { mutableStateOf<MediaPlayer?>(null) }
    val dateFmt = remember { SimpleDateFormat("EEE, dd MMM yyyy • hh:mm a", Locale.getDefault()) }

    val stopPlayback: () -> Unit = {
        runCatching { mediaPlayer.value?.stop() }
        runCatching { mediaPlayer.value?.release() }
        mediaPlayer.value = null
        playingMessageId = null
    }

    DisposableEffect(Unit) {
        onDispose {
            stopPlayback()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            micDeniedBanner = false
            stopPlayback()
            onContinueInVoiceCall(conversationId)
        } else {
            micDeniedBanner = true
        }
    }

    RitAmbientBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlassTopBar(
                title = convWithSummary?.conversation?.title ?: "Voice Transcript",
                subtitle = convWithSummary?.conversation?.let {
                    "${dateFmt.format(Date(it.startedAt))} • ${it.voiceName}"
                },
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("delete_detail_conv_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete conversation",
                            tint = RoseAlert
                        )
                    }
                }
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 680.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (micDeniedBanner) {
                    item {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = RoseAlert.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = stringResource(R.string.mic_permission_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }

                convWithSummary?.summary?.let { summary ->
                    item {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = VioletPrimary.copy(alpha = 0.14f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Conversation Summary",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = SoftPinkAccent
                                )
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = SoftPinkAccent.copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        text = summary.moodTag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextPrimary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = summary.summaryText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            if (summary.keyTopicsCsv.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Topics: ${summary.keyTopicsCsv}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    TranscriptTurnRow(
                        message = msg,
                        isPlaying = playingMessageId == msg.id,
                        onToggleAudio = {
                            if (playingMessageId == msg.id) {
                                stopPlayback()
                            } else {
                                val path = msg.audioFilePath
                                if (!path.isNullOrBlank() && File(path).exists()) {
                                    stopPlayback()
                                    runCatching {
                                        val player = MediaPlayer().apply {
                                            setDataSource(path)
                                            prepare()
                                            setOnCompletionListener { stopPlayback() }
                                            start()
                                        }
                                        mediaPlayer.value = player
                                        playingMessageId = msg.id
                                    }
                                }
                            }
                        }
                    )
                }
            }

            // Continue Voice Conversation Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Button(
                    onClick = {
                        val hasMic = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasMic) {
                            stopPlayback()
                            onContinueInVoiceCall(conversationId)
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("continue_voice_button"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.continue_conversation),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Conversation?") },
            text = { Text("This voice session transcript and summary will be permanently removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        stopPlayback()
                        viewModel.deleteConversation(conversationId)
                        showDeleteDialog = false
                        onBack()
                    }
                ) { Text("Delete", color = RoseAlert) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TranscriptTurnRow(
    message: MessageEntity,
    isPlaying: Boolean,
    onToggleAudio: () -> Unit
) {
    val isUser = message.role == "USER"
    val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val hasAudio = remember(message.audioFilePath) {
        !message.audioFilePath.isNullOrBlank() && File(message.audioFilePath).exists()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (isUser) CyanListening.copy(alpha = 0.14f) else GlassSurface.copy(alpha = 0.85f),
            border = BorderStroke(
                1.dp,
                if (isUser) CyanListening.copy(alpha = 0.4f) else GlassBorder
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "You" else "Rit",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isUser) CyanListening else SoftPinkAccent
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = timeFmt.format(Date(message.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = message.transcript,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                if (hasAudio) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onToggleAudio,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Stop audio clip" else "Play audio clip",
                            tint = SoftPinkAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isPlaying) "Stop Audio" else "Play Recording",
                            style = MaterialTheme.typography.labelSmall,
                            color = SoftPinkAccent
                        )
                    }
                }
            }
        }
    }
}
