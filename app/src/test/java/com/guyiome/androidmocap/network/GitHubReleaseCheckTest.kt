package com.guyiome.androidmocap.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseCheckTest {

    @Test
    fun `parseLatestReleaseJson lit un payload GitHub reel`() {
        val raw = """{"tag_name":"v0.2.0","html_url":"https://github.com/guyiome/AndroidMoCap/releases/tag/v0.2.0","name":"v0.2.0","draft":false,"prerelease":false}"""
        val release = parseLatestReleaseJson(raw)
        assertEquals("v0.2.0", release?.tagName)
        assertEquals("https://github.com/guyiome/AndroidMoCap/releases/tag/v0.2.0", release?.htmlUrl)
    }

    @Test
    fun `parseLatestReleaseJson ignore les champs inconnus`() {
        val raw = """{"tag_name":"v0.3.0","html_url":"https://example.com","assets":[],"body":"notes"}"""
        assertEquals("v0.3.0", parseLatestReleaseJson(raw)?.tagName)
    }

    @Test
    fun `parseLatestReleaseJson renvoie null sur JSON invalide ou incomplet`() {
        assertNull(parseLatestReleaseJson("not json"))
        assertNull(parseLatestReleaseJson("""{"tag_name":"v0.2.0"}""")) // html_url manquant
        assertNull(parseLatestReleaseJson(""))
    }

    @Test
    fun `parseVersionParts gere le prefixe v et les segments entiers`() {
        assertEquals(listOf(0, 2, 0), parseVersionParts("v0.2.0"))
        assertEquals(listOf(0, 2, 0), parseVersionParts("0.2.0"))
        assertEquals(listOf(1, 0), parseVersionParts("1.0"))
    }

    @Test
    fun `parseVersionParts renvoie null si un segment n'est pas un entier`() {
        assertNull(parseVersionParts("v0.3.0-beta.1"))
        assertNull(parseVersionParts("abc"))
        assertNull(parseVersionParts(""))
    }

    @Test
    fun `isNewerVersion detecte une version plus recente`() {
        assertTrue(isNewerVersion("0.2.0", "0.3.0"))
        assertTrue(isNewerVersion("0.2.0", "0.2.1"))
        assertTrue(isNewerVersion("0.2.0", "1.0.0"))
    }

    @Test
    fun `isNewerVersion renvoie false pour egal ou plus ancien`() {
        assertFalse(isNewerVersion("0.2.0", "0.2.0"))
        assertFalse(isNewerVersion("0.3.0", "0.2.0"))
        assertFalse(isNewerVersion("1.0.0", "0.9.9"))
    }

    @Test
    fun `isNewerVersion traite les segments manquants comme zero`() {
        assertFalse(isNewerVersion("0.2.0", "0.2")) // egal
        assertTrue(isNewerVersion("0.2", "0.2.1"))
        assertFalse(isNewerVersion("0.2.0.1", "0.2.0")) // 0.2.0.1 plus recent que 0.2.0
    }

    @Test
    fun `isNewerVersion renvoie false si une des deux versions ne parse pas -- defaut sur`() {
        assertFalse(isNewerVersion("0.2.0", "v0.3.0-beta.1"))
        assertFalse(isNewerVersion("garbage", "0.2.0"))
    }
}
