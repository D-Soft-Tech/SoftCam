package com.example.softcam.domain.usecases

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.google.mlkit.vision.face.Face

internal class FaceBoxOverlayImpl(
    overlay: FaceBoxOverlay,
    private val face: Face,
    private val imageRect: Rect
) :
    FaceBoxOverlay.FaceBox(overlay) {

    private val paint: Paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6.0f
    }

    override fun draw(canvas: Canvas?) {
        val rect = getBoxRect(
            imageRectWidth = imageRect.width().toFloat(),
            imageRectHeight = imageRect.height().toFloat(),
            faceBoundingBox = face.boundingBox
        )
        canvas?.drawRect(rect, paint)
    }

}