package com.guyiome.androidmocap.tracking

/** Catégorie d'un blendshape ARKit, pour le regroupement dans l'écran de sélection. */
enum class BlendshapeCategory(val label: String) {
    BROW("Sourcils"),
    EYE("Yeux"),
    CHEEK("Joues"),
    NOSE("Nez"),
    JAW("Mâchoire"),
    MOUTH("Bouche"),
    TONGUE("Langue"),
}

/**
 * Catalogue statique des 52 blendshapes ARKit que MediaPipe est censé produire (tongueOut excepté
 * en pratique -- limitation connue documentée dans l'étude), regroupés par catégorie pour l'écran
 * de sélection des réglages. Indépendant de ce que MediaPipe détecte réellement frame par frame :
 * sert uniquement de référence pour construire la liste "à cocher".
 */
object BlendshapeCatalog {
    val all: List<Pair<BlendshapeCategory, String>> = listOf(
        BlendshapeCategory.BROW to "browDownLeft",
        BlendshapeCategory.BROW to "browDownRight",
        BlendshapeCategory.BROW to "browInnerUp",
        BlendshapeCategory.BROW to "browOuterUpLeft",
        BlendshapeCategory.BROW to "browOuterUpRight",
        BlendshapeCategory.EYE to "eyeBlinkLeft",
        BlendshapeCategory.EYE to "eyeBlinkRight",
        BlendshapeCategory.EYE to "eyeLookDownLeft",
        BlendshapeCategory.EYE to "eyeLookDownRight",
        BlendshapeCategory.EYE to "eyeLookInLeft",
        BlendshapeCategory.EYE to "eyeLookInRight",
        BlendshapeCategory.EYE to "eyeLookOutLeft",
        BlendshapeCategory.EYE to "eyeLookOutRight",
        BlendshapeCategory.EYE to "eyeLookUpLeft",
        BlendshapeCategory.EYE to "eyeLookUpRight",
        BlendshapeCategory.EYE to "eyeSquintLeft",
        BlendshapeCategory.EYE to "eyeSquintRight",
        BlendshapeCategory.EYE to "eyeWideLeft",
        BlendshapeCategory.EYE to "eyeWideRight",
        BlendshapeCategory.CHEEK to "cheekPuff",
        BlendshapeCategory.CHEEK to "cheekSquintLeft",
        BlendshapeCategory.CHEEK to "cheekSquintRight",
        BlendshapeCategory.NOSE to "noseSneerLeft",
        BlendshapeCategory.NOSE to "noseSneerRight",
        BlendshapeCategory.JAW to "jawForward",
        BlendshapeCategory.JAW to "jawLeft",
        BlendshapeCategory.JAW to "jawOpen",
        BlendshapeCategory.JAW to "jawRight",
        BlendshapeCategory.MOUTH to "mouthClose",
        BlendshapeCategory.MOUTH to "mouthDimpleLeft",
        BlendshapeCategory.MOUTH to "mouthDimpleRight",
        BlendshapeCategory.MOUTH to "mouthFrownLeft",
        BlendshapeCategory.MOUTH to "mouthFrownRight",
        BlendshapeCategory.MOUTH to "mouthFunnel",
        BlendshapeCategory.MOUTH to "mouthLeft",
        BlendshapeCategory.MOUTH to "mouthLowerDownLeft",
        BlendshapeCategory.MOUTH to "mouthLowerDownRight",
        BlendshapeCategory.MOUTH to "mouthPressLeft",
        BlendshapeCategory.MOUTH to "mouthPressRight",
        BlendshapeCategory.MOUTH to "mouthPucker",
        BlendshapeCategory.MOUTH to "mouthRight",
        BlendshapeCategory.MOUTH to "mouthRollLower",
        BlendshapeCategory.MOUTH to "mouthRollUpper",
        BlendshapeCategory.MOUTH to "mouthShrugLower",
        BlendshapeCategory.MOUTH to "mouthShrugUpper",
        BlendshapeCategory.MOUTH to "mouthSmileLeft",
        BlendshapeCategory.MOUTH to "mouthSmileRight",
        BlendshapeCategory.MOUTH to "mouthStretchLeft",
        BlendshapeCategory.MOUTH to "mouthStretchRight",
        BlendshapeCategory.MOUTH to "mouthUpperUpLeft",
        BlendshapeCategory.MOUTH to "mouthUpperUpRight",
        BlendshapeCategory.TONGUE to "tongueOut",
    )

    /** Regroupement par catégorie, ordre stable (celui de [all]) -- utilisé par l'écran de sélection. */
    val byCategory: Map<BlendshapeCategory, List<String>> = all.groupBy({ it.first }, { it.second })

    /**
     * Blendshapes connus pour être peu fiables chez MediaPipe (score qui ne bouge quasiment
     * jamais, ou très bruité) -- croisé avec le mapping alternatif haibalabs/face-mesh-to-blendshapes
     * qui les force lui aussi à zéro/quasi-zéro, un bon indice de ce que le modèle officiel gère
     * mal. Sert uniquement à afficher un avertissement discret dans l'écran de sélection
     * ([com.guyiome.androidmocap.ui.BlendshapesScreen]) -- n'affecte ni la détection ni
     * l'envoi, juste l'information donnée à l'utilisateur avant qu'il coche. Voir
     * `AndroidMoCap_revue_technique.md`, point 17.
     */
    val unreliable: Set<String> = setOf(
        "jawForward",
        "jawLeft",
        "jawRight",
        "mouthDimpleLeft",
        "mouthDimpleRight",
        "cheekPuff",
        "tongueOut",
    )
}
