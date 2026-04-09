package com.shelfscan.android.test

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * Loads test images from androidTest assets into the app's cache directory
 * so that file-path-based APIs (MlKitOcrAdapter, OcrBasedSpineDetector) can read them.
 */
class TestImageLoader {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val tempFiles = mutableListOf<File>()

    fun loadAsset(assetName: String): String {
        val inputStream = context.assets.open(assetName)
        val tempFile = File(targetContext.cacheDir, "test_$assetName")
        tempFile.outputStream().use { output ->
            inputStream.copyTo(output)
        }
        tempFiles.add(tempFile)
        return tempFile.absolutePath
    }

    fun cleanup() {
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
        // Clean up any spine crop files
        targetContext.cacheDir.listFiles()
            ?.filter { it.name.startsWith("spine_") }
            ?.forEach { it.delete() }
    }
}
