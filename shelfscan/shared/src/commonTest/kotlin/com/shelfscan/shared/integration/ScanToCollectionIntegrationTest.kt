package com.shelfscan.shared.integration

import com.shelfscan.shared.core.model.*
import com.shelfscan.shared.data.repository.DefaultCollectionRepository
import com.shelfscan.shared.data.repository.DefaultScanRepository
import com.shelfscan.shared.domain.export.ExportCollectionUseCase
import com.shelfscan.shared.domain.scan.ProcessCapturedImageUseCase
import com.shelfscan.shared.feature.review.ReviewAction
import com.shelfscan.shared.feature.review.ReviewViewModel
import com.shelfscan.shared.feature.scan.ScanAction
import com.shelfscan.shared.feature.scan.ScanViewModel
import com.shelfscan.shared.platform.NoOpMetadataLookupService
import com.shelfscan.shared.platform.PassthroughImagePreprocessor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScanToCollectionIntegrationTest {

    @Test
    fun `full pipeline scan to review to save to export`() {
        val scanRepository = DefaultScanRepository()
        val collectionRepository = DefaultCollectionRepository()
        val testScope = TestScope()

        val processImage = ProcessCapturedImageUseCase(
            imagePreprocessor = PassthroughImagePreprocessor(),
            ocrEngine = ConfigurableFakeOcrEngine(
                defaultResult = ocrResultFor("The Great Gatsby")
            ),
            metadataLookupService = NoOpMetadataLookupService(),
            scanRepository = scanRepository
        )
        val scanViewModel = ScanViewModel(processImage = processImage, scope = testScope)
        val reviewViewModel = ReviewViewModel(
            collectionRepository = collectionRepository,
            exportUseCase = ExportCollectionUseCase(),
            scope = testScope
        )

        // Scan
        scanViewModel.onAction(ScanAction.CaptureImage(testImage))
        testScope.advanceUntilIdle()

        val session = scanViewModel.state.value.session
        assertNotNull(session)
        assertEquals(1, session.detectedItems.size)

        // Review
        reviewViewModel.onAction(ReviewAction.LoadSession(session))
        assertEquals(session.detectedItems.size, reviewViewModel.state.value.items.size)
        assertEquals("The Great Gatsby", reviewViewModel.state.value.items.first().title)

        // Edit
        val edited = reviewViewModel.state.value.items.first().copy(
            title = "The Great Gatsby (Verified)",
            source = ItemSource.USER_EDITED
        )
        reviewViewModel.onAction(ReviewAction.EditItem(edited))

        // Save
        reviewViewModel.onAction(ReviewAction.SaveToCollection("col_e2e", "End-to-End"))
        testScope.advanceUntilIdle()
        assertTrue(reviewViewModel.state.value.savedToCollection)

        // Verify persisted
        val saved = runBlocking { collectionRepository.getCollection("col_e2e") }
        assertNotNull(saved)
        assertEquals("The Great Gatsby (Verified)", saved.items.first().title)

        // Export
        reviewViewModel.onAction(ReviewAction.ExportCsv(reviewViewModel.state.value.items))
        val csv = reviewViewModel.state.value.exportedCsv
        assertNotNull(csv)
        assertTrue(csv.contains("The Great Gatsby (Verified)"))
    }

    @Test
    fun `multi-spine end-to-end preserves all items through pipeline`() {
        val scanRepository = DefaultScanRepository()
        val collectionRepository = DefaultCollectionRepository()
        val testScope = TestScope()

        val spines = threeSpines()
        val processImage = ProcessCapturedImageUseCase(
            imagePreprocessor = MultiSpineImagePreprocessor(spines),
            ocrEngine = ConfigurableFakeOcrEngine(
                resultsByRef = mapOf(
                    "crop_0" to ocrResultFor("Clean Code"),
                    "crop_1" to ocrResultFor("Refactoring"),
                    "crop_2" to ocrResultFor("Design Patterns")
                )
            ),
            metadataLookupService = NoOpMetadataLookupService(),
            scanRepository = scanRepository
        )
        val scanViewModel = ScanViewModel(processImage = processImage, scope = testScope)
        val reviewViewModel = ReviewViewModel(
            collectionRepository = collectionRepository,
            exportUseCase = ExportCollectionUseCase(),
            scope = testScope
        )

        scanViewModel.onAction(ScanAction.CaptureImage(testImage))
        testScope.advanceUntilIdle()

        val session = scanViewModel.state.value.session!!
        assertEquals(3, session.detectedItems.size)

        reviewViewModel.onAction(ReviewAction.LoadSession(session))
        reviewViewModel.onAction(ReviewAction.SaveToCollection("col_multi", "Multi Spine"))
        testScope.advanceUntilIdle()

        val saved = runBlocking { collectionRepository.getCollection("col_multi") }
        assertNotNull(saved)
        assertEquals(3, saved.items.size)

        reviewViewModel.onAction(ReviewAction.ExportCsv(reviewViewModel.state.value.items))
        val csv = reviewViewModel.state.value.exportedCsv!!
        assertTrue(csv.contains("Clean Code"))
        assertTrue(csv.contains("Refactoring"))
        assertTrue(csv.contains("Design Patterns"))
    }

    @Test
    fun `catalogue-enriched items survive full pipeline`() {
        val scanRepository = DefaultScanRepository()
        val collectionRepository = DefaultCollectionRepository()
        val testScope = TestScope()

        val processImage = ProcessCapturedImageUseCase(
            imagePreprocessor = PassthroughImagePreprocessor(),
            ocrEngine = ConfigurableFakeOcrEngine(
                defaultResult = ocrResultFor("Clean Code")
            ),
            metadataLookupService = FakeCatalogLookupService(
                matchesByTitle = mapOf(
                    "Clean Code" to listOf(
                        catalogMatchFor("Clean Code", creator = "Robert C. Martin")
                    )
                )
            ),
            scanRepository = scanRepository
        )
        val scanViewModel = ScanViewModel(processImage = processImage, scope = testScope)
        val reviewViewModel = ReviewViewModel(
            collectionRepository = collectionRepository,
            exportUseCase = ExportCollectionUseCase(),
            scope = testScope
        )

        scanViewModel.onAction(ScanAction.CaptureImage(testImage))
        testScope.advanceUntilIdle()

        val session = scanViewModel.state.value.session!!
        assertEquals(ItemSource.CATALOG_MATCHED, session.detectedItems.first().source)

        reviewViewModel.onAction(ReviewAction.LoadSession(session))
        reviewViewModel.onAction(ReviewAction.SaveToCollection("col_cat", "Catalogue"))
        testScope.advanceUntilIdle()

        val saved = runBlocking { collectionRepository.getCollection("col_cat") }
        assertNotNull(saved)
        assertEquals(ItemSource.CATALOG_MATCHED, saved.items.first().source)
        assertEquals("Robert C. Martin", saved.items.first().normalizedCreatorName)

        reviewViewModel.onAction(ReviewAction.ExportCsv(reviewViewModel.state.value.items))
        val csv = reviewViewModel.state.value.exportedCsv!!
        assertTrue(csv.contains("Clean Code"))
        assertTrue(csv.contains("Robert C. Martin"))
    }
}
