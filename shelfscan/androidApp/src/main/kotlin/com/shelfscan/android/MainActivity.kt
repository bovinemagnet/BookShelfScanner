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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.shelfscan.android.camera.CameraXAdapter
import com.shelfscan.android.ui.ReviewScreen
import com.shelfscan.android.viewmodel.AndroidReviewViewModel
import com.shelfscan.android.viewmodel.AndroidScanViewModel
import com.shelfscan.shared.core.model.ScanStatus
import com.shelfscan.shared.feature.review.ReviewAction
import com.shelfscan.shared.feature.review.ReviewViewModel
import com.shelfscan.shared.feature.scan.ScanAction
import com.shelfscan.shared.feature.scan.ScanState
import com.shelfscan.shared.feature.scan.ScanViewModel
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
                    reviewViewModel = androidReviewViewModel.shared
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
    reviewViewModel: ReviewViewModel
) {
    var currentScreen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val scanState by scanViewModel.state.collectAsState()
    val reviewState by reviewViewModel.state.collectAsState()

    LaunchedEffect(scanState.status) {
        if (scanState.status == ScanStatus.COMPLETE && scanState.session != null) {
            reviewViewModel.onAction(ReviewAction.LoadSession(scanState.session!!))
            currentScreen = Screen.REVIEW
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
                        "Scan failed. Please try again.",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { scanViewModel.onAction(ScanAction.RetryCapture) }) {
                        Text("Retry")
                    }
                }
            }
            else -> {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val image = cameraAdapter.captureImage()
                                scanViewModel.onAction(ScanAction.CaptureImage(image))
                            } catch (_: Exception) {
                                scanViewModel.onAction(ScanAction.RetryCapture)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text("Capture")
                }
            }
        }
    }
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
