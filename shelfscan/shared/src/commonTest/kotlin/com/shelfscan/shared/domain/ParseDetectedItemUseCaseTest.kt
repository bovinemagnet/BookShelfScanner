package com.shelfscan.shared.domain

import com.shelfscan.shared.core.model.BoundingBox
import com.shelfscan.shared.core.model.RecognizedTextBlock
import com.shelfscan.shared.domain.scan.ParseDetectedItemUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParseDetectedItemUseCaseTest {
    private val useCase = ParseDetectedItemUseCase()

    @Test
    fun `returns null candidates for empty blocks`() {
        val result = useCase.execute(emptyList())
        assertNull(result.titleCandidate)
        assertNull(result.creatorCandidate)
    }

    @Test
    fun `treats line containing an initial as author, not title`() {
        // "Robert C. Martin" was previously chosen as title because it was longest.
        // Real bookshelves: an initial like "C." is a strong author signal.
        val blocks = listOf(
            RecognizedTextBlock("Clean Code", 0.9f, null),
            RecognizedTextBlock("Robert C. Martin", 0.85f, null),
            RecognizedTextBlock("A Handbook", 0.7f, null)
        )
        val result = useCase.execute(blocks)
        assertEquals("Clean Code", result.titleCandidate)
        assertEquals("Robert C. Martin", result.creatorCandidate)
    }

    @Test
    fun `picks line with largest bounding box as title when boxes are available`() {
        // Title text is normally set in a larger font than the author byline.
        val blocks = listOf(
            RecognizedTextBlock("Clean Code", 0.9f, BoundingBox(0f, 0f, 100f, 80f)),     // tall
            RecognizedTextBlock("by R. Martin", 0.85f, BoundingBox(0f, 100f, 100f, 130f)) // short
        )
        val result = useCase.execute(blocks)
        assertEquals("Clean Code", result.titleCandidate)
    }

    @Test
    fun `parser confidence is higher when the initial heuristic fires`() {
        val withInitial = useCase.execute(
            listOf(
                RecognizedTextBlock("Clean Code", 0.9f, null),
                RecognizedTextBlock("Robert C. Martin", 0.85f, null)
            )
        )
        val withoutInitial = useCase.execute(
            listOf(
                RecognizedTextBlock("Some Title", 0.9f, null),
                RecognizedTextBlock("Some Author", 0.85f, null)
            )
        )
        assertTrue(
            withInitial.confidence > withoutInitial.confidence,
            "expected initial-driven parse (${withInitial.confidence}) > fallback (${withoutInitial.confidence})"
        )
    }

    @Test
    fun `confidence is minimal when there are no candidates`() {
        val result = useCase.execute(emptyList())
        assertTrue(result.confidence <= 0.1)
    }

    @Test
    fun `picks name-like line as creator`() {
        val blocks = listOf(
            RecognizedTextBlock("The Pragmatic Programmer", 0.9f, null),
            RecognizedTextBlock("Andrew Hunt", 0.85f, null)
        )
        val result = useCase.execute(blocks)
        assertEquals("The Pragmatic Programmer", result.titleCandidate)
        assertEquals("Andrew Hunt", result.creatorCandidate)
    }

    @Test
    fun `all-caps line is not selected as creator`() {
        val blocks = listOf(
            RecognizedTextBlock("DESIGN PATTERNS", 0.9f, null),
            RecognizedTextBlock("Gang of Four", 0.85f, null)
        )
        val result = useCase.execute(blocks)
        assertEquals("DESIGN PATTERNS", result.titleCandidate)
        assertEquals("Gang of Four", result.creatorCandidate)
    }

    @Test
    fun `single block returns title only`() {
        val blocks = listOf(RecognizedTextBlock("Refactoring", 0.9f, null))
        val result = useCase.execute(blocks)
        assertEquals("Refactoring", result.titleCandidate)
        assertNull(result.creatorCandidate)
    }

    @Test
    fun `all lines captured in result`() {
        val blocks = listOf(
            RecognizedTextBlock("Clean Code", 0.9f, null),
            RecognizedTextBlock("Robert C. Martin", 0.85f, null)
        )
        val result = useCase.execute(blocks)
        assertEquals(2, result.allLines.size)
    }
}
