package com.example.softcam.domain.contracts

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.icu.text.SimpleDateFormat
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.FLASH_MODE_ON
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.net.toFile
import androidx.lifecycle.LifecycleOwner
import coil.load
import com.example.softcam.R
import com.example.softcam.domain.models.SoftCamCameraType
import com.example.softcam.domain.models.SoftCamUseCase
import com.example.softcam.domain.usecases.SoftCamImageAnalyzer
import com.example.softcam.utils.AppConstants.CAPTURED_IMAGE_FILE_FORMAT
import com.example.softcam.utils.AppConstants.FILENAME_FORMAT
import com.example.softcam.utils.AppConstants.SOFT_CAM_TAG
import com.example.softcam.utils.AppConstants.STRING_CAPTURED_IMAGE_FILE_OUTPUT_NAME
import java.util.Locale
import java.util.concurrent.ExecutorService

internal class SoftCamService(
    private val lifecycleOwner: LifecycleOwner,
    private val context: Context,
    private val previewView: PreviewView,
    private val cameraExecutor: ExecutorService,
    private val imageAnalyzer: SoftCamImageAnalyzer,
    private val captureButton: ImageView,
    private val imageCaptureCallBack: SoftCamImageCaptureCallBack,
    private var imageCapture: ImageCapture? = null,
    private var videoCapture: VideoCapture<Recorder>? = null,
    private var recording: Recording? = null
) : SoftCamServiceContract {
    override fun startCamera(useCase: SoftCamUseCase, cameraType: SoftCamCameraType) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .build()

                // Build the Image Analysis UseCase
                val imageAnalyzer = ImageAnalysis.Builder()
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, imageAnalyzer)
                    }

                // Build the Image Capture UseCase
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(FLASH_MODE_ON)
                    .build()
                val cameraSelector =
                    CameraSelector.Builder()
                        .requireLensFacing(
                            if (cameraType == SoftCamCameraType.FRONT)
                                CameraSelector.LENS_FACING_FRONT
                            else CameraSelector.LENS_FACING_BACK
                        ).build()

                val orientationListener = object : OrientationEventListener(context) {
                    override fun onOrientationChanged(orientation: Int) {
                        val desiredOrientation = when (orientation) {
                            in 45..134 -> Surface.ROTATION_270
                            in 135..224 -> Surface.ROTATION_180
                            in 225..314 -> Surface.ROTATION_90
                            else -> Surface.ROTATION_0
                        }
                        imageCapture?.targetRotation = desiredOrientation
                    }
                }
                orientationListener.enable()

                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(
                            Quality.HIGHEST,
                            FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
                        )
                    )
                    .build()

                // Build the Video capture UseCase
                videoCapture = VideoCapture.withOutput(recorder)

                try {
                    cameraProvider.unbindAll()
                    when (useCase) {
                        SoftCamUseCase.PHOTO -> {
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalyzer,
                                imageCapture
                            )
                        }

                        SoftCamUseCase.VIDEO -> {
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                videoCapture
                            )
                        }
                    }
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                } catch (e: Exception) {
                    e.localizedMessage?.let { Log.e(SOFT_CAM_TAG, it) }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun takePhoto() {
        val imageCapture = imageCapture ?: run {
            Toast.makeText(
                context,
                context.getString(R.string.photo_capture_failed),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val photoName = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())

        val imageOutput =
            kotlin.io.path.createTempFile(
                STRING_CAPTURED_IMAGE_FILE_OUTPUT_NAME,
                CAPTURED_IMAGE_FILE_FORMAT
            )

        val contentValuesToPutToMediaStore = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, photoName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/SoftCam-Image")
            }
        }

        // use this to save to the device
        val phoneDirectoryOutput = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValuesToPutToMediaStore
        ).build()

        val outputOptions = ImageCapture.OutputFileOptions.Builder(imageOutput.toFile()).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                @SuppressLint("RestrictedApi")
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val outputFile = outputFileResults.savedUri?.toFile()
                    imageCaptureCallBack.onSuccess(outputFile)
                    imageCapture.camera?.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    exception.localizedMessage?.let { imageCaptureCallBack.onError(it) }
                    Toast.makeText(
                        context,
                        context.getString(R.string.photo_capture_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    override fun takeVideo() {
        val videoCapture = this.videoCapture ?: run {
            Toast.makeText(
                context,
                context.getString(R.string.video_capture_failed),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        captureButton.isEnabled = false

        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            recording = null
            return
        }

        // create and start a new recording session
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SoftCam-Video")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        recording = videoCapture.output
            .prepareRecording(context, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) ==
                    PermissionChecker.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        captureButton.apply {
                            contentDescription = context.getString(R.string.stop_capture)
                            isEnabled = true
                        }
                    }

                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT)
                                .show()
                            Log.d(SOFT_CAM_TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(
                                SOFT_CAM_TAG, "Video capture ends with error: " +
                                        "${recordEvent.error}"
                            )
                        }
                        captureButton.apply {
                            contentDescription = context.getString(R.string.start_capture)
                            isEnabled = true
                        }
                    }
                }
            }
    }
}