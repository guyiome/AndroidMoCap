package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Couvre [AdaptiveEarFloor] -- pur, testable en JVM. Voir kdoc de `EyeBlinkCorrection.kt` pour le
 * contexte device ayant motivé son ajout (clin d'œil droit réel écrasé par la référence "fermé"
 * fixe, trop basse pour un angle de caméra en contre-plongée, 9 août 2026).
 */
class AdaptiveEarFloorTest {

    private val delta = 0.001f

    @Test
    fun `valeur initiale est la reference fixe historique, aucun episode en cours`() {
        val floor = AdaptiveEarFloor()
        assertEquals(EAR_CLOSED_REFERENCE, floor.value, delta)
        assertNull(floor.episodeMin)
    }

    @Test
    fun `en dessous du seuil d'activite, rien ne change sans episode en cours`() {
        val floor = AdaptiveEarFloor()
        val next = floor.next(ear = 0.05f, rawBlinkScore = 0.1f)
        assertEquals(floor, next)
    }

    @Test
    fun `au-dessus du seuil, l'episode retient le minimum EAR observe`() {
        var floor = AdaptiveEarFloor()
        floor = floor.next(ear = 0.30f, rawBlinkScore = 0.5f)
        assertEquals(0.30f, floor.episodeMin!!, delta)
        // Un EAR encore plus bas doit abaisser le minimum retenu.
        floor = floor.next(ear = 0.20f, rawBlinkScore = 0.6f)
        assertEquals(0.20f, floor.episodeMin!!, delta)
        // Un EAR qui remonte (dérive de landmarks en cours d'épisode, ex. tenue longue) ne doit
        // PAS faire remonter le minimum retenu -- seul le point bas de l'épisode compte.
        floor = floor.next(ear = 0.28f, rawBlinkScore = 0.55f)
        assertEquals(0.20f, floor.episodeMin!!, delta)
    }

    @Test
    fun `la fin de l'episode melange partiellement son minimum dans la reference`() {
        var floor = AdaptiveEarFloor(value = 0.10f)
        floor = floor.next(ear = 0.33f, rawBlinkScore = 0.55f) // épisode : EAR mesuré élevé pour cet angle
        floor = floor.next(ear = 0.05f, rawBlinkScore = 0.1f) // score repasse sous le seuil : fin d'épisode
        val expected = 0.10f + EAR_FLOOR_EPISODE_BLEND * (0.33f - 0.10f)
        assertEquals(expected, floor.value, delta)
        assertNull("l'episode devrait être clos", floor.episodeMin)
    }

    @Test
    fun `plusieurs episodes convergent vers le minimum reel plutot que de sauter au dernier`() {
        // Reproduit le cas mesuré sur device : la vraie référence "fermé" à cet angle est bien
        // plus haute (~0,33) que la constante historique (0,10) -- plusieurs clignements réels
        // doivent faire converger la référence vers cette zone, pas y sauter d'un coup.
        var floor = AdaptiveEarFloor(value = EAR_CLOSED_REFERENCE)
        repeat(6) {
            floor = floor.next(ear = 0.33f, rawBlinkScore = 0.55f) // clignement
            floor = floor.next(ear = 0.35f, rawBlinkScore = 0.1f) // œil rouvert, fin d'épisode
        }
        assertTrue(
            "la reference aurait dû converger près de 0,33, valeur=${floor.value}",
            floor.value > 0.28f,
        )
    }

    @Test
    fun `un episode isole pollue (fuite) ne redefinit pas la reference a lui seul`() {
        var floor = AdaptiveEarFloor(value = EAR_CLOSED_REFERENCE)
        floor = floor.next(ear = 0.34f, rawBlinkScore = 0.46f) // fuite passée au-dessus du seuil
        floor = floor.next(ear = 0.34f, rawBlinkScore = 0.1f) // fin d'épisode
        assertTrue(
            "un seul episode ne devrait deplacer la reference que partiellement, valeur=${floor.value}",
            floor.value < 0.20f,
        )
    }
}
