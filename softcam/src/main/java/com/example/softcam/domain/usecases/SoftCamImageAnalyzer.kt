package com.example.softcam.domain.usecases

import android.content.Context
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.softcam.R
import com.example.softcam.domain.contracts.SoftCamFaceProcessor
import kotlinx.coroutines.CoroutineScope


internal class SoftCamImageAnalyzer(
    private val softCamProcessor: SoftCamFaceProcessor,
    private val context: Context,
    private val logErrorMessageAction: (message: String) -> Unit
) :
    ImageAnalysis.Analyzer {
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        if (image.image != null) {
            softCamProcessor.processImage(image)
        } else {
            logErrorMessageAction.invoke(
                context.getString(R.string.no_face_detected)
            )
        }
    }
}