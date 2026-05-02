package com.shelfscan.android.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import com.shelfscan.shared.core.model.OcrResult
import com.shelfscan.shared.core.model.ProcessedImage
import com.shelfscan.shared.platform.OcrEngine
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Adapter from the shared `OcrEngine` contract to ML Kit's `TextRecognizer`.
 * The recognizer is constructed once in `ShelfScanApplication` and shared
 * with `OcrBasedSpineDetector` to avoid loading the model twice.
 */
class MlKitOcrAdapter(
    private val context: Context,
    private val recognizer: TextRecognizer,
) : OcrEngine {

    override suspend fun recognizeText(image: ProcessedImage): OcrResult =
        suspendCancellableCoroutine { continuation ->
            val file = File(image.ref)
            if (!file.exists()) {
                continuation.resume(OcrResult(blocks = emptyList(), rawText = ""))
                return@suspendCancellableCoroutine
            }
            val inputImage = try {
                InputImage.fromFilePath(context, Uri.fromFile(file))
            } catch (e: Exception) {
                continuation.resumeWithException(e)
                return@suspendCancellableCoroutine
            }
            recognizer.process(inputImage)
                .addOnSuccessListener { result ->
                    continuation.resume(
                        OcrResult(blocks = result.toRecognizedTextBlocks(), rawText = result.text)
                    )
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        }
}
