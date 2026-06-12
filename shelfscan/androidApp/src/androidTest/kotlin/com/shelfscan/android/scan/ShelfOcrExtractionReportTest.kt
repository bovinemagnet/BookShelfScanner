package com.shelfscan.android.scan

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.shelfscan.android.image.OcrBasedSpineDetector
import com.shelfscan.android.ocr.MlKitOcrAdapter
import com.shelfscan.android.test.TestImageLoader
import com.shelfscan.shared.core.model.CapturedImage
import com.shelfscan.shared.data.repository.DefaultScanRepository
import com.shelfscan.shared.domain.scan.ProcessCapturedImageUseCase
import com.shelfscan.shared.platform.NoOpMetadataLookupService
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Diagnostic, not a pass/fail test: runs the full scan pipeline (spine
 * detection → per-spine OCR → title/creator parsing) over every bundled
 * shelf photo and writes what was extracted to the app's external files
 * directory, for manual accuracy review:
 *
 *   adb pull /sdcard/Android/data/com.shelfscan.android/files/ocr-report
 */
@RunWith(Parameterized::class)
class ShelfOcrExtractionReportTest(
    private val assetName: String,
) {
    companion object {
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

    @Test
    fun extractTitlesAndWriteReport() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val useCase =
            ProcessCapturedImageUseCase(
                imagePreprocessor = OcrBasedSpineDetector(context, recognizer),
                ocrEngine = MlKitOcrAdapter(context, recognizer),
                metadataLookupService = NoOpMetadataLookupService(),
                scanRepository = DefaultScanRepository(),
            )
        val imageLoader = TestImageLoader()
        try {
            val imagePath = imageLoader.loadAsset(assetName)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imagePath, options)

            val reportDir = File(context.getExternalFilesDir(null), "ocr-report")
            reportDir.mkdirs()

            // A diagnostic must not gate the suite: pipeline failures are
            // recorded in the report rather than failing the test run.
            val session =
                try {
                    runBlocking {
                        useCase.execute(
                            CapturedImage(
                                ref = imagePath,
                                widthPx = options.outWidth,
                                heightPx = options.outHeight,
                            ),
                            sessionId = assetName,
                        )
                    }
                } catch (e: Exception) {
                    File(reportDir, "$assetName.txt").writeText(
                        "$assetName ${options.outWidth}x${options.outHeight} PIPELINE FAILED: $e\n",
                    )
                    return
                }

            File(reportDir, "$assetName.txt").writeText(
                buildString {
                    appendLine(
                        "$assetName ${options.outWidth}x${options.outHeight} " +
                            "items=${session.detectedItems.size}",
                    )
                    session.detectedItems.forEach { item ->
                        val conf = item.confidence
                        appendLine(
                            "  [${conf.band} %.2f] title=${item.title} | creator=${item.creatorName}"
                                .format(conf.value),
                        )
                        appendLine("    raw=${item.rawText}")
                    }
                },
            )
        } finally {
            imageLoader.cleanup()
            recognizer.close()
        }
    }
}
