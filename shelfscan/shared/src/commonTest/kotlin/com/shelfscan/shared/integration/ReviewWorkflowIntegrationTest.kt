package com.shelfscan.shared.integration

import com.shelfscan.shared.core.model.ItemSource
import com.shelfscan.shared.core.model.MediaItem
import com.shelfscan.shared.data.repository.DefaultCollectionRepository
import com.shelfscan.shared.domain.export.ExportCollectionUseCase
import com.shelfscan.shared.feature.review.ReviewAction
import com.shelfscan.shared.feature.review.ReviewState
import com.shelfscan.shared.feature.review.ReviewViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewWorkflowIntegrationTest {

    private val collectionRepository = DefaultCollectionRepository()
    private val testScope = TestScope()
    private val viewModel = ReviewViewModel(
        collectionRepository = collectionRepository,
        exportUseCase = ExportCollectionUseCase(),
        scope = testScope
    )

    private fun loadSession(vararg items: MediaItem) {
        viewModel.onAction(ReviewAction.LoadSession(makeScanSession(items = items.toList())))
    }

    private fun getSavedCollection(id: String): com.shelfscan.shared.core.model.Collection {
        val result = runBlocking { collectionRepository.getCollection(id) }
        assertNotNull(result, "Collection '$id' should have been persisted")
        return result
    }

    @Test
    fun `load edit and save persists edited item to collection`() {
        loadSession(
            makeMediaItem(id = "item_0", title = "Clean Code"),
            makeMediaItem(id = "item_1", title = "Refactoring")
        )

        val edited = viewModel.state.value.items.first().copy(
            title = "Clean Code: Updated",
            source = ItemSource.USER_EDITED
        )
        viewModel.onAction(ReviewAction.EditItem(edited))
        viewModel.onAction(ReviewAction.SaveToCollection("col_1", "My Books"))
        testScope.advanceUntilIdle()

        val saved = getSavedCollection("col_1")
        assertEquals(2, saved.items.size)
        assertEquals("Clean Code: Updated", saved.items[0].title)
    }

    @Test
    fun `load delete and save persists remaining items`() {
        loadSession(
            makeMediaItem(id = "item_0"),
            makeMediaItem(id = "item_1"),
            makeMediaItem(id = "item_2")
        )

        viewModel.onAction(ReviewAction.DeleteItem("item_1"))
        viewModel.onAction(ReviewAction.SaveToCollection("col_2", "Filtered"))
        testScope.advanceUntilIdle()

        val saved = getSavedCollection("col_2")
        assertEquals(2, saved.items.size)
        assertTrue(saved.items.none { it.id == "item_1" })
    }

    @Test
    fun `add new item edit and save persists extra item`() {
        loadSession(makeMediaItem(id = "item_0"))

        viewModel.onAction(ReviewAction.AddItem)
        val newItem = viewModel.state.value.items.last()
        val editedNew = newItem.copy(
            title = "New Book",
            creatorName = "New Author",
            source = ItemSource.USER_EDITED
        )
        viewModel.onAction(ReviewAction.EditItem(editedNew))
        viewModel.onAction(ReviewAction.SaveToCollection("col_3", "With New"))
        testScope.advanceUntilIdle()

        val saved = getSavedCollection("col_3")
        assertEquals(2, saved.items.size)
        assertEquals("New Book", saved.items.last().title)
    }

    @Test
    fun `approve all then save sets all items to USER_EDITED`() {
        loadSession(
            makeMediaItem(id = "item_0", source = ItemSource.OCR_ONLY),
            makeMediaItem(id = "item_1", source = ItemSource.CATALOG_MATCHED)
        )

        viewModel.onAction(ReviewAction.ApproveAll)
        viewModel.onAction(ReviewAction.SaveToCollection("col_4", "Approved"))
        testScope.advanceUntilIdle()

        val saved = getSavedCollection("col_4")
        assertTrue(saved.items.all { it.source == ItemSource.USER_EDITED })
    }

    @Test
    fun `export CSV after edits contains edited data`() {
        loadSession(
            makeMediaItem(id = "item_0", title = "Original Title")
        )

        val edited = viewModel.state.value.items.first().copy(
            title = "Edited Title",
            source = ItemSource.USER_EDITED
        )
        viewModel.onAction(ReviewAction.EditItem(edited))
        viewModel.onAction(ReviewAction.ExportCsv(viewModel.state.value.items))

        val csv = viewModel.state.value.exportedCsv
        assertNotNull(csv)
        assertTrue(csv.contains("Edited Title"))
        assertEquals(2, csv.lines().size)
    }

    @Test
    fun `discard all clears state`() {
        loadSession(
            makeMediaItem(id = "item_0"),
            makeMediaItem(id = "item_1")
        )

        viewModel.onAction(ReviewAction.DiscardAll)

        assertEquals(ReviewState(), viewModel.state.value)
    }
}
