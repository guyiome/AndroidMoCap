package com.guyiome.androidmocap.network

import com.guyiome.androidmocap.tracking.BlendshapeScore
import com.guyiome.androidmocap.tracking.FaceTrackingResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Couvre le formatage du protocole iFacialMocap ([IFacialMocapSender.toIFacialMocapName] et
 * [IFacialMocapSender.buildMessage], rendues `internal` pour ces tests) -- aucune dépendance
 * socket/réseau, un mauvais format ici casse silencieusement la réception côté VBridger, comme
 * observé une fois en pratique.
 */
class IFacialMocapSenderTest {

    @Test
    fun `suffixe Left convertit en _L`() {
        assertEquals("eyeBlink_L", IFacialMocapSender.toIFacialMocapName("eyeBlinkLeft"))
    }

    @Test
    fun `suffixe Right convertit en _R`() {
        assertEquals("mouthSmile_R", IFacialMocapSender.toIFacialMocapName("mouthSmileRight"))
    }

    @Test
    fun `pas de suffixe -- nom inchange`() {
        assertEquals("jawOpen", IFacialMocapSender.toIFacialMocapName("jawOpen"))
    }

    @Test
    fun `_neutral est exclu (retourne null)`() {
        assertNull(IFacialMocapSender.toIFacialMocapName("_neutral"))
    }

    @Test
    fun `buildMessage formate les blendshapes et exclut _neutral`() {
        val result = FaceTrackingResult(
            faceDetected = true,
            blendshapes = listOf(
                BlendshapeScore("eyeBlinkLeft", 0.5f),
                BlendshapeScore("jawOpen", 0.25f),
                BlendshapeScore("_neutral", 0.1f),
            ),
            inferenceTimeMs = 10,
            timestampMs = 1000,
            headEulerDegrees = floatArrayOf(1f, 2f, 3f),
            leftEyeEulerDegrees = floatArrayOf(4f, 5f, 0f),
            rightEyeEulerDegrees = floatArrayOf(6f, 7f, 0f),
        )

        val message = IFacialMocapSender.buildMessage(result)

        assertTrue(message.contains("eyeBlink_L-50|"))
        assertTrue(message.contains("jawOpen-25|"))
        assertFalse(message.contains("_neutral"))
        assertTrue(message.endsWith("=head#1.0,2.0,3.0,0,0,0|rightEye#6.0,7.0,0|leftEye#4.0,5.0,0|"))
    }

    @Test
    fun `buildMessage sans blendshapes garde quand meme le bloc head-eye`() {
        val result = FaceTrackingResult(
            faceDetected = true,
            blendshapes = emptyList(),
            inferenceTimeMs = 0,
            timestampMs = 0,
        )
        val message = IFacialMocapSender.buildMessage(result)
        assertTrue(message.startsWith("=head#"))
    }
}
