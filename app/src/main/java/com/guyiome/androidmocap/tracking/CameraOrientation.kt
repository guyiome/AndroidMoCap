package com.guyiome.androidmocap.tracking

/**
 * Convention `OrientationEventListener` (0-359°, horaire) -> un des 4 paliers {0,90,180,270},
 * fenêtres de ±45° centrées sur chaque palier. Extrait de `MainScreen.kt` pour corriger un trou de
 * sa logique ad hoc précédente (le cas 135-225° tombait à tort sur 0° au lieu de 180°) -- utilisé
 * pour la rotation du panneau de blendshapes (cosmétique, Compose).
 *
 * **Historique** (12 août 2026, revue technique points 1/15/20) : ce fichier portait à l'origine
 * deux fonctions supplémentaires pour corriger dynamiquement la rotation de l'image caméra selon
 * l'orientation physique du téléphone (`ArCoreHeadPoseTracker`/`CameraController`), sur l'hypothèse
 * que la rotation fixe (`CameraCharacteristics.SENSOR_ORIENTATION`, jamais recalculée selon la tenue
 * physique) cassait le tracking en paysage. **Hypothèse infirmée par un test sur device** : la
 * rotation fixe fonctionne très bien dans toutes les orientations (confirmé par l'utilisateur) ; le
 * vrai souci observé (pose de tête -- roll/pitch aberrants en paysage, mesuré : roll 98,7°±147,5°
 * contre -0,12°±1,0° en portrait) venait d'une référence de calibrage restée en portrait, pas de
 * l'image caméra -- recalibrer en paysage a suffi à corriger la pose (roll revenu à 1,05°±2,88°).
 * Le correctif de rotation caméra a donc été entièrement retiré (voir historique git) ; seule cette
 * fonction, indépendante de cette hypothèse, a survécu.
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
