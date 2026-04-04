package com.shelfscan.shared.domain

import com.shelfscan.shared.core.model.ConfidenceBand
import com.shelfscan.shared.domain.scan.ScoreConfidenceUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScoreConfidenceUseCaseTest {
    private val useCase = ScoreConfidenceUseCase()

    @Test
    fun `high confidence when all scores are high`() {
        val result = useCase.execute(
            ScoreConfidenceUseCase.ScoreInput(
                segmentationConfidence = 1.0,
                ocrConfidence = 1.0,
                parserConfidence = 1.0,
                catalogMatchConfidence = 1.0
            )
        )
        assertEquals(ConfidenceBand.HIGH, result.band)
        assertEquals(1.0, result.value, 0.001)
    }

    @Test
    fun `needs review when all scores are zero`() {
        val result = useCase.execute(
            ScoreConfidenceUseCase.ScoreInput(
                segmentationConfidence = 0.0,
                ocrConfidence = 0.0,
                parserConfidence = 0.0,
                catalogMatchConfidence = 0.0
            )
        )
        assertEquals(ConfidenceBand.NEEDS_REVIEW, result.band)
        assertEquals(0.0, result.value, 0.001)
    }

    @Test
    fun `medium band for mid-range scores`() {
        val result = useCase.execute(
            ScoreConfidenceUseCase.ScoreInput(
                segmentationConfidence = 0.6,
                ocrConfidence = 0.6,
                parserConfidence = 0.6,
                catalogMatchConfidence = 0.6
            )
        )
        assertEquals(ConfidenceBand.MEDIUM, result.band)
    }

    @Test
    fun `low band for low scores`() {
        val result = useCase.execute(
            ScoreConfidenceUseCase.ScoreInput(
                segmentationConfidence = 0.3,
                ocrConfidence = 0.3,
                parserConfidence = 0.3,
                catalogMatchConfidence = 0.3
            )
        )
        assertEquals(ConfidenceBand.LOW, result.band)
    }

    @Test
    fun `catalog match has higher weight than other scores`() {
        // catalog weight is 0.30 vs segmentation/ocr 0.25 each
        val highCatalog = useCase.execute(
            ScoreConfidenceUseCase.ScoreInput(
                segmentationConfidence = 0.0,
                ocrConfidence = 0.0,
                parserConfidence = 0.0,
                catalogMatchConfidence = 1.0
            )
        )
        val highSegmentation = useCase.execute(
            ScoreConfidenceUseCase.ScoreInput(
                segmentationConfidence = 1.0,
                ocrConfidence = 0.0,
                parserConfidence = 0.0,
                catalogMatchConfidence = 0.0
            )
        )
        assertTrue(highCatalog.value > highSegmentation.value)
    }

    @Test
    fun `reasons are preserved in result`() {
        val reasons = listOf("blurred text", "no catalog match")
        val result = useCase.execute(
            ScoreConfidenceUseCase.ScoreInput(
                segmentationConfidence = 0.3,
                ocrConfidence = 0.3,
                parserConfidence = 0.2,
                catalogMatchConfidence = 0.0,
                reasons = reasons
            )
        )
        assertEquals(reasons, result.reasons)
    }
}
