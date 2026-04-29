package com.shelfscan.shared.platform

/**
 * iOS-only adapter that turns a Swift-implemented `IosImagePreprocessorCallback` into the
 * function-ref shape `CallbackBackedImagePreprocessor` expects, then delegates
 * `ImagePreprocessor` to that bridge.
 */
class SwiftBackedImagePreprocessor(
    callback: IosImagePreprocessorCallback,
) : ImagePreprocessor by CallbackBackedImagePreprocessor(
    normalize = { path, w, h, onSuccess, onError ->
        callback.normalizeForOcr(path, w, h, onSuccess, onError)
    },
    detect = { path, w, h, onSuccess, onError ->
        callback.detectShelfItems(path, w, h, onSuccess, onError)
    },
)
