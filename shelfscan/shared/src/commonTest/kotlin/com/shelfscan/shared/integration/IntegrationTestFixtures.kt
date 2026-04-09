package com.shelfscan.shared.integration

import com.shelfscan.shared.core.model.*
import com.shelfscan.shared.platform.ImagePreprocessor
import com.shelfscan.shared.platform.MetadataLookupService
import com.shelfscan.shared.platform.OcrEngine

// --- Factory functions ---

fun makeMediaItem(
    id: String = "item_0",
    title: String? = "Clean Code",
    creator: String? = "Robert C. Martin",
    mediaType: MediaType = MediaType.BOOK,
    source: ItemSource = ItemSource.OCR_ONLY,
    confidence: ConfidenceScore = ConfidenceScore(
        value = 0.85,
        band = ConfidenceBand.HIGH,
        reasons = emptyList()
    )
) = MediaItem(
    id = id,
    mediaType = mediaType,
    title = title,
    creatorName = creator,
    subtitle = null,
    normalizedTitle = title,
    normalizedCreatorName = creator,
    confidence = confidence,
    source = source,
    cropRef = null,
    rawText = listOfNotNull(title, creator)
)

fun makeScanSession(
    id: String = "session_1",
    items: List<MediaItem> = listOf(makeMediaItem()),
    status: ScanStatus = ScanStatus.COMPLETE
) = ScanSession(
    id = id,
    createdAt = 0L,
    sourceImageRef = "test.jpg",
    quality = ImageQualityAssessment(
        blurScore = 0.1,
        brightness = 0.8,
        isAcceptable = true,
        reasons = emptyList()
    ),
    status = status,
    detectedItems = items
)

// --- Configurable fakes ---

class ConfigurableFakeOcrEngine(
    private val resultsByRef: Map<String, OcrResult> = emptyMap(),
    private val defaultResult: OcrResult = OcrResult(
        blocks = listOf(
            RecognizedTextBlock(text = "Default Title", confidence = 0.9f, boundingBox = null)
        ),
        rawText = "Default Title"
    ),
    private val shouldThrow: Boolean = false
) : OcrEngine {
    override suspend fun recognizeText(image: ProcessedImage): OcrResult {
        if (shouldThrow) throw RuntimeException("OCR engine failure")
        return resultsByRef[image.ref] ?: defaultResult
    }
}

class FakeCatalogLookupService(
    private val matchesByTitle: Map<String, List<CatalogMatch>> = emptyMap()
) : MetadataLookupService {
    override suspend fun search(
        mediaType: MediaType,
        title: String?,
        creatorName: String?
    ): List<CatalogMatch> {
        return title?.let { matchesByTitle[it] } ?: emptyList()
    }
}

class MultiSpineImagePreprocessor(
    private val spines: List<DetectedSpine>
) : ImagePreprocessor {
    override suspend fun normalizeForOcr(image: CapturedImage): ProcessedImage {
        return ProcessedImage(
            ref = image.ref,
            widthPx = image.widthPx,
            heightPx = image.heightPx
        )
    }

    override suspend fun detectShelfItems(image: CapturedImage): List<DetectedSpine> {
        return spines
    }
}

// --- Common test data ---

val testImage = CapturedImage(ref = "/fake/shelf.jpg", widthPx = 1280, heightPx = 960)

fun ocrResultFor(text: String, confidence: Float = 0.9f) = OcrResult(
    blocks = listOf(RecognizedTextBlock(text = text, confidence = confidence, boundingBox = null)),
    rawText = text
)

fun catalogMatchFor(
    title: String,
    creator: String? = null,
    score: Double = 0.95
) = CatalogMatch(
    source = CatalogSource("test_catalog"),
    mediaType = MediaType.BOOK,
    title = title,
    creatorName = creator,
    year = null,
    score = score,
    externalId = "ext_${title.lowercase().replace(" ", "_")}"
)

fun threeSpines() = listOf(
    DetectedSpine(
        id = "spine_0",
        cropRef = "crop_0",
        boundingBox = BoundingBox(0f, 0f, 100f, 960f),
        confidence = 0.8
    ),
    DetectedSpine(
        id = "spine_1",
        cropRef = "crop_1",
        boundingBox = BoundingBox(100f, 0f, 200f, 960f),
        confidence = 0.7
    ),
    DetectedSpine(
        id = "spine_2",
        cropRef = "crop_2",
        boundingBox = BoundingBox(200f, 0f, 300f, 960f),
        confidence = 0.75
    )
)
