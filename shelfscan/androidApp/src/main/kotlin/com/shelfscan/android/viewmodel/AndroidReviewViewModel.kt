package com.shelfscan.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shelfscan.android.ShelfScanApplication
import com.shelfscan.shared.feature.review.ReviewViewModel

/**
 * AndroidX wrapper around the shared `ReviewViewModel`. See
 * [AndroidScanViewModel] for rationale.
 */
class AndroidReviewViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ShelfScanApplication

    val shared: ReviewViewModel = ReviewViewModel(
        collectionRepository = app.collectionRepository,
        scope = viewModelScope,
    )
}
