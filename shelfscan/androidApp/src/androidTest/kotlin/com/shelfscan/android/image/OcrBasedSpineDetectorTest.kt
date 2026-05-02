package com.shelfscan.android.image

import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.shelfscan.android.test.TestImageLoader
import com.shelfscan.shared.core.model.CapturedImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OcrBasedSpineDetectorTest {

    private lateinit var detector: OcrBasedSpineDetector
    private lateinit var recognizer: TextRecognizer
    private lateinit var imageLoader: TestImageLoader
    private lateinit var testImagePath: String

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        detector = OcrBasedSpineDetector(context, recognizer)
        imageLoader = TestImageLoader()
        testImagePath = imageLoader.loadAsset("test_bookshelf.jpg")
    }

    @After
    fun tearDown() {
        imageLoader.cleanup()
        recognizer.close()
    }

    @Test
    fun detectShelfItemsFromBundledImageReturnsSpines() = runBlocking {
        val image = CapturedImage(ref = testImagePath, widthPx = 4000, heightPx = 3000)
        val spines = detector.detectShelfItems(image)

        // Should return at least one spine (either detected or whole-image fallback)
        assertTrue(spines.isNotEmpty(), "Expected at least one spine")
    }

    @Test
    fun detectedSpinesHaveValidBoundingBoxes() = runBlocking {
        val image = CapturedImage(ref = testImagePath, widthPx = 4000, heightPx = 3000)
        val spines = detector.detectShelfItems(image)

        spines.forEach { spine ->
            val bbox = spine.boundingBox
            assertTrue(bbox.left >= 0f, "Left must be >= 0")
            assertTrue(bbox.top >= 0f, "Top must be >= 0")
            assertTrue(bbox.right > bbox.left, "Right must be > left")
            assertTrue(bbox.bottom > bbox.top, "Bottom must be > top")
        }
    }

    @Test
    fun detectedSpinesHavePositiveConfidence() = runBlocking {
        val image = CapturedImage(ref = testImagePath, widthPx = 4000, heightPx = 3000)
        val spines = detector.detectShelfItems(image)

        spines.forEach { spine ->
            assertTrue(spine.confidence > 0.0, "Confidence must be positive, got ${spine.confidence}")
        }
    }

    @Test
    fun detectedSpineCropRefsPointToExistingFiles() = runBlocking {
        val image = CapturedImage(ref = testImagePath, widthPx = 4000, heightPx = 3000)
        val spines = detector.detectShelfItems(image)

        spines.forEach { spine ->
            assertTrue(
                File(spine.cropRef).exists(),
                "cropRef should point to existing file: ${spine.cropRef}"
            )
        }
    }

    @Test
    fun normalizeForOcrPassesThroughImageData() = runBlocking {
        val image = CapturedImage(ref = testImagePath, widthPx = 4000, heightPx = 3000)
        val processed = detector.normalizeForOcr(image)

        assertEquals(testImagePath, processed.ref)
        assertEquals(4000, processed.widthPx)
        assertEquals(3000, processed.heightPx)
    }

    @Test
    fun nonExistentFileFallsBackToWholeImageSpine() = runBlocking {
        val image = CapturedImage(ref = "/nonexistent/image.jpg", widthPx = 640, heightPx = 480)
        val spines = detector.detectShelfItems(image)

        assertEquals(1, spines.size)
        assertEquals(0.5, spines[0].confidence)
        assertEquals("/nonexistent/image.jpg", spines[0].cropRef)
    }
}
