package com.shelfscan.android.image

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScanImageCacheTest {

    private val tempDir: File = createTempDirectory(prefix = "scan_cache_test").toFile()
        .apply { deleteOnExit() }
    private val cache = ScanImageCache(tempDir)

    @Test
    fun `capture file names are unique`() {
        val first = cache.newCaptureFile()
        val second = cache.newCaptureFile()

        assertTrue(first.name.startsWith("capture_") && first.name.endsWith(".jpg"))
        assertFalse(first.name == second.name, "two captures must never share a file name")
    }

    @Test
    fun `spine file names are unique for the same spine id`() {
        val first = cache.newSpineFile("3")
        val second = cache.newSpineFile("3")

        assertTrue(first.name.startsWith("spine_3_") && first.name.endsWith(".jpg"))
        assertFalse(first.name == second.name, "two crops of the same spine must never collide")
    }

    @Test
    fun `sweep deletes capture and spine images but nothing else`() {
        val capture = cache.newCaptureFile().apply { writeText("img") }
        val spine = cache.newSpineFile("0").apply { writeText("img") }
        val unrelated = File(tempDir, "keep_me.txt").apply { writeText("data") }

        cache.sweep()

        assertFalse(capture.exists(), "capture file should be swept")
        assertFalse(spine.exists(), "spine file should be swept")
        assertTrue(unrelated.exists(), "unrelated files must survive a sweep")
    }

    @Test
    fun `sweep on an empty directory is a no-op`() {
        cache.sweep()

        assertTrue(tempDir.exists())
    }
}
