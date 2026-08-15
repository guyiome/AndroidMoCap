package com.guyiome.androidmocap.network

import com.guyiome.androidmocap.tracking.BlendshapeCatalog
import com.guyiome.androidmocap.tracking.BlendshapeScore
import com.guyiome.androidmocap.tracking.FaceTrackingResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Couvre [VBridgerFormulas] : visage neutre (toutes les entrées à 0f) donne les mêmes valeurs de
 * repos que VBridger affiche pour ses formules composites (vérifié à la main dans la revue
 * technique, point 41 -- ex. `MouthSmile` = 0.50), plus quelques cas non-neutres pour les formules
 * les plus verbeuses et le registre de formules lui-même ([rawArkitFormulas]/[activeFormulas]/
 * [evaluate]).
 */
class VBridgerFormulasTest {

    private fun result(blendshapes: List<BlendshapeScore> = emptyList(), headEulerDegrees: FloatArray = floatArrayOf(0f, 0f, 0f)) =
        FaceTrackingResult(
            faceDetected = true,
            blendshapes = blendshapes,
            inferenceTimeMs = 0,
            timestampMs = 0,
            headEulerDegrees = headEulerDegrees,
        )

    private fun composite(name: String) = VBridgerFormulas.vbridgerCompositeFormulas.first { it.outputName == name }

    private fun valueFor(name: String, result: FaceTrackingResult): Float {
        val formula = composite(name)
        val b = result.blendshapes.associate { it.name to it.score }
        return formula.compute(b, result.headEulerDegrees)
    }

    @Test
    fun `rawArkitFormulas contient les 52 blendshapes du catalogue, dont tongueOut`() {
        assertEquals(BlendshapeCatalog.all.size, VBridgerFormulas.rawArkitFormulas.size)
        assertTrue(VBridgerFormulas.rawArkitFormulas.all { it.requiresParameterCreation })
        assertTrue(VBridgerFormulas.rawArkitFormulas.any { it.outputName == "tongueOut" })
    }

    @Test
    fun `vbridgerCompositeFormulas demandent aussi toutes ParameterCreationRequest`() {
        // Le jeu exact de paramètres nativement disponibles côté VTS n'est pas fiable depuis la
        // documentation tierce disponible (voir kdoc de tête du fichier) -- toutes les formules
        // composites tentent donc une création, sans distinction.
        assertTrue(VBridgerFormulas.vbridgerCompositeFormulas.all { it.requiresParameterCreation })
    }

    @Test
    fun `activeFormulas sans traduction retourne uniquement les formules brutes`() {
        val active = VBridgerFormulas.activeFormulas(useVBridgerTranslation = false)
        assertEquals(VBridgerFormulas.rawArkitFormulas, active)
        assertTrue(active.none { it.outputName == "MouthSmile" })
    }

    @Test
    fun `activeFormulas avec traduction retourne uniquement les formules composites -- exclusif, pas additif`() {
        val active = VBridgerFormulas.activeFormulas(useVBridgerTranslation = true)
        assertEquals(VBridgerFormulas.vbridgerCompositeFormulas, active)
        assertTrue(active.none { it.outputName == "jawOpen" }) // aucun blendshape ARKit brut mélangé
    }

    @Test
    fun `evaluate produit un BlendshapeScore par formule, dans l'ordre`() {
        val scores = VBridgerFormulas.evaluate(
            listOf(composite("JawOpen"), composite("MouthFunnel")),
            result(listOf(BlendshapeScore("jawOpen", 0.5f), BlendshapeScore("mouthFunnel", 0.25f))),
        )
        assertEquals(listOf(BlendshapeScore("JawOpen", 0.5f), BlendshapeScore("MouthFunnel", 0.25f)), scores)
    }

    @Test
    fun `FaceAngleX et BodyAngleX suivent directement le lacet, sans inversion redondante`() {
        // headEulerDegrees = (pitch, yaw, roll) -- voir kdoc du fichier. mirrorEulerDegrees (en amont
        // dans MainViewModel) inverse déjà le lacet quand le mode miroir est actif ; une inversion
        // supplémentaire ici annulerait ce miroir -- bug réel corrigé le 15 août 2026 (retour
        // utilisateur : orientation de tête non mirrorée), voir revue technique point 41.
        val turnedYaw = result(headEulerDegrees = floatArrayOf(0f, 10f, 0f))
        assertEquals(10f, valueFor("FaceAngleX", turnedYaw), 0.0001f)
        assertEquals(15f, valueFor("BodyAngleX", turnedYaw), 0.0001f) // x1.5
    }

    @Test
    fun `FaceAngleZ et BodyAngleZ inversent le roulis -- convention d'axe VTS opposee`() {
        // Corrige un retour utilisateur (15 août 2026) : l'inclinaison de tête bougeait bien avec le
        // réglage miroir (donc la logique de miroir elle-même était déjà correcte) mais dans le
        // mauvais sens -- convention d'axe VTS opposée à celle du référentiel pour cet axe.
        val tiltedRoll = result(headEulerDegrees = floatArrayOf(0f, 0f, 10f))
        assertEquals(-10f, valueFor("FaceAngleZ", tiltedRoll), 0.0001f)
        assertEquals(-15f, valueFor("BodyAngleZ", tiltedRoll), 0.0001f) // x1.5
    }

    // --- Visage neutre : valeurs de repos vérifiables à la main (voir revue technique, point 41) ---

    @Test
    fun `visage neutre -- angles de tête et bouche a zero`() {
        val neutral = result()
        assertEquals(0f, valueFor("FaceAngleX", neutral), 0f)
        assertEquals(0f, valueFor("FaceAngleY", neutral), 0f)
        assertEquals(0f, valueFor("FaceAngleZ", neutral), 0f)
        assertEquals(0f, valueFor("BodyAngleX", neutral), 0f)
        assertEquals(0f, valueFor("BodyAngleY", neutral), 0f)
        assertEquals(0f, valueFor("BodyAngleZ", neutral), 0f)
        assertEquals(0f, valueFor("JawOpen", neutral), 0f)
        assertEquals(0f, valueFor("MouthOpen", neutral), 0f)
        assertEquals(0f, valueFor("TongueOut", neutral), 0f)
        assertEquals(0f, valueFor("CheekPuff", neutral), 0f)
    }

    @Test
    fun `visage neutre -- MouthSmile vaut 0,50 comme affiche par VBridger`() {
        assertEquals(0.5f, valueFor("MouthSmile", result()), 0.0001f)
    }

    @Test
    fun `visage neutre -- yeux et sourcils au repos a 0,5`() {
        val neutral = result()
        assertEquals(0.5f, valueFor("EyeOpenLeft", neutral), 0f)
        assertEquals(0.5f, valueFor("EyeOpenRight", neutral), 0f)
        assertEquals(0.5f, valueFor("BrowLeftY", neutral), 0f)
        assertEquals(0.5f, valueFor("BrowRightY", neutral), 0f)
        assertEquals(0.5f, valueFor("Brows", neutral), 0f)
    }

    // --- Cas non-neutres, formules composites les plus verbeuses ---

    @Test
    fun `MouthSmile reagit au sourire et au froncement`() {
        val smiling = result(listOf(BlendshapeScore("mouthSmileLeft", 1f), BlendshapeScore("mouthSmileRight", 1f)))
        assertTrue(valueFor("MouthSmile", smiling) > 0.5f)

        val frowning = result(listOf(BlendshapeScore("mouthFrownLeft", 1f), BlendshapeScore("mouthFrownRight", 1f)))
        assertTrue(valueFor("MouthSmile", frowning) < 0.5f)
    }

    @Test
    fun `MouthPressLipOpen combine les levres retroussees et les levres roulees`() {
        val value = valueFor(
            "MouthPressLipOpen",
            result(
                listOf(
                    BlendshapeScore("mouthUpperUpLeft", 0.6f),
                    BlendshapeScore("mouthUpperUpRight", 0.6f),
                    BlendshapeScore("mouthLowerDownLeft", 0.6f),
                    BlendshapeScore("mouthLowerDownRight", 0.6f),
                    BlendshapeScore("mouthRollUpper", 0.2f),
                    BlendshapeScore("mouthRollLower", 0.2f),
                ),
            ),
        )
        // (0.6*4)/1.2 - (0.2+0.2) = 2 - 0.4 = 1.6, borné à 1.3.
        assertEquals(1.3f, value, 0.0001f)
    }

    @Test
    fun `MouthX s annule quand tongueOut vaut 1`() {
        val value = valueFor(
            "MouthX",
            result(listOf(BlendshapeScore("mouthLeft", 1f), BlendshapeScore("tongueOut", 1f))),
        )
        assertEquals(0f, value, 0.0001f)
    }

    @Test
    fun `EyeRightX et EyeLeftX lisent chacune leur propre cote, sans croisement -- coherence miroir`() {
        // Lecture directe (EyeRightX <- _R, EyeLeftX <- _L), pas le croisement du référentiel
        // d'origine -- voir kdoc de tête du fichier (cohérence avec EyeOpenLeft/Right, EyeSquintL/R
        // etc., qui n'ont elles jamais eu de croisement). Signe inversé par rapport au référentiel
        // d'origine (voir eyeGazeX) -- retour utilisateur, convention d'axe VTS opposée sur cet axe.
        val onlyLeftEyeLooksIn = result(listOf(BlendshapeScore("eyeLookInLeft", 0.5f)))
        assertEquals(0.1f, valueFor("EyeRightX", onlyLeftEyeLooksIn), 0.0001f) // -(-0.1 offset), aucune entrée _R
        assertEquals(-0.4f, valueFor("EyeLeftX", onlyLeftEyeLooksIn), 0.0001f) // -((0.5 - 0.1) - 0)
    }
}
