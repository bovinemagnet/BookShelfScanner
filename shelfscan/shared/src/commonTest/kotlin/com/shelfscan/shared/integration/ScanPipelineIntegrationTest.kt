package com.shelfscan.shared.integration

import com.shelfscan.shared.core.model.*
import com.shelfscan.shared.data.repository.DefaultScanRepository
import com.shelfscan.shared.domain.scan.ProcessCapturedImageUseCase
import com.shelfscan.shared.platform.NoOpMetadataLookupService
import com.shelfscan.shared.platform.PassthroughImagePreprocessor
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanPipelineIntegrationTest {

    @Test
    fun `single spine OCR-only produces one item with low confidence`() = runBlocking {
        val repository = DefaultScanRepository()
        val useCase = ProcessCapturedImageUseCase(
            imagePreprocessor = PassthroughImagePreprocessor(),
            ocrEngine = ConfigurableFakeOcrEngine(
                defaultResult = ocrResultFor("The Great Gatsby")
            ),
            metadataLookupService = NoOpMetadataLookupService(),
            scanRepository = repository
        )

        val session = useCase.execute(testImage, "session_single")

        assertEquals(ScanStatus.COMPLETE, session.status)
        assertEquals(1, session.detectedItems.size)

        val item = session.detectedItems.first()
        assertEquals("The Great Gatsby", item.title)
        assertEquals(ItemSource.OCR_ONLY, item.source)
        assertEquals(MediaType.BOOK, item.mediaType)
        assertTrue(
            item.confidence.band == ConfidenceBand.LOW || item.confidence.band == ConfidenceBand.NEEDS_REVIEW,
            "Expected LOW or NEEDS_REVIEW but got ${item.confidence.band}"
        )

        assertNotNull(repository.getSession("session_single"))
        Unit
    }

    @Test
    fun `multi-spine scenario produces items per spine with correct titles`() = runBlocking {
        val spines = threeSpines()
        val repository = DefaultScanRepository()
        val useCase = ProcessCapturedImageUseCase(
            imagePreprocessor = MultiSpineImagePreprocessor(spines),
            ocrEngine = ConfigurableFakeOcrEngine(
                resultsByRef = mapOf(
                    "crop_0" to ocrResultFor("Clean Code"),
                    "crop_1" to ocrResultFor("Refactoring"),
                    "crop_2" to ocrResultFor("Design Patterns")
                )
            ),
            metadataLookupService = NoOpMetadataLookupService(),
            scanRepository = repository
        )

        val session = useCase.execute(testImage, "session_multi")

        assertEquals(3, session.detectedItems.size)
        assertEquals("Clean Code", session.detectedItems[0].title)
        assertEquals("Refactoring", session.detectedItems[1].title)
        assertEquals("Design Patterns", session.detectedItems[2].title)
        assertTrue(session.detectedItems.all { it.source == ItemSource.OCR_ONLY })
        assertNotNull(repository.getSession("session_multi"))
        Unit
    }

    @Test
    fun `catalogue match enriches item with normalised data`() = runBlocking {
        val repository = DefaultScanRepository()
        val useCase = ProcessCapturedImageUseCase(
            imagePreprocessor = PassthroughImagePreprocessor(),
            ocrEngine = ConfigurableFakeOcrEngine(
                defaultResult = ocrResultFor("Clean Code")
            ),
            metadataLookupService = FakeCatalogLookupService(
                matchesByTitle = mapOf(
                    "Clean Code" to listOf(
                        catalogMatchFor("Clean Code", creator = "Robert C. Martin")
                    )
                )
            ),
            scanRepository = repository
        )

        val session = useCase.execute(testImage, "session_catalog")
        val item = session.detectedItems.first()

        assertEquals(ItemSource.CATALOG_MATCHED, item.source)
        assertEquals("Clean Code", item.normalizedTitle)
        assertEquals("Robert C. Martin", item.normalizedCreatorName)
        assertTrue(item.externalIds.isNotEmpty())
    }

    @Test
    fun `mixed match and miss produces different sources and confidences`() = runBlocking {
        val spines = listOf(
            DetectedSpine("spine_0", "crop_0", BoundingBox(0f, 0f, 100f, 960f), 0.8),
            DetectedSpine("spine_1", "crop_1", BoundingBox(100f, 0f, 200f, 960f), 0.7)
        )
        val repository = DefaultScanRepository()
        val useCase = ProcessCapturedImageUseCase(
            imagePreprocessor = MultiSpineImagePreprocessor(spines),
            ocrEngine = ConfigurableFakeOcrEngine(
                resultsByRef = mapOf(
                    "crop_0" to ocrResultFor("Clean Code"),
                    "crop_1" to ocrResultFor("Unknown Book")
                )
            ),
            metadataLookupService = FakeCatalogLookupService(
                matchesByTitle = mapOf(
                    "Clean Code" to listOf(
                        catalogMatchFor("Clean Code", creator = "Robert C. Martin")
                    )
                )
            ),
            scanRepository = repository
        )

        val session = useCase.execute(testImage, "session_mixed")

        assertEquals(ItemSource.CATALOG_MATCHED, session.detectedItems[0].source)
        assertEquals(ItemSource.OCR_ONLY, session.detectedItems[1].source)
        assertTrue(
            session.detectedItems[0].confidence.value > session.detectedItems[1].confidence.value,
            "Catalogue-matched item should have higher confidence"
        )
    }

    @Test
    fun `empty OCR result produces item with no title and needs review`() = runBlocking {
        val repository = DefaultScanRepository()
        val useCase = ProcessCapturedImageUseCase(
            imagePreprocessor = PassthroughImagePreprocessor(),
            ocrEngine = ConfigurableFakeOcrEngine(
                defaultResult = OcrResult(blocks = emptyList(), rawText = "")
            ),
            metadataLookupService = NoOpMetadataLookupService(),
            scanRepository = repository
        )

        val session = useCase.execute(testImage, "session_empty")
        val item = session.detectedItems.first()

        assertNull(item.title)
        assertEquals(ConfidenceBand.NEEDS_REVIEW, item.confidence.band)
        assertTrue(item.confidence.reasons.any { "no title" in it.lowercase() })
    }

    @Test
    fun `OCR engine exception propagates and session is not persisted`() = runBlocking {
        val repository = DefaultScanRepository()
        val useCase = ProcessCapturedImageUseCase(
            imagePreprocessor = PassthroughImagePreprocessor(),
            ocrEngine = ConfigurableFakeOcrEngine(shouldThrow = true),
            metadataLookupService = NoOpMetadataLookupService(),
            scanRepository = repository
        )

        assertFailsWith<RuntimeException> {
            useCase.execute(testImage, "session_error")
        }

        assertNull(repository.getSession("session_error"))
    }
}
