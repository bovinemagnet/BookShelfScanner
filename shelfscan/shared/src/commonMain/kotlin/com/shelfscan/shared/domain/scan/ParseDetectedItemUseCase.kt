package com.shelfscan.shared.domain.scan

import com.shelfscan.shared.core.model.RecognizedTextBlock

class ParseDetectedItemUseCase {
    data class ParsedItem(
        val titleCandidate: String?,
        val creatorCandidate: String?,
        val allLines: List<String>
    )

    fun execute(blocks: List<RecognizedTextBlock>): ParsedItem {
        val lines = blocks.map { it.text.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return ParsedItem(null, null, emptyList())

        val titleCandidate = lines.maxByOrNull { it.length }
        val creatorCandidate = lines
            .filter { it != titleCandidate }
            .firstOrNull { line ->
                line.contains(' ') && !line.all { c -> c.isUpperCase() || !c.isLetter() }
            }
        return ParsedItem(titleCandidate, creatorCandidate, lines)
    }
}
