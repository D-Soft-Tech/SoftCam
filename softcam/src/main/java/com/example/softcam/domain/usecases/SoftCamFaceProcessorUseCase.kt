package com.example.softcam.domain.usecases

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.example.softcam.R
import com.example.softcam.domain.contracts.SoftCamFaceProcessor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark

internal typealias FaceDetectionUseCaseCallBack = () -> Unit

internal class SoftCamFaceProcessorUseCase(
    private val faceBoxOverlay: FaceBoxOverlay,
    private val context: Context,
    private val logErrorMessageToUserAction: (message: String) -> Unit,
    private val callBack: FaceDetectionUseCaseCallBack,
) : SoftCamFaceProcessor {
    // High-accuracy landmark detection and face classification
    private val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build()

    private val faceDetector = FaceDetection.getClient(faceDetectorOptions)

    @OptIn(ExperimentalGetImage::class)
    override fun processImage(imageProxy: ImageProxy) {
        val inputMage =
            InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)
        faceDetector.process(inputMage)
            .addOnSuccessListener { faces ->
                faceBoxOverlay.clear()
                processFace(faces, imageProxy)
            }
            .addOnFailureListener { exception ->
                logErrorMessageToUserAction.invoke(
                    context.getString(R.string.no_face_detected),
                )
                exception.localizedMessage?.let { Log.d("FACE_PROCESSING_ERROR", it) }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun processFace(faces: MutableList<Face>, imageProxy: ImageProxy) {
        faces.forEach { face: Face ->
            val box = FaceBoxOverlayImpl(faceBoxOverlay, face, imageProxy.cropRect)

            faceBoxOverlay.add(box)

            // check if is human
            val isHuman = isHumanContourDetected(context, face) && isHumanDetected(context, face)

            // Check if user is smiling
            val isSmiling = isFaceSmiling(context, face)

            // Check if eyes are opened
            val areEyesOpened = areEyesOpened(context, face)

            if (isHuman && isSmiling && areEyesOpened) {
                callBack.invoke()
                imageProxy.close()
            }
        }
    }

    /**
     * Checks if the user's eyes are opened or not
     *
     * @param [context] This is used to extract the appropriate error message from string resource in the case that user's eye(s) is/are not opened
     * @param [face] The supplied image on which this check is to be carried out
     *
     * @return [Boolean] True if both eyes are opened otherwise False
     * */
    private fun areEyesOpened(context: Context, face: Face): Boolean =
        face.rightEyeOpenProbability?.let { rightEye ->
            face.leftEyeOpenProbability?.let { leftEye ->
                if (rightEye >= 0.5 && leftEye >= 0.5) {
                    true
                } else {
                    onEyesNotOpened(context)
                }
            } ?: run { onEyesNotOpened(context) }
        } ?: run { onEyesNotOpened(context) }

    private fun onEyesNotOpened(context: Context): Boolean {
        logErrorMessageToUserAction.invoke(
            context.getString(R.string.kindly_open_your_eyes)
        )
        return false
    }

    /**
     * Checks if the user is smiling or not
     *
     * @param[context] This is used to extract the appropriate error message from string resource in the case that the user being focused is not smiling
     * @param[face] The face being focused on which the check is to be performed
     *
     * @return [Boolean] True if the focused face is smiling otherwise False
     * */
    private fun isFaceSmiling(context: Context, face: Face): Boolean =
        face.smilingProbability?.let {
            if (it < 0.5) logErrorMessageToUserAction.invoke(
                context.getString(R.string.kindly_smile),
            )
            it >= 0.5
        } ?: run {
            logErrorMessageToUserAction.invoke(context.getString(R.string.kindly_smile))
            false
        }

    /**
     *
     * Returns true if the face passed to it is a human face otherwise false.
     * Human is detected if the mouth, ears, eyes, cheeks and nose landmarks are available
     *
     * @param context This is used to extract the appropriate error message from string resource in case of failure to detect a land mark
     * @param [face] The face on which the above mentioned landmarks are to be detected
     *
     * @return [Boolean] Whether a human is detected or not.
     * */
    private fun isHumanDetected(context: Context, face: Face): Boolean {
        val mouthPresent =
            (face.getLandmark(FaceLandmark.MOUTH_LEFT) != null && face.getLandmark(FaceLandmark.MOUTH_RIGHT) != null && face.getLandmark(
                FaceLandmark.MOUTH_BOTTOM
            ) != null)

        val mouthLandmark = if (!mouthPresent) context.getString(R.string.mouth) else ""

        val earPresent =
            (face.getLandmark(FaceLandmark.LEFT_EAR) != null || face.getLandmark(FaceLandmark.RIGHT_EAR) != null)

        val earLandmark = if (!earPresent) context.getString(R.string.ear) else ""

        val eyesPresent =
            (face.getLandmark(FaceLandmark.LEFT_EYE) != null && face.getLandmark(FaceLandmark.RIGHT_EYE) != null)

        val eyesLandmark = if (!eyesPresent) context.getString(R.string.eye) else ""

        val cheeksPresent =
            (face.getLandmark(FaceLandmark.LEFT_CHEEK) != null && face.getLandmark(FaceLandmark.RIGHT_CHEEK) != null)

        val cheeksLandmark = if (!cheeksPresent) context.getString(R.string.cheeks) else ""

        val nosePresent = (face.getLandmark(FaceLandmark.NOSE_BASE) != null)

        val landMarksExceptFromNose = mouthLandmark + earLandmark + eyesLandmark + cheeksLandmark
        val conjunction =
            if (landMarksExceptFromNose.isNotEmpty()) context.getString(R.string.and) else ""

        val noseLandmark = if (!mouthPresent) context.getString(R.string.nose) else ""

        val allLandmarks = landMarksExceptFromNose + conjunction + noseLandmark

        if (allLandmarks.isNotEmpty()) context.getString(R.string.focus_on_real_human, allLandmarks)

        return (mouthPresent && earPresent && eyesPresent && cheeksPresent && nosePresent)
    }

    /**
     *
     * Returns true if the face passed to it is a human face otherwise false.
     * Human is detected if the mouth, ears, eyes, cheeks and nose contour are available
     *
     * @param context This is used to extract the appropriate error message from string resource in case of failure to detect a contour
     * @param [face] The face on which the above mentioned contour are to be detected
     *
     * @return [Boolean] Whether a human is detected or not.
     * */
    private fun isHumanContourDetected(context: Context, face: Face): Boolean {
        val mouthPresent =
            (face.getContour(FaceContour.UPPER_LIP_BOTTOM) != null && face.getContour(FaceContour.LOWER_LIP_TOP) != null)

        val mouthLandmark = if (!mouthPresent) context.getString(R.string.mouth) else ""

        val eyesPresent =
            (face.getContour(FaceContour.LEFT_EYE) != null && face.getContour(FaceContour.RIGHT_EYE) != null)

        val eyesLandmark = if (!eyesPresent) context.getString(R.string.eye) else ""

        val cheeksPresent =
            (face.getContour(FaceContour.LEFT_CHEEK) != null && face.getContour(FaceContour.RIGHT_CHEEK) != null)

        val cheeksLandmark = if (!cheeksPresent) context.getString(R.string.cheeks) else ""

        val nosePresent =
            (face.getContour(FaceContour.NOSE_BOTTOM) != null && face.getContour(FaceContour.NOSE_BRIDGE) != null)

        val landMarksExceptFromNose = mouthLandmark + eyesLandmark + cheeksLandmark
        val conjunction =
            if (landMarksExceptFromNose.isNotEmpty()) context.getString(R.string.and) else ""

        val noseLandmark = if (!mouthPresent) context.getString(R.string.nose) else ""

        // val allLandmarks = landMarksExceptFromNose + conjunction + noseLandmark

        return (mouthPresent && eyesPresent && cheeksPresent && nosePresent)
    }
}