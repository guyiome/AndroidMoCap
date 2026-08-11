package com.guyiome.androidmocap.tracking

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.guyiome.androidmocap.R
import com.guyiome.androidmocap.logging.AppLog

/**
 * Enrobe MediaPipe Image Embedder pour l'étage 3 de la cascade de détection de la langue tirée
 * (revue technique, point 15) -- même mécanique de repli GPU->CPU que [FaceLandmarkerHelper], mais
 * en [RunningMode.IMAGE] (appel synchrone [embed]) plutôt que `LIVE_STREAM` : l'embedding ne tourne
 * que sur de rares frames déjà filtrées par les étages 1+2 (quelques Hz au plus, jamais le flux
 * 20-60Hz du tracking), pas besoin de la machinerie asynchrone (`detectAsync`/`ResultListener`)
 * qu'utilise [FaceLandmarkerHelper].
 *
 * Le modèle (`image_embedder.tflite`, MobileNetV3-small) est appliqué sur le recadrage buccal --
 * une image très différente de ce sur quoi ce modèle a été entraîné (photos naturelles). Ce n'est
 * pas un problème : la calibration personnelle ne dépend pas de ce que l'embedding "signifie"
 * sémantiquement, seulement qu'il soit cohérent entre "langue dehors" et "langue rentrée" pour ce
 * recadrage précis, cette caméra précise, cet utilisateur précis. Ne pas prendre ce choix de modèle
 * pour un bug si les embeddings bruts semblent "n'importe quoi" pris isolément.
 *
 * Instancié uniquement quand `tongueOutDetectionEnabled` est actif (voir `MainViewModel`) -- pas de
 * façon inconditionnelle comme [FaceLandmarkerHelper], pour ne pas charger un second modèle ni
 * réserver de délégué GPU/CPU pour les utilisateurs qui ne touchent jamais cette fonctionnalité
 * expérimentale.
 */
class TongueEmbeddingHelper(
    private val context: Context,
    private val tierConfig: TierConfig,
    private val forceGpuUnavailable: Boolean = false,
    private val onError: (String) -> Unit,
) {
    companion object {
        private const val TAG = "TongueEmbeddingHelper"

        /** Doit être téléchargé manuellement et placé dans app/src/main/assets -- voir le README. */
        private const val MODEL_ASSET_PATH = "image_embedder.tflite"
    }

    private var imageEmbedder: ImageEmbedder? = null

    /** Délégué effectivement actif après création. */
    var activeDelegateIsGpu: Boolean = false
        private set

    fun setup() {
        if (tierConfig.preferGpuDelegate && !forceGpuUnavailable) {
            val gpuOk = tryCreateEmbedder(Delegate.GPU)
            if (gpuOk) {
                activeDelegateIsGpu = true
                return
            }
            AppLog.w(TAG, "Délégué GPU indisponible sur cet appareil, repli sur CPU.")
        }
        val cpuOk = tryCreateEmbedder(Delegate.CPU)
        activeDelegateIsGpu = false
        if (!cpuOk) {
            onError(context.getString(R.string.error_tongue_embedder_init_failed))
        }
    }

    private fun tryCreateEmbedder(delegate: Delegate): Boolean {
        return try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET_PATH)
                .setDelegate(delegate)
                .build()

            val options = ImageEmbedder.ImageEmbedderOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                // L2-normalisé : les magnitudes des embeddings restent bien comportées pour un
                // éventuel log diagnostic des normes brutes -- sans effet sur la similarité cosinus
                // elle-même (déjà invariante à l'échelle par construction).
                .setL2Normalize(true)
                // Embeddings flottants, pas quantifiés -- classifyTongueState (TongueEmbeddingClassifier.kt)
                // travaille sur FloatArray, un embedding quantifié demanderait une comparaison différente.
                .setQuantize(false)
                .build()

            imageEmbedder?.close()
            imageEmbedder = ImageEmbedder.createFromOptions(context, options)
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Échec de création de l'ImageEmbedder avec delegate=$delegate", e)
            false
        }
    }

    /**
     * Calcule l'embedding de [bitmap] (déjà recadré sur la zone buccale, voir `mouthCropRegion` dans
     * `LipLandmarks.kt`) -- appel synchrone, à ne faire que sur des frames candidates déjà filtrées
     * par les étages 1+2, jamais en continu. `null` en cas d'échec (embedder pas prêt, image
     * invalide, aucun embedding renvoyé) plutôt qu'une exception -- même contrat que
     * `sampleMouthTonguePixelRatio` (MainViewModel.kt) dans le même genre de situation.
     */
    fun embed(bitmap: Bitmap): FloatArray? {
        val embedder = imageEmbedder ?: return null
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val embeddings = embedder.embed(mpImage).embeddingResult().embeddings()
            if (embeddings.isEmpty()) null else embeddings[0].floatEmbedding()
        } catch (e: Exception) {
            AppLog.e(TAG, "Échec du calcul d'embedding langue", e)
            null
        }
    }

    fun close() {
        imageEmbedder?.close()
        imageEmbedder = null
    }
}
