package com.example.softcam.presentation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.example.softcam.R
import com.example.softcam.databinding.SoftcamActivityBinding
import com.example.softcam.domain.contracts.SoftCamFaceProcessor
import com.example.softcam.domain.contracts.SoftCamImageCaptureCallBack
import com.example.softcam.domain.contracts.SoftCamService
import com.example.softcam.domain.contracts.SoftCamServiceContract
import com.example.softcam.domain.models.SoftCamCameraType
import com.example.softcam.domain.models.SoftCamUseCase
import com.example.softcam.domain.usecases.FaceBoxOverlay
import com.example.softcam.domain.usecases.SoftCamFaceProcessorUseCase
import com.example.softcam.domain.usecases.SoftCamImageAnalyzer
import com.example.softcam.utils.AppConstants.REQUIRED_PERMISSIONS
import com.example.softcam.utils.Extensions.slideDown
import com.example.softcam.utils.Extensions.slideUp
import com.example.softcam.utils.SoftCam.SOFT_CAM_RESULT_KEY
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class SoftCamActivity : AppCompatActivity(), SoftCamImageCaptureCallBack {
    private lateinit var binding: SoftcamActivityBinding
    private lateinit var previewView: PreviewView
    private lateinit var captureBtn: ImageView
    private lateinit var faceOverLay: FaceBoxOverlay
    private lateinit var switchCameraBtn: ImageView
    private lateinit var softCamService: SoftCamServiceContract
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageAnalyzer: SoftCamImageAnalyzer
    private lateinit var softCamFaceProcessor: SoftCamFaceProcessor
    private lateinit var infoLayout: ConstraintLayout
    private lateinit var infoTv: TextView
    private lateinit var coroutineScope: CoroutineScope

    private var cameraTypeIsSelfie: Boolean = true

    private val _errorMessageSharedFlow: MutableSharedFlow<String> = MutableSharedFlow(replay = 0)
    private val errorMessageSharedFlow: SharedFlow<String> get() = _errorMessageSharedFlow
    private var lastMessage: String? = null

    private val cameraPermissionRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var permissionGranted = true
        permissions.entries.forEach {
            if (it.key in REQUIRED_PERMISSIONS && !it.value) {
                permissionGranted = false
            }
        }
        if (!permissionGranted) {
            Snackbar.make(
                this,
                binding.root,
                getString(R.string.permission_declined),
                Snackbar.LENGTH_LONG
            ).show()
        } else {
            softCamService.startCamera(SoftCamUseCase.PHOTO, SoftCamCameraType.FRONT)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        cameraPermissionRequestLauncher.launch(REQUIRED_PERMISSIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.softcam_activity)
        initViews()
        coroutineScope = CoroutineScope(Dispatchers.Default + Job())

        softCamFaceProcessor = SoftCamFaceProcessorUseCase(
            faceOverLay,
            this,
            logErrorMessageToUserAction = {
                logErrorMessageAction(it)
            }
        ) {
            softCamService.takePhoto()
        }
        imageAnalyzer = SoftCamImageAnalyzer(softCamFaceProcessor, this) {
            logErrorMessageAction(it)
        }
        cameraExecutor = Executors.newSingleThreadExecutor()
        softCamService = SoftCamService(
            this,
            this,
            previewView,
            cameraExecutor,
            imageAnalyzer,
            captureBtn,
            this
        ) {
            logErrorMessageAction(it)
        }

        // Observe Error messages from CamService
        lifecycleScope.launch {
            errorMessageSharedFlow.collectLatest {
                showErrorMessage(it)
            }
        }

        if (allPermissionsGranted()) {
            softCamService.startCamera(
                SoftCamUseCase.PHOTO, SoftCamCameraType.FRONT
            )
        } else {
            requestPermissions()
        }

        switchCameraBtn.setOnClickListener {
            cameraTypeIsSelfie = !cameraTypeIsSelfie
            softCamService.startCamera(
                SoftCamUseCase.PHOTO,
                if (cameraTypeIsSelfie) SoftCamCameraType.FRONT else SoftCamCameraType.BACK
            )
        }

        /*
        captureBtn.setOnClickListener {
            softCamService.takePhoto()
        }
         */
    }

    private fun initViews() {
        with(binding) {
            previewView = viewFinder
            captureBtn = imageCaptureButton
            switchCameraBtn = switchCameraIcon
            faceOverLay = faceBoxOverlay
            infoLayout = info
            infoTv = messageTv
        }
    }

    private suspend fun showErrorMessage(message: String) {
        infoTv.text = message
        infoLayout.slideUp()
        delay(6000)
        infoLayout.slideDown()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        coroutineScope.cancel()
    }

    override fun onSuccess(capturedImage: File?) {
        capturedImage?.let {
            val resultIntent = Intent()
            resultIntent.apply {
                putExtra(SOFT_CAM_RESULT_KEY, it.toURI().toString())
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        } ?: run {
            val resultIntent = Intent()
            resultIntent.putExtra(SOFT_CAM_RESULT_KEY, "FAILED")
            setResult(Activity.RESULT_CANCELED, resultIntent)
            finish()
        }
    }

    override fun onError(errorMessage: String) {
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        val resultIntent = Intent()
        resultIntent.putExtra(SOFT_CAM_RESULT_KEY, "FAILED")
        setResult(Activity.RESULT_CANCELED, resultIntent)
        finish()
    }

    companion object {
        internal fun startCameraActivity(
            resultLauncher: ActivityResultLauncher<Intent>,
            context: Context
        ) {
            val intent = Intent(context, SoftCamActivity::class.java)
            resultLauncher.launch(intent)
        }

        @Deprecated(
            message = "This method has been deprecated and support for it would be dropped in future release",
            replaceWith = ReplaceWith("ActivityResultLauncher")
        )
        internal fun startCameraActivity(
            requestCode: Int,
            context: Activity
        ) {
            val intent = Intent(context, SoftCamActivity::class.java)
            context.startActivityForResult(intent, requestCode)
        }
    }

    private fun logErrorMessageAction(message: String) {
        if (lastMessage != message) {
            coroutineScope.launch {
                _errorMessageSharedFlow.emit(message)
                lastMessage = message
            }
        }
    }
}