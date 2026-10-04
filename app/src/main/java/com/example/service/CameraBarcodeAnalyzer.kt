package com.example.service

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

class CameraBarcodeAnalyzer(
    private val onBarcodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    // Configure ML Kit scanner to support all common 1D and 2D product barcode formats
    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_CODE_93,
            Barcode.FORMAT_ITF,
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_DATA_MATRIX
        )
        .build()

    private val scanner = BarcodeScanning.getClient(options)
    private val isScanning = AtomicBoolean(false)
    private var lastScannedTimestamp = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        val now = System.currentTimeMillis()

        // Debounce: prevent duplicate scan events within 1.5 seconds
        if (mediaImage != null && !isScanning.get() && (now - lastScannedTimestamp > 1500)) {
            isScanning.set(true)
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue
                        if (!rawValue.isNullOrBlank()) {
                            lastScannedTimestamp = System.currentTimeMillis()
                            onBarcodeDetected(rawValue)
                            break
                        }
                    }
                }
                .addOnFailureListener {
                    // Ignored: continue scanning subsequent frames
                }
                .addOnCompleteListener {
                    isScanning.set(false)
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
