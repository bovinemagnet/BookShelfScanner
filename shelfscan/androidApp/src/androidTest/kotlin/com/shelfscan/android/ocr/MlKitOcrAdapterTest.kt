package com.shelfscan.android.ocr

import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.shelfscan.android.test.TestImageLoader
import com.shelfscan.shared.core.model.ProcessedImage
import com.shelfscan.shared.domain.scan.ScanFailure
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MlKitOcrAdapterTest {

    private lateinit var adapter: MlKitOcrAdapter
    private lateinit var recognizer: TextRecognizer
    private lateinit var imageLoader: TestImageLoader
    private lateinit var testImagePath: String

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        adapter = MlKitOcrAdapter(context, recognizer)
        imageLoader = TestImageLoader()
        testImagePath = imageLoader.loadAsset("test_bookshelf.jpg")
    }

    @After
    fun tearDown() {
        imageLoader.cleanup()
        recognizer.close()
    }

    @Test
    fun recognitionExceedingTimeoutSurfacesAsOcrFailure() = runBlocking {
        // 1 ms is unreachable for a 4000x3000 image — the timeout must fire
        // and surface as a typed OCR failure, not a CancellationException.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val impatientAdapter = MlKitOcrAdapter(context, recognizer, timeoutMillis = 1)
        val image = ProcessedImage(ref = testImagePath, widthPx = 4000, heightPx = 3000)

        assertFailsWith<ScanFailure.Ocr> {
            impatientAdapter.recognizeText(image)
        }
        Unit
    }

    @Test
    fun recogniseTextFromBundledImageDoesNotCrash() = runBlocking {
        val image = ProcessedImage(ref = testImagePath, widthPx = 4000, heightPx = 3000)
        val result = adapter.recognizeText(image)

        assertTrue(result.blocks.isNotEmpty(), "Expected OCR to detect text blocks from bookshelf image")

    }

    @Test
    fun blocksHaveValidConfidenceValues() = runBlocking {
        val image = ProcessedImage(ref = testImagePath, widthPx = 4000, heightPx = 3000)
        val result = adapter.recognizeText(image)

        result.blocks.forEach { block ->
            assertTrue(
                block.confidence in 0.0f..1.0f,
                "Confidence ${block.confidence} out of range [0, 1]"
            )
        }
    }

    @Test
    fun blocksWithBoundingBoxesHaveValidDimensions() = runBlocking {
        val image = ProcessedImage(ref = testImagePath, widthPx = 4000, heightPx = 3000)
        val result = adapter.recognizeText(image)

        val boxedBlocks = result.blocks.filter { it.boundingBox != null }
        boxedBlocks.forEach { block ->
            val bbox = block.boundingBox!!
            assertTrue(bbox.right > bbox.left, "Bounding box width must be positive")
            assertTrue(bbox.bottom > bbox.top, "Bounding box height must be positive")
        }
    }

    @Test
    fun nonExistentFileReturnsEmptyResult() = runBlocking {
        val image = ProcessedImage(ref = "/nonexistent/path.jpg", widthPx = 100, heightPx = 100)
        val result = adapter.recognizeText(image)

        assertTrue(result.blocks.isEmpty())
        assertEquals("", result.rawText)
    }
}
