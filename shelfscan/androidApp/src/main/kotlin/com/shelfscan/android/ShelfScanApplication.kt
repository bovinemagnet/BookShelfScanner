package com.shelfscan.android

import android.app.Application
import com.shelfscan.android.image.OcrBasedSpineDetector
import com.shelfscan.android.ocr.MlKitOcrAdapter
import com.shelfscan.shared.data.metadata.OpenLibraryMetadataLookupService
import com.shelfscan.shared.data.repository.DefaultCollectionRepository
import com.shelfscan.shared.data.repository.DefaultScanRepository
import com.shelfscan.shared.domain.scan.ProcessCapturedImageUseCase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Process-scoped owner of singletons that must outlive Activity recreation:
 * the HTTP client, the in-memory repositories, and the ML Kit-backed adapters.
 *
 * Construction is idempotent across configuration changes — `onCreate` here
 * is called exactly once per process, regardless of how often the user
 * rotates the screen.
 *
 * Resource lifecycle (HttpClient close, ML Kit recogniser close, repository
 * thread-safety) is tracked separately in #19.
 */
class ShelfScanApplication : Application() {

    private lateinit var httpClient: HttpClient

    lateinit var scanRepository: DefaultScanRepository
        private set
    lateinit var collectionRepository: DefaultCollectionRepository
        private set
    lateinit var processCapturedImageUseCase: ProcessCapturedImageUseCase
        private set

    override fun onCreate() {
        super.onCreate()
        httpClient = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        scanRepository = DefaultScanRepository()
        collectionRepository = DefaultCollectionRepository()
        processCapturedImageUseCase = ProcessCapturedImageUseCase(
            imagePreprocessor = OcrBasedSpineDetector(this),
            ocrEngine = MlKitOcrAdapter(this),
            metadataLookupService = OpenLibraryMetadataLookupService(httpClient),
            scanRepository = scanRepository,
        )
    }
}
