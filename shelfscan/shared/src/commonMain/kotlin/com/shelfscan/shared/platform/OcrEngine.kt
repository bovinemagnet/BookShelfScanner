package com.shelfscan.shared.platform

import com.shelfscan.shared.core.model.OcrResult
import com.shelfscan.shared.core.model.ProcessedImage

interface OcrEngine {
    suspend fun recognizeText(image: ProcessedImage): OcrResult
}
