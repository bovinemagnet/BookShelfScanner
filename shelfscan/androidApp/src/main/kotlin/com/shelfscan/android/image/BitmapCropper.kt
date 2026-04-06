package com.shelfscan.android.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.shelfscan.shared.core.model.BoundingBox
import java.io.File
import java.io.FileOutputStream

class BitmapCropper(private val cacheDir: File) {

    fun cropAndSave(sourceRef: String, box: BoundingBox, id: String): String {
        val bitmap = BitmapFactory.decodeFile(sourceRef)
            ?: throw IllegalArgumentException("Cannot decode image: $sourceRef")

        val left = box.left.toInt().coerceIn(0, bitmap.width - 1)
        val top = box.top.toInt().coerceIn(0, bitmap.height - 1)
        val right = box.right.toInt().coerceIn(left + 1, bitmap.width)
        val bottom = box.bottom.toInt().coerceIn(top + 1, bitmap.height)

        val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        val outputFile = File(cacheDir, "spine_${id}_${System.currentTimeMillis()}.jpg")

        FileOutputStream(outputFile).use { out ->
            cropped.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        if (cropped !== bitmap) cropped.recycle()
        bitmap.recycle()

        return outputFile.absolutePath
    }
}
