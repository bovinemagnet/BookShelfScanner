package com.shelfscan.shared.domain

import com.shelfscan.shared.core.model.*
import com.shelfscan.shared.domain.export.ExportCollectionUseCase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
    fun `json export escapes control characters in string values per RFC 8259`() {
        // Real OCR output frequently has newlines and tabs; the spec says these
        // must be escaped (\n, \t) inside string values. Hand-rolled escaping
        // in the previous implementation passed them through verbatim, which
        // strict downstream consumers (Python json, jq, etc.) reject.
        val item = makeItem(
            title = "Line one\nLine two",
            creator = "Author\twith\ttabs"
        )
        val json = useCase.execute(listOf(item), ExportCollectionUseCase.ExportFormat.JSON)

        // The escaped substring must appear; the literal control chars must not.
        assertContains(json, "Line one\\nLine two")
        assertContains(json, "Author\\twith\\ttabs")

        // And it must round-trip through kotlinx-serialization.
        val first = Json.parseToJsonElement(json).jsonArray.first() as JsonObject
        assertEquals("Line one\nLine two", first["title"]!!.jsonPrimitive.content)
        assertEquals("Author\twith\ttabs", first["creatorName"]!!.jsonPrimitive.content)
    }

    @Test
    fun `json export of empty list is parseable`() {
        val json = useCase.execute(emptyList(), ExportCollectionUseCase.ExportFormat.JSON)
        val element = Json.parseToJsonElement(json)
        assertTrue(element is JsonArray && element.isEmpty())
    }

    @Test
    fun `empty list produces header only for csv`() {
        val csv = useCase.execute(emptyList())
        val lines = csv.lines().filter { it.isNotBlank() }
        assertEquals(1, lines.size)
    }
}
