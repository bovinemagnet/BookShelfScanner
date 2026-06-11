package com.shelfscan.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.shelfscan.android.camera.CameraXAdapter
import com.shelfscan.android.image.ScanImageCache
import com.shelfscan.android.ui.ReviewScreen
import com.shelfscan.android.viewmodel.AndroidReviewViewModel
import com.shelfscan.android.viewmodel.AndroidScanViewModel
import com.shelfscan.shared.core.model.ScanError
import com.shelfscan.shared.core.model.ScanStatus
import com.shelfscan.shared.feature.review.ReviewAction
import com.shelfscan.shared.feature.review.ReviewViewModel
import com.shelfscan.shared.feature.scan.ScanAction
import com.shelfscan.shared.feature.scan.ScanState
import com.shelfscan.shared.feature.scan.ScanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    var cameraPermissionGranted by mutableStateOf(false)
}

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    private val androidScanViewModel: AndroidScanViewModel by viewModels()
    private val androidReviewViewModel: AndroidReviewViewModel by viewModels()

    private lateinit var cameraAdapter: CameraXAdapter

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        mainViewModel.cameraPermissionGranted = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraAdapter = CameraXAdapter(this)
        val scanImageCache = ScanImageCache(cacheDir)

        mainViewModel.cameraPermissionGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!mainViewModel.cameraPermissionGranted) {
            requestPermission.launch(Manifest.permission.CAMERA)
        }

        setContent {
            MaterialTheme {
                ShelfScanApp(
                    cameraPermissionGranted = mainViewModel.cameraPermissionGranted,
                    onRequestPermission = { requestPermission.launch(Manifest.permission.CAMERA) },
                    cameraAdapter = cameraAdapter,
                    scanViewModel = androidScanViewModel.shared,
                    reviewViewModel = androidReviewViewModel.shared,
                    onScanFlowFinished = {
                        lifecycleScope.launch(Dispatchers.IO) { scanImageCache.sweep() }
                    }
                )
            }
        }
    }
}

enum class Screen { HOME, SCAN, REVIEW }

@Composable
fun ShelfScanApp(
    cameraPermissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    cameraAdapter: CameraXAdapter,
    scanViewModel: ScanViewModel,
    reviewViewModel: ReviewViewModel,
    onScanFlowFinished: () -> Unit = {}
) {
    var currentScreen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val scanState by scanViewModel.state.collectAsState()
    val reviewState by reviewViewModel.state.collectAsState()

    LaunchedEffect(scanState.status) {
        if (scanState.status == ScanStatus.COMPLETE) {
            scanState.session?.let { session ->
                reviewViewModel.onAction(ReviewAction.LoadSession(session))
                currentScreen = Screen.REVIEW
            }
        }
    }

    when (currentScreen) {
        Screen.HOME -> HomeScreen(
            onStartScan = {
                scanViewModel.onAction(ScanAction.RetryCapture)
                currentScreen = Screen.SCAN
            }
        )
        Screen.SCAN -> if (cameraPermissionGranted) {
            ScanScreen(
                cameraAdapter = cameraAdapter,
                scanViewModel = scanViewModel,
                scanState = scanState
            )
        } else {
            PermissionScreen(onRequestPermission = onRequestPermission)
        }
        Screen.REVIEW -> ReviewScreen(
            reviewViewModel = reviewViewModel,
            reviewState = reviewState,
            onDone = {
                scanViewModel.onAction(ScanAction.CancelScan)
                currentScreen = Screen.HOME
                // The session is saved (or discarded) by now — its cached
                // capture and crop images are no longer needed.
                onScanFlowFinished()
            }
        )
    }
}

@Composable
fun HomeScreen(onStartScan: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("ShelfScan", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Take a photo of a shelf to catalogue your books.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onStartScan, modifier = Modifier.fillMaxWidth()) {
            Text("Scan a Shelf")
        }
    }
}

@Composable
fun ScanScreen(
    cameraAdapter: CameraXAdapter,
    scanViewModel: ScanViewModel,
    scanState: ScanState
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val isCameraReady by cameraAdapter.isReady.collectAsState()

    // Release the camera when this screen leaves composition — the factory
    // below starts the preview, so the composable owns the camera lifecycle.
    DisposableEffect(Unit) {
        onDispose { cameraAdapter.stopPreview() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    cameraAdapter.startPreview(lifecycleOwner, previewView)
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        when {
            scanState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Processing...")
                    }
                }
            }
            scanState.status == ScanStatus.FAILED -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        scanFailureMessage(scanState.error),
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { scanViewModel.onAction(ScanAction.RetryCapture) }) {
                        Text("Retry")
                    }
                }
            }
            else -> {
                // Local in-flight flag avoids the race window between tap and
                // ScanState.isLoading flipping. Without it, fast double-taps
                // launch concurrent cameraAdapter.captureImage() calls.
                var isCapturing by remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        if (isCapturing) return@Button
                        isCapturing = true
                        coroutineScope.launch {
                            try {
                                val image = cameraAdapter.captureImage()
                                scanViewModel.onAction(ScanAction.CaptureImage(image))
                            } catch (_: Exception) {
                                scanViewModel.onAction(ScanAction.RetryCapture)
                            } finally {
                                isCapturing = false
                            }
                        }
                    },
                    enabled = !isCapturing && isCameraReady,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(if (isCameraReady) "Capture" else "Starting camera…")
                }
            }
        }
    }
}

private fun scanFailureMessage(error: ScanError?): String = when (error) {
    ScanError.OcrFailed -> "Couldn't read text on the spines. Try again with brighter, steadier light."
    ScanError.MetadataLookupFailed -> "Couldn't reach the catalogue. Check your connection and retry."
    ScanError.SaveFailed -> "Couldn't save the scan. Please try again."
    ScanError.ImageProcessingFailed -> "Couldn't process the photo. Please retake it."
    ScanError.CameraUnavailable -> "The camera is unavailable on this device."
    ScanError.PermissionDenied -> "Camera permission is required to scan a shelf."
    ScanError.ImageTooBlurry -> "The photo was too blurry. Please retake it."
    is ScanError.Unknown -> "Something went wrong. Please try again."
    null -> "Scan failed. Please try again."
}

@Composable
fun PermissionScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Camera Permission Required", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "ShelfScan needs camera access to scan your shelf.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
            Text("Grant Permission")
        }
    }
}
