package com.shelfscan.shared.domain

import com.shelfscan.shared.core.model.CapturedImage
import com.shelfscan.shared.core.model.ConfidenceBand
import com.shelfscan.shared.core.model.ItemSource
import com.shelfscan.shared.core.model.MediaType
import com.shelfscan.shared.core.model.OcrResult
import com.shelfscan.shared.core.model.ProcessedImage
import com.shelfscan.shared.core.model.RecognizedTextBlock
import com.shelfscan.shared.core.model.ScanStatus
import com.shelfscan.shared.data.repository.DefaultScanRepository
import com.shelfscan.shared.domain.scan.ProcessCapturedImageUseCase
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
    fun `execute with multi-line OCR picks longest as title`() { runBlocking {
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
        assertEquals("Robert C. Martin", item.title) // longest line
        assertEquals(3, item.rawText.size)
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
