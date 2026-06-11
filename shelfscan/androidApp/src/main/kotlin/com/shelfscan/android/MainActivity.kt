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
import androidx.compose.ui.res.stringResource
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
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.home_tagline),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onStartScan, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_scan_button))
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
                        Text(stringResource(R.string.scan_processing))
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
                        Text(stringResource(R.string.scan_retry))
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
                    Text(
                        stringResource(
                            if (isCameraReady) R.string.scan_capture else R.string.scan_camera_starting
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun scanFailureMessage(error: ScanError?): String = stringResource(
    when (error) {
        ScanError.OcrFailed -> R.string.error_scan_ocr
        ScanError.MetadataLookupFailed -> R.string.error_scan_metadata
        ScanError.SaveFailed -> R.string.error_scan_save
        ScanError.ImageProcessingFailed -> R.string.error_scan_image_processing
        ScanError.CameraUnavailable -> R.string.error_scan_camera_unavailable
        ScanError.PermissionDenied -> R.string.error_scan_permission_denied
        ScanError.ImageTooBlurry -> R.string.error_scan_too_blurry
        is ScanError.Unknown -> R.string.error_scan_unknown
        null -> R.string.error_scan_generic
    }
)

@Composable
fun PermissionScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.permission_rationale),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.permission_grant))
        }
    }
}
