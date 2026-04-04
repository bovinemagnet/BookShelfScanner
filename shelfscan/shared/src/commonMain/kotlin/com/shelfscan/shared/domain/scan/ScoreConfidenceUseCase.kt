package com.shelfscan.shared.domain.scan

import com.shelfscan.shared.core.model.ConfidenceBand
import com.shelfscan.shared.core.model.ConfidenceScore

class ScoreConfidenceUseCase {
    data class ScoreInput(
        val segmentationConfidence: Double,
        val ocrConfidence: Double,
        val parserConfidence: Double,
        val catalogMatchConfidence: Double,
        val reasons: List<String> = emptyList()
    )

    fun execute(input: ScoreInput): ConfidenceScore {
        val value = (0.25 * input.segmentationConfidence) +
            (0.25 * input.ocrConfidence) +
            (0.20 * input.parserConfidence) +
            (0.30 * input.catalogMatchConfidence)

        val band = when {
            value >= 0.75 -> ConfidenceBand.HIGH
            value >= 0.50 -> ConfidenceBand.MEDIUM
            value >= 0.25 -> ConfidenceBand.LOW
            else -> ConfidenceBand.NEEDS_REVIEW
        }

        return ConfidenceScore(value = value, band = band, reasons = input.reasons)
    }
}
