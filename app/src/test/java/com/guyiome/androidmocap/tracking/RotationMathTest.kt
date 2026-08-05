package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Couvre [RotationMath], la classe la plus délicate de l'app (voir son commentaire de tête) --
 * l'inversion d'axes en calibration horizontale venait d'ici. Les matrices de test sont dérivées
 * à la main (formules standard de rotation autour de X/Y/Z), PAS recopiées depuis l'implémentation
 * -- sinon le test ne validerait rien d'autre que "le code fait ce que le code fait".
 */
class RotationMathTest {

    private val delta = 0.01f

    @Test
    fun `multiply par l'identite ne change rien`() {
        val m = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f)
        val result = RotationMath.multiply(RotationMath.IDENTITY_3X3, m)
        for (i in 0 until 9) assertEquals(m[i], result[i], delta)
    }

    @Test
    fun `une matrice de rotation multipliee par sa transposee redonne l'identite`() {
        // Rotation quelconque (30° autour de Z) : une matrice de rotation est orthogonale, donc
        // R * R^T == identité, indépendamment de la formule de conversion en Euler testée à part.
        val theta = Math.toRadians(30.0)
        val c = cos(theta).toFloat()
        val s = sin(theta).toFloat()
        val rotationZ = floatArrayOf(
            c, -s, 0f,
            s, c, 0f,
            0f, 0f, 1f,
        )
        val result = RotationMath.multiply(rotationZ, RotationMath.transpose(rotationZ))
        for (i in 0 until 9) assertEquals(RotationMath.IDENTITY_3X3[i], result[i], delta)
    }

    @Test
    fun `transpose est involutive`() {
        val m = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f)
        val result = RotationMath.transpose(RotationMath.transpose(m))
        for (i in 0 until 9) assertEquals(m[i], result[i], delta)
    }

    @Test
    fun `rotation3x3FromColumnMajor4x4 extrait la bonne sous-matrice`() {
        // 4x4 column-major : élément (ligne r, colonne c) = m[c*4 + r]. On numérote chaque case
        // avec sa position "colonne*4+ligne" pour vérifier que l'extraction prend les bonnes cases.
        val m4x4 = FloatArray(16) { it.toFloat() }
        val result = RotationMath.rotation3x3FromColumnMajor4x4(m4x4)
        // Colonne 0 : lignes 0,1,2 -> indices 0,1,2. Colonne 1 : indices 4,5,6. Colonne 2 : indices 8,9,10.
        val expected = floatArrayOf(
            0f, 4f, 8f,
            1f, 5f, 9f,
            2f, 6f, 10f,
        )
        for (i in 0 until 9) assertEquals(expected[i], result[i], delta)
    }

    @Test
    fun `rotation3x3FromColumnMajor4x4 retombe sur l'identite si la matrice est trop courte`() {
        val result = RotationMath.rotation3x3FromColumnMajor4x4(floatArrayOf(1f, 2f, 3f))
        for (i in 0 until 9) assertEquals(RotationMath.IDENTITY_3X3[i], result[i], delta)
    }

    @Test
    fun `toEulerDegrees de l'identite est zero partout`() {
        val (pitch, yaw, roll) = RotationMath.toEulerDegrees(RotationMath.IDENTITY_3X3)
        assertEquals(0f, pitch, delta)
        assertEquals(0f, yaw, delta)
        assertEquals(0f, roll, delta)
    }

    @Test
    fun `une rotation pure autour de X ne donne que du pitch`() {
        val theta = Math.toRadians(20.0)
        val c = cos(theta).toFloat()
        val s = sin(theta).toFloat()
        val rotationX = floatArrayOf(
            1f, 0f, 0f,
            0f, c, -s,
            0f, s, c,
        )
        val (pitch, yaw, roll) = RotationMath.toEulerDegrees(rotationX)
        assertEquals(20f, pitch, delta)
        assertEquals(0f, yaw, delta)
        assertEquals(0f, roll, delta)
    }

    @Test
    fun `une rotation pure autour de Y ne donne que du yaw, signe inverse`() {
        // Régression : le yaw est documenté comme inversé par rapport à la formule brute
        // atan2(r02, r22), confirmé empiriquement sur device -- ce test fige ce comportement.
        val theta = Math.toRadians(15.0)
        val c = cos(theta).toFloat()
        val s = sin(theta).toFloat()
        val rotationY = floatArrayOf(
            c, 0f, s,
            0f, 1f, 0f,
            -s, 0f, c,
        )
        val (pitch, yaw, roll) = RotationMath.toEulerDegrees(rotationY)
        assertEquals(0f, pitch, delta)
        assertEquals(-15f, yaw, delta)
        assertEquals(0f, roll, delta)
    }

    @Test
    fun `une rotation pure autour de Z ne donne que du roll`() {
        val theta = Math.toRadians(30.0)
        val c = cos(theta).toFloat()
        val s = sin(theta).toFloat()
        val rotationZ = floatArrayOf(
            c, -s, 0f,
            s, c, 0f,
            0f, 0f, 1f,
        )
        val (pitch, yaw, roll) = RotationMath.toEulerDegrees(rotationZ)
        assertEquals(0f, pitch, delta)
        assertEquals(0f, yaw, delta)
        assertEquals(30f, roll, delta)
    }

    @Test
    fun `composeCalibratedEuler sans calibration ni rotation telephone redonne l'euler brut`() {
        val theta = Math.toRadians(20.0)
        val c = cos(theta).toFloat()
        val s = sin(theta).toFloat()
        val head = floatArrayOf(
            1f, 0f, 0f,
            0f, c, -s,
            0f, s, c,
        )
        val result = RotationMath.composeCalibratedEuler(
            headRotationMatrix = head,
            deviceDeltaMatrix = RotationMath.IDENTITY_3X3,
            headRotationReference = RotationMath.IDENTITY_3X3,
        )
        val expected = RotationMath.toEulerDegrees(head)
        for (i in 0 until 3) assertEquals(expected[i], result[i], delta)
    }

    @Test
    fun `composeCalibratedEuler annule la pose courante quand elle sert de reference`() {
        // Calibrer sur la pose actuelle (référence = pose de tête courante) doit ramener l'avatar
        // à zéro, quelle que soit l'inclinaison de la tête au moment de la calibration -- c'est
        // exactement le comportement attendu du bouton "Mise à zéro".
        val theta = Math.toRadians(45.0)
        val c = cos(theta).toFloat()
        val s = sin(theta).toFloat()
        val head = floatArrayOf(
            c, -s, 0f,
            s, c, 0f,
            0f, 0f, 1f,
        )
        val result = RotationMath.composeCalibratedEuler(
            headRotationMatrix = head,
            deviceDeltaMatrix = RotationMath.IDENTITY_3X3,
            headRotationReference = head,
        )
        for (angle in result) assertEquals(0f, angle, delta)
    }
}
