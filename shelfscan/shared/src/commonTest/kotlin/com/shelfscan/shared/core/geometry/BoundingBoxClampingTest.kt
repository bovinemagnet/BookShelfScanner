package com.shelfscan.shared.core.geometry

import com.shelfscan.shared.core.model.BoundingBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BoundingBoxClampingTest {

    @Test
    fun `box wholly inside image is unchanged`() {
        val rect = BoundingBox(10f, 20f, 110f, 120f).clampToImage(640, 480)
        assertEquals(ClampedRect(10, 20, 110, 120), rect)
    }

    @Test
    fun `box that overhangs the right and bottom edges is clamped`() {
        val rect = BoundingBox(500f, 400f, 800f, 600f).clampToImage(640, 480)
        assertEquals(640, rect.right)
        assertEquals(480, rect.bottom)
    }

    @Test
    fun `negative origins are clamped to zero`() {
        val rect = BoundingBox(-50f, -30f, 100f, 100f).clampToImage(640, 480)
        assertEquals(0, rect.left)
        assertEquals(0, rect.top)
    }

    @Test
    fun `degenerate box still has at least one pixel of width and height`() {
        val rect = BoundingBox(50f, 50f, 50f, 50f).clampToImage(640, 480)
        assertEquals(1, rect.width)
        assertEquals(1, rect.height)
    }

    @Test
    fun `box entirely beyond the image still produces a one-pixel rect at the edge`() {
        val rect = BoundingBox(700f, 500f, 800f, 600f).clampToImage(640, 480)
        // left coerced to 639, right must be > left so coerced to 640.
        assertEquals(639, rect.left)
        assertEquals(479, rect.top)
        assertEquals(1, rect.width)
        assertEquals(1, rect.height)
    }

    @Test
    fun `non-positive image dimensions are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            BoundingBox(0f, 0f, 10f, 10f).clampToImage(0, 100)
        }
    }
}
