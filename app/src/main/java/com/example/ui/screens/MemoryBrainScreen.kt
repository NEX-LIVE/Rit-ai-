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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.MemoryCategory
import com.example.data.local.MemoryEntity
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTopBar
import com.example.ui.components.RitAmbientBackdrop
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.MintSynced
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SoftPinkAccent
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletPrimary
import com.example.ui.viewmodel.RitViewModel

@Composable
fun MemoryBrainScreen(
    viewModel: RitViewModel,
    onBack: () -> Unit
) {
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<MemoryCategory?>(null) }
    var editingMemory by remember { mutableStateOf<MemoryEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val filtered = remember(memories, searchQuery, selectedCategory) {
        memories.filter { mem ->
            val matchesQuery = searchQuery.isBlank() ||
                mem.content.contains(searchQuery, ignoreCase = true)
            val matchesCat = selectedCategory == null ||
                mem.category.equals(selectedCategory!!.name, ignoreCase = true)
            matchesQuery && matchesCat
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = VioletPrimary,
                contentColor = Color.White,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("add_memory_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add memory")
            }
        }
    ) { _ ->
        RitAmbientBackdrop {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GlassTopBar(
                    title = stringResource(R.string.title_brain),
                    subtitle = "${memories.count { it.userConfirmed }} active memories",
                    onBack = onBack
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                        .padding(horizontal = 16.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search Rit's long-term memory...", color = TextSecondary) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
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
                            .testTag("memory_search_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("All") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VioletPrimary.copy(alpha = 0.30f),
                                selectedLabelColor = TextPrimary,
                                containerColor = GlassSurface.copy(alpha = 0.7f),
                                labelColor = TextSecondary
                            )
                        )
                        MemoryCategory.entries.forEach { cat ->
                            val selected = selectedCategory == cat
                            FilterChip(
                                selected = selected,
                                onClick = { selectedCategory = if (selected) null else cat },
                                label = { Text(cat.label) },
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

                Spacer(modifier = Modifier.height(8.dp))

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = VioletPrimary.copy(alpha = 0.75f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Rit's Brain is ready to learn",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tell Rit your name, preferences, ongoing projects, or say \"remember this\" during any voice call.",
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
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filtered, key = { it.id }) { mem ->
                            MemoryCard(
                                memory = mem,
                                onApprove = { viewModel.confirmSensitiveMemory(mem) },
                                onEdit = { editingMemory = mem },
                                onDelete = { viewModel.deleteMemory(mem.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        MemoryEditorDialog(
            title = "Add Memory for Rit",
            initialContent = "",
            initialCategory = MemoryCategory.FACT,
            onDismiss = { showAddDialog = false },
            onSave = { content, category ->
                viewModel.addManualMemory(content, category)
                showAddDialog = false
            }
        )
    }

    editingMemory?.let { target ->
        MemoryEditorDialog(
            title = "Edit Memory",
            initialContent = target.content,
            initialCategory = MemoryCategory.fromRaw(target.category),
            onDismiss = { editingMemory = null },
            onSave = { content, category ->
                viewModel.editMemory(target, content, category)
                editingMemory = null
            }
        )
    }
}

@Composable
private fun MemoryCard(
    memory: MemoryEntity,
    onApprove: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val category = MemoryCategory.fromRaw(memory.category)
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("memory_card_${memory.id}"),
        containerColor = if (!memory.userConfirmed) {
            AmberWarn.copy(alpha = 0.10f)
        } else {
            GlassSurface.copy(alpha = 0.78f)
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = VioletPrimary.copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, VioletPrimary.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = category.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                if (memory.isSensitive) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Sensitive memory",
                        tint = SoftPinkAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (!memory.userConfirmed) {
                    Text(
                        text = "Needs Confirmation",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberWarn
                    )
                }
            }

            Row {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("edit_memory_${memory.id}")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit memory", tint = TextSecondary)
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("delete_memory_${memory.id}")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete memory", tint = RoseAlert)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = memory.content,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )

        if (!memory.userConfirmed) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Text("Discard")
                }
                Button(onClick = onApprove, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Confirm & Keep")
                }
            }
        } else if (memory.isSynced) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Synced to cloud",
                style = MaterialTheme.typography.labelSmall,
                color = MintSynced
            )
        }
    }
}

@Composable
private fun MemoryEditorDialog(
    title: String,
    initialContent: String,
    initialCategory: MemoryCategory,
    onDismiss: () -> Unit,
    onSave: (String, MemoryCategory) -> Unit
) {
    var content by remember { mutableStateOf(initialContent) }
    var category by remember { mutableStateOf(initialCategory) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("What should Rit remember?") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MemoryCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(content, category) },
                enabled = content.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
