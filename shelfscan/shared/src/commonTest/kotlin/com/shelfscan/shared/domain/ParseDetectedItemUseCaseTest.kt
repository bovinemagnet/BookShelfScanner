package com.shelfscan.shared.domain

import com.shelfscan.shared.core.model.RecognizedTextBlock
import com.shelfscan.shared.domain.scan.ParseDetectedItemUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParseDetectedItemUseCaseTest {
    private val useCase = ParseDetectedItemUseCase()

    @Test
    fun `returns null candidates for empty blocks`() {
        val result = useCase.execute(emptyList())
        assertNull(result.titleCandidate)
        assertNull(result.creatorCandidate)
    }

    @Test
    fun `picks longest line as title`() {
        val blocks = listOf(
            RecognizedTextBlock("Clean Code", 0.9f, null),
            RecognizedTextBlock("Robert C. Martin", 0.85f, null),
            RecognizedTextBlock("A Handbook", 0.7f, null)
        )
        val result = useCase.execute(blocks)
        assertEquals("Robert C. Martin", result.titleCandidate)
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
