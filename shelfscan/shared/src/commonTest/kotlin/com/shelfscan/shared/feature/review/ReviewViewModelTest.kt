package com.shelfscan.shared.feature.review

import com.shelfscan.shared.core.model.*
import com.shelfscan.shared.data.repository.CollectionRepository
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewViewModelTest {

    private val fakeRepository = FakeCollectionRepository()
    private val testScope = TestScope()
    private val viewModel = ReviewViewModel(
        collectionRepository = fakeRepository,
        scope = testScope
    )

    private fun makeItem(
        id: String = "item1",
        title: String? = "Clean Code",
        creator: String? = "Robert C. Martin",
        mediaType: MediaType = MediaType.BOOK,
        source: ItemSource = ItemSource.OCR_ONLY
    ) = MediaItem(
        id = id,
        mediaType = mediaType,
        title = title,
        creatorName = creator,
        subtitle = null,
        normalizedTitle = title,
        normalizedCreatorName = creator,
        confidence = ConfidenceScore(
            value = 0.85,
            band = ConfidenceBand.HIGH,
            reasons = emptyList()
        ),
        source = source,
        cropRef = null,
        rawText = listOf("Clean Code", "Robert C. Martin")
    )

    private fun makeSession(vararg items: MediaItem) = ScanSession(
        id = "session1",
        createdAt = 0L,
        sourceImageRef = "test.jpg",
        quality = ImageQualityAssessment(
            blurScore = 0.1,
            brightness = 0.8,
            isAcceptable = true,
            reasons = emptyList()
        ),
        status = ScanStatus.COMPLETE,
        detectedItems = items.toList()
    )

    private fun loadItems(vararg items: MediaItem) {
        viewModel.onAction(ReviewAction.LoadSession(makeSession(*items)))
    }

    // --- Delete tests ---

    @Test
    fun `delete item removes it from state`() {
        loadItems(
            makeItem(id = "item1"),
            makeItem(id = "item2"),
            makeItem(id = "item3")
        )

        viewModel.onAction(ReviewAction.DeleteItem("item2"))

        val items = viewModel.state.value.items
        assertEquals(2, items.size)
        assertTrue(items.none { it.id == "item2" })
    }

    @Test
    fun `delete unknown id is no-op`() {
        loadItems(
            makeItem(id = "item1"),
            makeItem(id = "item2")
        )

        viewModel.onAction(ReviewAction.DeleteItem("nonexistent"))

        assertEquals(2, viewModel.state.value.items.size)
    }

    // --- Add item tests ---

    @Test
    fun `add item appends with USER_EDITED source`() {
        loadItems(makeItem(id = "item1"))

        viewModel.onAction(ReviewAction.AddItem)

        val items = viewModel.state.value.items
        assertEquals(2, items.size)
        assertEquals(ItemSource.USER_EDITED, items.last().source)
    }

    @Test
    fun `add item sets editingItemId to new item`() {
        loadItems(makeItem(id = "item1"))

        viewModel.onAction(ReviewAction.AddItem)

        val state = viewModel.state.value
        assertEquals(state.items.last().id, state.editingItemId)
    }

    @Test
    fun `add item creates NEEDS_REVIEW confidence`() {
        viewModel.onAction(ReviewAction.AddItem)

        val item = viewModel.state.value.items.first()
        assertEquals(ConfidenceBand.NEEDS_REVIEW, item.confidence.band)
    }

    // --- Start/Stop editing tests ---

    @Test
    fun `start editing sets editingItemId`() {
        loadItems(makeItem(id = "item1"))

        viewModel.onAction(ReviewAction.StartEditing("item1"))

        assertEquals("item1", viewModel.state.value.editingItemId)
    }

    @Test
    fun `stop editing clears editingItemId`() {
        loadItems(makeItem(id = "item1"))
        viewModel.onAction(ReviewAction.StartEditing("item1"))

        viewModel.onAction(ReviewAction.StopEditing)

        assertNull(viewModel.state.value.editingItemId)
    }

    // --- Edit item tests ---

    @Test
    fun `edit item clears editingItemId`() {
        loadItems(makeItem(id = "item1"))
        viewModel.onAction(ReviewAction.StartEditing("item1"))

        val edited = makeItem(id = "item1", title = "New Title", source = ItemSource.USER_EDITED)
        viewModel.onAction(ReviewAction.EditItem(edited))

        assertNull(viewModel.state.value.editingItemId)
    }

    @Test
    fun `edit item preserves USER_EDITED source`() {
        loadItems(makeItem(id = "item1", source = ItemSource.OCR_ONLY))

        val edited = makeItem(id = "item1", title = "New Title", source = ItemSource.USER_EDITED)
        viewModel.onAction(ReviewAction.EditItem(edited))

        assertEquals(ItemSource.USER_EDITED, viewModel.state.value.items.first().source)
    }

    // --- Approve all tests ---

    @Test
    fun `approve all sets every item source to USER_EDITED`() {
        loadItems(
            makeItem(id = "item1", source = ItemSource.OCR_ONLY),
            makeItem(id = "item2", source = ItemSource.CATALOG_MATCHED),
            makeItem(id = "item3", source = ItemSource.USER_EDITED)
        )

        viewModel.onAction(ReviewAction.ApproveAll)

        val items = viewModel.state.value.items
        assertTrue(items.all { it.source == ItemSource.USER_EDITED })
    }
}

private class FakeCollectionRepository : CollectionRepository {
    private val collections = mutableMapOf<String, com.shelfscan.shared.core.model.Collection>()

    override suspend fun saveCollection(collection: com.shelfscan.shared.core.model.Collection) {
        collections[collection.id] = collection
    }

    override suspend fun getCollection(id: String): com.shelfscan.shared.core.model.Collection? = collections[id]

    override suspend fun saveItems(collectionId: String, items: List<MediaItem>) {
        val existing = collections[collectionId] ?: return
        collections[collectionId] = existing.copy(items = items)
    }

    override suspend fun getCollectionItems(collectionId: String): List<MediaItem> =
        collections[collectionId]?.items ?: emptyList()

    override suspend fun getAllCollections(): List<com.shelfscan.shared.core.model.Collection> =
        collections.values.toList()
}
