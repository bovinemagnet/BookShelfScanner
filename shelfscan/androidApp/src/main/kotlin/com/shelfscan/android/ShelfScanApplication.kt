package com.shelfscan.android

import android.app.Application
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.shelfscan.android.image.OcrBasedSpineDetector
import com.shelfscan.android.ocr.MlKitOcrAdapter
import com.shelfscan.shared.data.metadata.OpenLibraryMetadataLookupService
import com.shelfscan.shared.data.repository.DefaultCollectionRepository
import com.shelfscan.shared.data.repository.DefaultScanRepository
import com.shelfscan.shared.domain.scan.ProcessCapturedImageUseCase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
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
    private lateinit var textRecognizer: TextRecognizer

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
            install(HttpTimeout) {
                requestTimeoutMillis = 5_000
                connectTimeoutMillis = 5_000
            }
        }
        // One ML Kit recogniser shared by both the OCR adapter and the
        // OCR-based segmenter. Loading the latin model is expensive — doing it
        // twice per Activity recreation was the previous default.
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        scanRepository = DefaultScanRepository()
        collectionRepository = DefaultCollectionRepository()
        processCapturedImageUseCase = ProcessCapturedImageUseCase(
            imagePreprocessor = OcrBasedSpineDetector(this, textRecognizer),
            ocrEngine = MlKitOcrAdapter(this, textRecognizer),
            metadataLookupService = OpenLibraryMetadataLookupService(httpClient),
            scanRepository = scanRepository,
        )
    }

    override fun onTerminate() {
        // Best-effort cleanup. Android docs note onTerminate is not guaranteed
        // to fire in production — a full process-shutdown leak protection
        // would need a different mechanism. Either way, we no longer leak
        // these per Activity recreation, which was the actual bug.
        if (::httpClient.isInitialized) httpClient.close()
        if (::textRecognizer.isInitialized) textRecognizer.close()
        super.onTerminate()
    }
}
