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
}
