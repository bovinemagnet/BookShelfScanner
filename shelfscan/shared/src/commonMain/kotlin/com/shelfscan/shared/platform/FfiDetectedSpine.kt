package com.shelfscan.shared.platform

/**
 * FFI-friendly DTO describing a single detected spine (or full-image segment) returned
 * from the iOS-side image preprocessor.
 */
data class FfiDetectedSpine(
    val id: String,
    val cropRef: String,
    val boundingBoxLeft: Float,
    val boundingBoxTop: Float,
    val boundingBoxRight: Float,
    val boundingBoxBottom: Float,
    val confidence: Double,
)
