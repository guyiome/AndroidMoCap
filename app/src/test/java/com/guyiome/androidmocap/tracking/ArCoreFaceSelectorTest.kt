package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Couvre [ArCoreFaceSelector], la seule décision non triviale de [ArCoreHeadPoseTracker] --
 * ARCore Augmented Faces peut suivre plusieurs visages simultanément (jusqu'à 4), l'app n'en
 * utilise qu'un seul. Extraite en fonction pure sur un type découplé d'ARCore
 * ([ArCoreFaceCandidate]) pour rester testable en JVM, exactement comme le reste des décisions à
 * risque de cette app (voir RotationMathTest, TrackingTierSelectorTest).
 */
class ArCoreFaceSelectorTest {

    @Test
    fun `aucun visage ne donne aucune selection`() {
        val result = ArCoreFaceSelector.pickPrimary(emptyList())
        assertNull(result)
    }

    @Test
    fun `un seul visage en cours de suivi est selectionne`() {
        val tracked = ArCoreFaceCandidate(isTracking = true, rotationQuaternion = floatArrayOf(0f, 0f, 0f, 1f))
        val result = ArCoreFaceSelector.pickPrimary(listOf(tracked))
        assertEquals(tracked, result)
    }

    @Test
    fun `un visage perdu (pas en cours de suivi) n'est jamais selectionne`() {
        val lost = ArCoreFaceCandidate(isTracking = false, rotationQuaternion = floatArrayOf(0f, 0f, 0f, 1f))
        val result = ArCoreFaceSelector.pickPrimary(listOf(lost))
        assertNull(result)
    }

    @Test
    fun `plusieurs visages detectes -- le premier en cours de suivi est retenu, les autres ignores`() {
        val lost = ArCoreFaceCandidate(isTracking = false, rotationQuaternion = floatArrayOf(1f, 0f, 0f, 0f))
        val tracked = ArCoreFaceCandidate(isTracking = true, rotationQuaternion = floatArrayOf(0f, 0f, 0f, 1f))
        val anotherTracked = ArCoreFaceCandidate(isTracking = true, rotationQuaternion = floatArrayOf(0f, 1f, 0f, 0f))
        val result = ArCoreFaceSelector.pickPrimary(listOf(lost, tracked, anotherTracked))
        assertEquals(tracked, result)
    }
}
