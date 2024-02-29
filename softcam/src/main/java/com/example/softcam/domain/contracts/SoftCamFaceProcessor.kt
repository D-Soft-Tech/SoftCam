package com.example.softcam.domain.contracts

import androidx.camera.core.ImageProxy

internal interface SoftCamFaceProcessor {
    fun processImage(imageProxy: ImageProxy)
}