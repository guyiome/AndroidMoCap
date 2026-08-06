package com.guyiome.androidmocap.tracking

import android.content.Context
import android.media.Image
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.util.Log
import com.google.ar.core.AugmentedFace
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.framework.image.MediaImageBuilder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Source caméra + pose de tête alternative pour le palier [TrackingTier.OPTIMAL], basée sur ARCore
 * Augmented Faces plutôt que CameraX -- voir `AndroidMoCap_revue_technique.md` point 13 pour le
 * contexte complet.
 *
 * ⚠️ Écrit sans accès à un appareil/émulateur pour compiler ou exécuter ce fichier (contrainte de
 * cette session) -- voir `AndroidMoCap_tests_unitaires.md` pour la liste précise de ce qui est
 * vérifié (rien, ici, au-delà de la relecture attentive) et des points les plus susceptibles de
 * demander un ajustement au premier build/run réel : le nom exact et la signature de
 * [MediaImageBuilder] (classe MediaPipe supposée exister pour construire un [MPImage] directement
 * depuis un `android.media.Image`, jamais exercée dans ce projet avant ce commit) -- **confirmé
 * identique dans MediaPipe 1.0.0 par décompilation `javap` lors de l'intégration de ce fichier dans
 * `main` (6 août 2026), donc plus un point d'incertitude** --, et l'ordre exact des appels ARCore
 * (sélection de la configuration caméra frontale, liaison de la texture GL, `resume()`) qui suit la
 * séquence documentée dans les échantillons officiels ARCore mais n'a pas été rejouée ici.
 *
 * ⚠️ **Risque connu et non résolu, identifié lors de l'intégration dans `main` (6 août 2026, voir
 * revue technique point 3/13)** : [emitCameraImage] construit le [MPImage] directement depuis
 * `frame.acquireCameraImage()`, sans aucune correction de rotation ni de miroir -- contrairement à
 * `CameraController.processFrame()`, qui applique explicitement `rotationDegrees` selon
 * l'orientation du capteur. Impact possible, à vérifier au premier test device : détection
 * MediaPipe dégradée et/ou overlay du mesh mal projeté quand ce tracker est actif. Ne pas
 * corriger à l'aveugle sans pouvoir observer le comportement réel sur un appareil.
 *
 * Raison d'être : ARCore Augmented Faces gère lui-même la capture caméra frontale en interne (via
 * `Session#setCameraTextureName`, qui nécessite un contexte GL) -- il ne peut donc pas cohabiter
 * avec CameraX sur la même caméra (Camera2 n'autorise qu'un seul client actif). Ce tracker pilote
 * donc sa propre caméra (via ARCore) et sert de source de frames pour MediaPipe (toujours utilisé
 * pour les blendshapes, [FaceLandmarkerHelper] ne change pas) ET de source de pose de tête (à la
 * place de `FaceLandmarkerResult#facialTransformationMatrixes()`).
 *
 * Nécessite un contexte GL pour exister (contrainte ARCore, pas un choix) : piloté par un
 * [GLSurfaceView] hébergé côté Compose via [attachTo] -- rien n'y est dessiné visuellement pour
 * l'instant (l'aperçu de ce palier est l'overlay du mesh de tracking existant, voir
 * `AndroidMoCap_revue_technique.md` point 13 pour la discussion sur l'aperçu caméra live, reportée).
 */
class ArCoreHeadPoseTracker(
    private val context: Context,
    /** Même signature que `CameraController.onFrame` -- alimente [FaceLandmarkerHelper.detectAsync] sans le modifier. */
    private val onFrame: (MPImage, frameTimeMs: Long) -> Unit,
    private val onHeadPoseRotationMatrix: (FloatArray) -> Unit,
    private val onError: (String) -> Unit,
    /**
     * Appelé si Augmented Faces ne peut pas être utilisé sur cet appareil (ARCore non installé ou
     * trop ancien, appareil incompatible, configuration caméra frontale refusée...) -- signal pour
     * que [com.guyiome.androidmocap.ui.MainViewModel] replie sur le palier `STANDARD` (MediaPipe
     * seul avec CameraX, comme avant ce commit) plutôt que de bloquer tout le tracking. Le palier
     * est en effet déterminé au moment de `DeviceCapabilityDetector.detect()` à partir de
     * `ArCoreApk.checkAvailability().isSupported`, qui reste vrai même si ARCore n'est pas encore
     * installé (seulement compatible) -- la création de session peut donc légitimement échouer ici
     * alors même que le palier OPTIMAL a été choisi.
     */
    private val onUnavailable: (String) -> Unit,
    initialTargetFps: Int = 30,
) : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "ArCoreHeadPoseTracker"

        // Même logique que CameraController.MAX_TRACKED_BITMAPS : nombre d'images caméra "en vol"
        // (transmises à MediaPipe, résultat pas encore reçu) toléré avant de laisser tomber une
        // frame plutôt que d'accumuler des `Image` non fermées -- ARCore limite en interne le
        // nombre d'images CPU acquises simultanément, les garder ouvertes trop longtemps ferait
        // échouer les acquisitions suivantes.
        private const val MAX_TRACKED_IMAGES = 2
    }

    @Volatile private var session: Session? = null
    @Volatile private var cameraTextureId: Int = 0

    @Volatile private var targetFps: Int = initialTargetFps.coerceAtLeast(1)
    @Volatile private var minFrameIntervalMs: Long = 1000L / targetFps
    private var lastAcceptedFrameElapsedMs: Long = 0L

    // Une `Image` ARCore n'est pas réutilisable (contrairement aux bitmaps de CameraController) --
    // uniquement suivie ici pour être fermée au bon moment, jamais remise dans un pool de libres.
    private val poolLock = Any()
    private val inFlightImages = HashMap<Long, Image>()

    /**
     * À appeler avec le [GLSurfaceView] hébergé côté Compose (voir `MainScreen`). Requis par ARCore
     * (la caméra ne peut être pilotée que via une texture GL, voir `Session#setCameraTextureName`),
     * indépendamment du fait que ce calque soit visuellement affiché ou non.
     */
    fun attachTo(glSurfaceView: GLSurfaceView) {
        glSurfaceView.setEGLContextClientVersion(2)
        glSurfaceView.setRenderer(this)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    /** Ajuste le débit cible à chaud, même principe que `CameraController.setTargetFps`. */
    fun setTargetFps(fps: Int) {
        targetFps = fps.coerceAtLeast(1)
        minFrameIntervalMs = 1000L / targetFps
    }

    /** À appeler sur ON_START (voir `MainViewModel.initializeTracking`). */
    fun start() {
        val currentSession = session ?: tryCreateSession()?.also {
            session = it
            maybeBindCameraTexture()
        } ?: return
        try {
            currentSession.resume()
        } catch (e: CameraNotAvailableException) {
            onError("Caméra déjà utilisée par une autre app, impossible de démarrer ARCore.")
        }
    }

    /** À appeler sur ON_STOP. */
    fun stop() {
        session?.pause()
    }

    /** À appeler à la fermeture du ViewModel (voir `MainViewModel.onCleared`). */
    fun close() {
        synchronized(poolLock) {
            inFlightImages.values.forEach { it.close() }
            inFlightImages.clear()
        }
        session?.close()
        session = null
    }

    /**
     * À appeler une fois que MediaPipe a terminé de traiter l'image portant ce timestamp -- même
     * mécanique que `CameraController.releaseFrame`, câblée sur le même callback
     * `FaceLandmarkerHelper.onFrameProcessed`. Contrairement au pool de bitmaps, l'image n'est pas
     * réutilisée : elle est simplement fermée (`Image#close()`), obligatoire côté ARCore pour
     * libérer la place et permettre l'acquisition de la suivante.
     */
    fun releaseFrame(frameTimeMs: Long) {
        val image = synchronized(poolLock) { inFlightImages.remove(frameTimeMs) } ?: return
        image.close()
    }

    private fun tryCreateSession(): Session? {
        return try {
            val newSession = Session(context)

            // Augmented Faces exige la caméra frontale -- la configuration caméra par défaut d'une
            // Session ARCore n'est pas garantie de l'être, doit être sélectionnée explicitement
            // avant configure()/resume() (séquence documentée dans les échantillons officiels ARCore).
            val cameraConfigFilter = CameraConfigFilter(newSession)
                .setFacingDirection(CameraConfig.FacingDirection.FRONT)
            val frontCameraConfigs = newSession.getSupportedCameraConfigs(cameraConfigFilter)
            if (frontCameraConfigs.isEmpty()) {
                newSession.close()
                onUnavailable("Aucune configuration caméra frontale compatible ARCore sur cet appareil.")
                return null
            }
            newSession.setCameraConfig(frontCameraConfigs[0])

            val config = Config(newSession)
            config.setAugmentedFaceMode(Config.AugmentedFaceMode.MESH3D)
            newSession.configure(config)

            newSession
        } catch (e: UnavailableArcoreNotInstalledException) {
            onUnavailable("ARCore n'est pas installé sur cet appareil.")
            null
        } catch (e: UnavailableApkTooOldException) {
            onUnavailable("Version d'ARCore installée trop ancienne.")
            null
        } catch (e: UnavailableSdkTooOldException) {
            onUnavailable("Cette version de l'app nécessite une mise à jour ARCore plus récente.")
            null
        } catch (e: UnavailableDeviceNotCompatibleException) {
            onUnavailable("Cet appareil n'est pas compatible ARCore.")
            null
        } catch (e: UnavailableUserDeclinedInstallationException) {
            onUnavailable("Installation d'ARCore refusée.")
            null
        } catch (e: Exception) {
            // Filet de sécurité générique -- notamment le cas où la caméra frontale ne supporte pas
            // Augmented Faces sur cet appareil précis (device ARCore-compatible mais sans face
            // mode) : ARCore ne garantit pas un type d'exception dédié distinct pour ce cas selon
            // les versions du SDK. Toujours traité comme un repli, jamais un crash.
            Log.e(TAG, "Session ARCore/Augmented Faces indisponible", e)
            onUnavailable("Configuration ARCore Augmented Faces refusée par cet appareil (${e.message}).")
            null
        }
    }

    private fun maybeBindCameraTexture() {
        val currentSession = session ?: return
        if (cameraTextureId == 0) return
        currentSession.setCameraTextureName(cameraTextureId)
    }

    // --- GLSurfaceView.Renderer : appelé depuis le thread GL dédié créé par GLSurfaceView, donc
    // TOUJOURS différent du thread principal -- même situation déjà gérée ailleurs dans l'app
    // (FaceLandmarkerHelper.onLiveStreamResult tourne aussi sur son propre thread de callback),
    // aucune nouvelle classe de risque introduite ici. ---

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        cameraTextureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        maybeBindCameraTexture()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val currentSession = session ?: return
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // session.update() doit être appelé à chaque onDrawFrame pour garder la session ARCore
        // synchronisée (recommandation ARCore) -- seul le travail EN AVAL (transmission à
        // MediaPipe, extraction de pose) est soumis au débit cible du palier, comme pour
        // CameraController.processFrame.
        val frame = try {
            currentSession.update()
        } catch (e: CameraNotAvailableException) {
            onError("Caméra indisponible pour ARCore : ${e.message}")
            return
        }

        val nowElapsed = SystemClock.elapsedRealtime()
        if (nowElapsed - lastAcceptedFrameElapsedMs < minFrameIntervalMs) return
        lastAcceptedFrameElapsedMs = nowElapsed

        emitCameraImage(frame)
        emitHeadPose(currentSession)
    }

    private fun emitCameraImage(frame: com.google.ar.core.Frame) {
        val image = try {
            frame.acquireCameraImage()
        } catch (e: NotYetAvailableException) {
            return
        } catch (e: Exception) {
            Log.w(TAG, "Échec d'acquisition de l'image caméra ARCore", e)
            return
        }

        val inFlightCount = synchronized(poolLock) { inFlightImages.size }
        if (inFlightCount >= MAX_TRACKED_IMAGES) {
            image.close()
            return
        }

        val mpImage = try {
            MediaImageBuilder(image).build()
        } catch (e: Exception) {
            Log.e(TAG, "Échec de conversion Image ARCore -> MPImage", e)
            image.close()
            return
        }

        val frameTimeMs = SystemClock.uptimeMillis()
        synchronized(poolLock) { inFlightImages[frameTimeMs] = image }
        onFrame(mpImage, frameTimeMs)
    }

    private fun emitHeadPose(session: Session) {
        val candidates = session.getAllTrackables(AugmentedFace::class.java).map { face ->
            ArCoreFaceCandidate(
                isTracking = face.trackingState == TrackingState.TRACKING,
                rotationQuaternion = face.centerPose.rotationQuaternion,
            )
        }
        val primary = ArCoreFaceSelector.pickPrimary(candidates) ?: return
        val q = primary.rotationQuaternion
        val matrix = RotationMath.rotation3x3FromQuaternion(q[0], q[1], q[2], q[3])
        onHeadPoseRotationMatrix(matrix)
    }
}
