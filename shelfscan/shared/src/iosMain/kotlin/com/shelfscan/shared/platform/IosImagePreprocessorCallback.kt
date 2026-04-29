package com.shelfscan.shared.platform

/**
 * Swift-facing image preprocessor callback. Two methods, one for normalisation and one
 * for segmentation. Both take primitive image dimensions to keep the Swift call sites
 * simple and avoid Kotlin/Native generics over Swift value types.
 */
interface IosImagePreprocessorCallback {
    fun normalizeForOcr(
        imagePath: String,
        widthPx: Int,
        heightPx: Int,
        onSuccess: (processedRef: String, widthPx: Int, heightPx: Int) -> Unit,
        onError: (message: String) -> Unit,
    )

    fun detectShelfItems(
        imagePath: String,
        widthPx: Int,
        heightPx: Int,
        onSuccess: (spines: List<FfiDetectedSpine>) -> Unit,
        onError: (message: String) -> Unit,
    )
}
