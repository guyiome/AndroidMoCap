package com.guyiome.androidmocap.tracking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.Image
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.view.Surface
import com.google.ar.core.AugmentedFace
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.SessionPausedException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.guyiome.androidmocap.camera.CameraController
import com.guyiome.androidmocap.logging.AppLog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Source caméra + pose de tête alternative pour le palier [TrackingTier.OPTIMAL], basée sur ARCore
 * Augmented Faces plutôt que CameraX. Historique détaillé des correctifs qui ont mené à l'état actuel (format
 * d'image ARCore/crash `MediaImageBuilder`, déport de la conversion YUV->RGB sur
 * [imageProcessingExecutor], fuite de ressource dans [tryCreateSession], rotation caméra) --
 * volontairement pas reproduit ici en détail (revue de code du 12 août 2026 : ce kdoc avait
 * accumulé un historique d'incidents trop long à parcourir avant d'atteindre le comportement
 * actuel). L'ordre `onPause()`/`session.pause()` dans [stop] a sa propre justification détaillée
 * sur place (crash confirmé sur device, voir son kdoc) -- pas dupliqué ici non plus.
 *
 * ⚠️ **Encore ouvert, non résolu** : un warning natif MediaPipe
 * (`aimatter_landmarks_3Dmesh.cc: Not able to find preprocess rotation with expected timestamp`)
 * apparaît en continu sur ce palier, absent sur `STANDARD` (CameraX) -- tracking reste fonctionnel
 * malgré ce warning (pas de dégradation qualitative observée que par examen visuel). Piste la plus
 * probable, non confirmée : [emitCameraImage] génère `frameTimeMs` après la conversion asynchrone
 * sur [imageProcessingExecutor], donc décalé par rapport à l'instant réel de capture --
 * contrairement à `CameraController`, qui appelle `detectAsync` de façon synchrone sur son propre
 * thread caméra dédié. Ne pas déplacer la génération du timestamp à l'aveugle sans confirmer cette
 * hypothèse en premier (ex. logger la valeur réelle passée à `detectAsync` et la comparer au
 * timestamp du message natif).
 *
 * Raison d'être : ARCore Augmented Faces gère lui-même la capture caméra frontale en interne (via
 * `Session#setCameraTextureName`, qui nécessite un contexte GL) -- il ne peut donc pas cohabiter
 * avec CameraX sur la même caméra (Camera2 n'autorise qu'un seul client actif). Ce tracker pilote
 * donc sa propre caméra (via ARCore) et sert de source de frames pour MediaPipe (toujours utilisé
 * pour les blendshapes, [FaceLandmarkerHelper] ne change pas) ET de source de pose de tête (à la
 * place de `FaceLandmarkerResult#facialTransformationMatrixes()`).
 *
 * Nécessite un contexte GL pour exister (contrainte ARCore, pas un choix) : piloté par un
 * [GLSurfaceView] hébergé côté Compose via [attachTo]. Ce contexte GL sert aussi à afficher un fond
 * caméra live (quad plein écran texturé, [drawCameraBackground]) -- réutilise directement la
 * texture OES qu'ARCore alimente déjà via `Session#setCameraTextureName` pour piloter le tracking,
 * jamais échantillonnée par notre propre code avant ce correctif.
 * Orientation de ce fond : voir kdoc de [backgroundQuadCoords].
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

        // Nombre de conversions YUV->Bitmap tolérées "en vol" (soumises à imageProcessingExecutor,
        // pas encore terminées) avant de laisser tomber une frame plutôt que d'empiler du travail
        // en retard -- même philosophie que CameraController.MAX_TRACKED_BITMAPS. Fixé à 1 (pas 2
        // comme côté CameraX) : demande explicite de l'utilisateur (6 août 2026) de garder les
        // données envoyées au récepteur (blendshapes/pose) prioritaires et à jour plutôt que de
        // traiter coûte que coûte chaque frame caméra -- un retard qui s'accumule sur un thread
        // dédié est pire pour la latence perçue qu'une frame simplement sautée.
        private const val MAX_PENDING_CONVERSIONS = 1

        // Nombre de bitmaps convertis conservés en mémoire (voir lastBitmaps ci-dessous)
        // avant de purger le plus ancien jamais réclamé -- même valeur et même garde-fou que
        // CameraController.MAX_TRACKED_BITMAPS, pour la même raison : éviter une fuite si un frame
        // n'obtient jamais de résultat MediaPipe (releaseFrame() jamais appelé pour lui).
        private const val MAX_TRACKED_BITMAPS = 3
    }

    // Conversion YUV->RGB + rotation déplacées hors du thread GL (onDrawFrame) : faites pixel par
    // pixel, elles ralentissaient à la fois le rendu et la cadence de session.update() -- observé
    // concrètement sur device (retour utilisateur, 6 août 2026, latence perçue nettement réduite
    // après correction de la rotation, mais le principe reste : ce travail n'a aucune raison de
    // bloquer le thread qui pilote ARCore). Un seul thread dédié (pas de parallélisme inutile, une
    // seule caméra) plutôt qu'un pool.
    private val imageProcessingExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val pendingConversions = AtomicInteger(0)

    // Cache du dernier bitmap converti par frame (cascade de détection de la langue
    // tirée, étage 2) -- PAS un pool comme CameraController.inFlightBitmaps : ARCore alloue déjà un
    // Bitmap neuf à chaque frame sans réutilisation (voir emitCameraImage/yuv420ToBitmap), donc rien
    // à recycler ici, juste à conserver un instant de plus au lieu de le laisser sortir de portée
    // dès emitCameraImage() terminé. Écrit depuis imageProcessingExecutor, lu depuis le thread de
    // callback MediaPipe (MainViewModel.handleTrackingResult(), via peekLastBitmap) -- verrou
    // dédié, même précaution que CameraController.poolLock.
    private val bitmapCacheLock = Any()
    private val lastBitmaps = HashMap<Long, Bitmap>()

    @Volatile private var session: Session? = null
    // Mémorisé (pas seulement reçu en paramètre transitoire de attachTo()) pour pouvoir piloter
    // son cycle de vie (onResume()/onPause()) depuis start()/stop() -- voir ces deux méthodes.
    @Volatile private var glSurfaceView: GLSurfaceView? = null
    @Volatile private var cameraTextureId: Int = 0
    // Dimensions du GLSurfaceView, connues seulement une fois onSurfaceChanged() appelé --
    // mémorisées pour pouvoir (re)appliquer setDisplayGeometry() dès qu'une session existe, quel
    // que soit l'ordre d'arrivée entre la création de la session (start()) et celle de la surface
    // GL, même principe que cameraTextureId/maybeBindCameraTexture() ci-dessous.
    @Volatile private var lastKnownSurfaceWidth: Int = 0
    @Volatile private var lastKnownSurfaceHeight: Int = 0

    @Volatile private var targetFps: Int = initialTargetFps.coerceAtLeast(1)
    @Volatile private var minFrameIntervalMs: Long = 1000L / targetFps
    private var lastAcceptedFrameElapsedMs: Long = 0L

    // Rotation (degrés, horaire) à appliquer à l'image YUV brute pour l'amener à l'orientation
    // portrait attendue par MediaPipe -- lue une fois la caméra ARCore sélectionnée (voir
    // tryCreateSession) via Camera2 (CameraCharacteristics.SENSOR_ORIENTATION), l'app étant
    // verrouillée portrait (device rotation toujours ROTATION_0, voir maybeSetDisplayGeometry).
    // Même formule que celle utilisée en interne par CameraX pour une caméra frontale à
    // destination fixe 0° (CameraOrientationUtil.getRelativeImageRotation, isOppositeFacing=true
    // -> rotation relative = orientation du capteur telle quelle) -- sans miroir : le miroir reste
    // géré uniquement à l'affichage (voir LandmarkProjection), jamais dans l'image envoyée à
    // MediaPipe, même convention que CameraController. Confirmé nécessaire sur device (retour
    // utilisateur 6 août 2026 : rotation visiblement absente avant ce correctif) mais formule non
    // vérifiée visuellement -- à confirmer au prochain test (le mesh doit apparaître à l'endroit,
    // tourner la tête doit bouger le mesh dans le sens intuitif).
    @Volatile private var cameraRotationDegrees: Int = 0

    // Réutilisé à chaque appel de rotateBitmap() plutôt que de passer `null` à Canvas.drawBitmap
    // -- même convention que CameraController.rotationPaint (isFilterBitmap = false : rotation
    // par multiples de 90°, aucun intérêt à lisser).
    private val rotationPaint = Paint().apply { isFilterBitmap = false }

    // --- Fond caméra live : quad plein écran texturé avec la texture OES qu'ARCore alimente déjà
    // via setCameraTextureName (voir cameraTextureId/maybeBindCameraTexture ci-dessus) -- jusqu'ici
    // jamais échantillonnée par notre propre code. Aucun pattern GL existant ailleurs dans ce repo
    // (ni shader, ni VBO) : implémentation reprise du pattern de référence Google
    // (`BackgroundRenderer.java`, sample ARCore), adaptée au style de ce fichier. ---

    private var backgroundProgram: Int = 0
    private var backgroundPositionAttrib: Int = 0
    private var backgroundTexCoordAttrib: Int = 0
    private var backgroundTextureUniform: Int = 0

    /**
     * Positions du quad (a_Position), en NDC (-1..1) -- **fixe, hold-independent, jamais modifiée
     * après création**, exactement comme [cameraRotationDegrees] côté CPU (le fond n'a en fait
     * jamais eu besoin de suivre la tenue physique, une hypothèse testée et
     * infirmée sur device le 12 août 2026 -- voir historique git pour le détail complet de cette
     * recherche, y compris un balayage empirique des 8 symétries du carré, piloté par adb, qui a
     * permis d'identifier directement cette valeur après plusieurs échecs de dérivation manuelle).
     *
     * Correspond à une rotation de 90° (sens B, voir historique) du quad standard non tourné
     * (bas-gauche/bas-droite/haut-gauche/haut-droite -> (-1,-1)/(1,-1)/(-1,1)/(1,1)), sans miroir
     * -- confirmée correcte sur device dans les 4 tenues testées (portrait, paysage dans les deux
     * sens, tête en bas), y compris pour la latéralité (main droite affichée à droite, comportement
     * natif/anatomique voulu ici -- **contrairement à** `FaceMeshOverlay`/`LandmarkProjection`, qui
     * mirrorent le mesh pour rester cohérents avec `PreviewView` sur les paliers CameraX ; ce fond
     * ARCore n'a pas cet équivalent à suivre puisqu'il n'existe pas de `PreviewView` sur ce palier).
     */
    private val backgroundQuadCoords = directFloatBuffer(
        floatArrayOf(1f, -1f, 1f, 1f, -1f, -1f, -1f, 1f),
    )

    // UV (a_TexCoord) FIXE elle aussi -- mapping identité standard (bas-gauche->(0,0),
    // bas-droite->(1,0), haut-gauche->(0,1), haut-droite->(1,1)), non transformée : c'est
    // backgroundQuadCoords ci-dessus qui porte la rotation.
    private val backgroundTexCoords = directFloatBuffer(
        floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f),
    )

    // Coupe le dessin du fond sans toucher au reste du pipeline ARCore (session.update(), pose,
    // blendshapes continuent de tourner) -- utilisé en mode éco, même principe que
    // CameraController.previewEnabled/setPreviewEnabled. Actif par défaut, même défaut que
    // previewEnabled.
    @Volatile private var backgroundRenderingEnabled: Boolean = true

    private val backgroundVertexShader = """
        attribute vec4 a_Position;
        attribute vec2 a_TexCoord;
        varying vec2 v_TexCoord;
        void main() {
            gl_Position = a_Position;
            v_TexCoord = a_TexCoord;
        }
    """.trimIndent()

    // samplerExternalOES : seul type d'échantillonneur valide pour une texture OES (celle qu'ARCore
    // alimente via setCameraTextureName) -- nécessite l'extension GL_OES_EGL_image_external, sans
    // quoi le shader échoue à la compilation sur la plupart des GPU Android.
    private val backgroundFragmentShader = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 v_TexCoord;
        uniform samplerExternalOES sTexture;
        void main() {
            gl_FragColor = texture2D(sTexture, v_TexCoord);
        }
    """.trimIndent()

    private fun directFloatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(values); position(0) }

    /**
     * À appeler avec le [GLSurfaceView] hébergé côté Compose (voir `MainScreen`). Requis par ARCore
     * (la caméra ne peut être pilotée que via une texture GL, voir `Session#setCameraTextureName`),
     * indépendamment du fait que ce calque soit visuellement affiché ou non.
     */
    fun attachTo(glSurfaceView: GLSurfaceView) {
        this.glSurfaceView = glSurfaceView
        glSurfaceView.setEGLContextClientVersion(2)
        glSurfaceView.setRenderer(this)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        // Cas où start() a déjà tourné avant que MainScreen ne compose la surface et appelle
        // attachTo() (ordre normal : initializeTracking() appelle start() immédiatement, voir
        // MainViewModel) -- le résumé explicite du thread de rendu n'avait alors pas encore pu se
        // faire faute de GLSurfaceView existant, rattrapé ici. setRenderer() doit précéder
        // onResume() (exigence GLSurfaceView), d'où l'ordre des lignes ci-dessus.
        if (session != null) glSurfaceView.onResume()
    }

    /** Ajuste le débit cible à chaud, même principe que `CameraController.setTargetFps`. */
    fun setTargetFps(fps: Int) {
        targetFps = fps.coerceAtLeast(1)
        minFrameIntervalMs = 1000L / targetFps
    }

    /**
     * Active/désactive le dessin du fond caméra à chaud -- pendant du mode éco, même principe que
     * `CameraController.setPreviewEnabled`. `session.update()` et le reste du pipeline (pose,
     * blendshapes) continuent de tourner : seul le draw call du quad ([drawCameraBackground]) est
     * sauté.
     */
    fun setBackgroundRenderingEnabled(enabled: Boolean) {
        backgroundRenderingEnabled = enabled
    }

    /** À appeler sur ON_START (voir `MainViewModel.initializeTracking`). */
    fun start() {
        val currentSession = session ?: tryCreateSession()?.also {
            session = it
            // Seul log INFO du chemin ARCore (palier OPTIMAL) -- pendant longtemps CameraController
            // (palier STANDARD/COMPATIBLE) était le seul fichier avec un log INFO inconditionnel,
            // laissant le fichier de logs exportable vide sur un device qui tourne sur ARCore même
            // au niveau INFO (constaté sur device).
            AppLog.i(TAG, "Session ARCore/Augmented Faces démarrée")
            maybeBindCameraTexture()
            maybeSetDisplayGeometry()
        } ?: return
        try {
            currentSession.resume()
        } catch (e: CameraNotAvailableException) {
            onError("Caméra déjà utilisée par une autre app, impossible de démarrer ARCore.")
            return
        }
        // Après la session, jamais avant (GLSurfaceView.onResume() a besoin d'un renderer déjà
        // posé -- voir attachTo()). Peut être no-op si la surface n'existe pas encore, rattrapé
        // alors par attachTo() lui-même.
        glSurfaceView?.onResume()
    }

    /**
     * À appeler sur ON_STOP. Ordre important, corrige un crash confirmé sur device (6 août 2026,
     * `SessionPausedException` levée depuis [onDrawFrame] -- `session.update()` appelé après que
     * la session ait déjà été mise en pause) : [GLSurfaceView.onPause] est appelé **avant**
     * `session.pause()`, pour arrêter le thread de rendu (qui appelle `session.update()` à chaque
     * frame) avant de mettre la session elle-même en pause, plutôt que l'inverse -- sans ça, une
     * frame de rendu déjà en cours peut appeler `session.update()` sur une session tout juste mise
     * en pause. [onDrawFrame] garde malgré tout un filet de sécurité (voir son `catch`) : cet ordre
     * réduit la fenêtre de course sans prétendre l'éliminer totalement (`onPause()` n'est pas
     * garanti sur le thread de rendu, purement synchrone).
     */
    fun stop() {
        glSurfaceView?.onPause()
        session?.pause()
    }

    /** À appeler à la fermeture du ViewModel (voir `MainViewModel.onCleared`). */
    fun close() {
        // shutdown() (pas shutdownNow()) : laisse une conversion déjà en cours se terminer
        // proprement plutôt que de l'interrompre en plein milieu -- même convention que
        // CameraController.stop()/cameraExecutor.
        imageProcessingExecutor.shutdown()
        session?.close()
        session = null
        synchronized(bitmapCacheLock) { lastBitmaps.clear() }
    }

    /**
     * L'`Image` ARCore source elle-même est fermée de façon synchrone dans [emitCameraImage] juste
     * après la conversion en `Bitmap`, plus besoin de la garder ouverte jusqu'à ce que MediaPipe ait
     * fini de traiter le frame -- mais depuis l'ajout de la cascade langue tirée (étage 2), le
     * `Bitmap` converti, lui, est retenu dans [lastBitmaps] pour permettre [peekLastBitmap] ; cet
     * appel (routé depuis `MainViewModel.onFrameProcessed`, mutuellement exclusif avec
     * `CameraController.releaseFrame`) le retire de la table une fois que MediaPipe a fini avec ce
     * frame précis.
     */
    fun releaseFrame(frameTimeMs: Long) {
        synchronized(bitmapCacheLock) { lastBitmaps.remove(frameTimeMs) }
    }

    /**
     * Lecture seule d'un bitmap converti encore retenu, SANS le retirer (contrairement à
     * [releaseFrame]) -- même contrat que `CameraController.peekPooledBitmap` : destiné à un accès
     * pixel synchrone et bref depuis `MainViewModel.handleTrackingResult()` (= `onResult`, appelé
     * avant `onFrameProcessed`/`releaseFrame` dans `FaceLandmarkerHelper.onLiveStreamResult()`, de
     * façon synchrone sur le même thread). Ne jamais conserver la référence retournée au-delà de cet
     * appel synchrone.
     */
    fun peekLastBitmap(frameTimeMs: Long): Bitmap? = synchronized(bitmapCacheLock) { lastBitmaps[frameTimeMs] }

    private fun tryCreateSession(): Session? {
        // Hissé hors du try (au lieu d'un val local) pour pouvoir le fermer depuis le catch
        // générique ci-dessous si une étape après Session(context) échoue (relecture du 7 août
        // 2026) -- jusqu'ici seul le chemin "pas de config caméra frontale" fermait
        // explicitement la session en cas d'échec, les autres fuyaient la Session ARCore native.
        var newSession: Session? = null
        return try {
            newSession = Session(context)

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
            cameraRotationDegrees = readCameraSensorOrientation(frontCameraConfigs[0].cameraId)

            val config = Config(newSession)
            config.setAugmentedFaceMode(Config.AugmentedFaceMode.MESH3D)
            newSession.configure(config)

            newSession
        } catch (e: UnavailableArcoreNotInstalledException) {
            // newSession reste null dans ces 5 cas -- Session(context) elle-même est ce qui lève
            // ces exceptions précises (vérification de disponibilité faite à la construction),
            // rien à fermer.
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
            // les versions du SDK. Toujours traité comme un repli, jamais un crash. Contrairement
            // aux 5 catch ci-dessus, newSession peut très bien être non-null ici (échec après
            // Session(context), ex. getSupportedCameraConfigs()/setCameraConfig()/configure()) --
            // à fermer pour ne pas fuir la Session ARCore native.
            AppLog.e(TAG, "Session ARCore/Augmented Faces indisponible", e)
            newSession?.close()
            onUnavailable("Configuration ARCore Augmented Faces refusée par cet appareil (${e.message}).")
            null
        }
    }

    /**
     * Lit `CameraCharacteristics.SENSOR_ORIENTATION` pour la caméra choisie par ARCore -- même
     * source que celle utilisée par CameraX en interne pour calculer sa propre correction de
     * rotation. Repli sur 0° si indisponible (device exotique, permission caméra révoquée entre
     * temps...) plutôt que de planter -- au pire l'image reste dans l'orientation du capteur,
     * comportement identique à avant ce correctif.
     */
    private fun readCameraSensorOrientation(cameraId: String): Int {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraManager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        } catch (e: Exception) {
            AppLog.w(TAG, "Impossible de lire l'orientation du capteur caméra ARCore, repli sur 0°", e)
            0
        }
    }

    private fun maybeBindCameraTexture() {
        val currentSession = session ?: return
        if (cameraTextureId == 0) return
        currentSession.setCameraTextureName(cameraTextureId)
    }

    /**
     * `Session#setDisplayGeometry()` -- jamais appelé avant ce correctif, cause confirmée sur
     * device (logcat utilisateur, 6 août 2026) du warning natif répété en continu "Display
     * geometry has an invalid width: 0". Rotation fixée à [Surface.ROTATION_0] : l'app est
     * verrouillée portrait (voir `AndroidManifest.xml`), pas besoin de lire la rotation
     * d'affichage réelle -- même hypothèse déjà faite ailleurs dans l'app pour la matrice de
     * rotation caméra de `CameraController` (si ce verrouillage
     * change un jour sur tablette).
     */
    private fun maybeSetDisplayGeometry() {
        val currentSession = session ?: return
        if (lastKnownSurfaceWidth == 0 || lastKnownSurfaceHeight == 0) return
        currentSession.setDisplayGeometry(Surface.ROTATION_0, lastKnownSurfaceWidth, lastKnownSurfaceHeight)
    }

    /**
     * Compile et lie le programme GL du fond caméra ([backgroundVertexShader]/
     * [backgroundFragmentShader]) -- une seule fois par surface GL créée ([onSurfaceCreated]),
     * jamais recompilé ensuite. Échec de compilation/liaison journalisé puis le fond reste
     * simplement non dessiné ([backgroundProgram] reste à 0, voir [drawCameraBackground]) plutôt
     * que de planter tout le renderer -- même philosophie de repli silencieux que
     * [maybeBindCameraTexture].
     *
     * Lie aussi les attributs de position/UV ici, une seule fois -- [backgroundQuadCoords]/
     * [backgroundTexCoords] sont des `val` fixes qui ne changent jamais après construction, et
     * [drawCameraBackground] tourne à la cadence de l'écran (jusqu'à 60-120 Hz, pas au débit de
     * tracking) : réémettre `glUseProgram`/`glVertexAttribPointer`/`glEnableVertexAttribArray` à
     * chaque frame serait un travail GPU/driver redondant pour un état qui ne bouge jamais.
     * Seul le programme GL de ce fichier existe dans ce contexte GL (aucun autre code ne touche
     * `glUseProgram`/les tableaux d'attributs génériques) -- rien d'autre ne peut invalider cet
     * état entre deux frames.
     */
    private fun setupBackgroundProgram() {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, backgroundVertexShader)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, backgroundFragmentShader)
        if (vertexShader == 0 || fragmentShader == 0) {
            // Ne pas fuir le shader qui a réussi si l'autre a échoué -- glDeleteShader(0) est un
            // no-op silencieux côté GL, donc sûr même pour celui qui a déjà échoué (compileShader
            // renvoie alors 0).
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            return
        }

        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            AppLog.e(TAG, "Échec de liaison du programme GL du fond caméra : ${GLES20.glGetProgramInfoLog(program)}")
            GLES20.glDeleteProgram(program)
            backgroundProgram = 0 // explicite : repli propre si onSurfaceCreated a déjà tourné une fois
            return
        }

        backgroundProgram = program
        backgroundPositionAttrib = GLES20.glGetAttribLocation(program, "a_Position")
        backgroundTexCoordAttrib = GLES20.glGetAttribLocation(program, "a_TexCoord")
        backgroundTextureUniform = GLES20.glGetUniformLocation(program, "sTexture")

        GLES20.glUseProgram(backgroundProgram)
        GLES20.glVertexAttribPointer(backgroundPositionAttrib, 2, GLES20.GL_FLOAT, false, 0, backgroundQuadCoords)
        GLES20.glEnableVertexAttribArray(backgroundPositionAttrib)
        GLES20.glVertexAttribPointer(backgroundTexCoordAttrib, 2, GLES20.GL_FLOAT, false, 0, backgroundTexCoords)
        GLES20.glEnableVertexAttribArray(backgroundTexCoordAttrib)
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] != GLES20.GL_TRUE) {
            AppLog.e(TAG, "Échec de compilation du shader fond caméra (type=$type) : ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    // Programme + attributs de position/UV déjà liés une seule fois dans setupBackgroundProgram()
    // (voir son kdoc) -- il ne reste ici que ce qui change réellement à chaque frame : la
    // texture OES (son CONTENU est rafraîchi par ARCore via session.update(), pas son ID) et le
    // draw call lui-même.
    private fun drawCameraBackground() {
        if (backgroundProgram == 0) return // compilation/liaison a échoué, voir setupBackgroundProgram

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glUniform1i(backgroundTextureUniform, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
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
        setupBackgroundProgram()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        lastKnownSurfaceWidth = width
        lastKnownSurfaceHeight = height
        maybeSetDisplayGeometry()
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
        } catch (e: SessionPausedException) {
            // Filet de sécurité (crash confirmé sur device, 6 août 2026, voir stop()) : la
            // session a été mise en pause entre le début de cette frame de rendu et cet appel --
            // rien d'anormal, on saute simplement cette frame plutôt que de planter. L'ordre
            // GLSurfaceView.onPause() avant session.pause() dans stop() réduit déjà la fréquence
            // de ce cas, sans l'éliminer totalement (course intrinsèque, pas garantie par l'API).
            return
        }

        // Dessiné à chaque onDrawFrame, pas soumis au throttle targetFps ci-dessous -- la texture
        // OES vient d'être rafraîchie par session.update() ci-dessus, donc toujours à jour ici (le
        // throttle ne gère que le travail EN AVAL, emitCameraImage/emitHeadPose, voir plus haut).
        if (backgroundRenderingEnabled) {
            drawCameraBackground()
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
            AppLog.w(TAG, "Échec d'acquisition de l'image caméra ARCore", e)
            return
        }

        // Contre-pression explicite : si imageProcessingExecutor a déjà MAX_PENDING_CONVERSIONS
        // conversions en cours, cette frame est abandonnée immédiatement plutôt que mise en file --
        // acquireCameraImage() doit rester sur le thread GL (lié à session.update()), mais la suite
        // (conversion YUV->RGB + rotation, coûteuse) est déportée ci-dessous.
        if (pendingConversions.get() >= MAX_PENDING_CONVERSIONS) {
            image.close()
            return
        }
        pendingConversions.incrementAndGet()

        imageProcessingExecutor.execute {
            try {
                // frame.acquireCameraImage() est toujours en YUV_420_888 côté ARCore (pas d'option
                // RGBA comme pour CameraX/ImageAnalysis, voir CameraController) -- MediaPipe exige
                // du RGBA_8888 (voir le crash documenté dans le kdoc de cette classe), d'où la
                // conversion manuelle. L'Image source n'est plus nécessaire une fois ses pixels
                // copiés dans le Bitmap : fermée ici, pas besoin de la garder ouverte jusqu'à ce
                // que MediaPipe ait fini avec le frame (contrairement à l'ancienne version, voir
                // releaseFrame()).
                val bitmap = try {
                    val raw = yuv420ToBitmap(image)
                    if (cameraRotationDegrees == 0) raw else rotateBitmap(raw, cameraRotationDegrees)
                } finally {
                    image.close()
                }

                val mpImage = BitmapImageBuilder(bitmap).build()
                val frameTimeMs = SystemClock.uptimeMillis()
                // Conservé pour peekLastBitmap() (étage 2 de la cascade langue tirée) -- AVANT onFrame() pour que le
                // bitmap soit déjà disponible si MediaPipe répond très vite (peu probable vu le
                // callback asynchrone, mais pas d'hypothèse d'ordonnancement à faire ici).
                synchronized(bitmapCacheLock) {
                    if (lastBitmaps.size >= MAX_TRACKED_BITMAPS) {
                        lastBitmaps.keys.minOrNull()?.let { lastBitmaps.remove(it) }
                    }
                    lastBitmaps[frameTimeMs] = bitmap
                }
                onFrame(mpImage, frameTimeMs)
            } catch (e: Exception) {
                AppLog.e(TAG, "Échec de conversion YUV -> Bitmap de l'image caméra ARCore", e)
            } finally {
                pendingConversions.decrementAndGet()
            }
        }
    }

    /**
     * Convertit une `Image` caméra YUV_420_888 (format renvoyé par ARCore, voir [emitCameraImage])
     * en `Bitmap` ARGB_8888 -- coefficients BT.601 standard, identiques à ceux utilisés par la
     * plupart des conversions YUV->RGB caméra Android. Alloue un nouveau `Bitmap` à chaque appel
     * (pas de pool de réutilisation, voir le risque de performance documenté dans le kdoc de cette
     * classe -- option écartée ici : demanderait de réintroduire un mécanisme de "rendu au pool une
     * fois MediaPipe terminé" comme l'ancienne version de ce fichier avant sa simplification, pour
     * un gain incertain sans pouvoir mesurer sur device). Gère le cas où U et V sont entrelacés
     * (`pixelStride == 2`, format NV21/NV12 courant) comme le cas où ils sont sur des plans séparés
     * (`pixelStride == 1`).
     *
     * Perf (7 août 2026, relecture globale) : chaque plan est copié en bloc dans un `ByteArray`
     * une seule fois ([ByteBuffer.toByteArray]) plutôt que lu pixel par pixel via
     * `ByteBuffer.get(index)` (jusqu'à ~1.5 lecture par pixel × 3 plans, contre 3 transferts en
     * bloc ici) -- même valeurs lues, même formule appliquée, uniquement le mécanisme d'accès qui
     * change.
     */
    private fun yuv420ToBitmap(image: Image): Bitmap {
        val width = image.width
        val height = image.height

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBytes = yPlane.buffer.toByteArray()
        val uBytes = uPlane.buffer.toByteArray()
        val vBytes = vPlane.buffer.toByteArray()

        val pixels = IntArray(width * height)
        for (row in 0 until height) {
            val yRowOffset = row * yPlane.rowStride
            val uvRow = row / 2
            val uRowOffset = uvRow * uPlane.rowStride
            val vRowOffset = uvRow * vPlane.rowStride
            for (col in 0 until width) {
                val y = yBytes[yRowOffset + col].toInt() and 0xFF
                val uvCol = col / 2
                val u = (uBytes[uRowOffset + uvCol * uPlane.pixelStride].toInt() and 0xFF) - 128
                val v = (vBytes[vRowOffset + uvCol * vPlane.pixelStride].toInt() and 0xFF) - 128

                val r = (y + 1.370705f * v).toInt().coerceIn(0, 255)
                val g = (y - 0.337633f * u - 0.698001f * v).toInt().coerceIn(0, 255)
                val b = (y + 1.732446f * u).toInt().coerceIn(0, 255)

                pixels[row * width + col] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Copie l'intégralité d'un `ByteBuffer` (typiquement direct, cas des plans d'`Image` caméra)
     * dans un `ByteArray` neuf, en bloc -- indépendant de la position courante du buffer d'origine
     * ([duplicate] partage le contenu mais a sa propre position/limite, jamais mutée ici) et sans
     * supposer qu'elle vaut déjà 0 ([rewind] avant lecture).
     */
    private fun ByteBuffer.toByteArray(): ByteArray {
        val duplicate = duplicate()
        duplicate.rewind()
        val bytes = ByteArray(duplicate.remaining())
        duplicate.get(bytes)
        return bytes
    }

    /**
     * Applique [rotationDegrees] (horaire) à [source] -- même technique que
     * `CameraController.processFrame()` (`Matrix.postRotate` + remap des bounds pour centrer le
     * résultat), et réutilise directement `CameraController.rotatedDimensions()` (fonction pure
     * déjà testée en JVM) pour les dimensions de sortie plutôt que de dupliquer ce calcul. [source]
     * est recyclé immédiatement après usage : deux `Bitmap` par frame (brut + tourné) sinon,
     * inutile de garder le premier plus longtemps que nécessaire vu le coût mémoire déjà identifié
     * comme risque de performance (voir kdoc de cette classe).
     */
    private fun rotateBitmap(source: Bitmap, rotationDegrees: Int): Bitmap {
        val (outWidth, outHeight) = CameraController.rotatedDimensions(source.width, source.height, rotationDegrees)
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
            val bounds = RectF(0f, 0f, source.width.toFloat(), source.height.toFloat())
            mapRect(bounds)
            postTranslate(-bounds.left, -bounds.top)
        }
        val rotated = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        Canvas(rotated).drawBitmap(source, matrix, rotationPaint)
        source.recycle()
        return rotated
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
