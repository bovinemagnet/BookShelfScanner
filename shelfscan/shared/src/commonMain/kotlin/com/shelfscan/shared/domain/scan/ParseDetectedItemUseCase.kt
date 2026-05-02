package com.shelfscan.shared.domain.scan

import com.shelfscan.shared.core.model.RecognizedTextBlock

/**
 * Picks the most likely title and author/creator from a per-spine OCR result.
 *
 * Heuristics, in order of strength:
 *  1. **Bounding-box height** — when boxes are available, the line set in the
 *     largest font is almost always the title.
 *  2. **Initial pattern (`R.`, `J. K.`)** — a line containing an initial is
 *     overwhelmingly an author byline. The longest *other* line is the title.
 *  3. **Longest non-author line** — fallback when no font-size or initial cues
 *     are present.
 *
 * `confidence` reflects which heuristic fired, so downstream scoring can
 * weight it instead of treating every parse identically.
 */
class ParseDetectedItemUseCase {
    data class ParsedItem(
        val titleCandidate: String?,
        val creatorCandidate: String?,
        val allLines: List<String>,
        val confidence: Double
    )

    fun execute(blocks: List<RecognizedTextBlock>): ParsedItem {
        val candidates = blocks.filter { it.text.trim().isNotBlank() }
        val lines = candidates.map { it.text.trim() }
        if (candidates.isEmpty()) {
            return ParsedItem(null, null, emptyList(), confidence = 0.1)
        }

        val authorBlock = candidates.firstOrNull { containsInitial(it.text.trim()) }
        val authorCandidate = authorBlock?.text?.trim()

        val titlePool = candidates.filter { it !== authorBlock }.ifEmpty { candidates }

        val hasBoxes = titlePool.any { it.boundingBox != null }
        val titleBlock = if (hasBoxes) {
            titlePool.maxByOrNull { it.boundingBox?.let { b -> b.bottom - b.top } ?: 0f }
        } else {
            titlePool.maxByOrNull { it.text.trim().length }
        }
        val titleCandidate = titleBlock?.text?.trim()

        val creatorCandidate = authorCandidate ?: lines
            .filter { it != titleCandidate }
            .firstOrNull { line ->
                line.contains(' ') && !line.all { c -> c.isUpperCase() || !c.isLetter() }
            }

        val confidence = computeConfidence(
            hasBoxes = hasBoxes,
            usedInitial = authorBlock != null,
            titleFound = titleCandidate != null
        )

        return ParsedItem(titleCandidate, creatorCandidate, lines, confidence)
    }

    private fun containsInitial(text: String): Boolean =
        INITIAL_PATTERN.containsMatchIn(text)

    private fun computeConfidence(hasBoxes: Boolean, usedInitial: Boolean, titleFound: Boolean): Double =
        when {
            !titleFound -> 0.1
            hasBoxes && usedInitial -> 0.85
            hasBoxes -> 0.75
            usedInitial -> 0.7
            else -> 0.55
        }

    private companion object {
        // A standalone capital letter immediately followed by a dot — "R.", "C." —
        // is a strong byline signal that almost never appears in real book titles.
        val INITIAL_PATTERN = Regex("(?:^|\\s)[A-Z]\\.")
    }
}
