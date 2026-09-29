package com.example.siteinspection

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import java.io.File
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SiteInspectionApp()
                }
            }
        }
    }
}

// Global Serial Number tracker for the current session
var currentSerialNumber = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteInspectionApp() {
    val context = LocalContext.current
    var capturedImageFile by remember { mutableStateOf<File?>(null) }
    var hasPermissions by remember { mutableStateOf(false) }

    // Launcher for Camera and Location Permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Site Inspection Logger") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (!hasPermissions) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Camera & Location permissions are required to use this app.")
                }
            } else if (capturedImageFile == null) {
                CameraView(
                    onPhotoCaptured = { file -> capturedImageFile = file },
                    onError = { exc -> Toast.makeText(context, "Error: ${exc.message}", Toast.LENGTH_SHORT).show() }
                )
            } else {
                InspectionFormView(
                    imageFile = capturedImageFile!!,
                    onRetake = { capturedImageFile = null },
                    onSaved = {
                        currentSerialNumber++
                        capturedImageFile = null
                    }
                )
            }
        }
    }
}

@Composable
fun CameraView(
    onPhotoCaptured: (File) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val executor = ContextCompat.getMainExecutor(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = androidx.camera.core.Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, executor)
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Capture Floating Button
        FloatingActionButton(
            onClick = {
                val photoFile = File(
                    context.cacheDir,
                    "IMG_${System.currentTimeMillis()}.jpg"
                )
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                imageCapture.takePicture(
                    outputOptions,
                    Executors.newSingleThreadExecutor(),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            onPhotoCaptured(photoFile)
                        }

                        override fun onError(exc: ImageCaptureException) {
                            onError(exc)
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Camera, contentDescription = "Take Photo", tint = Color.White)
        }
    }
}

@Composable
fun InspectionFormView(
    imageFile: File,
    onRetake: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val metadataCollector = remember { MetadataCollector(context) }

    // Automatic Metadata States
    var dateText by remember { mutableStateOf(metadataCollector.getCurrentDate()) }
    var timeText by remember { mutableStateOf(metadataCollector.getCurrentTime()) }
    var locationText by remember { mutableStateOf("Fetching GPS location...") }

    // Fetch GPS on screen load
    LaunchedEffect(Unit) {
        metadataCollector.fetchGPSLocation { gps ->
            locationText = gps
        }
    }

    // Manual Entry Form States
    var buildingInput by remember { mutableStateOf("") }
    var levelInput by remember { mutableStateOf("") }
    var commentsInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Photo Thumbnail
        Image(
            painter = rememberAsyncImagePainter(imageFile),
            contentDescription = "Captured Photo",
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )

        // Auto-Collected Metadata Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "Auto-Collected Data", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(text = "• S.No: $currentSerialNumber")
                Text(text = "• Date: $dateText")
                Text(text = "• Time: $timeText")
                Text(text = "• GPS Location: $locationText")
            }
        }

        // Manual Input Fields
        OutlinedTextField(
            value = buildingInput,
            onValueChange = { buildingInput = it },
            label = { Text("Building / Block") },
            placeholder = { Text("e.g. Building A") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = levelInput,
            onValueChange = { levelInput = it },
            label = { Text("Level / Floor") },
            placeholder = { Text("e.g. Level 2 / Basement 1") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = commentsInput,
            onValueChange = { commentsInput = it },
            label = { Text("Comments / Site Notes") },
            placeholder = { Text("e.g. Conduit dressing completed") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRetake,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Retake")
            }

            Button(
                onClick = {
                    val record = InspectionRecord(
                        serialNumber = currentSerialNumber,
                        date = dateText,
                        time = timeText,
                        location = locationText,
                        imagePath = imageFile.absolutePath,
                        building = buildingInput,
                        level = levelInput,
                        comments = commentsInput
                    )

                    val savedFile = ExcelExporter.appendRecordToDailyExcel(context, record)
                    Toast.makeText(
                        context,
                        "Saved to ${savedFile.name}",
                        Toast.LENGTH_LONG
                    ).show()

                    onSaved()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save Row")
            }
        }
    }
}
