package com.shelfscan.shared.domain

import com.shelfscan.shared.core.model.BoundingBox
import com.shelfscan.shared.core.model.RecognizedTextBlock
import com.shelfscan.shared.domain.scan.SpineClusteringAlgorithm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpineClusteringAlgorithmTest {

    private val algorithm = SpineClusteringAlgorithm()

    private fun block(
        text: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        confidence: Float = 0.9f
    ) = RecognizedTextBlock(
        text = text,
        confidence = confidence,
        boundingBox = BoundingBox(left, top, right, bottom)
    )

    @Test
    fun emptyInputReturnsEmptyClusters() {
        val result = algorithm.cluster(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun singleBlockReturnsSingleCluster() {
        val blocks = listOf(block("Title", 10f, 0f, 50f, 200f))
        val result = algorithm.cluster(blocks)
        assertEquals(1, result.size)
        assertEquals(1, result[0].blocks.size)
        assertEquals("Title", result[0].blocks[0].text)
    }

    @Test
    fun twoBlocksFarApartReturnTwoClusters() {
        val blocks = listOf(
            block("Book A", 10f, 0f, 50f, 200f),
            block("Book B", 300f, 0f, 340f, 200f)
        )
        val result = algorithm.cluster(blocks)
        assertEquals(2, result.size)
    }

    @Test
    fun twoBlocksStackedVerticallySameSpineReturnOneCluster() {
        // Same X range, different Y — stacked text on one spine
        val blocks = listOf(
            block("Title", 10f, 0f, 50f, 100f),
            block("Author", 10f, 110f, 50f, 200f)
        )
        val result = algorithm.cluster(blocks)
        assertEquals(1, result.size)
        assertEquals(2, result[0].blocks.size)
    }

    @Test
    fun blocksWithNullBoundingBoxesAreFiltered() {
        val blocks = listOf(
            RecognizedTextBlock("no box", 0.9f, null),
            block("has box", 10f, 0f, 50f, 200f)
        )
        val result = algorithm.cluster(blocks)
        assertEquals(1, result.size)
        assertEquals(1, result[0].blocks.size)
        assertEquals("has box", result[0].blocks[0].text)
    }

    @Test
    fun threeDistinctSpinesReturnThreeClusters() {
        val blocks = listOf(
            block("Book A Title", 10f, 0f, 50f, 200f),
            block("Book A Author", 15f, 210f, 45f, 300f),
            block("Book B Title", 200f, 0f, 240f, 200f),
            block("Book C Title", 400f, 0f, 440f, 200f),
            block("Book C Author", 405f, 210f, 435f, 300f)
        )
        val result = algorithm.cluster(blocks)
        assertEquals(3, result.size)
        assertEquals(2, result[0].blocks.size) // Book A
        assertEquals(1, result[1].blocks.size) // Book B
        assertEquals(2, result[2].blocks.size) // Book C
    }

    @Test
    fun clusterBoundingBoxIsMergedFromAllBlocks() {
        val blocks = listOf(
            block("Line 1", 10f, 0f, 50f, 100f),
            block("Line 2", 5f, 110f, 55f, 200f)
        )
        val result = algorithm.cluster(blocks)
        assertEquals(1, result.size)
        val box = result[0].boundingBox
        assertEquals(5f, box.left)
        assertEquals(0f, box.top)
        assertEquals(55f, box.right)
        assertEquals(200f, box.bottom)
    }

    @Test
    fun clustersAreSortedLeftToRight() {
        // Provide blocks in reverse order
        val blocks = listOf(
            block("Right Book", 400f, 0f, 440f, 200f),
            block("Left Book", 10f, 0f, 50f, 200f),
            block("Middle Book", 200f, 0f, 240f, 200f)
        )
        val result = algorithm.cluster(blocks)
        assertEquals(3, result.size)
        assertTrue(result[0].boundingBox.left < result[1].boundingBox.left)
        assertTrue(result[1].boundingBox.left < result[2].boundingBox.left)
    }

    @Test
    fun overlappingBlocksInSameRegionClusterTogether() {
        val blocks = listOf(
            block("Part A", 10f, 0f, 60f, 100f),
            block("Part B", 20f, 50f, 70f, 150f)
        )
        val result = algorithm.cluster(blocks)
        assertEquals(1, result.size)
        assertEquals(2, result[0].blocks.size)
    }
}
