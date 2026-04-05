package com.shelfscan.shared.platform

import com.shelfscan.shared.core.model.BoundingBox
import com.shelfscan.shared.core.model.CapturedImage
import com.shelfscan.shared.core.model.DetectedSpine
import com.shelfscan.shared.core.model.ProcessedImage

class PassthroughImagePreprocessor : ImagePreprocessor {
    override suspend fun normalizeForOcr(image: CapturedImage): ProcessedImage {
        return ProcessedImage(
            ref = image.ref,
            widthPx = image.widthPx,
            heightPx = image.heightPx
        )
    }

    override suspend fun detectShelfItems(image: CapturedImage): List<DetectedSpine> {
        return listOf(
            DetectedSpine(
                id = "spine_0",
                cropRef = image.ref,
                boundingBox = BoundingBox(
                    0f, 0f,
                    image.widthPx.toFloat(),
                    image.heightPx.toFloat()
                ),
                confidence = 0.5
            )
        )
    }
}
