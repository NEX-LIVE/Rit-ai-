package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ai.RitOrbState
import com.example.data.local.VoiceLanguage
import com.example.ui.components.GlassCard
import com.example.ui.components.RitAmbientBackdrop
import com.example.ui.components.RitOrbCanvas
import com.example.ui.theme.CyanListening
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.MintSynced
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SoftPinkAccent
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletPrimary
import com.example.ui.theme.VoidBlack
import com.example.ui.viewmodel.RitViewModel

@Composable
fun VoiceHomeScreen(
    viewModel: RitViewModel,
    onOpenHistory: () -> Unit,
    onOpenBrain: () -> Unit,
    onOpenVoices: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val voiceSession by viewModel.voiceSession.collectAsStateWithLifecycle()
    val pendingSensitive by viewModel.pendingSensitiveMemories.collectAsStateWithLifecycle()
    val isSignedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()

    var micPermissionDenied by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Optional foreground call notification permission */ }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            micPermissionDenied = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            viewModel.startVoiceCall()
        } else {
            micPermissionDenied = true
        }
    }

    val startCallWithPermissionCheck: () -> Unit = {
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            micPermissionDenied = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            viewModel.startVoiceCall()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    RitAmbientBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP HEADER: Brand + Voice/Language Pill + Navigation Icons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "RIT AI",
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = if (isSignedIn && settings.cloudBackupEnabled) {
                                    Icons.Outlined.CloudDone
                                } else {
                                    Icons.Outlined.CloudOff
                                },
                                contentDescription = if (isSignedIn && settings.cloudBackupEnabled) {
                                    "Cloud backup enabled"
                                } else {
                                    "Local device brain"
                                },
                                tint = if (isSignedIn && settings.cloudBackupEnabled) MintSynced else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.rit_tagline),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassIconCircleButton(
                            onClick = onOpenHistory,
                            contentDescription = stringResource(R.string.cd_history),
                            testTag = "history_button"
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = TextPrimary
                            )
                        }

                        GlassIconCircleButton(
                            onClick = onOpenBrain,
                            contentDescription = stringResource(R.string.cd_brain),
                            testTag = "brain_button"
                        ) {
                            BadgedBox(
                                badge = {
                                    if (pendingSensitive.isNotEmpty()) {
                                        Badge(containerColor = SoftPinkAccent) {
                                            Text(
                                                text = pendingSensitive.size.toString(),
                                                color = VoidBlack
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = TextPrimary
                                )
                            }
                        }

                        GlassIconCircleButton(
                            onClick = onOpenSettings,
                            contentDescription = stringResource(R.string.cd_settings),
                            testTag = "settings_button"
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = TextPrimary
                            )
                        }
                    }
                }

                // Active Voice & Privacy Status Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onOpenVoices,
                        shape = RoundedCornerShape(50),
                        color = GlassSurface.copy(alpha = 0.8f),
                        border = BorderStroke(1.dp, GlassBorder),
                        modifier = Modifier.testTag("voice_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = SoftPinkAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${settings.selectedVoiceName} • ${settings.selectedLanguage}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                        }
                    }

                    if (voiceSession.isCallActive) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (voiceSession.isMuted) {
                                GlassSurface
                            } else {
                                CyanListening.copy(alpha = 0.14f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (voiceSession.isMuted) GlassBorder else CyanListening.copy(alpha = 0.6f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            if (voiceSession.isMuted) RoseAlert else MintSynced,
                                            CircleShape
                                        )
                                )
                                Text(
                                    text = if (voiceSession.isMuted) "Mic Muted" else "Mic Active",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // CENTER: Animated AI Orb & Voice-Only Status
            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 560.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                RitOrbCanvas(
                    orbState = voiceSession.orbState,
                    isMuted = voiceSession.isMuted,
                    contentDesc = stringResource(R.string.cd_voice_orb),
                    onClick = {
                        if (voiceSession.isCallActive) {
                            viewModel.endVoiceCall()
                        } else {
                            startCallWithPermissionCheck()
                        }
                    },
                    modifier = Modifier.testTag("voice_orb")
                )

                Spacer(modifier = Modifier.height(20.dp))

                val statusText = when {
                    voiceSession.isMuted && voiceSession.isCallActive ->
                        stringResource(R.string.status_muted)
                    voiceSession.statusLine.isNotBlank() ->
                        voiceSession.statusLine
                    else -> when (voiceSession.orbState) {
                        RitOrbState.IDLE -> stringResource(R.string.status_idle)
                        RitOrbState.CONNECTING -> stringResource(R.string.status_connecting)
                        RitOrbState.LISTENING -> stringResource(R.string.status_listening)
                        RitOrbState.THINKING -> stringResource(R.string.status_thinking)
                        RitOrbState.SPEAKING -> stringResource(R.string.status_speaking)
                        RitOrbState.DISCONNECTED -> stringResource(R.string.status_disconnected)
                    }
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                if (voiceSession.isCallActive) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.mic_active_disclosure),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                // Memory Saved Toast Pill
                AnimatedVisibility(
                    visible = !voiceSession.lastMemorySavedNotice.isNullOrBlank(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        onClick = { viewModel.dismissMemoryNotice() },
                        shape = RoundedCornerShape(16.dp),
                        color = VioletPrimary.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, VioletPrimary.copy(alpha = 0.5f)),
                        modifier = Modifier.padding(top = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = SoftPinkAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = voiceSession.lastMemorySavedNotice.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }

                // Pending Sensitive Memory Approval Card
                if (pendingSensitive.isNotEmpty()) {
                    val latestPending = pendingSensitive.first()
                    Spacer(modifier = Modifier.height(14.dp))
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = GlassSurface.copy(alpha = 0.90f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = SoftPinkAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Sensitive Memory Confirmation",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = latestPending.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.deleteMemory(latestPending.id) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Don't Save")
                            }
                            Button(
                                onClick = { viewModel.confirmSensitiveMemory(latestPending) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save Memory")
                            }
                        }
                    }
                }

                // Error Banner
                if (!voiceSession.errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = RoseAlert.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = voiceSession.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        TextButton(onClick = { viewModel.dismissVoiceError() }) {
                            Text("Dismiss", color = SoftPinkAccent)
                        }
                    }
                }

                // Microphone Permission Rationale Card
                if (micPermissionDenied) {
                    Spacer(modifier = Modifier.height(14.dp))
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.mic_permission_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.mic_permission_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            modifier = Modifier.testTag("grant_mic_button")
                        ) {
                            Text(stringResource(R.string.grant_mic_permission))
                        }
                    }
                }
            }

            // BOTTOM VOICE CALL CONTROLS (Zero text input / zero keyboard)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!voiceSession.isCallActive) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VoiceLanguage.entries.forEach { lang ->
                            val selected = settings.selectedLanguage.equals(lang.displayName, ignoreCase = true)
                            Surface(
                                onClick = { viewModel.selectLanguage(lang) },
                                shape = RoundedCornerShape(50),
                                color = if (selected) VioletPrimary.copy(alpha = 0.28f) else GlassSurface.copy(alpha = 0.65f),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) VioletPrimary else GlassBorder
                                ),
                                modifier = Modifier.testTag("lang_chip_${lang.name.lowercase()}")
                            ) {
                                Text(
                                    text = lang.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) TextPrimary else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    if (voiceSession.isCallActive) {
                        Surface(
                            onClick = { viewModel.toggleMute() },
                            shape = CircleShape,
                            color = if (voiceSession.isMuted) RoseAlert.copy(alpha = 0.25f) else GlassSurface,
                            border = BorderStroke(
                                1.dp,
                                if (voiceSession.isMuted) RoseAlert else GlassBorder
                            ),
                            modifier = Modifier
                                .size(56.dp)
                                .testTag("mute_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (voiceSession.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = stringResource(R.string.cd_mute_mic),
                                    tint = if (voiceSession.isMuted) RoseAlert else TextPrimary
                                )
                            }
                        }
                    }

                    // Primary Start / End Voice Call Button
                    val callActive = voiceSession.isCallActive || voiceSession.orbState == RitOrbState.CONNECTING
                    Button(
                        onClick = {
                            if (callActive) {
                                viewModel.endVoiceCall()
                            } else {
                                startCallWithPermissionCheck()
                            }
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = TextPrimary
                        ),
                        modifier = Modifier
                            .size(76.dp)
                            .shadow(
                                elevation = 18.dp,
                                shape = CircleShape,
                                ambientColor = if (callActive) RoseAlert else VioletPrimary,
                                spotColor = if (callActive) RoseAlert else SoftPinkAccent
                            )
                            .background(
                                brush = if (callActive) {
                                    Brush.linearGradient(listOf(RoseAlert, Color(0xFFE11D48)))
                                } else {
                                    Brush.linearGradient(listOf(VioletPrimary, SoftPinkAccent))
                                },
                                shape = CircleShape
                            )
                            .testTag("call_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (callActive) Icons.Default.CallEnd else Icons.Default.Call,
                            contentDescription = stringResource(
                                if (callActive) R.string.cd_end_call else R.string.cd_start_call
                            ),
                            modifier = Modifier.size(30.dp),
                            tint = Color.White
                        )
                    }
                }

                Text(
                    text = if (voiceSession.isCallActive) "Tap red button to end voice call" else "Voice-only mode • No typing needed",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun GlassIconCircleButton(
    onClick: () -> Unit,
    contentDescription: String,
    testTag: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = GlassSurface.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag(testTag)
        ) {
            Box(contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}
