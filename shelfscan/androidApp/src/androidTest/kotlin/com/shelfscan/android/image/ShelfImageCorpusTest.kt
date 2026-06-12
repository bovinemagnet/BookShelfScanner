package com.shelfscan.android.image

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.shelfscan.android.test.TestImageLoader
import com.shelfscan.shared.core.model.CapturedImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import kotlin.test.assertTrue

/**
 * Runs spine detection across every bookshelf photo bundled in androidTest assets.
 * New photos dropped into the assets folder are picked up automatically.
 */
@RunWith(Parameterized::class)
class ShelfImageCorpusTest(
    private val assetName: String,
) {
    companion object {
        // ML Kit decodes the full bitmap in-process; beyond this pixel count the
        // test process exhausts its heap before recognition can start.
        private const val MAX_DECODABLE_PIXELS = 40_000_000L

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun shelfImages(): List<String> {
            val assets = InstrumentationRegistry.getInstrumentation().context.assets
            return assets
                .list("")
                .orEmpty()
                .filter {
                    it.endsWith(".jpg", ignoreCase = true) ||
                        it.endsWith(".png", ignoreCase = true)
                }.sorted()
        }
    }

    private lateinit var detector: OcrBasedSpineDetector
    private lateinit var recognizer: TextRecognizer
    private lateinit var imageLoader: TestImageLoader
    private lateinit var imagePath: String
    private var widthPx = 0
    private var heightPx = 0

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        detector = OcrBasedSpineDetector(context, recognizer)
        imageLoader = TestImageLoader()
        imagePath = imageLoader.loadAsset(assetName)

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imagePath, options)
        widthPx = options.outWidth
        heightPx = options.outHeight

        assumeTrue(
            "$assetName is ${widthPx}x$heightPx — too large to decode in the test process heap",
            widthPx.toLong() * heightPx <= MAX_DECODABLE_PIXELS,
        )
    }

    @After
    fun tearDown() {
        imageLoader.cleanup()
        recognizer.close()
    }

    @Test
    fun detectsSpinesWithValidGeometry() {
        runBlocking {
            val image = CapturedImage(ref = imagePath, widthPx = widthPx, heightPx = heightPx)
            val spines = detector.detectShelfItems(image)

            assertTrue(spines.isNotEmpty(), "$assetName: expected at least one spine")
            assertTrue(
                spines.none { it.isWholeImageFallback },
                "$assetName: OCR found no readable text — whole-image fallback returned",
            )

            spines.forEach { spine ->
                val bbox = spine.boundingBox
                assertTrue(bbox.left >= 0f, "$assetName/${spine.id}: left must be >= 0")
                assertTrue(bbox.top >= 0f, "$assetName/${spine.id}: top must be >= 0")
                assertTrue(bbox.right > bbox.left, "$assetName/${spine.id}: right must be > left")
                assertTrue(bbox.bottom > bbox.top, "$assetName/${spine.id}: bottom must be > top")
                assertTrue(
                    spine.confidence > 0.0,
                    "$assetName/${spine.id}: confidence must be positive, got ${spine.confidence}",
                )
                assertTrue(
                    File(spine.cropRef).exists(),
                    "$assetName/${spine.id}: cropRef should point to existing file: ${spine.cropRef}",
                )
            }
        }
    }
}
