package com.guyiome.androidmocap.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Seule la décision "cet appareil impose-t-il la permission ?" est testable en JVM --
 * [LocalNetworkPermission.isGranted] dépend d'un `Context` Android réel, et c'est justement
 * pour ça que le seuil a été isolé dans une fonction pure.
 */
class LocalNetworkPermissionTest {

    @Test
    fun `la permission n'est pas imposee avant Android 17`() {
        // minSdk du projet, puis les versions intermédiaires jusqu'à Android 16 inclus.
        assertFalse(LocalNetworkPermission.isEnforcedOn(30))
        assertFalse(LocalNetworkPermission.isEnforcedOn(33))
        assertFalse(LocalNetworkPermission.isEnforcedOn(36))
    }

    @Test
    fun `la permission est imposee a partir d'Android 17`() {
        assertTrue(LocalNetworkPermission.isEnforcedOn(37))
    }

    @Test
    fun `les versions ulterieures restent soumises a la permission`() {
        // Garde-fou contre une comparaison d'égalité stricte au lieu d'un seuil : une future
        // version d'Android ne doit pas repasser silencieusement en accès libre.
        assertTrue(LocalNetworkPermission.isEnforcedOn(38))
        assertTrue(LocalNetworkPermission.isEnforcedOn(45))
    }

    @Test
    fun `le nom de la permission est celui attendu par la plateforme`() {
        // Littéral volontaire (voir le kdoc) : une faute de frappe ici ne casserait rien à la
        // compilation, seulement l'octroi de la permission au runtime -- donc elle est figée ici.
        assertEquals("android.permission.ACCESS_LOCAL_NETWORK", LocalNetworkPermission.NAME)
        assertEquals(37, LocalNetworkPermission.FIRST_ENFORCING_SDK)
    }
}
