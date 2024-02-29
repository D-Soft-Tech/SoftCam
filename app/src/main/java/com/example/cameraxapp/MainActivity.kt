package com.example.cameraxapp

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import coil.load
import com.example.cameraxapp.databinding.ActivityMainBinding
import com.example.softcam.utils.SoftCam

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var capturedImageIV: ImageView
    private lateinit var launchCameraButton: Button

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let {
                    it.getStringExtra(SoftCam.SOFT_CAM_RESULT_KEY)?.let { uriString ->
                        if (uriString.isNotEmpty()) {
                            val capturedPhoto = Uri.parse(uriString)
                            capturedImageIV.load(capturedPhoto)
                        }
                    } ?: run {
                        Toast.makeText(
                            this,
                            getString(R.string.photo_capture_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } ?: run {
                    Toast.makeText(
                        this,
                        getString(R.string.photo_capture_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    private val permissionRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var permissionGranted = true
        permissions.entries.forEach {
            if (it.key in REQUIRED_PERMISSIONS && !it.value) {
                permissionGranted = false
            }
        }
        if (!permissionGranted) {
            Toast.makeText(this, getString(R.string.permission_declined), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, getString(R.string.permission_accepted), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        initViews()
        if (!allPermissionsGranted()) {
            permissionRequestLauncher.launch(REQUIRED_PERMISSIONS)
        }

        launchCameraButton.setOnClickListener {
            SoftCam.launchSoftCam(cameraLauncher, this)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    private fun initViews() {
        with(binding) {
            capturedImageIV = capturedPhoto
            launchCameraButton = launchCameraBtn
        }
    }
}