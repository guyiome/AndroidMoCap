package com.guyiome.androidmocap.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** Couvre [appLanguageFromTag] -- pure, testable en JVM. */
class AppLanguageTest {

    @Test
    fun `tag fr retrouve FRENCH`() {
        assertEquals(AppLanguage.FRENCH, appLanguageFromTag("fr"))
    }

    @Test
    fun `tag en retrouve ENGLISH`() {
        assertEquals(AppLanguage.ENGLISH, appLanguageFromTag("en"))
    }

    @Test
    fun `tag null retrouve SYSTEM`() {
        assertEquals(AppLanguage.SYSTEM, appLanguageFromTag(null))
    }

    @Test
    fun `tag vide retrouve SYSTEM`() {
        // AppCompatDelegate.getApplicationLocales().toLanguageTags() renvoie une chaîne vide (pas
        // null) quand aucune langue n'est forcée -- distinct du cas null, doit retomber sur SYSTEM
        // comme lui plutôt que de planter ou matcher par erreur une entrée.
        assertEquals(AppLanguage.SYSTEM, appLanguageFromTag(""))
    }

    @Test
    fun `tag inconnu retrouve SYSTEM`() {
        assertEquals(AppLanguage.SYSTEM, appLanguageFromTag("de"))
    }
}
