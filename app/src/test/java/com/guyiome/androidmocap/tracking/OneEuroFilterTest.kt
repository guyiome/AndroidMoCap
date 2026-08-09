package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Couvre [OneEuroFilter] -- pur, testable en JVM. */
class OneEuroFilterTest {

    private val delta = 0.001f

    @Test
    fun `valeur initiale est nulle avant tout echantillon`() {
        assertNull(OneEuroFilter().value)
    }

    @Test
    fun `premier echantillon traverse sans lissage`() {
        val filter = OneEuroFilter().next(0.42f, elapsedMs = 33)
        assertEquals(0.42f, filter.value!!, delta)
    }

    @Test
    fun `elapsedMs invalide traverse sans lissage`() {
        val filter = OneEuroFilter(value = 0.1f).next(0.9f, elapsedMs = 0)
        assertEquals(0.9f, filter.value!!, delta)
    }

    @Test
    fun `signal constant reste stable`() {
        var filter = OneEuroFilter(minCutoff = 1f)
        repeat(20) { filter = filter.next(0.5f, elapsedMs = 33) }
        assertEquals(0.5f, filter.value!!, delta)
    }

    @Test
    fun `un saut brusque isole est amorti (coupure minimale)`() {
        // Avec un beta nul, la coupure reste à minCutoff quelle que soit la vitesse -- un unique
        // saut de bruit doit rester nettement amorti, pas suivi tel quel.
        var filter = OneEuroFilter(minCutoff = 1f, beta = 0f, value = 0.1f, derivative = 0f)
        filter = filter.next(0.9f, elapsedMs = 33)
        assertTrue("le saut ne devrait pas être suivi tel quel (valeur=${filter.value})", filter.value!! < 0.5f)
    }

    @Test
    fun `beta plus eleve reduit le retard sur un mouvement rapide et soutenu`() {
        // Même minCutoff, deux beta différents, même rampe en entrée -- celui avec le beta le
        // plus élevé doit avoir rattrapé une cible qui continue de monter davantage.
        val target = 0.9f
        var lowBeta = OneEuroFilter(minCutoff = 1f, beta = 0f, value = 0.1f)
        var highBeta = OneEuroFilter(minCutoff = 1f, beta = 20f, value = 0.1f)
        repeat(10) {
            lowBeta = lowBeta.next(target, elapsedMs = 33)
            highBeta = highBeta.next(target, elapsedMs = 33)
        }
        assertTrue(
            "beta élevé (${highBeta.value}) devrait avoir moins de retard que beta nul (${lowBeta.value})",
            highBeta.value!! > lowBeta.value!!,
        )
    }

    @Test
    fun `mincutoff plus bas lisse davantage un signal bruite au repos`() {
        // Un signal qui oscille autour d'une moyenne (bruit de capteur typique) -- une coupure
        // minimale plus basse doit produire une sortie dont l'amplitude d'oscillation est plus
        // faible (plus de lissage).
        val noisyInputs = listOf(0.10f, 0.14f, 0.09f, 0.15f, 0.08f, 0.13f, 0.11f, 0.16f, 0.07f, 0.12f)

        fun runFilter(minCutoff: Float): List<Float> {
            var filter = OneEuroFilter(minCutoff = minCutoff, beta = 0f)
            return noisyInputs.map { input ->
                filter = filter.next(input, elapsedMs = 33)
                filter.value!!
            }
        }

        val outputsLowCutoff = runFilter(minCutoff = 0.5f)
        val outputsHighCutoff = runFilter(minCutoff = 50f)

        fun amplitude(values: List<Float>) = values.max() - values.min()

        assertTrue(
            "coupure basse (amplitude=${amplitude(outputsLowCutoff)}) devrait lisser plus que coupure haute (amplitude=${amplitude(outputsHighCutoff)})",
            amplitude(outputsLowCutoff) < amplitude(outputsHighCutoff),
        )
    }

    @Test
    fun `converge vers une cible tenue suffisamment longtemps`() {
        var filter = OneEuroFilter(minCutoff = 1f, beta = 0f, value = 0f)
        repeat(200) { filter = filter.next(0.7f, elapsedMs = 33) }
        assertEquals(0.7f, filter.value!!, 0.01f)
    }
}
