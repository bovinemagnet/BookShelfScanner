package com.shelfscan.shared.domain.scan

/**
 * Typed failure thrown by `ProcessCapturedImageUseCase` so that the
 * presentation layer can map each pipeline phase to a user-meaningful
 * `ScanError` without having to grep for exception classes from every
 * platform's underlying library (ML Kit, Vision, Ktor, etc.).
 *
 * The original cause is preserved on `Throwable.cause` for logging.
 */
sealed class ScanFailure(cause: Throwable) : RuntimeException(cause.message, cause) {
    class ImageProcessing(cause: Throwable) : ScanFailure(cause)
    class Ocr(cause: Throwable) : ScanFailure(cause)
    class MetadataLookup(cause: Throwable) : ScanFailure(cause)
    class Save(cause: Throwable) : ScanFailure(cause)
}
