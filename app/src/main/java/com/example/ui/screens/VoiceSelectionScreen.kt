package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.VoicePreferenceEntity
import com.example.data.voice.GeminiVoiceCatalog
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTopBar
import com.example.ui.components.RitAmbientBackdrop
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SoftPinkAccent
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletPrimary
import com.example.ui.viewmodel.RitViewModel

@Composable
fun VoiceSelectionScreen(
    viewModel: RitViewModel,
    onBack: () -> Unit
) {
    val voices by viewModel.voices.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val playingVoice by viewModel.playingPreviewVoice.collectAsStateWithLifecycle()
    val previewError by viewModel.previewError.collectAsStateWithLifecycle()

    RitAmbientBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlassTopBar(
                title = stringResource(R.string.title_voices),
                subtitle = "${voices.size} official Gemini native female voices",
                onBack = {
                    viewModel.stopVoicePreview()
                    onBack()
                }
            )

            if (!previewError.isNullOrBlank()) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    containerColor = RoseAlert.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = previewError.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    TextButton(onClick = { viewModel.dismissPreviewError() }) {
                        Text("Dismiss", color = SoftPinkAccent)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(voices, key = { it.voiceName }) { voice ->
                    val isSelected = voice.voiceName.equals(settings.selectedVoiceName, ignoreCase = true)
                    val isPlaying = playingVoice == voice.voiceName
                    VoiceOptionCard(
                        voice = voice,
                        isSelected = isSelected,
                        isPlayingPreview = isPlaying,
                        onSelect = { viewModel.selectVoice(voice.voiceName) },
                        onTogglePreview = {
                            if (isPlaying) {
                                viewModel.stopVoicePreview()
                            } else {
                                viewModel.playVoicePreview(voice.voiceName)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceOptionCard(
    voice: VoicePreferenceEntity,
    isSelected: Boolean,
    isPlayingPreview: Boolean,
    onSelect: () -> Unit,
    onTogglePreview: () -> Unit
) {
    val catalogMeta = GeminiVoiceCatalog.findByName(voice.voiceName)
    val accent = Color(catalogMeta.accentColorHex)

    GlassCard(
        onClick = onSelect,
        containerColor = if (isSelected) {
            VioletPrimary.copy(alpha = 0.22f)
        } else {
            GlassSurface.copy(alpha = 0.78f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voice_card_${voice.voiceName}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.20f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = accent
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = voice.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = accent.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = voice.toneDescription,
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = voice.personalityFit,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onTogglePreview,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("preview_voice_${voice.voiceName}")
                ) {
                    if (isPlayingPreview) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(30.dp),
                                strokeWidth = 2.dp,
                                color = SoftPinkAccent
                            )
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop preview for ${voice.displayName}",
                                tint = SoftPinkAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Gemini voice preview for ${voice.displayName}",
                            tint = TextPrimary
                        )
                    }
                }

                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected voice",
                        tint = SoftPinkAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
