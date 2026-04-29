package com.shelfscan.shared.platform

import com.shelfscan.shared.core.model.CapturedImage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CallbackBackedImagePreprocessorTest {

    private val sampleImage = CapturedImage(ref = "/tmp/img.jpg", widthPx = 100, heightPx = 200)

    @Test
    fun `normalizeForOcr resumes with ProcessedImage on success`() = runBlocking {
        val preprocessor = CallbackBackedImagePreprocessor(
            normalize = { _, _, _, onSuccess, _ ->
                onSuccess("/tmp/normalized.jpg", 800, 600)
            },
            detect = { _, _, _, onSuccess, _ -> onSuccess(emptyList()) },
        )

        val result = preprocessor.normalizeForOcr(sampleImage)

        assertEquals("/tmp/normalized.jpg", result.ref)
        assertEquals(800, result.widthPx)
        assertEquals(600, result.heightPx)
    }

    @Test
    fun `normalizeForOcr propagates CallbackImagePreprocessorException on error`() = runBlocking {
        val preprocessor = CallbackBackedImagePreprocessor(
            normalize = { _, _, _, _, onError -> onError("disk full") },
            detect = { _, _, _, onSuccess, _ -> onSuccess(emptyList()) },
        )

        val ex = assertFailsWith<CallbackImagePreprocessorException> {
            preprocessor.normalizeForOcr(sampleImage)
        }
        assertEquals("disk full", ex.message)
    }

    @Test
    fun `detectShelfItems maps spines correctly on success`() = runBlocking {
        val preprocessor = CallbackBackedImagePreprocessor(
            normalize = { _, _, _, onSuccess, _ -> onSuccess("", 0, 0) },
            detect = { _, _, _, onSuccess, _ ->
                onSuccess(
                    listOf(
                        FfiDetectedSpine(
                            id = "spine_0",
                            cropRef = "/tmp/spine_0.jpg",
                            boundingBoxLeft = 0f,
                            boundingBoxTop = 0f,
                            boundingBoxRight = 50f,
                            boundingBoxBottom = 200f,
                            confidence = 0.7,
                        )
                    )
                )
            },
        )

        val spines = preprocessor.detectShelfItems(sampleImage)

        assertEquals(1, spines.size)
        assertEquals("spine_0", spines[0].id)
        assertEquals("/tmp/spine_0.jpg", spines[0].cropRef)
        assertEquals(0f, spines[0].boundingBox.left)
        assertEquals(50f, spines[0].boundingBox.right)
        assertEquals(0.7, spines[0].confidence)
    }

    @Test
    fun `detectShelfItems propagates CallbackImagePreprocessorException on error`() = runBlocking {
        val preprocessor = CallbackBackedImagePreprocessor(
            normalize = { _, _, _, onSuccess, _ -> onSuccess("", 0, 0) },
            detect = { _, _, _, _, onError -> onError("segmentation failed") },
        )

        val ex = assertFailsWith<CallbackImagePreprocessorException> {
            preprocessor.detectShelfItems(sampleImage)
        }
        assertEquals("segmentation failed", ex.message)
    }

    @Test
    fun `passes image ref and dimensions through to callbacks`() = runBlocking {
        var capturedNormalizePath = ""
        var capturedDetectWidth = 0
        val preprocessor = CallbackBackedImagePreprocessor(
            normalize = { path, _, _, onSuccess, _ ->
                capturedNormalizePath = path
                onSuccess("", 0, 0)
            },
            detect = { _, w, _, onSuccess, _ ->
                capturedDetectWidth = w
                onSuccess(emptyList())
            },
        )

        preprocessor.normalizeForOcr(sampleImage)
        preprocessor.detectShelfItems(sampleImage)

        assertEquals("/tmp/img.jpg", capturedNormalizePath)
        assertEquals(100, capturedDetectWidth)
    }

    @Test
    fun `normalizeForOcr tolerates a late onError after onSuccess`() = runBlocking {
        val preprocessor = CallbackBackedImagePreprocessor(
            normalize = { _, _, _, onSuccess, onError ->
                onSuccess("/tmp/normalized.jpg", 1, 2)
                onError("late error")
            },
            detect = { _, _, _, onSuccess, _ -> onSuccess(emptyList()) },
        )

        val result = preprocessor.normalizeForOcr(sampleImage)

        assertEquals("/tmp/normalized.jpg", result.ref)
        assertEquals(1, result.widthPx)
        assertEquals(2, result.heightPx)
    }

    @Test
    fun `detectShelfItems tolerates a duplicate onSuccess fire`() = runBlocking {
        val preprocessor = CallbackBackedImagePreprocessor(
            normalize = { _, _, _, onSuccess, _ -> onSuccess("", 0, 0) },
            detect = { _, _, _, onSuccess, _ ->
                onSuccess(emptyList())
                onSuccess(
                    listOf(
                        FfiDetectedSpine(
                            id = "late",
                            cropRef = "",
                            boundingBoxLeft = 0f, boundingBoxTop = 0f,
                            boundingBoxRight = 0f, boundingBoxBottom = 0f,
                            confidence = 0.0,
                        )
                    )
                )
            },
        )

        val spines = preprocessor.detectShelfItems(sampleImage)

        assertEquals(0, spines.size)
    }
}
