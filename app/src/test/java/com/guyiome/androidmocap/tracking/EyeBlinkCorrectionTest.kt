package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Couvre [earOpenness], [correctBlinkScore], [EyeOpennessSmoother] et [correctEyeBlinkScores] --
 * purs, testables en JVM.
 */
class EyeBlinkCorrectionTest {

    private val delta = 0.001f

    @Test
    fun `earOpenness vaut 1 pour un oeil aussi ouvert que la reference`() {
        assertEquals(1f, earOpenness(EAR_OPEN_REFERENCE), delta)
    }

    @Test
    fun `earOpenness vaut 0 pour un oeil aussi ferme que la reference`() {
        assertEquals(0f, earOpenness(EAR_CLOSED_REFERENCE), delta)
    }

    @Test
    fun `earOpenness reste borne meme au-dela des references`() {
        assertEquals(1f, earOpenness(EAR_OPEN_REFERENCE + 0.5f), delta)
        assertEquals(0f, earOpenness(EAR_CLOSED_REFERENCE - 0.5f), delta)
    }

    @Test
    fun `correctBlinkScore laisse passer une fermeture reelle (EAR bas)`() {
        val corrected = correctBlinkScore(rawScore = 0.65f, openness = 0.1f)
        assertEquals(0.65f, corrected, delta)
    }

    @Test
    fun `correctBlinkScore attenue une fuite (EAR encore tres ouvert)`() {
        val corrected = correctBlinkScore(rawScore = 0.38f, openness = 0.95f)
        assertEquals(0.38f * (1f - 0.95f), corrected, delta)
    }

    @Test
    fun `correctBlinkScore ne corrige rien pile au seuil`() {
        val corrected = correctBlinkScore(rawScore = 0.5f, openness = EAR_DAMPING_THRESHOLD)
        assertEquals(0.5f, corrected, delta)
    }

    // --- EyeOpennessSmoother : le cœur du correctif "reset pendant une tenue longue" ---

    @Test
    fun `EyeOpennessSmoother suit une fermeture instantanement`() {
        val smoother = EyeOpennessSmoother(value = 0.9f)
        val next = smoother.next(instantOpenness = 0.1f, elapsedMs = 33)
        assertEquals(0.1f, next.value, delta)
    }

    @Test
    fun `EyeOpennessSmoother lisse une remontee brusque au lieu de la suivre tout de suite`() {
        // Reproduit le bug mesuré sur device (9 août 2026) : l'œil reste fermé (EAR instantané bas
        // au départ) mais l'EAR dérive vers l'ouvert d'une frame à l'autre -- la valeur lissée ne
        // doit pas sauter directement à l'instantané.
        val smoother = EyeOpennessSmoother(value = 0.1f)
        val next = smoother.next(instantOpenness = 0.9f, elapsedMs = 33)
        assertTrue("la remontée doit être partielle, pas immédiate (valeur=${next.value})", next.value < 0.9f)
        assertTrue("mais elle doit quand même progresser vers le haut", next.value > 0.1f)
    }

    @Test
    fun `EyeOpennessSmoother finit par rattraper une remontee soutenue`() {
        // Sur un temps largement supérieur à la constante de temps (4x ici), la valeur lissée doit
        // se rapprocher de l'instantané (une vraie réouverture doit finir par être crue, pas
        // rester bloquée indéfiniment).
        var smoother = EyeOpennessSmoother(value = 0.1f)
        repeat(60) {
            smoother = smoother.next(instantOpenness = 0.9f, elapsedMs = 200)
        }
        assertTrue("après 12s à 0,9 d'ouverture, le lissage devrait avoir rattrapé (valeur=${smoother.value})", smoother.value > 0.85f)
    }

    @Test
    fun `EyeOpennessSmoother resiste a une remontee soutenue pendant les premieres secondes`() {
        // Reproduit le pire cas du bug mesuré sur device : l'œil reste fermé mais l'EAR instantané
        // bondit vers l'ouvert et y reste -- même après 1s (bien en dessous de la constante de
        // temps), le lissage ne doit pas avoir suivi jusqu'au seuil d'atténuation.
        var smoother = EyeOpennessSmoother(value = 0.15f)
        repeat(2) { smoother = smoother.next(instantOpenness = 0.9f, elapsedMs = 500) }
        assertTrue(
            "la remontée ne devrait pas avoir déjà atteint le seuil d'atténuation après 1s (valeur=${smoother.value})",
            smoother.value < EAR_DAMPING_THRESHOLD,
        )
    }

    // --- correctEyeBlinkScores : orchestration + état porté d'une frame à l'autre ---

    private fun landmarksWith(leftOpen: Boolean, rightOpen: Boolean): List<Pair<Float, Float>> {
        val landmarks = MutableList(500) { 0f to 0f }
        fun place(indices: EyeIndices, open: Boolean) {
            val verticalGap = if (open) 0.3f else 0.02f
            landmarks[indices.cornerNear] = 0f to 0.5f
            landmarks[indices.cornerFar] = 1f to 0.5f
            landmarks[indices.upperNear] = 0.25f to (0.5f - verticalGap)
            landmarks[indices.lowerNear] = 0.25f to (0.5f + verticalGap)
            landmarks[indices.upperFar] = 0.75f to (0.5f - verticalGap)
            landmarks[indices.lowerFar] = 0.75f to (0.5f + verticalGap)
        }
        place(EyeLandmarkIndices.LEFT_EYE, leftOpen)
        place(EyeLandmarkIndices.RIGHT_EYE, rightOpen)
        return landmarks
    }

    @Test
    fun `correctEyeBlinkScores ne touche qu'aux blendshapes des yeux`() {
        val blendshapes = listOf(
            BlendshapeScore("eyeBlinkLeft", 0.5f),
            BlendshapeScore("eyeBlinkRight", 0.5f),
            BlendshapeScore("jawOpen", 0.7f),
        )
        val landmarks = landmarksWith(leftOpen = false, rightOpen = true)

        val (corrected, _) = correctEyeBlinkScores(blendshapes, landmarks, EyeBlinkCorrectionState(), elapsedMs = 33)
        val byName = corrected.associate { it.name to it.score }

        assertEquals(0.5f, byName.getValue("eyeBlinkLeft"), delta)
        assertTrue("eyeBlinkRight aurait dû être atténué, valeur=${byName["eyeBlinkRight"]}", byName.getValue("eyeBlinkRight") < 0.1f)
        assertEquals(0.7f, byName.getValue("jawOpen"), delta)
    }

    @Test
    fun `correctEyeBlinkScores renvoie les blendshapes et l'etat inchanges sans landmarks`() {
        val blendshapes = listOf(BlendshapeScore("eyeBlinkLeft", 0.5f))
        val state = EyeBlinkCorrectionState()
        val (result, resultState) = correctEyeBlinkScores(blendshapes, emptyList(), state, elapsedMs = 33)
        assertEquals(blendshapes, result)
        assertEquals(state, resultState)
    }

    @Test
    fun `correctEyeBlinkScores garde une fermeture tenue meme si l'EAR derive sur les premieres secondes`() {
        // Bout en bout de la régression : œil gauche fermé au départ, l'EAR dérive frame après
        // frame vers l'ouvert (dérive de landmarks simulée, comme mesuré sur device), le score brut
        // reste constant à 0,6 -- sur les 2 premières secondes de dérive, la version corrigée ne
        // doit pas s'être déjà effondrée à 0 comme le faisait l'ancienne version sans lissage.
        var state = EyeBlinkCorrectionState()
        val blendshapes = listOf(BlendshapeScore("eyeBlinkLeft", 0.6f), BlendshapeScore("eyeBlinkRight", 0.1f))
        var lastCorrectedLeft = 0f
        for (i in 1..4) {
            val leftOpenFraction = 0.02f + (0.25f - 0.02f) * i / 4 // dérive verticale progressive
            val landmarks = MutableList(500) { 0f to 0f }
            val indices = EyeLandmarkIndices.LEFT_EYE
            landmarks[indices.cornerNear] = 0f to 0.5f
            landmarks[indices.cornerFar] = 1f to 0.5f
            landmarks[indices.upperNear] = 0.25f to (0.5f - leftOpenFraction)
            landmarks[indices.lowerNear] = 0.25f to (0.5f + leftOpenFraction)
            landmarks[indices.upperFar] = 0.75f to (0.5f - leftOpenFraction)
            landmarks[indices.lowerFar] = 0.75f to (0.5f + leftOpenFraction)
            val (corrected, nextState) = correctEyeBlinkScores(blendshapes, landmarks, state, elapsedMs = 500)
            state = nextState
            lastCorrectedLeft = corrected.first { it.name == "eyeBlinkLeft" }.score
        }
        assertTrue(
            "la fermeture tenue ne devrait pas déjà s'être effondrée à 0 après seulement 2s (valeur=$lastCorrectedLeft)",
            lastCorrectedLeft > 0.05f,
        )
    }
}
