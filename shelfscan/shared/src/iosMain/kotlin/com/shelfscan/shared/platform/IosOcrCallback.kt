package com.shelfscan.shared.platform

/**
 * Swift-facing OCR callback. A Swift class implements this protocol (via the
 * Kotlin/Native-generated Objective-C protocol) and is passed to
 * `IosShelfScanFactory.createProcessCapturedImageUseCase`.
 *
 * `imagePath` is a filesystem path on iOS. `onSuccess` carries the raw recognised text
 * (newline-joined) and a list of `FfiOcrLine`. `onError` carries a user-readable message.
 */
interface IosOcrCallback {
    fun recognizeText(
        imagePath: String,
        onSuccess: (rawText: String, lines: List<FfiOcrLine>) -> Unit,
        onError: (message: String) -> Unit,
    )
}
