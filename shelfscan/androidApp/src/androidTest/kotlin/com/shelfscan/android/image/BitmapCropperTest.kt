package com.shelfscan.android.image

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.shelfscan.android.test.TestImageLoader
import com.shelfscan.shared.core.model.BoundingBox
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Verifies the crop geometry of [BitmapCropper] against the bundled 4000×3000
 * `test_bookshelf.jpg`. Unlike `OcrBasedSpineDetectorTest` (which only asserts the
 * crop file exists), these assert the cropped output's actual pixel dimensions match
 * the box after clamping — exercising the `BitmapRegionDecoder` decode path directly.
 */
class BitmapCropperTest {

    private lateinit var cropper: BitmapCropper
    private lateinit var imageLoader: TestImageLoader
    private lateinit var testImagePath: String
    private lateinit var cropDir: File

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        imageLoader = TestImageLoader()
        testImagePath = imageLoader.loadAsset("test_bookshelf.jpg")
        cropDir = File(context.cacheDir, "cropper_test").apply { mkdirs() }
        cropper = BitmapCropper(cropDir)
    }

    @After
    fun tearDown() {
        imageLoader.cleanup()
        cropDir.deleteRecursively()
    }

    @Test
    fun cropProducesFileWithClampedDimensions() {
        val box = BoundingBox(left = 1000f, top = 500f, right = 1500f, bottom = 2000f)

        val outPath = cropper.cropAndSave(testImagePath, box, "0")

        assertTrue(File(outPath).exists(), "Cropped file should exist: $outPath")
        val (width, height) = imageSize(outPath)
        assertEquals(500, width, "Cropped width should equal right - left")
        assertEquals(1500, height, "Cropped height should equal bottom - top")
    }

    @Test
    fun cropClampsBoxExceedingImageBounds() {
        // Box runs past the right and bottom edges of the 4000×3000 image.
        val box = BoundingBox(left = 3900f, top = 2900f, right = 5000f, bottom = 4000f)

        val outPath = cropper.cropAndSave(testImagePath, box, "edge")

        val (width, height) = imageSize(outPath)
        assertEquals(100, width, "Width should be clamped to 4000 - 3900")
        assertEquals(100, height, "Height should be clamped to 3000 - 2900")
    }

    @Test
    fun cropFullImageBoxReturnsFullDimensions() {
        val box = BoundingBox(left = 0f, top = 0f, right = 4000f, bottom = 3000f)

        val outPath = cropper.cropAndSave(testImagePath, box, "full")

        val (width, height) = imageSize(outPath)
        assertEquals(4000, width)
        assertEquals(3000, height)
    }

    @Test
    fun croppedFileUsesSpinePrefixAndId() {
        val box = BoundingBox(left = 0f, top = 0f, right = 100f, bottom = 100f)

        val outPath = cropper.cropAndSave(testImagePath, box, "7")

        val name = File(outPath).name
        assertTrue(name.startsWith("spine_7_"), "Unexpected file name: $name")
        assertTrue(name.endsWith(".jpg"), "Unexpected file name: $name")
    }

    @Test
    fun cropOfNonExistentSourceThrows() {
        val box = BoundingBox(left = 0f, top = 0f, right = 100f, bottom = 100f)

        assertFailsWith<IllegalArgumentException> {
            cropper.cropAndSave("/nonexistent/image.jpg", box, "0")
        }
    }

    private fun imageSize(path: String): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        return opts.outWidth to opts.outHeight
    }
}
