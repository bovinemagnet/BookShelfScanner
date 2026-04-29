package com.shelfscan.shared.platform

import com.shelfscan.shared.core.model.BoundingBox
import com.shelfscan.shared.core.model.OcrResult
import com.shelfscan.shared.core.model.ProcessedImage
import com.shelfscan.shared.core.model.RecognizedTextBlock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Suspend bridge for `OcrEngine` implementations whose recognition step is callback-based.
 *
 * Lives in `commonMain` so it is JVM-testable. The `iosMain` `SwiftBackedOcrEngine` adapts a
 * Swift-implemented `IosOcrCallback` into the `recognize` function reference this class
 * accepts.
 *
 * @param recognize callback that performs OCR and reports results. The contract is
 *   "fire exactly one of `onSuccess` or `onError`, ideally once" — but duplicate or
 *   late callbacks are tolerated and silently dropped, since some real callback APIs
 *   (Apple Vision) can fire more than once across cancellation paths.
 */
class CallbackBackedOcrEngine(
    private val recognize: (
        imagePath: String,
        onSuccess: (rawText: String, lines: List<FfiOcrLine>) -> Unit,
        onError: (message: String) -> Unit,
    ) -> Unit,
) : OcrEngine {

    override suspend fun recognizeText(image: ProcessedImage): OcrResult =
        suspendCancellableCoroutine { cont ->
            recognize(
                image.ref,
                { rawText, lines ->
                    if (cont.isActive) {
                        val blocks = lines.map { line ->
                            RecognizedTextBlock(
                                text = line.text,
                                confidence = line.confidence,
                                boundingBox = if (line.hasBoundingBox) {
                                    BoundingBox(
                                        left = line.boundingBoxLeft,
                                        top = line.boundingBoxTop,
                                        right = line.boundingBoxRight,
                                        bottom = line.boundingBoxBottom,
                                    )
                                } else null,
                            )
                        }
                        cont.resume(OcrResult(blocks = blocks, rawText = rawText))
                    }
                },
                { message ->
                    if (cont.isActive) {
                        cont.resumeWithException(CallbackOcrException(message))
                    }
                },
            )
        }
}
