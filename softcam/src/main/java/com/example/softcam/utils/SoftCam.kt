package com.example.softcam.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.example.softcam.presentation.SoftCamActivity

object SoftCam {
    const val SOFT_CAM_RESULT_KEY = "soft_cam_result_key"
    fun launchSoftCam(resultLauncher: ActivityResultLauncher<Intent>, context: Context) {
        SoftCamActivity.startCameraActivity(resultLauncher, context)
    }

    @Deprecated(
        message = "This method has been deprecated and support for it would be dropped in future release",
        replaceWith = ReplaceWith("SoftCam.launchSoftCam(resultLauncher, context)", "com.example.softcam.utils")
    )
    fun launchSoftCam(requestCode: Int, context: Activity) {
        SoftCamActivity.startCameraActivity(requestCode, context)
    }
}