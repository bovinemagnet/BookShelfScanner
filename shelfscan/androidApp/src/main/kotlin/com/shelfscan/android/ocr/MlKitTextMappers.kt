package com.shelfscan.android.ocr

import com.google.mlkit.vision.text.Text
import com.shelfscan.shared.core.model.BoundingBox
import com.shelfscan.shared.core.model.RecognizedTextBlock

internal fun Text.toRecognizedTextBlocks(): List<RecognizedTextBlock> =
    textBlocks.flatMap { block ->
        block.lines.map { line ->
            RecognizedTextBlock(
                text = line.text,
                confidence = line.confidence,
                boundingBox = line.boundingBox?.let {
                    BoundingBox(
                        left = it.left.toFloat(),
                        top = it.top.toFloat(),
                        right = it.right.toFloat(),
                        bottom = it.bottom.toFloat(),
                    )
                },
            )
        }
    }
