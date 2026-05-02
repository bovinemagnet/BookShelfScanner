package com.shelfscan.shared.core.model

enum class MediaType { BOOK, MOVIE, CD, UNKNOWN }

enum class ConfidenceBand { HIGH, MEDIUM, LOW, NEEDS_REVIEW }

enum class ScanStatus { PENDING, PROCESSING, COMPLETE, FAILED }

enum class ItemSource { OCR_ONLY, CATALOG_MATCHED, USER_EDITED }

data class ConfidenceScore(
    val value: Double,
    val band: ConfidenceBand,
    val reasons: List<String>
)

data class MediaItem(
    val id: String,
    val mediaType: MediaType,
    val title: String?,
    val creatorName: String?,
    val subtitle: String?,
    val normalizedTitle: String?,
    val normalizedCreatorName: String?,
    val confidence: ConfidenceScore,
    val source: ItemSource,
    val cropRef: String?,
    val rawText: List<String>,
    val externalIds: Map<String, String> = emptyMap()
)

data class ImageQualityAssessment(
    val blurScore: Double,
    val brightness: Double,
    val isAcceptable: Boolean,
    val reasons: List<String>
)

data class CapturedImage(
    val ref: String,
    val widthPx: Int,
    val heightPx: Int
)

data class ProcessedImage(
    val ref: String,
    val widthPx: Int,
    val heightPx: Int
)

data class DetectedSpine(
    val id: String,
    val cropRef: String,
    val boundingBox: BoundingBox,
    val confidence: Double
)

data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class RecognizedTextBlock(
    val text: String,
    val confidence: Float,
    val boundingBox: BoundingBox?
)

data class OcrResult(
    val blocks: List<RecognizedTextBlock>,
    val rawText: String
)

data class CatalogSource(val name: String)

data class CatalogMatch(
    val source: CatalogSource,
    val mediaType: MediaType,
    val title: String,
    val creatorName: String?,
    val year: Int?,
    val score: Double,
    val externalId: String
)

data class ScanSession(
    val id: String,
    val createdAt: Long,
    val sourceImageRef: String,
    val quality: ImageQualityAssessment,
    val status: ScanStatus,
    val detectedItems: List<MediaItem> = emptyList()
)

data class Collection(
    val id: String,
    val name: String,
    val createdAt: Long,
    val items: List<MediaItem> = emptyList()
)

sealed interface ScanError {
    data object CameraUnavailable : ScanError
    data object PermissionDenied : ScanError
    data object ImageTooBlurry : ScanError
    data object ImageProcessingFailed : ScanError
    data object OcrFailed : ScanError
    data object MetadataLookupFailed : ScanError
    data object SaveFailed : ScanError
}
