package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie la cohérence structurelle du catalogue statique -- sert de filet si quelqu'un modifie
 * la liste (ajout/suppression/faute de frappe dans un nom) sans remarquer l'effet de bord sur le
 * regroupement par catégorie.
 */
class BlendshapeCatalogTest {

    @Test
    fun `52 blendshapes ARKit au total`() {
        assertEquals(52, BlendshapeCatalog.all.size)
    }

    @Test
    fun `aucun nom en double`() {
        val names = BlendshapeCatalog.all.map { it.second }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `byCategory recouvre exactement all, dans le meme ordre par categorie`() {
        val flattenedFromByCategory = BlendshapeCatalog.byCategory.values.flatten()
        val namesFromAll = BlendshapeCatalog.all.map { it.second }
        assertEquals(namesFromAll, flattenedFromByCategory)
    }

    @Test
    fun `toutes les categories de l'enum sont representees`() {
        val categoriesPresentes = BlendshapeCatalog.byCategory.keys
        for (category in BlendshapeCategory.entries) {
            assertTrue("Catégorie $category absente de byCategory", category in categoriesPresentes)
        }
    }

    @Test
    fun `tous les blendshapes marques peu fiables existent bien dans le catalogue`() {
        // Filet contre une faute de frappe dans BlendshapeCatalog.unreliable -- un nom mal
        // orthographié n'afficherait simplement jamais
        // l'avertissement dans BlendshapeSelectionScreen, silencieusement.
        val allNames = BlendshapeCatalog.all.map { it.second }.toSet()
        for (name in BlendshapeCatalog.unreliable) {
            assertTrue("'$name' est marqué peu fiable mais absent du catalogue", name in allNames)
        }
    }

    @Test
    fun `au moins un blendshape est marque peu fiable`() {
        // Garde-fou trivial : un ensemble vide ne serait pas forcément une erreur de code, mais
        // rendrait ce test (et la fonctionnalité) sans objet -- vérifie qu'on ne l'a pas vidé par
        // erreur en modifiant le catalogue.
        assertTrue(BlendshapeCatalog.unreliable.isNotEmpty())
    }
}
