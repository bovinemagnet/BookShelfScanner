package com.shelfscan.shared.platform

/**
 * iOS-only adapter that turns a Swift-implemented `IosOcrCallback` into the function-ref
 * shape `CallbackBackedOcrEngine` expects, then delegates `OcrEngine` to that bridge.
 */
class SwiftBackedOcrEngine(callback: IosOcrCallback) : OcrEngine by CallbackBackedOcrEngine(
    recognize = { path, onSuccess, onError ->
        callback.recognizeText(path, onSuccess, onError)
    },
)
