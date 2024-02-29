package com.example.softcam.domain.usecases

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.ceil

internal open class FaceBoxOverlay(context: Context?, attributes: AttributeSet?) :
    View(context, attributes) {
    private val lock = Any()
    private val faceBoxes: MutableList<FaceBox> = mutableListOf()
    private var mScale: Float? = null
    private var mOffsetX: Float? = null
    private var mOffsetY: Float? = null

    abstract class FaceBox(private val overlay: FaceBoxOverlay) {
        abstract fun draw(canvas: Canvas?)

        fun getBoxRect(
            imageRectWidth: Float,
            imageRectHeight: Float,
            faceBoundingBox: Rect
        ): RectF {
            val scaleX = overlay.width.toFloat() / imageRectHeight
            val scaleY = overlay.width.toFloat() / imageRectWidth
            val scale = scaleX.coerceAtLeast(scaleY)
            overlay.mScale = scale

            val offSetX = (overlay.width.toFloat() - ceil(imageRectHeight * scale)) / 2.0f
            val offSetY = (overlay.height.toFloat() - ceil(imageRectWidth * scale)) / 2.0f

            overlay.mOffsetX = offSetX
            overlay.mOffsetY = offSetY

            val mappedBoxes = RectF().apply {
                left = faceBoundingBox.right * scale + offSetX
                top = faceBoundingBox.top * scale + offSetY
                right = faceBoundingBox.left * scale + offSetX
                bottom = faceBoundingBox.bottom * scale + offSetY
            }

            val centerX = overlay.width.toFloat() / 2

            return mappedBoxes.apply {
                left = centerX + (centerX - left)
                right = centerX - (right - centerX)
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            faceBoxes.clear()
        }
        postInvalidate()
    }

    fun add(faceBox: FaceBox) {
        synchronized(lock) { faceBoxes.add(faceBox) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        synchronized(lock) {
            for (graphic in faceBoxes) {
                graphic.draw(canvas)
            }
        }
    }
}