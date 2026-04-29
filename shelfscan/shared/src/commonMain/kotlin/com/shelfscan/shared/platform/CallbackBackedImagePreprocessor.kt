package com.shelfscan.shared.platform

import com.shelfscan.shared.core.model.BoundingBox
import com.shelfscan.shared.core.model.CapturedImage
import com.shelfscan.shared.core.model.DetectedSpine
import com.shelfscan.shared.core.model.ProcessedImage
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Suspend bridge for `ImagePreprocessor` implementations whose normalisation and
 * segmentation steps are callback-based.
 *
 * Lives in `commonMain` so it is JVM-testable. The `iosMain` `SwiftBackedImagePreprocessor`
 * adapts a Swift-implemented `IosImagePreprocessorCallback` into the function references
 * this class accepts.
 *
 * @param normalize callback that performs OCR-friendly normalisation. Contract: fire
 *   exactly one of `onSuccess` or `onError`, ideally once. Duplicate or late fires are
 *   tolerated and silently dropped.
 * @param detect callback that performs spine segmentation. Same single-fire contract.
 */
class CallbackBackedImagePreprocessor(
    private val normalize: (
        imagePath: String,
        widthPx: Int,
        heightPx: Int,
        onSuccess: (processedRef: String, widthPx: Int, heightPx: Int) -> Unit,
        onError: (message: String) -> Unit,
    ) -> Unit,
    private val detect: (
        imagePath: String,
        widthPx: Int,
        heightPx: Int,
        onSuccess: (spines: List<FfiDetectedSpine>) -> Unit,
        onError: (message: String) -> Unit,
    ) -> Unit,
) : ImagePreprocessor {

    override suspend fun normalizeForOcr(image: CapturedImage): ProcessedImage =
        suspendCancellableCoroutine { cont ->
            normalize(
                image.ref,
                image.widthPx,
                image.heightPx,
                { ref, w, h ->
                    if (cont.isActive) {
                        cont.resume(ProcessedImage(ref = ref, widthPx = w, heightPx = h))
                    }
                },
                { message ->
                    if (cont.isActive) {
                        cont.resumeWithException(CallbackImagePreprocessorException(message))
                    }
                },
            )
        }

    override suspend fun detectShelfItems(image: CapturedImage): List<DetectedSpine> =
        suspendCancellableCoroutine { cont ->
            detect(
                image.ref,
                image.widthPx,
                image.heightPx,
                { spines ->
                    if (cont.isActive) {
                        val mapped = spines.map { spine ->
                            DetectedSpine(
                                id = spine.id,
                                cropRef = spine.cropRef,
                                boundingBox = BoundingBox(
                                    left = spine.boundingBoxLeft,
                                    top = spine.boundingBoxTop,
                                    right = spine.boundingBoxRight,
                                    bottom = spine.boundingBoxBottom,
                                ),
                                confidence = spine.confidence,
                            )
                        }
                        cont.resume(mapped)
                    }
                },
                { message ->
                    if (cont.isActive) {
                        cont.resumeWithException(CallbackImagePreprocessorException(message))
                    }
                },
            )
        }
}
