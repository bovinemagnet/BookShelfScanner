package com.shelfscan.shared.feature.review

import com.shelfscan.shared.core.model.*
import com.shelfscan.shared.data.repository.CollectionRepository
import com.shelfscan.shared.domain.export.ExportCollectionUseCase
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ReviewAction {
    data class LoadSession(val session: ScanSession) : ReviewAction
    data class EditItem(val item: MediaItem) : ReviewAction
    data class DeleteItem(val id: String) : ReviewAction
    data object AddItem : ReviewAction
    data class StartEditing(val id: String) : ReviewAction
    data object StopEditing : ReviewAction
    data object ApproveAll : ReviewAction
    data class SaveToCollection(val collectionId: String, val collectionName: String) : ReviewAction
    data class ExportCsv(val items: List<MediaItem>) : ReviewAction
    data object DiscardAll : ReviewAction
}

data class ReviewState(
    val items: List<MediaItem> = emptyList(),
    val editingItemId: String? = null,
    val savedToCollection: Boolean = false,
    val exportedCsv: String? = null,
    val error: ScanError? = null,
    val isLoading: Boolean = false
)

class ReviewViewModel(
    private val collectionRepository: CollectionRepository,
    private val exportUseCase: ExportCollectionUseCase = ExportCollectionUseCase(),
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(ReviewState())
    val state: StateFlow<ReviewState> = _state.asStateFlow()

    @OptIn(ExperimentalUuidApi::class)
    fun onAction(action: ReviewAction) {
        when (action) {
            is ReviewAction.LoadSession -> {
                _state.value = ReviewState(items = action.session.detectedItems)
            }
            is ReviewAction.EditItem -> {
                val updated = _state.value.items.map { if (it.id == action.item.id) action.item else it }
                _state.value = _state.value.copy(items = updated, editingItemId = null)
            }
            is ReviewAction.DeleteItem -> {
                val current = _state.value
                val filtered = current.items.filter { it.id != action.id }
                val editingId = if (current.editingItemId == action.id) null else current.editingItemId
                _state.value = current.copy(items = filtered, editingItemId = editingId)
            }
            is ReviewAction.AddItem -> {
                val newId = Uuid.random().toString()
                val newItem = MediaItem(
                    id = newId,
                    mediaType = MediaType.UNKNOWN,
                    title = null,
                    creatorName = null,
                    subtitle = null,
                    normalizedTitle = null,
                    normalizedCreatorName = null,
                    confidence = ConfidenceScore(
                        value = 0.0,
                        band = ConfidenceBand.NEEDS_REVIEW,
                        reasons = listOf("Manually added")
                    ),
                    source = ItemSource.USER_EDITED,
                    cropRef = null,
                    rawText = emptyList()
                )
                _state.value = _state.value.copy(
                    items = _state.value.items + newItem,
                    editingItemId = newId
                )
            }
            is ReviewAction.StartEditing -> {
                _state.value = _state.value.copy(editingItemId = action.id)
            }
            is ReviewAction.StopEditing -> {
                _state.value = _state.value.copy(editingItemId = null)
            }
            is ReviewAction.ApproveAll -> {
                val approved = _state.value.items.map { it.copy(source = ItemSource.USER_EDITED) }
                _state.value = _state.value.copy(items = approved, editingItemId = null)
            }
            is ReviewAction.SaveToCollection -> saveToCollection(
                action.collectionId,
                action.collectionName
            )
            is ReviewAction.ExportCsv -> {
                val csv = exportUseCase.execute(action.items, ExportCollectionUseCase.ExportFormat.CSV)
                _state.value = _state.value.copy(exportedCsv = csv)
            }
            is ReviewAction.DiscardAll -> {
                _state.value = ReviewState()
            }
        }
    }

    private fun saveToCollection(collectionId: String, collectionName: String) {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val collection = Collection(
                    id = collectionId,
                    name = collectionName,
                    createdAt = 0L,
                    items = _state.value.items
                )
                collectionRepository.saveCollection(collection)
                _state.value = _state.value.copy(savedToCollection = true, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = ScanError.SaveFailed, isLoading = false)
            }
        }
    }
}
