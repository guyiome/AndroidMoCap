package com.guyiome.androidmocap.ui

/**
 * Convention `OrientationEventListener` (0-359°, horaire) -> un des 4 paliers {0,90,180,270},
 * fenêtres de ±45° centrées sur chaque palier. Extrait de `MainScreen.kt` pour corriger un trou de
 * sa logique ad hoc précédente (le cas 135-225° tombait à tort sur 0° au lieu de 180°) -- utilisé
 * pour la rotation du panneau de blendshapes (cosmétique, Compose).
 *
 * **Historique** (déplacé/renommé le 12 août 2026) : vivait à
 * l'origine dans `tracking/CameraOrientation.kt`, aux côtés de deux fonctions supplémentaires pour
 * corriger dynamiquement la rotation de l'image caméra selon la tenue physique du téléphone --
 * hypothèse testée sur device puis infirmée, fonctions
 * retirées. Seule cette fonction a survécu, purement cosmétique/UI (rotation d'un panneau Compose,
 * aucun rapport avec l'orientation de l'image caméra) -- déplacée dans `ui/` sous un nom qui reflète
 * son rôle réel plutôt que de garder un nom et un emplacement hérités d'une hypothèse abandonnée.
 */
internal fun snapToRotationBucket(rawDegrees: Int): Int {
    val normalized = ((rawDegrees % 360) + 360) % 360
    return when {
        normalized < 45 || normalized >= 315 -> 0
        normalized < 135 -> 90
        normalized < 225 -> 180
        else -> 270
    }
}
