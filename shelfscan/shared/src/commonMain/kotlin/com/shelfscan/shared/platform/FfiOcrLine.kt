package com.shelfscan.shared.platform

/**
 * FFI-friendly DTO carrying a single recognised text line across the Swift/Kotlin boundary.
 *
 * Bounding box is flattened to primitive `Float` fields because Swift→Kotlin generics over
 * optional value types add friction with no benefit. `hasBoundingBox = false` indicates the
 * underlying recognised block had no spatial information.
 */
data class FfiOcrLine(
    val text: String,
    val confidence: Float,
    val hasBoundingBox: Boolean,
    val boundingBoxLeft: Float,
    val boundingBoxTop: Float,
    val boundingBoxRight: Float,
    val boundingBoxBottom: Float,
)
