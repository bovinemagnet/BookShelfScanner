package com.shelfscan.android.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import com.shelfscan.shared.core.geometry.clampToImage
import com.shelfscan.shared.core.model.BoundingBox
import java.io.File
import java.io.FileOutputStream

/**
 * Crops a region of an on-disk image and writes it back as a JPEG.
 *
 * Uses `BitmapRegionDecoder` so only the requested rectangle is materialised
 * in memory — a 4000×3000 source image cropped to a 200×3000 spine costs
 * ~2.4 MB rather than the ~48 MB that a full `decodeFile` would.
 */
class BitmapCropper(private val cacheDir: File) {

    fun cropAndSave(sourceRef: String, box: BoundingBox, id: String): String {
        val (sourceWidth, sourceHeight) = readImageSize(sourceRef)

        val rect = box.clampToImage(sourceWidth, sourceHeight)
        val regionRect = Rect(rect.left, rect.top, rect.right, rect.bottom)

        val cropped = decodeRegion(sourceRef, regionRect)
            ?: throw IllegalArgumentException("Cannot decode region of image: $sourceRef")

        val outputFile = File(cacheDir, "spine_${id}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outputFile).use { out ->
            cropped.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        cropped.recycle()

        return outputFile.absolutePath
    }

    private fun readImageSize(sourceRef: String): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceRef, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
            throw IllegalArgumentException("Cannot read dimensions of image: $sourceRef")
        }
        return opts.outWidth to opts.outHeight
    }

    private fun decodeRegion(sourceRef: String, region: Rect): Bitmap? {
        val decoder = BitmapRegionDecoder.newInstance(sourceRef, false)
        return try {
            decoder.decodeRegion(region, BitmapFactory.Options())
        } finally {
            decoder.recycle()
        }
    }
}
