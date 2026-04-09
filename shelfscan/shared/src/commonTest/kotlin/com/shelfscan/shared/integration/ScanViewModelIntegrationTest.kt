package com.shelfscan.shared.integration

import com.shelfscan.shared.core.model.ScanError
import com.shelfscan.shared.core.model.ScanStatus
import com.shelfscan.shared.data.repository.DefaultScanRepository
import com.shelfscan.shared.domain.scan.ProcessCapturedImageUseCase
import com.shelfscan.shared.feature.scan.ScanAction
import com.shelfscan.shared.feature.scan.ScanState
import com.shelfscan.shared.feature.scan.ScanViewModel
import com.shelfscan.shared.platform.NoOpMetadataLookupService
import com.shelfscan.shared.platform.PassthroughImagePreprocessor
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelIntegrationTest {

    private fun createViewModel(
        ocrEngine: ConfigurableFakeOcrEngine = ConfigurableFakeOcrEngine(
            defaultResult = ocrResultFor("The Great Gatsby")
        ),
        testScope: TestScope = TestScope()
    ): Pair<ScanViewModel, TestScope> {
        val useCase = ProcessCapturedImageUseCase(
            imagePreprocessor = PassthroughImagePreprocessor(),
            ocrEngine = ocrEngine,
            metadataLookupService = NoOpMetadataLookupService(),
            scanRepository = DefaultScanRepository()
        )
        return ScanViewModel(processImage = useCase, scope = testScope) to testScope
    }

    @Test
    fun `happy path transitions to COMPLETE with session`() {
        val (viewModel, scope) = createViewModel()

        viewModel.onAction(ScanAction.CaptureImage(testImage))
        scope.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(ScanStatus.COMPLETE, state.status)
        assertNotNull(state.session)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `session contains expected items from pipeline`() {
        val spines = threeSpines()
        val scope = TestScope()
        val useCase = ProcessCapturedImageUseCase(
            imagePreprocessor = MultiSpineImagePreprocessor(spines),
            ocrEngine = ConfigurableFakeOcrEngine(
                resultsByRef = mapOf(
                    "crop_0" to ocrResultFor("Clean Code"),
                    "crop_1" to ocrResultFor("Refactoring"),
                    "crop_2" to ocrResultFor("Design Patterns")
                )
            ),
            metadataLookupService = NoOpMetadataLookupService(),
            scanRepository = DefaultScanRepository()
        )
        val viewModel = ScanViewModel(processImage = useCase, scope = scope)

        viewModel.onAction(ScanAction.CaptureImage(testImage))
        scope.advanceUntilIdle()

        val items = viewModel.state.value.session!!.detectedItems
        assertEquals(3, items.size)
        assertEquals("Clean Code", items[0].title)
        assertEquals("Refactoring", items[1].title)
        assertEquals("Design Patterns", items[2].title)
    }

    @Test
    fun `OCR failure produces error state`() {
        val (viewModel, scope) = createViewModel(
            ocrEngine = ConfigurableFakeOcrEngine(shouldThrow = true)
        )

        viewModel.onAction(ScanAction.CaptureImage(testImage))
        scope.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(ScanStatus.FAILED, state.status)
        assertEquals(ScanError.OcrFailed, state.error)
        assertFalse(state.isLoading)
        assertNull(state.session)
    }

    @Test
    fun `RetryCapture resets to initial state`() {
        val (viewModel, scope) = createViewModel()

        viewModel.onAction(ScanAction.CaptureImage(testImage))
        scope.advanceUntilIdle()
        assertNotNull(viewModel.state.value.session)

        viewModel.onAction(ScanAction.RetryCapture)

        assertEquals(ScanState(), viewModel.state.value)
    }

    @Test
    fun `CancelScan resets to initial state`() {
        val (viewModel, scope) = createViewModel()

        viewModel.onAction(ScanAction.CaptureImage(testImage))
        scope.advanceUntilIdle()
        assertNotNull(viewModel.state.value.session)

        viewModel.onAction(ScanAction.CancelScan)

        assertEquals(ScanState(), viewModel.state.value)
    }
}
