package com.shelfscan.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shelfscan.android.ShelfScanApplication
import com.shelfscan.shared.feature.scan.ScanViewModel

/**
 * AndroidX wrapper around the shared `ScanViewModel`.
 *
 * Constructing the wrapper via `by viewModels()` ties its lifetime to the
 * `ViewModelStore` rather than the Activity instance, so scan progress and
 * the resulting session survive rotation. Coroutines launched inside
 * `ScanViewModel` are bound to `viewModelScope` and cancelled when the user
 * actually navigates away, not when the Activity is merely recreated.
 */
class AndroidScanViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ShelfScanApplication

    val shared: ScanViewModel = ScanViewModel(
        processImage = app.processCapturedImageUseCase,
        scope = viewModelScope,
    )
}
