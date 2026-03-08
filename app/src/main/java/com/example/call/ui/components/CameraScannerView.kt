package com.example.call.ui.components

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraScannerView(
    onResult: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    val textRecognizer = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    var scanMode by remember { mutableStateOf("QR") } // "QR" or "TEXT"
    val currentScanMode by rememberUpdatedState(scanMode)

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    if (currentScanMode == "QR") {
                                        barcodeScanner.process(image)
                                            .addOnSuccessListener { barcodes ->
                                                for (barcode in barcodes) {
                                                    val rawValue = barcode.rawValue
                                                    if (rawValue != null) {
                                                        val phone = if (rawValue.startsWith("tel:", ignoreCase = true)) {
                                                            rawValue.substring(4)
                                                        } else {
                                                            rawValue
                                                        }
                                                        if (phone.any { it.isDigit() }) {
                                                            onResult(phone)
                                                        }
                                                    }
                                                }
                                            }
                                            .addOnFailureListener {
                                                Log.e("CameraScannerView", "Barcode processing failed", it)
                                            }
                                            .addOnCompleteListener {
                                                imageProxy.close()
                                            }
                                    } else {
                                        textRecognizer.process(image)
                                            .addOnSuccessListener { visionText ->
                                                val imageWidth = image.width.toFloat()
                                                val imageHeight = image.height.toFloat()
                                                val centerX = imageWidth / 2f
                                                val centerY = imageHeight / 2f
                                                
                                                val boxWidth = imageWidth * 0.9f
                                                val boxHeight = imageHeight * 0.3f

                                                
                                                val leftBound = centerX - (boxWidth / 2f)
                                                val rightBound = centerX + (boxWidth / 2f)
                                                val topBound = centerY - (boxHeight / 2f)
                                                val bottomBound = centerY + (boxHeight / 2f)

                                                for (block in visionText.textBlocks) {
                                                    val boundingBox = block.boundingBox
                                                    if (boundingBox != null) {
                                                        val blockCenterX = boundingBox.exactCenterX()
                                                        val blockCenterY = boundingBox.exactCenterY()
                                                        
                                                        if (blockCenterX >= leftBound && blockCenterX <= rightBound &&
                                                            blockCenterY >= topBound && blockCenterY <= bottomBound) {
                                                            
                                                            val clean = block.text.replace(Regex("[^0-9+*#]"), "")
                                                            if (clean.length >= 5) {
                                                                onResult(clean)
                                                                break // Break on first valid number found inside box
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            .addOnFailureListener {
                                                Log.e("CameraScannerView", "Text processing failed", it)
                                            }
                                            .addOnCompleteListener {
                                                imageProxy.close()
                                            }
                                    }

                                } else {
                                    imageProxy.close()
                                }
                            }
                        }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        Log.e("CameraScannerView", "Use case binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )

        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Scanner",
                tint = Color.White
            )
        }

        // HUD / Overlay
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.dp.toPx()
            
            val frameWidth = if (scanMode == "QR") size.minDimension * 0.6f else size.width * 0.85f
            val frameHeight = if (scanMode == "QR") size.minDimension * 0.6f else size.height * 0.15f
            
            val left = (size.width - frameWidth) / 2
            val top = (size.height - frameHeight) / 2
            
            drawRect(color = Color.Black.copy(alpha = 0.4f))
            
            drawRect(
                color = Color.Transparent,
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(frameWidth, frameHeight),
                blendMode = androidx.compose.ui.graphics.BlendMode.Clear
            )
            
            val cornerLen = 40.dp.toPx()
            val color = Color.White
            
            drawLine(color, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(left + cornerLen, top), strokeWidth)
            drawLine(color, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(left, top + cornerLen), strokeWidth)
            
            drawLine(color, androidx.compose.ui.geometry.Offset(left + frameWidth, top), androidx.compose.ui.geometry.Offset(left + frameWidth - cornerLen, top), strokeWidth)
            drawLine(color, androidx.compose.ui.geometry.Offset(left + frameWidth, top), androidx.compose.ui.geometry.Offset(left + frameWidth, top + cornerLen), strokeWidth)
            
            drawLine(color, androidx.compose.ui.geometry.Offset(left, top + frameHeight), androidx.compose.ui.geometry.Offset(left + cornerLen, top + frameHeight), strokeWidth)
            drawLine(color, androidx.compose.ui.geometry.Offset(left, top + frameHeight), androidx.compose.ui.geometry.Offset(left, top + frameHeight - cornerLen), strokeWidth)
            
            drawLine(color, androidx.compose.ui.geometry.Offset(left + frameWidth, top + frameHeight), androidx.compose.ui.geometry.Offset(left + frameWidth - cornerLen, top + frameHeight), strokeWidth)
            drawLine(color, androidx.compose.ui.geometry.Offset(left + frameWidth, top + frameHeight), androidx.compose.ui.geometry.Offset(left + frameWidth, top + frameHeight - cornerLen), strokeWidth)

            // Mode Label
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    this.color = android.graphics.Color.WHITE
                    textSize = 40f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawText("Align ${if(scanMode == "QR") "QR Code" else "Number"} within frame", size.width / 2, top - 60f, paint)
            }
        }

        // Toggle Buttons at the bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                .padding(4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(if (scanMode == "QR") com.example.call.ui.theme.VisionPrimary else Color.Transparent, CircleShape)
                    .clickable { scanMode = "QR" }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Text("QR Code", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .background(if (scanMode == "TEXT") com.example.call.ui.theme.VisionPrimary else Color.Transparent, CircleShape)
                    .clickable { scanMode = "TEXT" }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Text("Numbers", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
