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
                },
                { message ->
                    cont.resumeWithException(CallbackOcrException(message))
                },
            )
        }
}
