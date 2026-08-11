package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Couvre [TongueColorBaseline.next] et [TongueColorBaseline.isElevated] -- purs, testables en JVM. */
class TongueColorBaselineTest {

    private val delta = 0.0001f

    @Test
    fun `premier echantillon fixe directement la reference`() {
        val state = TongueColorBaseline().next(0.85f)
        assertEquals(0.85f, state.value, delta)
        assertEquals(1, state.sampleCount)
    }

    @Test
    fun `un ratio dans la zone normale fait deriver la reference par EMA`() {
        // value=0.85 -> +0.02 marge = 0.87 ; 0.86 <= 0.87 -> zone normale
        // nouvelle valeur = 0.85 + 0.05*(0.86-0.85) = 0.8505
        val state = TongueColorBaseline().next(0.85f).next(0.86f, elevatedMargin = 0.02f, alpha = 0.05f)
        assertEquals(0.8505f, state.value, delta)
        assertEquals(2, state.sampleCount)
    }

    @Test
    fun `un ratio eleve ne fait pas deriver la reference`() {
        val afterNormal = TongueColorBaseline().next(0.85f).next(0.86f, elevatedMargin = 0.02f, alpha = 0.05f)
        // value=0.8505 -> +0.02 marge = 0.8705 ; 0.95 > 0.8705 -> zone elevee, valeur inchangee
        val state = afterNormal.next(0.95f, elevatedMargin = 0.02f, alpha = 0.05f)
        assertEquals(afterNormal.value, state.value, delta)
        assertEquals(3, state.sampleCount)
    }

    @Test
    fun `isElevated faux tant qu'aucun echantillon n'est integre`() {
        assertFalse(TongueColorBaseline().isElevated(0.99f))
    }

    @Test
    fun `isElevated faux sous la marge`() {
        val state = TongueColorBaseline().next(0.85f).next(0.86f, elevatedMargin = 0.02f, alpha = 0.05f)
        assertFalse(state.isElevated(0.86f, margin = 0.02f))
    }

    @Test
    fun `isElevated vrai au-dessus de la marge`() {
        val state = TongueColorBaseline().next(0.85f).next(0.86f, elevatedMargin = 0.02f, alpha = 0.05f)
        assertTrue(state.isElevated(0.95f, margin = 0.02f))
    }
}
