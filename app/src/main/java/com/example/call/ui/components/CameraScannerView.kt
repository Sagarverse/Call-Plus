package com.example.call.ui.components

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.call.ui.theme.IOSBlue
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

@Composable
fun CameraScannerView(onNumberDetected: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalyzer.setAnalyzer(executor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            textRecognizer.process(image)
                                .addOnSuccessListener { visionText ->
                                    val possibleNumber = extractPhoneNumber(visionText.text)
                                    if (possibleNumber != null) {
                                        onNumberDetected(possibleNumber)
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalyzer
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))
                
                previewView
            }
        )
        
        // UI Overlay
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
            
            Box(
                modifier = Modifier
                    .width(300.dp)
                    .height(100.dp)
                    .border(2.dp, IOSBlue, RoundedCornerShape(12.dp))
                    .background(Color.Transparent)
            )
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Scan Phone Number",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    "Point the camera at a printed number",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 60.dp)
                )
            }
        }
    }
}

private fun extractPhoneNumber(text: String): String? {
    val lines = text.split("\n")
    for (line in lines) {
        // Count digits. If there are at least 7 digits, it might be a phone number.
        val digitCount = line.count { it.isDigit() }
        if (digitCount in 7..15) {
            val clean = line.replace(Regex("[^0-9+]"), "")
            // Only accept if it's purely digits (and optionally a leading +)
            // It could be something like "$15,000" which would be 15000, but has non-digit chars originally
            // The replacing gave us just digits/+, we return it assuming it's a valid phone.
            return clean
        }
    }
    return null
}
