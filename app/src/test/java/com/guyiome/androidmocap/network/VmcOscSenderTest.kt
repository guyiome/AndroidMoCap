package com.guyiome.androidmocap.network

import com.guyiome.androidmocap.tracking.BlendshapeScore
import com.guyiome.androidmocap.tracking.FaceTrackingResult
import com.illposed.osc.OSCBundle
import com.illposed.osc.OSCMessage
import com.illposed.osc.OSCSerializerAndParserBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Couvre [VmcOscSender.buildBundle] (rendue `internal`, pure -- aucune dépendance socket/réseau) :
 * regroupe les messages `/VMC/Ext/Blend/Val` + `/Apply` dans un seul `OSCBundle` envoyé en un seul
 * paquet UDP, au lieu d'un envoi séparé par blendshape (jusqu'à une cinquantaine par frame avant ce
 * changement). Couvre aussi [VmcOscSender.serializeToBytes]
 * (correctif du 8 août 2026, voir le kdoc de tête de `VmcOscSender.kt`) : preuve en JVM, sans device,
 * que le remplacement d'`OSCPortOut` sérialise vraiment sans planter et produit des octets valides,
 * pas seulement que le code compile.
 */
class VmcOscSenderTest {

    private fun result(blendshapes: List<BlendshapeScore>) = FaceTrackingResult(
        faceDetected = true,
        blendshapes = blendshapes,
        inferenceTimeMs = 0,
        timestampMs = 0,
    )

    @Test
    fun `un paquet Val par blendshape, dans l'ordre, plus un paquet Apply final`() {
        val packets = VmcOscSender.buildBundle(
            result(listOf(BlendshapeScore("jawOpen", 0.5f), BlendshapeScore("eyeBlinkLeft", 0.25f)))
        ).packets

        assertEquals(3, packets.size)

        val firstVal = packets[0] as OSCMessage
        assertEquals("/VMC/Ext/Blend/Val", firstVal.address)
        assertEquals(listOf("jawOpen", 0.5f), firstVal.arguments)

        val secondVal = packets[1] as OSCMessage
        assertEquals("/VMC/Ext/Blend/Val", secondVal.address)
        assertEquals(listOf("eyeBlinkLeft", 0.25f), secondVal.arguments)

        val apply = packets[2] as OSCMessage
        assertEquals("/VMC/Ext/Blend/Apply", apply.address)
        assertTrue(apply.arguments.isEmpty())
    }

    @Test
    fun `aucun blendshape -- le bundle contient quand meme le paquet Apply`() {
        val packets = VmcOscSender.buildBundle(result(emptyList())).packets
        assertEquals(1, packets.size)
        assertEquals("/VMC/Ext/Blend/Apply", (packets[0] as OSCMessage).address)
    }

    @Test
    fun `serializeToBytes produit des octets qui se reparsent en le meme bundle`() {
        // N'utilise ni OSCPortOut ni UDPTransport -- exactement le chemin emprunté par send(),
        // preuve que la voie de contournement de LibraryInfo fonctionne réellement de bout en bout
        // (sérialisation ET parsing), pas seulement qu'elle compile.
        val bundle = VmcOscSender.buildBundle(
            result(listOf(BlendshapeScore("jawOpen", 0.5f), BlendshapeScore("eyeBlinkLeft", 0.25f)))
        )
        val builder = OSCSerializerAndParserBuilder()
        val bytes = VmcOscSender.serializeToBytes(bundle, builder, ByteBuffer.allocate(4096))

        val reparsed = builder.buildParser().convert(ByteBuffer.wrap(bytes)) as OSCBundle
        val packets = reparsed.packets

        assertEquals(3, packets.size)
        assertEquals(listOf("jawOpen", 0.5f), (packets[0] as OSCMessage).arguments)
        assertEquals(listOf("eyeBlinkLeft", 0.25f), (packets[1] as OSCMessage).arguments)
        assertEquals("/VMC/Ext/Blend/Apply", (packets[2] as OSCMessage).address)
    }
}
