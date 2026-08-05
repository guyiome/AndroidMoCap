package com.guyiome.androidmocap.tracking

/**
 * Utilitaires de rotation 3x3 (row-major), partagés entre la pose de tête (MediaPipe) et
 * l'orientation du téléphone ([com.guyiome.androidmocap.sensors.DeviceOrientationTracker]).
 *
 * Toutes les compositions (tête + rotation du téléphone + référence de calibration) se font en
 * multipliant des matrices de rotation, et on ne convertit en degrés qu'une seule fois, à la
 * toute fin. C'est plus robuste que soustraire des angles d'Euler déjà décomposés séparément :
 * ceux-ci ne s'additionnent pas linéairement pour des rotations un peu importantes (ordre des
 * axes non commutatif) -- cause probable de l'inversion d'axes observée quand la calibration est
 * faite téléphone à l'horizontale plutôt qu'à la verticale.
 */
object RotationMath {

    val IDENTITY_3X3: FloatArray = floatArrayOf(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 1f,
    )

    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val result = FloatArray(9)
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                var sum = 0f
                for (k in 0 until 3) sum += a[row * 3 + k] * b[k * 3 + col]
                result[row * 3 + col] = sum
            }
        }
        return result
    }

    fun transpose(m: FloatArray): FloatArray = floatArrayOf(
        m[0], m[3], m[6],
        m[1], m[4], m[7],
        m[2], m[5], m[8],
    )

    /** Extrait la sous-matrice de rotation 3x3 row-major depuis la matrice 4x4 column-major de MediaPipe. */
    fun rotation3x3FromColumnMajor4x4(m: FloatArray): FloatArray {
        if (m.size < 16) return IDENTITY_3X3.copyOf()
        // column-major 4x4 : élément (ligne r, colonne c) = m[c*4 + r]
        return floatArrayOf(
            m[0], m[4], m[8],
            m[1], m[5], m[9],
            m[2], m[6], m[10],
        )
    }

    /**
     * [pitch (X), yaw (Y), roll (Z)] en degrés depuis une matrice de rotation 3x3 row-major.
     *
     * Yaw inversé par rapport à la formule brute atan2(r02, r22) -- confirmé empiriquement sur
     * device pour la pose de tête brute. Appliqué une seule fois, sur la matrice finale déjà
     * composée (tête + compensation téléphone + calibration) : pas besoin de deviner si la
     * rotation du téléphone a besoin du même signe, la matrice résultante encode tout.
     */
    fun toEulerDegrees(m: FloatArray): FloatArray {
        val pitch = Math.toDegrees(Math.asin((-m[5]).toDouble().coerceIn(-1.0, 1.0))).toFloat()
        val yaw = -Math.toDegrees(Math.atan2(m[2].toDouble(), m[8].toDouble())).toFloat()
        val roll = Math.toDegrees(Math.atan2(m[3].toDouble(), m[4].toDouble())).toFloat()
        return floatArrayOf(pitch, yaw, roll)
    }

    /**
     * Combine en un seul appel testable les trois étapes faites à chaque frame dans
     * [com.guyiome.androidmocap.ui.MainViewModel.handleTrackingResult] : annuler la rotation du
     * téléphone lui-même depuis la calibration ([deviceDeltaMatrix]), puis exprimer le résultat
     * relatif à la pose de tête de référence capturée à la calibration ([headRotationReference]).
     * Extrait ici (plutôt que laissé inline dans le ViewModel) précisément pour pouvoir couvrir ce
     * calcul -- le plus délicat de l'app, à l'origine de l'inversion d'axes déjà rencontrée -- par
     * des tests unitaires indépendants d'Android.
     */
    fun composeCalibratedEuler(
        headRotationMatrix: FloatArray,
        deviceDeltaMatrix: FloatArray,
        headRotationReference: FloatArray,
    ): FloatArray {
        val headCompensated = multiply(deviceDeltaMatrix, headRotationMatrix)
        val finalRotation = multiply(transpose(headRotationReference), headCompensated)
        return toEulerDegrees(finalRotation)
    }
}
