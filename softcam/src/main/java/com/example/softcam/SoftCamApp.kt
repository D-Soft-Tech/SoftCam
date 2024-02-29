package com.example.softcam

import android.app.Application
import android.util.Log
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig
import java.util.concurrent.Executors

class SoftCamApp : Application(), CameraXConfig.Provider {
    override fun getCameraXConfig(): CameraXConfig {
        return CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
            .setCameraExecutor(Executors.newSingleThreadExecutor())
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
    }
}