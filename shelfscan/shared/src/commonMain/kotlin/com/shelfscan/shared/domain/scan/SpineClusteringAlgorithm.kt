package com.shelfscan.shared.domain.scan

import com.shelfscan.shared.core.model.BoundingBox
import com.shelfscan.shared.core.model.RecognizedTextBlock

data class SpineCluster(
    val boundingBox: BoundingBox,
    val blocks: List<RecognizedTextBlock>
)

class SpineClusteringAlgorithm(
    private val gapMultiplier: Double = 1.5
) {
    fun cluster(blocks: List<RecognizedTextBlock>): List<SpineCluster> {
        val withBoxes = blocks.filter { it.boundingBox != null }
        if (withBoxes.isEmpty()) return emptyList()

        val sorted = withBoxes.sortedBy { horizontalCentre(it.boundingBox!!) }

        val averageWidth = sorted.map { it.boundingBox!!.right - it.boundingBox.left }
            .average()
        val gapThreshold = (averageWidth * gapMultiplier).toFloat()

        val clusters = mutableListOf<MutableList<RecognizedTextBlock>>()
        var currentCluster = mutableListOf(sorted.first())

        for (i in 1 until sorted.size) {
            val prevCentre = horizontalCentre(sorted[i - 1].boundingBox!!)
            val currCentre = horizontalCentre(sorted[i].boundingBox!!)

            if (currCentre - prevCentre > gapThreshold) {
                clusters.add(currentCluster)
                currentCluster = mutableListOf(sorted[i])
            } else {
                currentCluster.add(sorted[i])
            }
        }
        clusters.add(currentCluster)

        return clusters.map { clusterBlocks ->
            SpineCluster(
                boundingBox = mergeBoundingBoxes(clusterBlocks),
                blocks = clusterBlocks
            )
        }
    }

    private fun horizontalCentre(box: BoundingBox): Float =
        (box.left + box.right) / 2f

    private fun mergeBoundingBoxes(blocks: List<RecognizedTextBlock>): BoundingBox {
        val boxes = blocks.mapNotNull { it.boundingBox }
        return BoundingBox(
            left = boxes.minOf { it.left },
            top = boxes.minOf { it.top },
            right = boxes.maxOf { it.right },
            bottom = boxes.maxOf { it.bottom }
        )
    }
}
