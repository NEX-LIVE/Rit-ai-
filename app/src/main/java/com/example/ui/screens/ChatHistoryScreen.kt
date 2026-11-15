package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.ConversationWithSummary
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
import com.example.ui.viewmodel.DateFilterType
import com.example.ui.viewmodel.RitViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistoryScreen(
    viewModel: RitViewModel,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val query by viewModel.historyQuery.collectAsStateWithLifecycle()
    val dateFilter by viewModel.dateFilter.collectAsStateWithLifecycle()

    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var conversationToDelete by remember { mutableStateOf<ConversationWithSummary?>(null) }

    RitAmbientBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlassTopBar(
                title = stringResource(R.string.title_history),
                subtitle = "${conversations.size} saved voice sessions",
                onBack = onBack,
                actions = {
                    if (conversations.isNotEmpty()) {
                        IconButton(
                            onClick = { showDeleteAllDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("delete_all_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Delete all conversation history",
                                tint = RoseAlert
                            )
                        }
                    }
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
                    .padding(horizontal = 16.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.setHistoryQuery(it) },
                    placeholder = { Text("Search spoken transcripts or summaries...", color = TextSecondary) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setHistoryQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search", tint = TextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VioletPrimary,
                        unfocusedBorderColor = GlassBorder,
                        focusedContainerColor = GlassSurface.copy(alpha = 0.78f),
                        unfocusedContainerColor = GlassSurface.copy(alpha = 0.65f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_search_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DateFilterType.entries.forEach { filterType ->
                        val selected = dateFilter.type == filterType
                        val label = if (filterType == DateFilterType.CUSTOM_DAY &&
                            selected &&
                            dateFilter.customDayStartMs != null
                        ) {
                            SimpleDateFormat("dd MMM", Locale.getDefault())
                                .format(Date(dateFilter.customDayStartMs!!))
                        } else {
                            filterType.label
                        }

                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (filterType == DateFilterType.CUSTOM_DAY) {
                                    showDatePicker = true
                                } else {
                                    viewModel.setDateFilter(filterType)
                                }
                            },
                            label = { Text(label) },
                            leadingIcon = if (filterType == DateFilterType.CUSTOM_DAY) {
                                {
                                    Icon(
                                        Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VioletPrimary.copy(alpha = 0.30f),
                                selectedLabelColor = TextPrimary,
                                containerColor = GlassSurface.copy(alpha = 0.7f),
                                labelColor = TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = GlassBorder,
                                selectedBorderColor = VioletPrimary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (conversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = VioletPrimary.copy(alpha = 0.75f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (query.isNotBlank() || dateFilter.type != DateFilterType.ALL) {
                                "No matching voice conversations"
                            } else {
                                "No voice conversations yet"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Start a voice call with Rit and your spoken transcripts & summaries will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(conversations, key = { it.conversation.id }) { item ->
                        ConversationHistoryCard(
                            item = item,
                            onClick = { onOpenConversation(item.conversation.id) },
                            onDelete = { conversationToDelete = item }
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = utcMillis
                            }
                            val localCal = Calendar.getInstance().apply {
                                set(
                                    utcCal.get(Calendar.YEAR),
                                    utcCal.get(Calendar.MONTH),
                                    utcCal.get(Calendar.DAY_OF_MONTH),
                                    0, 0, 0
                                )
                                set(Calendar.MILLISECOND, 0)
                            }
                            viewModel.setDateFilter(DateFilterType.CUSTOM_DAY, localCal.timeInMillis)
                        }
                        showDatePicker = false
                    }
                ) { Text("Filter") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    conversationToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { conversationToDelete = null },
            title = { Text("Delete Conversation?") },
            text = { Text("This will permanently remove \"${target.conversation.title}\" and its voice transcript.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteConversation(target.conversation.id)
                        conversationToDelete = null
                    }
                ) { Text("Delete", color = RoseAlert) }
            },
            dismissButton = {
                TextButton(onClick = { conversationToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete All Voice History?") },
            text = { Text("This deletes all saved voice transcripts and conversation summaries. Long-term memories in Rit's Brain remain untouched.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllConversations()
                        showDeleteAllDialog = false
                    }
                ) { Text("Delete All", color = RoseAlert) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ConversationHistoryCard(
    item: ConversationWithSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val conv = item.conversation
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault()) }

    GlassCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_card_${conv.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conv.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateFmt.format(Date(conv.startedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("delete_conv_${conv.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete conversation",
                    tint = TextSecondary
                )
            }
        }

        if (!item.summary?.summaryText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.summary!!.summaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallTag(text = conv.voiceName, accent = SoftPinkAccent)
            SmallTag(text = conv.language, accent = VioletPrimary)
            SmallTag(text = "${conv.messageCount} turns", accent = TextSecondary)
            item.summary?.moodTag?.takeIf { it.isNotBlank() }?.let { mood ->
                SmallTag(text = mood, accent = SoftPinkAccent)
            }
        }
    }
}

@Composable
private fun SmallTag(text: String, accent: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
