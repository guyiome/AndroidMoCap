package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

/** Couvre [mirrorBlendshapeName] et [mirrorBlendshapes] -- purs, testables en JVM. */
class MirroringTest {

    private val delta = 0.001f

    @Test
    fun `mirrorBlendshapeName echange le suffixe Left-Right`() {
        assertEquals("eyeBlinkRight", mirrorBlendshapeName("eyeBlinkLeft"))
        assertEquals("eyeBlinkLeft", mirrorBlendshapeName("eyeBlinkRight"))
    }

    @Test
    fun `mirrorBlendshapeName laisse inchange un nom sans variante gauche-droite`() {
        assertEquals("jawOpen", mirrorBlendshapeName("jawOpen"))
        assertEquals("browInnerUp", mirrorBlendshapeName("browInnerUp"))
        assertEquals("tongueOut", mirrorBlendshapeName("tongueOut"))
    }

    @Test
    fun `mirrorBlendshapeName gere les noms a mot unique jaw-mouth`() {
        assertEquals("jawRight", mirrorBlendshapeName("jawLeft"))
        assertEquals("mouthRight", mirrorBlendshapeName("mouthLeft"))
    }

    @Test
    fun `mirrorBlendshapes echange les scores entre paires`() {
        val input = listOf(
            BlendshapeScore("eyeBlinkLeft", 0.8f),
            BlendshapeScore("eyeBlinkRight", 0.1f),
        )
        val mirrored = mirrorBlendshapes(input)
        val byName = mirrored.associate { it.name to it.score }
        assertEquals(0.1f, byName.getValue("eyeBlinkLeft"), delta)
        assertEquals(0.8f, byName.getValue("eyeBlinkRight"), delta)
    }

    @Test
    fun `mirrorBlendshapes laisse inchange un blendshape sans paire`() {
        val input = listOf(BlendshapeScore("jawOpen", 0.42f))
        val mirrored = mirrorBlendshapes(input)
        assertEquals(0.42f, mirrored.single().score, delta)
    }

    @Test
    fun `mirrorBlendshapes applique deux fois redonne les scores d'origine`() {
        val input = listOf(
            BlendshapeScore("mouthSmileLeft", 0.6f),
            BlendshapeScore("mouthSmileRight", 0.2f),
            BlendshapeScore("jawOpen", 0.5f),
        )
        val twice = mirrorBlendshapes(mirrorBlendshapes(input))
        val byName = twice.associate { it.name to it.score }
        assertEquals(0.6f, byName.getValue("mouthSmileLeft"), delta)
        assertEquals(0.2f, byName.getValue("mouthSmileRight"), delta)
        assertEquals(0.5f, byName.getValue("jawOpen"), delta)
    }

    @Test
    fun `mirrorFaceTrackingResult mirrore blendshapes, tete et regard ensemble`() {
        val result = FaceTrackingResult(
            faceDetected = true,
            blendshapes = listOf(BlendshapeScore("eyeBlinkLeft", 0.9f), BlendshapeScore("eyeBlinkRight", 0.05f)),
            inferenceTimeMs = 10L,
            timestampMs = 1000L,
            headEulerDegrees = floatArrayOf(5f, 20f, -8f),
            leftEyeEulerDegrees = floatArrayOf(3f, 15f, 0f),
            rightEyeEulerDegrees = floatArrayOf(-2f, -10f, 0f),
        )
        val mirrored = mirrorFaceTrackingResult(result)

        val byName = mirrored.blendshapes.associate { it.name to it.score }
        assertEquals(0.05f, byName.getValue("eyeBlinkLeft"), delta)
        assertEquals(0.9f, byName.getValue("eyeBlinkRight"), delta)

        assertEquals(5f, mirrored.headEulerDegrees[0], delta) // pitch inchangé
        assertEquals(-20f, mirrored.headEulerDegrees[1], delta) // yaw inversé
        assertEquals(8f, mirrored.headEulerDegrees[2], delta) // roll inversé

        // Regard : gauche/droite échangés ET chacun mirroré (yaw inversé).
        assertEquals(-2f, mirrored.leftEyeEulerDegrees[0], delta)
        assertEquals(10f, mirrored.leftEyeEulerDegrees[1], delta)
        assertEquals(3f, mirrored.rightEyeEulerDegrees[0], delta)
        assertEquals(-15f, mirrored.rightEyeEulerDegrees[1], delta)
    }

    @Test
    fun `mirrorBlendshapes couvre bien toutes les paires du vrai catalogue`() {
        // Garde-fou : construit une entrée par nom du catalogue avec un score = son index (valeurs
        // toutes différentes), applique le miroir, et vérifie que chaque paire Left/Right a
        // effectivement échangé -- attrape une régression si BlendshapeCatalog évolue avec une
        // nouvelle paire mal nommée (suffixe différent de Left/Right).
        val input = BlendshapeCatalog.all.mapIndexed { index, (_, name) -> BlendshapeScore(name, index.toFloat()) }
        val originalByName = input.associate { it.name to it.score }
        val mirrored = mirrorBlendshapes(input).associate { it.name to it.score }
        for (name in originalByName.keys) {
            val counterpart = mirrorBlendshapeName(name)
            val expected = originalByName.getValue(counterpart)
            assertEquals("mismatch pour $name (attendu la valeur de $counterpart)", expected, mirrored.getValue(name), delta)
        }
    }
}
