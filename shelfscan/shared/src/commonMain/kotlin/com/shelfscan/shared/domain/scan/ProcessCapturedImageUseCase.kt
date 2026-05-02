package com.shelfscan.shared.domain.scan

import com.shelfscan.shared.core.model.*
import com.shelfscan.shared.platform.ImagePreprocessor
import com.shelfscan.shared.platform.MetadataLookupService
import com.shelfscan.shared.platform.OcrEngine
import com.shelfscan.shared.data.repository.ScanRepository

class ProcessCapturedImageUseCase(
    private val imagePreprocessor: ImagePreprocessor,
    private val ocrEngine: OcrEngine,
    private val metadataLookupService: MetadataLookupService,
    private val scanRepository: ScanRepository,
    private val parseItem: ParseDetectedItemUseCase = ParseDetectedItemUseCase(),
    private val scoreConfidence: ScoreConfidenceUseCase = ScoreConfidenceUseCase(),
    private val clock: () -> Long = { 0L }
) {
    suspend fun execute(image: CapturedImage, sessionId: String): ScanSession {
        val processed = runImagePhase { imagePreprocessor.normalizeForOcr(image) }
        val spines = runImagePhase { imagePreprocessor.detectShelfItems(image) }

        val items = spines.mapIndexed { index, spine ->
            // Dimensions describe whatever `ref` points at — the cropped spine
            // file in the normal path, or the full image in fallback paths.
            // Fall back to whole-image dimensions if the bounding box is degenerate.
            val spineImage = ProcessedImage(
                ref = spine.cropRef,
                widthPx = spineDimension(spine.boundingBox.right - spine.boundingBox.left, processed.widthPx),
                heightPx = spineDimension(spine.boundingBox.bottom - spine.boundingBox.top, processed.heightPx),
            )
            val ocrResult = runOcrPhase { ocrEngine.recognizeText(spineImage) }
            val parsed = parseItem.execute(ocrResult.blocks)

            val catalogMatches = if (parsed.titleCandidate != null) {
                runMetadataPhase {
                    metadataLookupService.search(
                        mediaType = MediaType.BOOK,
                        title = parsed.titleCandidate,
                        creatorName = parsed.creatorCandidate
                    )
                }
            } else emptyList()

            val topMatch = catalogMatches.firstOrNull()
            val ocrConf = ocrResult.blocks.map { it.confidence.toDouble() }
                .takeIf { it.isNotEmpty() }?.average() ?: 0.0
            val catalogConf = topMatch?.score ?: 0.0

            val confidence = scoreConfidence.execute(
                ScoreConfidenceUseCase.ScoreInput(
                    segmentationConfidence = spine.confidence,
                    ocrConfidence = ocrConf,
                    parserConfidence = parsed.confidence,
                    catalogMatchConfidence = catalogConf,
                    reasons = buildList {
                        if (ocrConf < 0.5) add("low OCR confidence")
                        if (catalogConf == 0.0) add("no catalog match")
                        if (parsed.titleCandidate == null) add("no title extracted")
                    }
                )
            )

            MediaItem(
                id = "${sessionId}_item_$index",
                mediaType = topMatch?.mediaType ?: MediaType.BOOK,
                title = topMatch?.title ?: parsed.titleCandidate,
                creatorName = topMatch?.creatorName ?: parsed.creatorCandidate,
                subtitle = null,
                normalizedTitle = topMatch?.title,
                normalizedCreatorName = topMatch?.creatorName,
                confidence = confidence,
                source = if (topMatch != null) ItemSource.CATALOG_MATCHED else ItemSource.OCR_ONLY,
                cropRef = spine.cropRef,
                rawText = parsed.allLines,
                externalIds = topMatch?.let { mapOf(it.source.name to it.externalId) } ?: emptyMap()
            )
        }

        val session = ScanSession(
            id = sessionId,
            createdAt = clock(),
            sourceImageRef = image.ref,
            quality = ImageQualityAssessment(
                blurScore = 1.0,
                brightness = 0.8,
                isAcceptable = true,
                reasons = emptyList()
            ),
            status = ScanStatus.COMPLETE,
            detectedItems = items
        )

        runSavePhase { scanRepository.saveSession(session) }
        return session
    }

    private fun spineDimension(spineSize: Float, fallback: Int): Int {
        val rounded = spineSize.toInt()
        return if (rounded > 0) rounded else fallback
    }

    private inline fun <T> runImagePhase(block: () -> T): T = try {
        block()
    } catch (e: ScanFailure) {
        throw e
    } catch (e: Throwable) {
        throw ScanFailure.ImageProcessing(e)
    }

    private inline fun <T> runOcrPhase(block: () -> T): T = try {
        block()
    } catch (e: ScanFailure) {
        throw e
    } catch (e: Throwable) {
        throw ScanFailure.Ocr(e)
    }

    private inline fun <T> runMetadataPhase(block: () -> T): T = try {
        block()
    } catch (e: ScanFailure) {
        throw e
    } catch (e: Throwable) {
        throw ScanFailure.MetadataLookup(e)
    }

    private inline fun <T> runSavePhase(block: () -> T): T = try {
        block()
    } catch (e: ScanFailure) {
        throw e
    } catch (e: Throwable) {
        throw ScanFailure.Save(e)
    }
}
