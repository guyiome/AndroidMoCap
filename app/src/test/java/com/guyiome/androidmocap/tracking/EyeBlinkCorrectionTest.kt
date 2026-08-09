package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

/** Couvre [earOpenness], [correctBlinkScore] et [correctEyeBlinkScores] -- purs, testables en JVM. */
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
        // Cas mesuré : vrai clin d'œil, EAR effondré -> openness proche de 0, rien à corriger.
        val corrected = correctBlinkScore(rawScore = 0.65f, openness = 0.1f)
        assertEquals(0.65f, corrected, delta)
    }

    @Test
    fun `correctBlinkScore attenue une fuite (EAR encore tres ouvert)`() {
        // Cas mesuré : fuite pendant le clin d'œil de l'autre côté, EAR à peine descendu -> forte
        // atténuation plutôt qu'un passage tel quel.
        val corrected = correctBlinkScore(rawScore = 0.38f, openness = 0.95f)
        assertEquals(0.38f * (1f - 0.95f), corrected, delta)
    }

    @Test
    fun `correctBlinkScore ne corrige rien pile au seuil`() {
        val corrected = correctBlinkScore(rawScore = 0.5f, openness = EAR_DAMPING_THRESHOLD)
        assertEquals(0.5f, corrected, delta)
    }

    @Test
    fun `correctEyeBlinkScores ne touche qu'aux blendshapes des yeux`() {
        val blendshapes = listOf(
            BlendshapeScore("eyeBlinkLeft", 0.5f),
            BlendshapeScore("eyeBlinkRight", 0.5f),
            BlendshapeScore("jawOpen", 0.7f),
        )
        // Landmarks factices : œil gauche (LEFT_EYE) fermé, œil droit (RIGHT_EYE) grand ouvert.
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
        place(EyeLandmarkIndices.LEFT_EYE, open = false)
        place(EyeLandmarkIndices.RIGHT_EYE, open = true)

        val corrected = correctEyeBlinkScores(blendshapes, landmarks)
        val byName = corrected.associate { it.name to it.score }

        // Œil gauche vraiment fermé (EAR bas) -> score laissé tel quel.
        assertEquals(0.5f, byName.getValue("eyeBlinkLeft"), delta)
        // Œil droit grand ouvert (EAR haut) -> score de fuite fortement atténué.
        assert(byName.getValue("eyeBlinkRight") < 0.1f) { "eyeBlinkRight aurait dû être atténué, valeur=${byName["eyeBlinkRight"]}" }
        // Blendshape non concerné -> inchangé.
        assertEquals(0.7f, byName.getValue("jawOpen"), delta)
    }

    @Test
    fun `correctEyeBlinkScores renvoie les blendshapes inchanges sans landmarks`() {
        val blendshapes = listOf(BlendshapeScore("eyeBlinkLeft", 0.5f))
        assertEquals(blendshapes, correctEyeBlinkScores(blendshapes, emptyList()))
    }
}
