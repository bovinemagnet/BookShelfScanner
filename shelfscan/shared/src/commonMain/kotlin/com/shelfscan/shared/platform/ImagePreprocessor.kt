package com.shelfscan.shared.platform

import com.shelfscan.shared.core.model.CapturedImage
import com.shelfscan.shared.core.model.DetectedSpine
import com.shelfscan.shared.core.model.ProcessedImage

interface ImagePreprocessor {
    suspend fun normalizeForOcr(image: CapturedImage): ProcessedImage
    suspend fun detectShelfItems(image: CapturedImage): List<DetectedSpine>
}
