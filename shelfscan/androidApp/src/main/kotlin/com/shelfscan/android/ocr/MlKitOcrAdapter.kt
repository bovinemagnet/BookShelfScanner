package com.shelfscan.android.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.shelfscan.shared.core.model.OcrResult
import com.shelfscan.shared.core.model.ProcessedImage
import com.shelfscan.shared.platform.OcrEngine
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitOcrAdapter(private val context: Context) : OcrEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

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
