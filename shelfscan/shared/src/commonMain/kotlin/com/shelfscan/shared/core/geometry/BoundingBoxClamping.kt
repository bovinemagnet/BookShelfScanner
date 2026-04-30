package com.shelfscan.shared.core.geometry

import com.shelfscan.shared.core.model.BoundingBox

/**
 * Integer rectangle clamped into a `[0, width) × [0, height)` image, with at
 * least one pixel on each side. Returned as four ints rather than a domain
 * type to keep the platform crop call sites trivial.
 */
data class ClampedRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

fun BoundingBox.clampToImage(imageWidth: Int, imageHeight: Int): ClampedRect {
    require(imageWidth > 0 && imageHeight > 0) { "Image dimensions must be positive" }
    val left = left.toInt().coerceIn(0, imageWidth - 1)
    val top = top.toInt().coerceIn(0, imageHeight - 1)
    val right = right.toInt().coerceIn(left + 1, imageWidth)
    val bottom = bottom.toInt().coerceIn(top + 1, imageHeight)
    return ClampedRect(left, top, right, bottom)
}
