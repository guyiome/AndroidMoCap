package com.guyiome.androidmocap.tracking

/** Un blendshape ARKit (ex. "jawOpen", "eyeBlinkLeft") avec son score MediaPipe entre 0 et 1. */
data class BlendshapeScore(val name: String, val score: Float)

data class FaceTrackingResult(
    val faceDetected: Boolean,
    val blendshapes: List<BlendshapeScore>,
    val inferenceTimeMs: Long,
    val timestampMs: Long,
    /**
     * Rotation de la tête en degrés [pitch (X), yaw (Y), roll (Z)], BRUTE (relative à la caméra,
     * non compensée pour la rotation du téléphone ni la calibration -- cette compensation se fait
     * dans MainViewModel à partir de [headRotationMatrix]). (0,0,0) si aucun visage détecté.
     */
    val headEulerDegrees: FloatArray = floatArrayOf(0f, 0f, 0f),
    /**
     * Matrice de rotation 3x3 (row-major) brute correspondant à [headEulerDegrees], utilisée pour
     * composer la compensation de rotation du téléphone en espace matriciel (voir [RotationMath]).
     */
    val headRotationMatrix: FloatArray = RotationMath.IDENTITY_3X3,
    /** Direction du regard [pitch, yaw, 0] par œil, dérivée des blendshapes eyeLookUp/Down/In/Out. */
    val leftEyeEulerDegrees: FloatArray = floatArrayOf(0f, 0f, 0f),
    val rightEyeEulerDegrees: FloatArray = floatArrayOf(0f, 0f, 0f),
)
