package com.shelfscan.android.ocr

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.shelfscan.shared.core.model.BoundingBox
import com.shelfscan.shared.core.model.OcrResult
import com.shelfscan.shared.core.model.ProcessedImage
import com.shelfscan.shared.core.model.RecognizedTextBlock
import com.shelfscan.shared.platform.OcrEngine
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitOcrAdapter : OcrEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognizeText(image: ProcessedImage): OcrResult =
        suspendCancellableCoroutine { continuation ->
            val file = File(image.ref)
            if (!file.exists()) {
                continuation.resume(OcrResult(blocks = emptyList(), rawText = ""))
                return@suspendCancellableCoroutine
            }
            val inputImage = InputImage.fromFilePath(
                file.parentFile!!.let { throw IllegalStateException("Use context-aware factory") },
                android.net.Uri.fromFile(file)
            )
            recognizer.process(inputImage)
                .addOnSuccessListener { result ->
                    val blocks = result.textBlocks.flatMap { block ->
                        block.lines.map { line ->
                            val bbox = line.boundingBox
                            RecognizedTextBlock(
                                text = line.text,
                                confidence = line.confidence ?: 0.7f,
                                boundingBox = bbox?.let {
                                    BoundingBox(
                                        left = it.left.toFloat(),
                                        top = it.top.toFloat(),
                                        right = it.right.toFloat(),
                                        bottom = it.bottom.toFloat()
                                    )
                                }
                            )
                        }
                    }
                    continuation.resume(OcrResult(blocks = blocks, rawText = result.text))
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        }
}
