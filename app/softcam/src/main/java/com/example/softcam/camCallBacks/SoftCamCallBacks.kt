package com.example.softcam.camCallBacks

interface SoftCamCallBacks {
    fun onSuccess(onCaptureSucceededAction: (capturedImage: String) -> Unit)
    fun onFailed(onCaptureFailedAction: (errorMessage: String) -> Unit)
}