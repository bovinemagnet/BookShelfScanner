package com.shelfscan.shared.feature.scan

import com.shelfscan.shared.core.model.*
import com.shelfscan.shared.domain.scan.ProcessCapturedImageUseCase
import com.shelfscan.shared.domain.scan.ScanFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ScanAction {
    data class CaptureImage(val image: CapturedImage) : ScanAction
    data object RetryCapture : ScanAction
    data object CancelScan : ScanAction
}

data class ScanState(
    val status: ScanStatus = ScanStatus.PENDING,
    val session: ScanSession? = null,
    val error: ScanError? = null,
    val isLoading: Boolean = false
)

class ScanViewModel(
    private val processImage: ProcessCapturedImageUseCase,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    fun onAction(action: ScanAction) {
        when (action) {
            is ScanAction.CaptureImage -> processCapture(action.image)
            is ScanAction.RetryCapture -> _state.value = ScanState()
            is ScanAction.CancelScan -> _state.value = ScanState()
        }
    }

    private fun processCapture(image: CapturedImage) {
        scope.launch {
            _state.value = ScanState(status = ScanStatus.PROCESSING, isLoading = true)
            try {
                val sessionId = "session_${image.ref}"
                val session = processImage.execute(image, sessionId)
                _state.value = ScanState(
                    status = ScanStatus.COMPLETE,
                    session = session,
                    isLoading = false
                )
            } catch (e: Throwable) {
                _state.value = ScanState(
                    status = ScanStatus.FAILED,
                    error = e.toScanError(),
                    isLoading = false
                )
            }
        }
    }

    private fun Throwable.toScanError(): ScanError = when (this) {
        is ScanFailure.ImageProcessing -> ScanError.ImageProcessingFailed
        is ScanFailure.Ocr -> ScanError.OcrFailed
        is ScanFailure.MetadataLookup -> ScanError.MetadataLookupFailed
        is ScanFailure.Save -> ScanError.SaveFailed
        else -> ScanError.OcrFailed
    }
}
