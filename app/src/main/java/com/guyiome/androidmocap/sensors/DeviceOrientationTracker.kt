package com.guyiome.androidmocap.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.guyiome.androidmocap.tracking.RotationMath

/**
 * Lit l'orientation physique du téléphone en continu via le capteur fusionné
 * TYPE_GAME_ROTATION_VECTOR (accéléromètre + gyroscope, sans magnétomètre -- on n'a besoin que de
 * la rotation *relative* du téléphone dans l'espace, pas du nord magnétique, ce qui évite les
 * sautes près de composants électroniques/aimants comme les enceintes).
 *
 * Sert à distinguer "la tête a bougé" de "le téléphone/support a bougé" : MediaPipe calcule la
 * pose de la tête relative à la caméra, donc si on incline/tourne le téléphone sans bouger la
 * tête, MediaPipe voit quand même une rotation. On expose ici la rotation du téléphone comme une
 * MATRICE (pas des angles déjà décomposés) pour que MainViewModel la compose avec la pose de tête
 * en espace matriciel -- voir [RotationMath] pour l'explication de pourquoi c'est nécessaire.
 */
class DeviceOrientationTracker(context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "DeviceOrientationTracker"
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    /** Matrice de rotation 3x3 row-major du téléphone dans son repère propre, mise à jour en continu. */
    @Volatile
    private var rotationMatrix: FloatArray = RotationMath.IDENTITY_3X3.copyOf()

    fun start() {
        val sensor = rotationSensor
        if (sensor == null) {
            Log.w(TAG, "TYPE_GAME_ROTATION_VECTOR indisponible -- compensation de rotation du téléphone désactivée.")
            return
        }
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /** Capture la matrice de rotation courante, à conserver comme référence de calibration. */
    fun snapshotRotationMatrix(): FloatArray = rotationMatrix.copyOf()

    /**
     * Rotation du téléphone depuis [reference] (matrice 3x3 row-major, capturée à la calibration),
     * exprimée dans le repère du téléphone à cet instant-là -- càd "de combien et autour de quel
     * axe le téléphone a-t-il tourné depuis la calibration, vu depuis lui-même". C'est cette
     * matrice qu'il faut composer (multiplier) avec la pose de tête brute, PAS soustraire des
     * angles d'Euler déjà décomposés (non valide pour des rotations un peu importantes).
     */
    fun rotationDeltaMatrix(reference: FloatArray): FloatArray =
        RotationMath.multiply(RotationMath.transpose(reference), rotationMatrix)

    override fun onSensorChanged(event: SensorEvent) {
        val matrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        rotationMatrix = matrix
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
