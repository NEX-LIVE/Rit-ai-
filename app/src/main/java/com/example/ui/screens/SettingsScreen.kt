package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.VoiceLanguage
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTopBar
import com.example.ui.components.RitAmbientBackdrop
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.MintSynced
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SoftPinkAccent
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletPrimary
import com.example.ui.viewmodel.RitViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: RitViewModel,
    onBack: () -> Unit,
    onOpenVoices: () -> Unit,
    onOpenBrain: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isSignedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()
    val authEmail by viewModel.authUserEmail.collectAsStateWithLifecycle()
    val authName by viewModel.authUserName.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var showDeleteHistoryDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var alsoClearCloud by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = viewModel.exportAllDataJson()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        }
                    }
                }.onSuccess {
                    exportMessage = "RIT AI data exported successfully."
                }.onFailure {
                    exportMessage = it.localizedMessage ?: "Export failed."
                }
            }
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
                title = stringResource(R.string.title_settings),
                subtitle = "Voice, Memory, Cloud Backup & Privacy",
                onBack = onBack
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val activeBanner = statusBanner ?: exportMessage
                if (!activeBanner.isNullOrBlank()) {
                    item {
                        GlassCard(containerColor = VioletPrimary.copy(alpha = 0.18f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = activeBanner,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    viewModel.dismissBanner()
                                    exportMessage = null
                                }) {
                                    Text("OK", color = SoftPinkAccent)
                                }
                            }
                        }
                    }
                }

                // 1. Gemini Female Voice Selection
                item {
                    SettingsActionCard(
                        icon = Icons.Default.GraphicEq,
                        title = "1. Gemini Female Voice Selection",
                        subtitle = "Active voice: ${settings.selectedVoiceName} (18 official Gemini voices)",
                        onClick = onOpenVoices,
                        testTag = "settings_voice_row"
                    )
                }

                // 2. Language Selection
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = SoftPinkAccent)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "2. Language Selection",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Rit adapts pronunciation, natural pauses, and conversational warmth.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VoiceLanguage.entries.forEach { lang ->
                                val selected = settings.selectedLanguage.equals(lang.displayName, ignoreCase = true)
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.selectLanguage(lang) },
                                    label = { Text(lang.displayName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = VioletPrimary.copy(alpha = 0.30f),
                                        selectedLabelColor = TextPrimary,
                                        containerColor = GlassSurface.copy(alpha = 0.7f),
                                        labelColor = TextSecondary
                                    )
                                )
                            }
                        }
                    }
                }

                // 3. Memory Management
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Psychology, contentDescription = null, tint = VioletPrimary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "3. Memory Management",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                            }
                            TextButton(
                                onClick = onOpenBrain,
                                modifier = Modifier.testTag("settings_open_brain_button")
                            ) {
                                Text("Open Brain", color = SoftPinkAccent)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        SettingsToggleRow(
                            title = "Auto-remember useful facts",
                            subtitle = "Automatically extract preferences, projects, and dates from voice chats",
                            checked = settings.allowAutoMemoryExtraction,
                            onCheckedChange = { viewModel.updateAutoExtract(it) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsToggleRow(
                            title = "Ask before sensitive info",
                            subtitle = "Confirm before storing sensitive personal details in Rit's Brain",
                            checked = settings.askBeforeSensitiveMemory,
                            onCheckedChange = { viewModel.updateAskBeforeSensitive(it) }
                        )
                    }
                }

                // 4. Conversation History Management
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, tint = SoftPinkAccent)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "4. Conversation History Management",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = onOpenHistory,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("View History")
                            }
                            OutlinedButton(
                                onClick = { showDeleteHistoryDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseAlert),
                                border = BorderStroke(1.dp, RoseAlert.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Delete History")
                            }
                        }
                    }
                }

                // 5. Cloud Backup Settings (Google Sign-In + Firestore)
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = MintSynced)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "5. Cloud Backup Settings",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (!isSignedIn) {
                            Text(
                                text = "Sign in with Google to optionally back up and sync Rit's memories and voice history across devices.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.signInWithGoogle(context) },
                                enabled = !isSyncing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("google_sign_in_button")
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Text(stringResource(R.string.sign_in_google))
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MintSynced.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, MintSynced.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = authName ?: "Google Account Connected",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary
                                        )
                                        authEmail?.let {
                                            Text(
                                                text = it,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                    TextButton(
                                        onClick = { viewModel.signOutGoogle(context) },
                                        enabled = !isSyncing,
                                        modifier = Modifier.testTag("google_sign_out_button")
                                    ) {
                                        Text(stringResource(R.string.sign_out_google), color = RoseAlert)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Automatic Cloud Sync",
                                subtitle = "Back up memories, summaries, and transcripts to private Firestore storage",
                                checked = settings.cloudBackupEnabled,
                                onCheckedChange = { viewModel.setCloudBackupEnabled(it) }
                            )
                            settings.lastSyncedAt?.let { syncedAt ->
                                val fmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Last synced: ${fmt.format(Date(syncedAt))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MintSynced
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { viewModel.syncNow() },
                                    enabled = !isSyncing,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sync Now")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.restoreNow() },
                                    enabled = !isSyncing,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Restore")
                                }
                            }
                        }
                    }
                }

                // 6. Privacy Settings
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = SoftPinkAccent)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "6. Privacy Settings",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsToggleRow(
                            title = "Allow Barge-In Interruption",
                            subtitle = "Speak anytime while Rit is talking to naturally interrupt",
                            checked = settings.bargeInEnabled,
                            onCheckedChange = { viewModel.updateBargeIn(it) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsToggleRow(
                            title = "Store Audio Recordings (Optional)",
                            subtitle = "Off by default. Rit never records audio secretly; only transcripts & summaries are saved unless enabled.",
                            checked = settings.storeAudioRecordings,
                            onCheckedChange = { viewModel.updateStoreAudioRecordings(it) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                val fileName = "rit_ai_backup_${System.currentTimeMillis()}.json"
                                exportJsonLauncher.launch(fileName)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("export_data_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export All Data (JSON)")
                        }
                    }
                }

                // 7. Clear All Data
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = RoseAlert.copy(alpha = 0.10f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = RoseAlert)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "7. Clear All Data",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Permanently wipe local Room memories, transcripts, summaries, and reset preferences.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { showClearAllDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = RoseAlert),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("clear_all_data_button")
                        ) {
                            Text("Clear All RIT AI Data", color = Color.White)
                        }
                    }
                }

                // 8. About Rit AI
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = VioletPrimary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "8. About Rit AI",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "RIT AI v1.0 • Voice-Only Personal Companion\nPowered by Google Gemini Live API via Firebase AI Logic & App Check, with local Room Database persistence and optional zero-trust Firestore cloud backup.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }

    if (showDeleteHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteHistoryDialog = false },
            title = { Text("Delete All Conversation History?") },
            text = { Text("All saved voice transcripts and summaries will be deleted. Long-term memories in Rit's Brain will remain.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllConversations()
                        showDeleteHistoryDialog = false
                    }
                ) { Text("Delete", color = RoseAlert) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteHistoryDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear All Data?") },
            text = {
                Column {
                    Text("This permanently erases all local memories, transcripts, summaries, and settings.")
                    if (isSignedIn) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = alsoClearCloud,
                                onCheckedChange = { alsoClearCloud = it }
                            )
                            Text("Also delete my Firestore cloud backup")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllData(alsoDeleteCloud = isSignedIn && alsoClearCloud)
                        showClearAllDialog = false
                    }
                ) { Text("Erase Everything", color = RoseAlert) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    GlassCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(icon, contentDescription = null, tint = SoftPinkAccent)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextSecondary
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
