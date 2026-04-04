package com.shelfscan.shared.feature.review

import com.shelfscan.shared.core.model.*
import com.shelfscan.shared.data.repository.CollectionRepository
import com.shelfscan.shared.domain.export.ExportCollectionUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ReviewAction {
    data class LoadSession(val session: ScanSession) : ReviewAction
    data class EditItem(val item: MediaItem) : ReviewAction
    data class SaveToCollection(val collectionId: String, val collectionName: String) : ReviewAction
    data class ExportCsv(val items: List<MediaItem>) : ReviewAction
    data object DiscardAll : ReviewAction
}

data class ReviewState(
    val items: List<MediaItem> = emptyList(),
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

    fun onAction(action: ReviewAction) {
        when (action) {
            is ReviewAction.LoadSession -> {
                _state.value = ReviewState(items = action.session.detectedItems)
            }
            is ReviewAction.EditItem -> {
                val updated = _state.value.items.map { if (it.id == action.item.id) action.item else it }
                _state.value = _state.value.copy(items = updated)
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
