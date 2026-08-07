package com.guyiome.androidmocap.tracking

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.guyiome.androidmocap.R

/**
 * Enrobe MediaPipe Face Landmarker en mode LIVE_STREAM.
 *
 * Tente d'abord le délégué GPU si le [TierConfig] le préfère ; en cas d'échec (device sans
 * délégué GPU compatible), retombe automatiquement sur CPU plutôt que de planter -- c'est la
 * mécanique de repli qui garantit que l'app reste fonctionnelle sur tous les paliers.
 */
class FaceLandmarkerHelper(
    private val context: Context,
    private val tierConfig: TierConfig,
    /**
     * Mock de debug (panneau caché de `DiagnosticsScreen`, voir `AppSettingsStore.debugForceGpuUnavailable`
     * et revue technique point 35) : force le repli CPU même si [TierConfig.preferGpuDelegate] est
     * vrai -- réutilise le chemin de repli CPU déjà existant dans [setup] sans le modifier.
     */
    private val forceGpuUnavailable: Boolean = false,
    private val onResult: (FaceTrackingResult) -> Unit,
    /**
     * Appelé une fois qu'un frame donné a été entièrement traité (résultat reçu ou non), avec le
     * timestamp exact qui avait été passé à [detectAsync] pour ce frame -- signal utilisé par
     * [com.guyiome.androidmocap.camera.CameraController] pour savoir quand un bitmap de son pool
     * de réutilisation redevient libre, voir CameraController.releaseFrame(). Volontairement basé
     * sur le timestamp (garanti renvoyé à l'identique par l'API LIVE_STREAM) plutôt que sur l'objet
     * MPImage lui-même : MediaPipe peut le convertir/l'envelopper en interne, auquel cas ce n'est
     * plus le même objet et une comparaison par identité ne matcherait jamais côté CameraController.
     */
    private val onFrameProcessed: (frameTimeMs: Long) -> Unit = {},
    private val onError: (String) -> Unit,
) {
    companion object {
        private const val TAG = "FaceLandmarkerHelper"

        /** Doit être téléchargé manuellement et placé dans app/src/main/assets -- voir le README. */
        private const val MODEL_ASSET_PATH = "face_landmarker.task"

        /**
         * Le regard des yeux (direction du globe oculaire) n'est pas fourni directement par
         * MediaPipe -- seuls les blendshapes eyeLookUp/Down/In/Out (intensité 0-1) le sont. On les
         * convertit ici en un angle approximatif [pitch, yaw, 0] par œil, envoyé dans les champs
         * rightEye#/leftEye# du protocole iFacialMocap (distinct des blendshapes eux-mêmes, qui
         * sont envoyés en plus). Extrait en fonction pure (`internal`, aucune dépendance MediaPipe
         * ni Android) pour être testable en JVM -- une conversion de nom/signe erronée ici cause le
         * même genre de bug silencieux déjà rencontré avec le nommage des blendshapes eyeSquint.
         *
         * Convention : yaw positif = regard vers la gauche du sujet, pitch positif = regard vers le haut.
         * Angle max arbitraire (30°) à ajuster selon le rendu observé.
         */
        internal fun computeEyeGazeDegrees(blendshapes: List<BlendshapeScore>): Pair<FloatArray, FloatArray> {
            fun score(name: String) = blendshapes.firstOrNull { it.name == name }?.score ?: 0f
            val maxAngle = 30f

            val leftPitch = (score("eyeLookUpLeft") - score("eyeLookDownLeft")) * maxAngle
            val leftYaw = (score("eyeLookOutLeft") - score("eyeLookInLeft")) * maxAngle
            val rightPitch = (score("eyeLookUpRight") - score("eyeLookDownRight")) * maxAngle
            val rightYaw = (score("eyeLookInRight") - score("eyeLookOutRight")) * maxAngle

            return floatArrayOf(leftPitch, leftYaw, 0f) to floatArrayOf(rightPitch, rightYaw, 0f)
        }
    }

    private var faceLandmarker: FaceLandmarker? = null

    /** Délégué effectivement actif après création (utile pour l'indicateur de palier dans l'UI). */
    var activeDelegateIsGpu: Boolean = false
        private set

    // Extraction du mesh (478 points) coûteuse pour rien tant que l'overlay optionnel n'est pas
    // affiché (désactivé par défaut) -- @Volatile car modifié depuis le thread UI/ViewModel
    // (setLandmarksNeeded) et lu depuis le thread de callback MediaPipe (onLiveStreamResult).
    @Volatile private var landmarksNeeded: Boolean = false

    /** À appeler quand l'overlay du mesh est activé/désactivé dans les réglages. */
    fun setLandmarksNeeded(needed: Boolean) {
        landmarksNeeded = needed
    }

    fun setup() {
        if (tierConfig.preferGpuDelegate && !forceGpuUnavailable) {
            val gpuOk = tryCreateLandmarker(Delegate.GPU)
            if (gpuOk) {
                activeDelegateIsGpu = true
                return
            }
            Log.w(TAG, "Délégué GPU indisponible sur cet appareil, repli sur CPU.")
        }
        val cpuOk = tryCreateLandmarker(Delegate.CPU)
        activeDelegateIsGpu = false
        if (!cpuOk) {
            onError(context.getString(R.string.error_mediapipe_init_failed))
        }
    }

    private fun tryCreateLandmarker(delegate: Delegate): Boolean {
        return try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET_PATH)
                .setDelegate(delegate)
                .build()

            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(1)
                .setMinFaceDetectionConfidence(0.5f)
                .setMinFacePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setOutputFaceBlendshapes(true)
                .setOutputFacialTransformationMatrixes(true)
                .setResultListener(::onLiveStreamResult)
                .setErrorListener { e -> onError(e.message ?: context.getString(R.string.error_mediapipe_unknown)) }
                .build()

            faceLandmarker?.close()
            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Échec de création du FaceLandmarker avec delegate=$delegate", e)
            false
        }
    }

    /** À appeler pour chaque frame caméra, depuis [com.guyiome.androidmocap.camera.CameraController]. */
    fun detectAsync(image: MPImage, frameTimeMs: Long) {
        faceLandmarker?.detectAsync(image, frameTimeMs)
    }

    fun close() {
        faceLandmarker?.close()
        faceLandmarker = null
    }

    private fun onLiveStreamResult(result: FaceLandmarkerResult, input: MPImage) {
        val blendshapesOptional = result.faceBlendshapes()
        val blendshapes = if (blendshapesOptional.isPresent && blendshapesOptional.get().isNotEmpty()) {
            blendshapesOptional.get()[0].map { category ->
                BlendshapeScore(name = category.categoryName(), score = category.score())
            }
        } else {
            emptyList()
        }

        val matrixOptional = result.facialTransformationMatrixes()
        val headRotationMatrix = if (matrixOptional.isPresent && matrixOptional.get().isNotEmpty()) {
            RotationMath.rotation3x3FromColumnMajor4x4(matrixOptional.get()[0])
        } else {
            RotationMath.IDENTITY_3X3
        }
        val headEuler = RotationMath.toEulerDegrees(headRotationMatrix)

        val (leftEyeEuler, rightEyeEuler) = computeEyeGazeDegrees(blendshapes)

        // Mesh complet (478 points -- surface + iris), coordonnées déjà normalisées [0,1] par
        // MediaPipe -- extrait uniquement si l'overlay optionnel (voir
        // com.guyiome.androidmocap.ui.FaceMeshOverlay) est actif : contrairement aux blendshapes,
        // ce mesh ne sert qu'à cet affichage, désactivé par défaut -- inutile de construire cette
        // liste de 478 éléments à chaque frame pour la quasi-totalité des sessions.
        val landmarksOptional = result.faceLandmarks()
        val landmarks = if (landmarksNeeded && landmarksOptional.isNotEmpty()) {
            landmarksOptional[0].map { landmark -> landmark.x() to landmark.y() }
        } else {
            emptyList()
        }

        onResult(
            FaceTrackingResult(
                faceDetected = blendshapes.isNotEmpty(),
                blendshapes = blendshapes,
                inferenceTimeMs = SystemClock.uptimeMillis() - result.timestampMs(),
                timestampMs = result.timestampMs(),
                headEulerDegrees = headEuler,
                headRotationMatrix = headRotationMatrix,
                leftEyeEulerDegrees = leftEyeEuler,
                rightEyeEulerDegrees = rightEyeEuler,
                faceLandmarks = landmarks,
                // Dimensions de l'image réellement analysée (espace de normalisation de
                // faceLandmarks) -- permet à LandmarkProjection de reproduire le recadrage centré
                // de PreviewView plutôt qu'un étirement naïf. Voir point 27 de la revue technique.
                imageWidthPx = input.width,
                imageHeightPx = input.height,
            )
        )
        onFrameProcessed(result.timestampMs())
    }
}
