package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Couvre [earOpenness], [correctBlinkScore] et [correctEyeBlinkScores] -- purs, testables en JVM.
 * Le lissage temporel lui-même ([OneEuroFilter]) est testé séparément dans
 * `OneEuroFilterTest.kt` -- ici on vérifie seulement son intégration dans le pipeline eyeBlink.
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
    fun `correctEyeBlinkScores garde une fermeture tenue face a une derive realiste`() {
        // Bout en bout de la régression : œil gauche fermé au départ, l'EAR dérive frame après
        // frame vers l'ouvert (dérive de landmarks simulée) au même rythme que celui mesuré
        // réellement sur device (~9s pour un effondrement complet côté ancien lissage sans
        // mémoire, protocole 18 pas de 500ms) -- le score brut reste constant à 0,6 ; à mi-parcours
        // (~4,5s), la version corrigée ne doit pas encore s'être effondrée à 0.
        var state = EyeBlinkCorrectionState()
        val blendshapes = listOf(BlendshapeScore("eyeBlinkLeft", 0.6f), BlendshapeScore("eyeBlinkRight", 0.1f))
        val steps = 18
        var correctedAtMidpoint = 0f
        for (i in 1..steps) {
            val leftOpenFraction = 0.05f + (0.20f - 0.05f) * i / steps // dérive verticale progressive
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
            if (i == steps / 2) correctedAtMidpoint = corrected.first { it.name == "eyeBlinkLeft" }.score
        }
        assertTrue(
            "la fermeture tenue ne devrait pas déjà s'être effondrée à 0 à mi-parcours (valeur=$correctedAtMidpoint)",
            correctedAtMidpoint > 0.05f,
        )
    }

    // --- AdaptiveEarFloor intégré : régression du clignement droit écrasé à angle de caméra
    //     inhabituel (revue technique, cas mesuré sur device le 9 août 2026) ---

    private fun rightEyeLandmarksWithGap(openFraction: Float): List<Pair<Float, Float>> {
        val landmarks = MutableList(500) { 0f to 0f }
        val indices = EyeLandmarkIndices.RIGHT_EYE
        landmarks[indices.cornerNear] = 0f to 0.5f
        landmarks[indices.cornerFar] = 1f to 0.5f
        landmarks[indices.upperNear] = 0.25f to (0.5f - openFraction)
        landmarks[indices.lowerNear] = 0.25f to (0.5f + openFraction)
        landmarks[indices.upperFar] = 0.75f to (0.5f - openFraction)
        landmarks[indices.lowerFar] = 0.75f to (0.5f + openFraction)
        return landmarks
    }

    @Test
    fun `un clignement droit reel a angle inhabituel s'ameliore apres quelques episodes`() {
        // Reproduit le cas mesuré sur device : à cet angle, l'EAR d'un œil droit réellement fermé
        // ne descend qu'à ~0,165 d'ouverture (EAR ~0,33) au lieu des ~0,05 (EAR ~0,10) habituels --
        // trop proche de la référence "ouvert" (0,175, EAR ~0,35) pour que la référence fixe
        // laisse passer la correction. Ici, plusieurs clignements identiques doivent faire
        // converger la correction vers le brut, pas juste le tout premier.
        var state = EyeBlinkCorrectionState()
        val closedBlendshapes = listOf(BlendshapeScore("eyeBlinkLeft", 0.05f), BlendshapeScore("eyeBlinkRight", 0.55f))
        val openBlendshapes = listOf(BlendshapeScore("eyeBlinkLeft", 0.05f), BlendshapeScore("eyeBlinkRight", 0.05f))
        val closedLandmarks = rightEyeLandmarksWithGap(0.165f)
        val openLandmarks = rightEyeLandmarksWithGap(0.175f)

        fun blink(): Float {
            val (corrected, nextState) = correctEyeBlinkScores(closedBlendshapes, closedLandmarks, state, elapsedMs = 150)
            state = nextState
            val result = corrected.first { it.name == "eyeBlinkRight" }.score
            val (_, reopenedState) = correctEyeBlinkScores(openBlendshapes, openLandmarks, state, elapsedMs = 150)
            state = reopenedState
            return result
        }

        val firstBlinkCorrected = blink()
        repeat(5) { blink() }
        val laterBlinkCorrected = blink()

        assertTrue(
            "le tout premier clignement à cet angle doit encore être fortement atténué par la référence fixe de départ, valeur=$firstBlinkCorrected",
            firstBlinkCorrected < 0.15f,
        )
        assertTrue(
            "après plusieurs clignements identiques, la correction doit nettement s'améliorer, valeur=$laterBlinkCorrected",
            laterBlinkCorrected > firstBlinkCorrected + 0.15f,
        )
    }
}
