package com.example.softcam.utils

import android.Manifest
import android.os.Build


internal object AppConstants {
    const val SOFT_CAM_TAG = "SOFT_CAM_TAG"
    const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss"
    val REQUIRED_PERMISSIONS =
        mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()

    const val STRING_CAPTURED_IMAGE_FILE_OUTPUT_NAME = "SOFT_CAM_CAPTURED_IMAGE"
    const val CAPTURED_IMAGE_FILE_FORMAT = ".jpg"
}