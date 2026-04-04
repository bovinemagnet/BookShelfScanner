package com.shelfscan.shared.domain

import com.shelfscan.shared.core.model.*
import com.shelfscan.shared.domain.export.ExportCollectionUseCase
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportCollectionUseCaseTest {
    private val useCase = ExportCollectionUseCase()

    private fun makeItem(
        id: String = "item1",
        title: String? = "Clean Code",
        creator: String? = "Robert C. Martin",
        mediaType: MediaType = MediaType.BOOK
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
        source = ItemSource.CATALOG_MATCHED,
        cropRef = null,
        rawText = listOf("Clean Code", "Robert C. Martin")
    )

    @Test
    fun `csv export contains header`() {
        val csv = useCase.execute(listOf(makeItem()))
        assertTrue(csv.startsWith("id,mediaType,title,creatorName"))
    }

    @Test
    fun `csv export contains item data`() {
        val csv = useCase.execute(listOf(makeItem()))
        assertContains(csv, "Clean Code")
        assertContains(csv, "Robert C. Martin")
        assertContains(csv, "BOOK")
    }

    @Test
    fun `csv escapes commas in fields`() {
        val item = makeItem(title = "Hello, World")
        val csv = useCase.execute(listOf(item))
        assertContains(csv, "\"Hello, World\"")
    }

    @Test
    fun `csv handles null title`() {
        val item = makeItem(title = null, creator = null)
        val csv = useCase.execute(listOf(item))
        assertTrue(csv.contains(","))
    }

    @Test
    fun `json export produces valid structure`() {
        val json = useCase.execute(
            listOf(makeItem()),
            ExportCollectionUseCase.ExportFormat.JSON
        )
        assertTrue(json.startsWith("["))
        assertTrue(json.endsWith("]"))
        assertContains(json, "\"title\"")
        assertContains(json, "Clean Code")
    }

    @Test
    fun `csv export handles multiple items`() {
        val items = listOf(
            makeItem("item1", "Clean Code", "Robert C. Martin"),
            makeItem("item2", "Refactoring", "Martin Fowler")
        )
        val csv = useCase.execute(items)
        val lines = csv.lines()
        assertEquals(3, lines.size) // header + 2 items
    }

    @Test
    fun `empty list produces header only for csv`() {
        val csv = useCase.execute(emptyList())
        val lines = csv.lines().filter { it.isNotBlank() }
        assertEquals(1, lines.size)
    }
}
