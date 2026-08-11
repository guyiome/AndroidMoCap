package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Couvre [rgbToHsv], [isTongueColoredPixel], [tonguePixelRatio], [colorGateOpen] -- purs, JVM. */
class MouthColorAnalysisTest {

    private val delta = 0.01f

    private fun argb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun `rgbToHsv rouge pur donne teinte 0 saturation et valeur maximales`() {
        val hsv = rgbToHsv(255, 0, 0)
        assertEquals(0f, hsv[0], delta)
        assertEquals(1f, hsv[1], delta)
        assertEquals(1f, hsv[2], delta)
    }

    @Test
    fun `rgbToHsv vert pur donne teinte 120`() {
        val hsv = rgbToHsv(0, 255, 0)
        assertEquals(120f, hsv[0], delta)
        assertEquals(1f, hsv[1], delta)
        assertEquals(1f, hsv[2], delta)
    }

    @Test
    fun `rgbToHsv bleu pur donne teinte 240`() {
        val hsv = rgbToHsv(0, 0, 255)
        assertEquals(240f, hsv[0], delta)
        assertEquals(1f, hsv[1], delta)
        assertEquals(1f, hsv[2], delta)
    }

    @Test
    fun `rgbToHsv (255,0,51) donne teinte 348 (bande rose apres enjambement 0-360)`() {
        // max=255(r), min=0(g), delta=255 -> H = 60 * mod6((0-51)/255) = 60 * mod6(-0.2) = 60*5.8 = 348
        val hsv = rgbToHsv(255, 0, 51)
        assertEquals(348f, hsv[0], delta)
        assertEquals(1f, hsv[1], delta)
        assertEquals(1f, hsv[2], delta)
    }

    @Test
    fun `rgbToHsv blanc pur donne saturation zero`() {
        val hsv = rgbToHsv(255, 255, 255)
        assertEquals(0f, hsv[1], delta)
        assertEquals(1f, hsv[2], delta)
    }

    @Test
    fun `rgbToHsv noir pur donne valeur zero`() {
        val hsv = rgbToHsv(0, 0, 0)
        assertEquals(0f, hsv[1], delta)
        assertEquals(0f, hsv[2], delta)
    }

    @Test
    fun `isTongueColoredPixel rouge pur est couleur langue`() {
        assertTrue(isTongueColoredPixel(argb(255, 0, 0)))
    }

    @Test
    fun `isTongueColoredPixel rose sature (348) est couleur langue`() {
        assertTrue(isTongueColoredPixel(argb(255, 0, 51)))
    }

    @Test
    fun `isTongueColoredPixel teinte peau (environ 34) n'est pas couleur langue`() {
        // (224,172,105): max=224(r), min=105(b), delta=119 -> H = 60*mod6((172-105)/119) ~= 33.8,
        // hors des deux bandes (25, 335) malgre saturation/valeur elevees.
        assertFalse(isTongueColoredPixel(argb(224, 172, 105)))
    }

    @Test
    fun `isTongueColoredPixel blanc (dents) n'est pas couleur langue`() {
        assertFalse(isTongueColoredPixel(argb(255, 255, 255)))
    }

    @Test
    fun `isTongueColoredPixel ombre sombre n'est pas couleur langue`() {
        assertFalse(isTongueColoredPixel(argb(20, 20, 20)))
    }

    @Test
    fun `tonguePixelRatio renvoie zero sur un tableau vide`() {
        assertEquals(0f, tonguePixelRatio(IntArray(0)), delta)
    }

    @Test
    fun `tonguePixelRatio 1 quand tous les pixels sont couleur langue`() {
        val pixels = IntArray(10) { argb(255, 0, 0) }
        assertEquals(1f, tonguePixelRatio(pixels), delta)
    }

    @Test
    fun `tonguePixelRatio 0 quand aucun pixel n'est couleur langue`() {
        val pixels = IntArray(10) { argb(224, 172, 105) }
        assertEquals(0f, tonguePixelRatio(pixels), delta)
    }

    @Test
    fun `tonguePixelRatio 0,5 sur un melange moitie-moitie`() {
        val pixels = IntArray(10) { i -> if (i < 5) argb(255, 0, 0) else argb(224, 172, 105) }
        assertEquals(0.5f, tonguePixelRatio(pixels), delta)
    }

    @Test
    fun `colorGateOpen ferme sous le seuil`() {
        assertFalse(colorGateOpen(0.05f, threshold = 0.12f))
    }

    @Test
    fun `colorGateOpen ouvert pile au seuil`() {
        assertTrue(colorGateOpen(0.12f, threshold = 0.12f))
    }

    @Test
    fun `colorGateOpen ouvert au-dessus du seuil`() {
        assertTrue(colorGateOpen(0.5f, threshold = 0.12f))
    }
}
