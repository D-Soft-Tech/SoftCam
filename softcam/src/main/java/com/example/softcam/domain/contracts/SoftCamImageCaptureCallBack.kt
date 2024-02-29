package com.example.softcam.domain.contracts

import java.io.File

internal interface SoftCamImageCaptureCallBack {
    fun onSuccess(capturedImage: File?)
    fun onError(errorMessage: String)
}