package com.guyiome.androidmocap.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.guyiome.androidmocap.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Pilote la caméra frontale via CameraX et transmet chaque frame (déjà tournée pour être
 * "à l'endroit") au callback [onFrame] pour inférence MediaPipe.
 *
 * Important : on ne mirroir PAS l'image envoyée au modèle (seul le PreviewView affiché à l'écran
 * est mirroré automatiquement par CameraX) -- sinon les blendshapes "Left"/"Right" seraient inversés
 * par rapport à l'anatomie réelle de l'utilisateur.
 */
class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onFrame: (MPImage, frameTimeMs: Long) -> Unit,
    /** Palier courant (voir TierConfig.targetFps) -- les frames en trop sont ignorées avant même
     *  d'allouer quoi que ce soit, voir [processFrame]. Ajustable à chaud via [setTargetFps]. */
    initialTargetFps: Int = 30,
    private val onError: (String) -> Unit = {},
) {
    companion object {
        private const val TAG = "CameraController"

        // Nombre de bitmaps "en vol" toléré (envoyés à MediaPipe, résultat pas encore reçu) avant
        // qu'on se mette à laisser tomber des frames plutôt que d'attendre -- MediaPipe ne devrait
        // quasiment jamais avoir plus d'une frame de retard en LIVE_STREAM, 3 laisse une marge de
        // sécurité sans risquer une file qui grossit indéfiniment si le device peine.
        private const val MAX_TRACKED_BITMAPS = 3

        // Résolution demandée pour l'analyse ImageAnalysis (donc CameraX/MediaPipe -- n'affecte pas
        // l'aperçu affiché à l'écran, un usage caméra séparé) -- palier STANDARD/COMPATIBLE
        // uniquement, revue technique issue #7 (OPTIMAL utilise sa propre capture caméra ARCore,
        // hors de ce fichier). MediaPipe recadre et redimensionne l'image en interne quelle que soit
        // sa résolution d'entrée (doc officielle Face Landmarker) -- au-delà d'un certain point, plus
        // de pixels ne rapporte donc aucune précision de détection en plus, seulement du coût pur
        // (décodage, copie/rotation du bitmap déjà identifiée comme point chaud, mémoire). Valeur
        // volontairement la même pour les deux paliers plutôt que différenciée -- MediaPipe recadrant
        // de toute façon, un chiffre plus petit sur COMPATIBLE ne changerait pas la précision, juste
        // le coût, et une seule valeur reste plus simple. Choix pragmatique : une résolution CameraX
        // courante et clairement généreuse, pas un chiffre "optimal" -- aucune source fiable trouvée
        // pour la taille d'entrée interne exacte du modèle Android utilisé ici. Estimation à ajuster
        // sur device (mesurer le coût par frame avant/après plutôt que deviner plus loin), même
        // traitement que les autres seuils non calés sur device de ce projet.
        private val CAMERA_ANALYSIS_TARGET_RESOLUTION = Size(640, 480)

        /**
         * Dimensions (largeur, hauteur) du bitmap final une fois la rotation caméra appliquée --
         * extrait en fonction pure (aucune dépendance Android) pour être testable en JVM, voir
         * [processFrame] qui en dépend pour dimensionner le bitmap cible.
         */
        internal fun rotatedDimensions(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int> {
            val swapDimensions = rotationDegrees == 90 || rotationDegrees == 270
            return if (swapDimensions) height to width else width to height
        }
    }

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewView: PreviewView? = null

    // "ImageAnalysis" (dont MediaPipe a besoin) reste lié en permanence dès le démarrage -- on ne
    // le touche plus jamais lors des bascules du mode économie d'énergie. Une implémentation
    // précédente faisait un unbindAll() + rebind complet à chaque bascule Preview on/off : ça
    // interrompait brièvement TOUT le flux caméra (analyse comprise) le temps de la
    // reconfiguration matérielle, ce qui figeait l'avatar pendant la transition. Seul l'usage
    // "Preview" (l'aperçu affiché à l'écran, le plus coûteux en énergie/chaleur) est désormais
    // ajouté/retiré individuellement via bindPreview()/unbindPreview(), sans jamais toucher à
    // "ImageAnalysis".
    private var imageAnalysis: ImageAnalysis? = null
    private var previewUseCase: Preview? = null
    private var previewEnabled: Boolean = true

    // --- Débit cible (palier) : évite de traiter plus de frames que nécessaire sur les appareils
    // d'entrée de gamme (voir TrackingTierSelector.targetFps, jusqu'ici jamais appliqué nulle
    // part). La frame est ignorée AVANT toute allocation/rotation -- c'est là qu'est le coût réel. ---
    @Volatile private var targetFps: Int = initialTargetFps.coerceAtLeast(1)
    @Volatile private var minFrameIntervalMs: Long = 1000L / targetFps
    private var lastAcceptedFrameElapsedMs: Long = 0L

    // --- Pool de bitmaps réutilisés pour éviter une allocation à chaque frame (c'était le
    // principal point chaud du pipeline). Un bitmap n'est rendu au pool des "libres" qu'une fois
    // que MediaPipe a fini de le lire (voir releaseFrame(), appelé depuis FaceLandmarkerHelper
    // quand le résultat de CE frame précis arrive) -- jamais réécrit pendant qu'il est encore en
    // cours d'analyse. Accès protégé par [poolLock] car releaseFrame() est appelé depuis le thread
    // de callback de MediaPipe, différent de celui de la caméra.
    //
    // Suivi par timestamp de frame (Long), PAS par l'objet MPImage lui-même : MediaPipe peut très
    // bien convertir/envelopper l'image en interne avant de l'échoir dans le résultat, auquel cas
    // ce n'est plus le même objet et une comparaison par identité ne matche jamais -- observé en
    // pratique : le pool se bloquait après [MAX_TRACKED_BITMAPS] frames (plus aucune ne se
    // libérait), gelant tout le tracking (plus de données envoyées à VBridger/VMC) après un très
    // court instant. Le timestamp, lui, est garanti par l'API LIVE_STREAM d'être renvoyé identique
    // via FaceLandmarkerResult.timestampMs(). ---
    private val poolLock = Any()
    private val freeBitmaps = ArrayDeque<Bitmap>()
    private val inFlightBitmaps = HashMap<Long, Bitmap>()

    // Bitmap tampon pour la copie brute (avant rotation) -- purement transitoire, entièrement
    // écrit puis lu de façon synchrone dans processFrame() sur l'unique thread caméra : aucun
    // risque à le réutiliser directement, contrairement aux bitmaps du pool ci-dessus qui partent
    // vers MediaPipe.
    private var rawScratchBitmap: Bitmap? = null
    private var cachedRotationDegrees: Int = Int.MIN_VALUE
    private var cachedRotationMatrix: Matrix? = null
    private val rotationPaint = Paint().apply { isFilterBitmap = false }

    fun start(previewView: PreviewView) {
        this.previewView = previewView
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindImageAnalysis()
            if (previewEnabled) bindPreview()
        }, ContextCompat.getMainExecutor(context))
    }

    /** Ajuste le débit cible à chaud (ex. rétrogradation de palier en cas de throttling thermique). */
    fun setTargetFps(fps: Int) {
        targetFps = fps.coerceAtLeast(1)
        minFrameIntervalMs = 1000L / targetFps
    }

    /**
     * Active/désactive l'usage caméra "Preview" à chaud, sans jamais toucher à "ImageAnalysis"
     * (voir commentaire sur les champs ci-dessus) -- le tracking ne connaît donc aucune coupure
     * ni gel pendant la transition.
     */
    fun setPreviewEnabled(enabled: Boolean) {
        if (previewEnabled == enabled) return
        previewEnabled = enabled
        if (enabled) bindPreview() else unbindPreview()
    }

    private fun bindImageAnalysis() {
        val provider = cameraProvider ?: return
        if (imageAnalysis != null) return
        // ResolutionStrategy, pas l'ancien setTargetResolution() (déprécié depuis CameraX 1.3, voir
        // CAMERA_ANALYSIS_TARGET_RESOLUTION) -- FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER accepte la
        // résolution supportée la plus proche dans un sens ou l'autre plutôt que d'exiger une
        // correspondance exacte (rarement disponible telle quelle selon l'appareil).
        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(CAMERA_ANALYSIS_TARGET_RESOLUTION, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
            )
            .build()
        val analysis = ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(resolutionSelector)
            .build()
            .also { it.setAnalyzer(cameraExecutor) { imageProxy -> processFrame(imageProxy) } }

        try {
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            imageAnalysis = analysis
            // Résolution réellement retenue par CameraX (peut différer de la cible demandée selon
            // ce que l'appareil supporte, voir FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER ci-dessus) --
            // logué pour vérifier sur device sans deviner (revue technique, issue #7).
            Log.i(TAG, "ImageAnalysis lié, résolution retenue : ${analysis.resolutionInfo?.resolution}")
        } catch (e: Exception) {
            Log.e(TAG, "Échec de bind CameraX (analyse)", e)
            onError(context.getString(R.string.error_camera_analysis_bind_failed, e.message ?: ""))
        }
    }

    private fun bindPreview() {
        val provider = cameraProvider ?: return
        val preview = previewView ?: return
        if (previewUseCase != null) return
        val useCase = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
        try {
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, useCase)
            previewUseCase = useCase
        } catch (e: Exception) {
            Log.e(TAG, "Échec de bind CameraX (aperçu)", e)
            onError(context.getString(R.string.error_camera_preview_bind_failed, e.message ?: ""))
        }
    }

    private fun unbindPreview() {
        val provider = cameraProvider ?: return
        val useCase = previewUseCase ?: return
        provider.unbind(useCase)
        previewUseCase = null
    }

    private fun processFrame(imageProxy: ImageProxy) {
        imageProxy.use { proxy ->
            val nowElapsed = SystemClock.elapsedRealtime()
            if (nowElapsed - lastAcceptedFrameElapsedMs < minFrameIntervalMs) {
                // Frame ignorée pour respecter le débit cible du palier -- avant toute
                // allocation/rotation, c'est là qu'est le coût réel sur les appareils d'entrée de
                // gamme (voir TrackingTier).
                return
            }
            lastAcceptedFrameElapsedMs = nowElapsed

            val rotationDegrees = proxy.imageInfo.rotationDegrees
            val (outWidth, outHeight) = rotatedDimensions(proxy.width, proxy.height, rotationDegrees)

            val target = acquirePooledBitmap(outWidth, outHeight)
                // Pool épuisé : MediaPipe a plusieurs frames de retard -- on laisse tomber celle-ci
                // plutôt que d'attendre ou d'allouer indéfiniment.
                ?: return

            if (rotationDegrees == 0) {
                target.copyPixelsFromBuffer(proxy.planes[0].buffer)
            } else {
                val raw = rawScratchBitmap?.takeIf { it.width == proxy.width && it.height == proxy.height }
                    ?: Bitmap.createBitmap(proxy.width, proxy.height, Bitmap.Config.ARGB_8888)
                        .also { rawScratchBitmap = it }
                raw.copyPixelsFromBuffer(proxy.planes[0].buffer)

                // L'angle de rotation caméra est constant pour toute la session (app verrouillée
                // portrait) -- la matrice n'est donc recalculée que si jamais il change.
                if (cachedRotationDegrees != rotationDegrees) {
                    cachedRotationDegrees = rotationDegrees
                    cachedRotationMatrix = Matrix().apply {
                        postRotate(rotationDegrees.toFloat())
                        val bounds = RectF(0f, 0f, proxy.width.toFloat(), proxy.height.toFloat())
                        mapRect(bounds)
                        postTranslate(-bounds.left, -bounds.top)
                    }
                }
                Canvas(target).drawBitmap(raw, cachedRotationMatrix!!, rotationPaint)
            }

            val mpImage = BitmapImageBuilder(target).build()
            val frameTimeMs = SystemClock.uptimeMillis()
            synchronized(poolLock) { inFlightBitmaps[frameTimeMs] = target }
            onFrame(mpImage, frameTimeMs)
        }
    }

    /**
     * À appeler une fois que MediaPipe a terminé de traiter le frame portant ce timestamp (voir
     * [com.guyiome.androidmocap.tracking.FaceLandmarkerHelper], qui le reçoit en écho via
     * `FaceLandmarkerResult.timestampMs()`) -- rend son bitmap réutilisable pour une frame future,
     * sans jamais risquer d'écraser une image encore en cours d'analyse.
     */
    fun releaseFrame(frameTimeMs: Long) {
        val bitmap = synchronized(poolLock) { inFlightBitmaps.remove(frameTimeMs) } ?: return
        synchronized(poolLock) { freeBitmaps.addLast(bitmap) }
    }

    private fun acquirePooledBitmap(width: Int, height: Int): Bitmap? = synchronized(poolLock) {
        val match = freeBitmaps.firstOrNull { it.width == width && it.height == height }
        if (match != null) {
            freeBitmaps.remove(match)
            return@synchronized match
        }
        // Bitmaps libres d'une taille différente (résolution caméra changée en cours de route,
        // cas rare) : on les abandonne plutôt que de les garder indéfiniment en mémoire.
        freeBitmaps.clear()
        if (inFlightBitmaps.size >= MAX_TRACKED_BITMAPS) return@synchronized null
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    fun stop() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        // Remis à null (relecture globale du 7 août 2026, point 17) : sans ça, bindImageAnalysis()/
        // bindPreview() no-opaient silencieusement sur ces références encore non-null si start()
        // était un jour rappelé sur cette même instance après stop() -- dormant aujourd'hui (seul
        // appelant : MainViewModel.onCleared(), jamais suivi d'un nouveau start()), mais un piège
        // latent pour tout futur appelant qui réutiliserait l'instance.
        imageAnalysis = null
        previewUseCase = null
        synchronized(poolLock) {
            freeBitmaps.clear()
            inFlightBitmaps.clear()
        }
    }
}
