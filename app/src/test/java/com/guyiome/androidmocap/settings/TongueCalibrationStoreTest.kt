package com.guyiome.androidmocap.settings

import com.guyiome.androidmocap.tracking.TongueCalibrationResult
import java.io.File
import java.nio.file.Files
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Couvre [TongueCalibrationStore] en JVM pur -- ne dépend que de `java.io.File`, pas de `Context`/
 * DataStore, donc testable malgré son package `settings/` (contrairement à `AppSettingsStore`).
 * Le test sous locale française reproduit un bug réel trouvé sur device le 11 août 2026 : `save()`
 * utilisait `"%.8f".format(it)` sans locale explicite, qui écrit une virgule décimale en français
 * ("-0,00334370") -- collision avec le séparateur CSV, chaque nombre coupé en deux à la relecture
 * (un vecteur de N valeurs se relisait à 2N), similarité cosinus toujours à 0.0 (voir
 * TongueEmbeddingClassifier.cosineSimilarity, incompatibilité de taille) -- silencieux, sans
 * exception, jamais détecté par `load()`.
 */
class TongueCalibrationStoreTest {

    private val delta = 0.0000001f
    private lateinit var tempDir: File
    private lateinit var store: TongueCalibrationStore
    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("tongue_calibration_test").toFile()
        store = TongueCalibrationStore(tempDir)
        originalLocale = Locale.getDefault()
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
        tempDir.deleteRecursively()
    }

    @Test
    fun `load renvoie null si jamais calibre`() {
        assertNull(store.load())
    }

    @Test
    fun `save puis load restitue les memes valeurs, locale par defaut`() {
        val original = TongueCalibrationResult(
            tongueOutReference = floatArrayOf(-0.00334370f, 0.12345678f, 1f, -1f, 0f),
            tongueInReference = floatArrayOf(0.02381479f, -0.03258535f, 0.5f),
        )
        store.save(original)
        val loaded = store.load()
        assertArrayEquals(original.tongueOutReference, loaded?.tongueOutReference, delta)
        assertArrayEquals(original.tongueInReference, loaded?.tongueInReference, delta)
    }

    @Test
    fun `save puis load restitue les memes valeurs sous locale francaise -- non-regression`() {
        // Reproduit exactement les conditions du bug device : locale par défaut française pendant
        // l'écriture (la relecture, elle, ne dépend jamais de la locale -- String.toFloat() attend
        // toujours un point, voir kdoc de TongueCalibrationStore.load()).
        Locale.setDefault(Locale.FRANCE)
        val original = TongueCalibrationResult(
            tongueOutReference = floatArrayOf(-0.00334370f, 0.12345678f, 1f, -1f, 0f),
            tongueInReference = floatArrayOf(0.02381479f, -0.03258535f, 0.5f),
        )
        store.save(original)
        val loaded = store.load()
        assertArrayEquals(original.tongueOutReference, loaded?.tongueOutReference, delta)
        assertArrayEquals(original.tongueInReference, loaded?.tongueInReference, delta)
        // La régression exacte observée sur device : sans le correctif, chaque vecteur de taille N
        // se relit à 2N valeurs au lieu de N.
        assert(loaded?.tongueOutReference?.size == original.tongueOutReference.size)
        assert(loaded?.tongueInReference?.size == original.tongueInReference.size)
    }

    @Test
    fun `load renvoie null si le fichier n'a qu'une seule ligne`() {
        File(tempDir, "tongue_calibration.csv").writeText("0.1,0.2,0.3\n")
        assertNull(store.load())
    }

    // Pas de test pour un contenu qui déclenche une NumberFormatException dans load() (ex. ligne
    // vide) : ce chemin passe par `.onFailure { AppLog.w(...) }`, qui appelle `android.util.Log`
    // non mocké en JVM pur -- même limite que le reste d'`AppLog`, non testé pour la même raison.
    // Les chemins de retour anticipé (fichier absent, une seule ligne) ne touchent jamais AppLog et
    // restent couverts ci-dessus/ci-dessous.

    @Test
    fun `clear supprime le fichier, load repasse a null`() {
        store.save(TongueCalibrationResult(floatArrayOf(0.1f), floatArrayOf(0.2f)))
        assert(store.load() != null)
        store.clear()
        assertNull(store.load())
    }
}
