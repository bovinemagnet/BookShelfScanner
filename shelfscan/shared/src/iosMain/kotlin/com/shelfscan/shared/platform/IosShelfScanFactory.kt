package com.shelfscan.shared.platform

import com.shelfscan.shared.data.metadata.OpenLibraryMetadataLookupService
import com.shelfscan.shared.data.repository.DefaultScanRepository
import com.shelfscan.shared.data.repository.ScanRepository
import com.shelfscan.shared.domain.scan.ProcessCapturedImageUseCase
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Single Swift entry point for constructing the shared scan pipeline on iOS.
 *
 * Swift implements `IosOcrCallback` and `IosImagePreprocessorCallback`, hands them to
 * `createProcessCapturedImageUseCase`, and uses the returned `ProcessCapturedImageUseCase`
 * exactly the same way Android does.
 *
 * Note on parameter types: `metadataService` and `scanRepository` are nullable so that
 * Swift call sites can pass `nil` to opt into the defaults. Kotlin default arguments
 * don't propagate through Kotlin/Native to Swift; we get the same effect by
 * substituting the default inside the body when `null` is passed.
 *
 * Defaults:
 * - `metadataService` defaults to a live `OpenLibraryMetadataLookupService` over Ktor
 *   (works on Kotlin/Native — same code path as Android).
 * - `scanRepository` defaults to an in-memory `DefaultScanRepository`.
 */
object IosShelfScanFactory {
    fun createProcessCapturedImageUseCase(
        ocrCallback: IosOcrCallback,
        preprocessorCallback: IosImagePreprocessorCallback,
        metadataService: MetadataLookupService? = null,
        scanRepository: ScanRepository? = null,
    ): ProcessCapturedImageUseCase = ProcessCapturedImageUseCase(
        imagePreprocessor = SwiftBackedImagePreprocessor(preprocessorCallback),
        ocrEngine = SwiftBackedOcrEngine(ocrCallback),
        metadataLookupService = metadataService ?: defaultMetadataService(),
        scanRepository = scanRepository ?: DefaultScanRepository(),
    )

    private fun defaultMetadataService(): MetadataLookupService {
        val client = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return OpenLibraryMetadataLookupService(client = client)
    }
}
