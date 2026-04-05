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
        val processed = imagePreprocessor.normalizeForOcr(image)
        val spines = imagePreprocessor.detectShelfItems(image)

        val items = spines.mapIndexed { index, spine ->
            val spineImage = ProcessedImage(
                ref = spine.cropRef,
                widthPx = processed.widthPx,
                heightPx = processed.heightPx
            )
            val ocrResult = ocrEngine.recognizeText(spineImage)
            val parsed = parseItem.execute(ocrResult.blocks)

            val catalogMatches = if (parsed.titleCandidate != null) {
                metadataLookupService.search(
                    mediaType = MediaType.BOOK,
                    title = parsed.titleCandidate,
                    creatorName = parsed.creatorCandidate
                )
            } else emptyList()

            val topMatch = catalogMatches.firstOrNull()
            val ocrConf = ocrResult.blocks.map { it.confidence.toDouble() }
                .takeIf { it.isNotEmpty() }?.average() ?: 0.0
            val catalogConf = topMatch?.score ?: 0.0

            val confidence = scoreConfidence.execute(
                ScoreConfidenceUseCase.ScoreInput(
                    segmentationConfidence = spine.confidence,
                    ocrConfidence = ocrConf,
                    parserConfidence = if (parsed.titleCandidate != null) 0.7 else 0.1,
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

        scanRepository.saveSession(session)
        return session
    }
}
