package com.guyiome.androidmocap.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFormattingTest {

    // --- formatLogLine ---

    @Test
    fun `formatLogLine produit AAAA-MM-JJ HH-mm-ss-SSS NIVEAU-TAG message`() {
        // 1723219200000 ms = 2024-08-09 12:00:00.000 UTC -- horodatage fixe arbitraire, peu importe
        // la date exacte, seul le format compte ici.
        val line = formatLogLine(LogLevel.ERROR, "MonTag", "un message", timestampMs = 1723219200000L)
        assertTrue("ligne=$line", line.matches(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} E/MonTag: un message""")))
    }

    @Test
    fun `formatLogLine ajoute la pile d'appel si throwable fourni`() {
        val error = IllegalStateException("boom")
        val line = formatLogLine(LogLevel.WARN, "Tag", "message", timestampMs = 0L, throwable = error)
        assertTrue(line.contains("W/Tag: message"))
        assertTrue("pile absente : $line", line.contains("IllegalStateException: boom"))
    }

    @Test
    fun `formatLogLine sans throwable ne contient qu'une ligne`() {
        val line = formatLogLine(LogLevel.INFO, "Tag", "message", timestampMs = 0L)
        assertEquals(1, line.lines().size)
    }

    // --- maskIpAddresses ---

    @Test
    fun `maskIpAddresses masque les deux derniers octets`() {
        assertEquals("192.168.x.x", maskIpAddresses("192.168.1.42"))
    }

    @Test
    fun `maskIpAddresses masque une IP integree dans un message plus long`() {
        val masked = maskIpAddresses("Erreur WebSocket VTube Studio (192.168.1.5:8001)")
        assertEquals("Erreur WebSocket VTube Studio (192.168.x.x:8001)", masked)
    }

    @Test
    fun `maskIpAddresses masque plusieurs IP dans le meme message`() {
        val masked = maskIpAddresses("de 10.0.0.1 vers 172.16.5.9")
        assertEquals("de 10.0.x.x vers 172.16.x.x", masked)
    }

    @Test
    fun `maskIpAddresses laisse un message sans IP inchange`() {
        val message = "Échec de bind CameraX (analyse)"
        assertEquals(message, maskIpAddresses(message))
    }

    // --- appendWithRotation ---

    @Test
    fun `appendWithRotation concatene simplement sous la limite de taille`() {
        val result = appendWithRotation("ligne1", "ligne2", maxSizeChars = 1000)
        assertEquals("ligne1\nligne2", result)
    }

    @Test
    fun `appendWithRotation garde les entrees les plus recentes au-dela de la limite`() {
        val existing = "ancienne1\nancienne2\nancienne3"
        val result = appendWithRotation(existing, "nouvelle", maxSizeChars = 18)
        assertTrue("les plus anciennes devraient avoir disparu, resultat=$result", !result.contains("ancienne1"))
        assertTrue("la plus recente doit rester, resultat=$result", result.contains("nouvelle"))
    }

    @Test
    fun `appendWithRotation ne laisse pas de ligne partielle en tete apres troncature`() {
        val existing = "AAAAAAAAAA\nBBBBBBBBBB\nCCCCCCCCCC"
        val result = appendWithRotation(existing, "DDDDDDDDDD", maxSizeChars = 15)
        // Chaque ligne restante doit être une ligne complète connue, jamais un fragment.
        result.lines().forEach { line ->
            assertTrue("ligne inattendue/partielle : $line", line.isEmpty() || line in listOf("AAAAAAAAAA", "BBBBBBBBBB", "CCCCCCCCCC", "DDDDDDDDDD"))
        }
    }

    @Test
    fun `appendWithRotation sur contenu initial vide n'ajoute pas de saut de ligne en tete`() {
        val result = appendWithRotation("", "premiere ligne", maxSizeChars = 1000)
        assertEquals("premiere ligne", result)
    }

    // --- shouldPersist ---

    @Test
    fun `shouldPersist accepte un niveau au moins aussi severe que le seuil`() {
        assertTrue(shouldPersist(LogLevel.ERROR, configuredMinimum = LogLevel.ERROR))
        assertTrue(shouldPersist(LogLevel.WARN, configuredMinimum = LogLevel.INFO))
    }

    @Test
    fun `shouldPersist rejette un niveau moins severe que le seuil`() {
        assertFalse(shouldPersist(LogLevel.INFO, configuredMinimum = LogLevel.WARN))
        assertFalse(shouldPersist(LogLevel.DEBUG, configuredMinimum = LogLevel.ERROR))
    }
}
