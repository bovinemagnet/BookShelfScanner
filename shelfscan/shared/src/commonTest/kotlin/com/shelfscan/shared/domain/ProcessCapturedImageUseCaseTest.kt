package com.shelfscan.shared.domain

import com.shelfscan.shared.core.model.CapturedImage
import com.shelfscan.shared.core.model.CatalogMatch
import com.shelfscan.shared.core.model.ConfidenceBand
import com.shelfscan.shared.core.model.ItemSource
import com.shelfscan.shared.core.model.MediaType
import com.shelfscan.shared.core.model.OcrResult
import com.shelfscan.shared.core.model.ProcessedImage
import com.shelfscan.shared.core.model.RecognizedTextBlock
import com.shelfscan.shared.core.model.ScanStatus
import com.shelfscan.shared.data.repository.DefaultScanRepository
import com.shelfscan.shared.domain.scan.ProcessCapturedImageUseCase
import com.shelfscan.shared.integration.ConfigurableFakeOcrEngine
import com.shelfscan.shared.integration.MultiSpineImagePreprocessor
import com.shelfscan.shared.integration.catalogMatchFor
import com.shelfscan.shared.integration.ocrResultFor
import com.shelfscan.shared.integration.threeSpines
import com.shelfscan.shared.platform.MetadataLookupService
import com.shelfscan.shared.platform.NoOpMetadataLookupService
import com.shelfscan.shared.platform.OcrEngine
import com.shelfscan.shared.platform.PassthroughImagePreprocessor
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProcessCapturedImageUseCaseTest {

    private class FakeOcrEngine(private val fakeText: String) : OcrEngine {
        override suspend fun recognizeText(image: ProcessedImage): OcrResult {
            return OcrResult(
                blocks = listOf(
                    RecognizedTextBlock(text = fakeText, confidence = 0.9f, boundingBox = null)
                ),
                rawText = fakeText
            )
        }
    }

    @Test
    fun `execute returns session with one item from OCR text`() {
        runBlocking {
            val repository = DefaultScanRepository()
            val useCase = ProcessCapturedImageUseCase(
                imagePreprocessor = PassthroughImagePreprocessor(),
                ocrEngine = FakeOcrEngine("The Great Gatsby"),
                metadataLookupService = NoOpMetadataLookupService(),
                scanRepository = repository
            )

            val image = CapturedImage(ref = "/fake/path.jpg", widthPx = 100, heightPx = 200)
            val session = useCase.execute(image, "test_session")

            assertEquals(ScanStatus.COMPLETE, session.status)
            assertEquals(1, session.detectedItems.size)

            val item = session.detectedItems.first()
            assertEquals("The Great Gatsby", item.title)
            assertEquals(ItemSource.OCR_ONLY, item.source)
            assertEquals(MediaType.BOOK, item.mediaType)

            // Verify session was persisted
            assertNotNull(repository.getSession("test_session"))
        }
    }

    @Test
    fun `execute with multi-line OCR picks the title and author correctly`() { runBlocking {
        val multiLineEngine = object : OcrEngine {
            override suspend fun recognizeText(image: ProcessedImage): OcrResult {
                return OcrResult(
                    blocks = listOf(
                        RecognizedTextBlock(text = "Clean Code", confidence = 0.9f, boundingBox = null),
                        RecognizedTextBlock(text = "Robert C. Martin", confidence = 0.85f, boundingBox = null),
                        RecognizedTextBlock(text = "A Handbook", confidence = 0.7f, boundingBox = null)
                    ),
                    rawText = "Clean Code\nRobert C. Martin\nA Handbook"
                )
            }
        }

        val useCase = ProcessCapturedImageUseCase(
            imagePreprocessor = PassthroughImagePreprocessor(),
            ocrEngine = multiLineEngine,
            metadataLookupService = NoOpMetadataLookupService(),
            scanRepository = DefaultScanRepository()
        )

        val image = CapturedImage(ref = "/fake/path.jpg", widthPx = 100, heightPx = 200)
        val session = useCase.execute(image, "test_multi")

        val item = session.detectedItems.first()
        assertEquals("Clean Code", item.title)
        assertEquals("Robert C. Martin", item.creatorName)
        assertEquals(3, item.rawText.size)
    } }

    @Test
    fun `metadata failure on one spine degrades that item instead of failing the scan`() { runBlocking {
        val flakyLookup = object : MetadataLookupService {
            var calls = 0
            override suspend fun search(
                mediaType: MediaType,
                title: String?,
                creatorName: String?
            ): List<CatalogMatch> {
                calls++
                if (calls == 2) throw RuntimeException("upstream went down")
                return listOf(catalogMatchFor(title ?: ""))
            }
        }
        val useCase = ProcessCapturedImageUseCase(
            imagePreprocessor = MultiSpineImagePreprocessor(threeSpines()),
            ocrEngine = ConfigurableFakeOcrEngine(
                resultsByRef = mapOf(
                    "crop_0" to ocrResultFor("Clean Code"),
                    "crop_1" to ocrResultFor("Refactoring"),
                    "crop_2" to ocrResultFor("Design Patterns")
                )
            ),
            metadataLookupService = flakyLookup,
            scanRepository = DefaultScanRepository()
        )

        val image = CapturedImage(ref = "/fake/shelf.jpg", widthPx = 1280, heightPx = 960)
        val session = useCase.execute(image, "test_partial_metadata")

        assertEquals(3, session.detectedItems.size, "all spines must survive one lookup failure")
        assertEquals(ItemSource.CATALOG_MATCHED, session.detectedItems[0].source)
        assertEquals(ItemSource.OCR_ONLY, session.detectedItems[1].source)
        assertEquals(ItemSource.CATALOG_MATCHED, session.detectedItems[2].source)
        assertTrue(
            "metadata lookup failed" in session.detectedItems[1].confidence.reasons,
            "the degraded item must say why: ${session.detectedItems[1].confidence.reasons}"
        )
    } }

    @Test
    fun `session createdAt is stamped with current time by default`() { runBlocking {
        val useCase = ProcessCapturedImageUseCase(
            imagePreprocessor = PassthroughImagePreprocessor(),
            ocrEngine = FakeOcrEngine("The Great Gatsby"),
            metadataLookupService = NoOpMetadataLookupService(),
            scanRepository = DefaultScanRepository()
        )

        val image = CapturedImage(ref = "/fake/path.jpg", widthPx = 100, heightPx = 200)
        val session = useCase.execute(image, "test_clock")

        assertTrue(session.createdAt > 0L, "expected a real timestamp, got ${session.createdAt}")
    } }

    @Test
    fun `confidence is low when no catalogue match`() { runBlocking {
        val useCase = ProcessCapturedImageUseCase(
            imagePreprocessor = PassthroughImagePreprocessor(),
            ocrEngine = FakeOcrEngine("Some Book Title"),
            metadataLookupService = NoOpMetadataLookupService(),
            scanRepository = DefaultScanRepository()
        )

        val image = CapturedImage(ref = "/fake/path.jpg", widthPx = 100, heightPx = 200)
        val session = useCase.execute(image, "test_confidence")

        val item = session.detectedItems.first()
        // With no catalogue match (0.0) and passthrough segmentation (0.5),
        // confidence should be LOW or NEEDS_REVIEW
        assertTrue(
            item.confidence.band == ConfidenceBand.LOW || item.confidence.band == ConfidenceBand.NEEDS_REVIEW,
            "Expected LOW or NEEDS_REVIEW but got ${item.confidence.band}"
        )
    } }
}
