package com.shelfscan.android.image

import java.io.File
import java.util.UUID

/**
 * Owns the naming and lifecycle of scan images written to the cache directory:
 * full-shelf captures (`capture_*.jpg`) and per-spine crops (`spine_*.jpg`).
 *
 * UUID-based names rule out collisions between concurrent writes; [sweep]
 * reclaims the space once a scan flow has finished, and again on app start
 * to clear anything a killed process left behind.
 */
class ScanImageCache(private val cacheDir: File) {

    fun newCaptureFile(): File = File(cacheDir, "capture_${UUID.randomUUID()}.jpg")

    fun newSpineFile(id: String): File = File(cacheDir, "spine_${id}_${UUID.randomUUID()}.jpg")

    /** Deletes every capture and spine image. Only call when no scan is in flight. */
    fun sweep() {
        cacheDir.listFiles()?.forEach { file ->
            val isScanImage = file.isFile &&
                file.name.endsWith(".jpg") &&
                (file.name.startsWith("capture_") || file.name.startsWith("spine_"))
            if (isScanImage) file.delete()
        }
    }
}
