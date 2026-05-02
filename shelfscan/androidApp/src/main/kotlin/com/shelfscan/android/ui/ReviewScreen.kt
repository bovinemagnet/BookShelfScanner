package com.shelfscan.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shelfscan.shared.core.model.ConfidenceBand
import com.shelfscan.shared.core.model.ItemSource
import com.shelfscan.shared.core.model.MediaItem
import com.shelfscan.shared.core.model.MediaType
import com.shelfscan.shared.core.model.ScanError
import com.shelfscan.shared.feature.review.ReviewAction
import com.shelfscan.shared.feature.review.ReviewState
import com.shelfscan.shared.feature.review.ReviewViewModel

@Composable
fun ReviewScreen(
    reviewViewModel: ReviewViewModel,
    reviewState: ReviewState,
    onDone: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(reviewState.items.size) {
        if (reviewState.items.isNotEmpty() && reviewState.editingItemId == reviewState.items.last().id) {
            listState.animateScrollToItem(reviewState.items.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text("Review Results", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        if (reviewState.items.isEmpty()) {
            Text("No items detected. Try scanning again.")
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${reviewState.items.size} item(s) detected",
                    style = MaterialTheme.typography.bodyLarge
                )
                OutlinedButton(
                    onClick = { reviewViewModel.onAction(ReviewAction.AddItem) }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Missing Item")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f)
            ) {
                items(reviewState.items, key = { it.id }) { item ->
                    val isEditing = reviewState.editingItemId == item.id
                    val cardColors = when (item.confidence.band) {
                        ConfidenceBand.NEEDS_REVIEW -> CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                        ConfidenceBand.LOW -> CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                        else -> CardDefaults.cardColors()
                    }

                    Card(
                        colors = cardColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.title ?: "Unknown Title",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Row {
                                    IconButton(
                                        onClick = { reviewViewModel.onAction(ReviewAction.StartEditing(item.id)) },
                                        enabled = reviewState.editingItemId == null || isEditing
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                                    }
                                    IconButton(
                                        onClick = { reviewViewModel.onAction(ReviewAction.DeleteItem(item.id)) }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    }
                                }
                            }

                            AnimatedVisibility(visible = isEditing) {
                                EditItemForm(
                                    item = item,
                                    onSave = { edited -> reviewViewModel.onAction(ReviewAction.EditItem(edited)) },
                                    onCancel = { reviewViewModel.onAction(ReviewAction.StopEditing) }
                                )
                            }

                            AnimatedVisibility(visible = !isEditing) {
                                Column {
                                    item.creatorName?.let {
                                        Text(text = it, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(item.confidence.band.name) }
                                        )
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(item.mediaType.name) }
                                        )
                                    }
                                    if (item.rawText.isNotEmpty()) {
                                        Text(
                                            text = "Raw: ${item.rawText.joinToString(" | ")}",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        reviewState.error?.let { error ->
            Text(
                text = reviewErrorMessage(error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        when {
            reviewState.savedToCollection -> {
                Text("Saved!", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Back to Home")
                }
            }
            reviewState.isLoading -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Saving…")
                }
            }
            else -> {
                var showDiscardConfirm by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            reviewViewModel.onAction(
                                ReviewAction.SaveToCollection(
                                    collectionId = "default",
                                    collectionName = "My Books"
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save")
                    }
                    FilledTonalButton(
                        onClick = { reviewViewModel.onAction(ReviewAction.ApproveAll) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Approve All")
                    }
                    OutlinedButton(
                        onClick = { showDiscardConfirm = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Discard")
                    }
                }

                if (showDiscardConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDiscardConfirm = false },
                        title = { Text("Discard scan?") },
                        text = {
                            Text(
                                "This removes all ${reviewState.items.size} detected " +
                                    "items. You can't undo this."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showDiscardConfirm = false
                                reviewViewModel.onAction(ReviewAction.DiscardAll)
                                onDone()
                            }) {
                                Text("Discard", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDiscardConfirm = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun reviewErrorMessage(error: ScanError): String = when (error) {
    ScanError.SaveFailed -> "Couldn't save the collection. Please try again."
    ScanError.MetadataLookupFailed -> "Couldn't look up book details right now."
    ScanError.OcrFailed -> "Couldn't read text on the spines."
    ScanError.ImageProcessingFailed -> "Couldn't process the photo."
    ScanError.CameraUnavailable -> "Camera unavailable."
    ScanError.PermissionDenied -> "Camera permission required."
    ScanError.ImageTooBlurry -> "Photo was too blurry."
}

@Composable
private fun EditItemForm(
    item: MediaItem,
    onSave: (MediaItem) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember(item.id) { mutableStateOf(item.title ?: "") }
    var creator by remember(item.id) { mutableStateOf(item.creatorName ?: "") }
    var mediaType by remember(item.id) { mutableStateOf(item.mediaType) }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = creator,
            onValueChange = { creator = it },
            label = { Text("Creator") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text("Type", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MediaType.entries.forEach { type ->
                FilterChip(
                    selected = mediaType == type,
                    onClick = { mediaType = type },
                    label = { Text(type.name) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onSave(
                        item.copy(
                            title = title.ifBlank { null },
                            creatorName = creator.ifBlank { null },
                            mediaType = mediaType,
                            source = ItemSource.USER_EDITED
                        )
                    )
                }
            ) {
                Text("Save")
            }
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}
