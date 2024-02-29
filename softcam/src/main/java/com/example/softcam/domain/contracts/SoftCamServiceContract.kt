package com.example.softcam.domain.contracts

import com.example.softcam.domain.models.SoftCamCameraType
import com.example.softcam.domain.models.SoftCamUseCase

internal interface SoftCamServiceContract {
    fun startCamera(useCase: SoftCamUseCase, cameraType: SoftCamCameraType)
    fun takePhoto()
    fun takeVideo()
}