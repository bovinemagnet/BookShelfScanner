package com.shelfscan.android.image

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.shelfscan.android.ocr.toRecognizedTextBlocks
import com.shelfscan.shared.core.model.BoundingBox
import com.shelfscan.shared.core.model.CapturedImage
import com.shelfscan.shared.core.model.DetectedSpine
import com.shelfscan.shared.core.model.ProcessedImage
import com.shelfscan.shared.core.model.RecognizedTextBlock
import com.shelfscan.shared.domain.scan.SpineClusteringAlgorithm
import com.shelfscan.shared.platform.ImagePreprocessor
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

class OcrBasedSpineDetector(
    private val context: Context,
    private val clusterAlgorithm: SpineClusteringAlgorithm = SpineClusteringAlgorithm(),
    private val bitmapCropper: BitmapCropper = BitmapCropper(context.cacheDir)
) : ImagePreprocessor {

    private val recogniser = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun normalizeForOcr(image: CapturedImage): ProcessedImage {
        return ProcessedImage(
            ref = image.ref,
            widthPx = image.widthPx,
            heightPx = image.heightPx
        )
    }

    override suspend fun detectShelfItems(image: CapturedImage): List<DetectedSpine> {
        val blocks = runOcrOnFullImage(image.ref)

        if (blocks.isEmpty()) {
            return listOf(wholeImageSpine(image))
        }

        val clusters = clusterAlgorithm.cluster(blocks)

        if (clusters.isEmpty()) {
            return listOf(wholeImageSpine(image))
        }

        return clusters.mapIndexed { index, cluster ->
            val cropRef = bitmapCropper.cropAndSave(image.ref, cluster.boundingBox, index.toString())
            val avgConfidence = cluster.blocks.map { it.confidence.toDouble() }.average()

            DetectedSpine(
                id = "spine_$index",
                cropRef = cropRef,
                boundingBox = cluster.boundingBox,
                confidence = avgConfidence
            )
        }
    }

    private suspend fun runOcrOnFullImage(imageRef: String): List<RecognizedTextBlock> =
        suspendCancellableCoroutine { continuation ->
            val file = File(imageRef)
            if (!file.exists()) {
                continuation.resume(emptyList())
                return@suspendCancellableCoroutine
            }

            val inputImage = try {
                InputImage.fromFilePath(context, Uri.fromFile(file))
            } catch (e: Exception) {
                continuation.resume(emptyList())
                return@suspendCancellableCoroutine
            }

            recogniser.process(inputImage)
                .addOnSuccessListener { result ->
                    continuation.resume(result.toRecognizedTextBlocks())
                }
                .addOnFailureListener {
                    continuation.resume(emptyList())
                }
        }

    private fun wholeImageSpine(image: CapturedImage): DetectedSpine {
        return DetectedSpine(
            id = "spine_0",
            cropRef = image.ref,
            boundingBox = BoundingBox(
                0f, 0f,
                image.widthPx.toFloat(),
                image.heightPx.toFloat()
            ),
            confidence = 0.5
        )
    }
}
