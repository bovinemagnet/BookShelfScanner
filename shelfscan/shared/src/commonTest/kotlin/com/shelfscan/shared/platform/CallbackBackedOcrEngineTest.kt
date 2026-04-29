package com.shelfscan.shared.platform

import com.shelfscan.shared.core.model.ProcessedImage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CallbackBackedOcrEngineTest {

    private val sampleImage = ProcessedImage(ref = "/tmp/img.jpg", widthPx = 100, heightPx = 200)

    @Test
    fun `resumes successfully when callback fires onSuccess`() = runBlocking {
        val engine = CallbackBackedOcrEngine(
            recognize = { _, onSuccess, _ ->
                onSuccess(
                    "hello world",
                    listOf(
                        FfiOcrLine(
                            text = "hello world",
                            confidence = 0.9f,
                            hasBoundingBox = false,
                            boundingBoxLeft = 0f,
                            boundingBoxTop = 0f,
                            boundingBoxRight = 0f,
                            boundingBoxBottom = 0f,
                        )
                    )
                )
            }
        )

        val result = engine.recognizeText(sampleImage)

        assertEquals("hello world", result.rawText)
        assertEquals(1, result.blocks.size)
        assertEquals("hello world", result.blocks[0].text)
        assertEquals(0.9f, result.blocks[0].confidence)
        assertNull(result.blocks[0].boundingBox)
    }

    @Test
    fun `propagates CallbackOcrException when callback fires onError`() = runBlocking {
        val engine = CallbackBackedOcrEngine(
            recognize = { _, _, onError -> onError("vision failed") }
        )

        val ex = assertFailsWith<CallbackOcrException> {
            engine.recognizeText(sampleImage)
        }
        assertEquals("vision failed", ex.message)
    }

    @Test
    fun `passes the image ref through to the callback as imagePath`() = runBlocking {
        var capturedPath = ""
        val engine = CallbackBackedOcrEngine(
            recognize = { path, onSuccess, _ ->
                capturedPath = path
                onSuccess("", emptyList())
            }
        )

        engine.recognizeText(sampleImage)

        assertEquals("/tmp/img.jpg", capturedPath)
    }

    @Test
    fun `maps bounding box when hasBoundingBox is true`() = runBlocking {
        val engine = CallbackBackedOcrEngine(
            recognize = { _, onSuccess, _ ->
                onSuccess(
                    "boxed",
                    listOf(
                        FfiOcrLine(
                            text = "boxed",
                            confidence = 0.8f,
                            hasBoundingBox = true,
                            boundingBoxLeft = 10f,
                            boundingBoxTop = 20f,
                            boundingBoxRight = 110f,
                            boundingBoxBottom = 60f,
                        )
                    )
                )
            }
        )

        val result = engine.recognizeText(sampleImage)
        val box = result.blocks[0].boundingBox
        assertEquals(10f, box?.left)
        assertEquals(20f, box?.top)
        assertEquals(110f, box?.right)
        assertEquals(60f, box?.bottom)
    }
}
