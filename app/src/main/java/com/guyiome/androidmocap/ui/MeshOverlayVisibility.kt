package com.guyiome.androidmocap.ui

/**
 * Décide si l'overlay du mesh de tracking ([FaceMeshOverlay]) doit être affiché sur l'écran
 * principal ([MainScreen]) -- fonction pure, aucune dépendance Android/Compose, testable en JVM
 * (même principe que [LandmarkProjection]).
 *
 * Suit uniquement [faceMeshOverlayEnabled] (réglage diagnostic optionnel, Affichage & confort),
 * pour tous les paliers. **Historique** : au palier `OPTIMAL` (ARCore), le mesh était forcé affiché
 * indépendamment de ce réglage, faute d'aperçu caméra live sur ce palier -- devenu inutile depuis
 * l'ajout du fond caméra live GL (`ArCoreHeadPoseTracker.drawCameraBackground`), retiré pour
 * revenir à la parité avec les paliers CameraX.
 *
 * Le mode économie d'énergie garde la priorité par défaut : l'overlay disparaît en mode éco, sauf
 * si [keepMeshOverlayInPowerSave] (réglage dédié, tous paliers confondus) est actif.
 */
internal fun computeMeshOverlayVisible(
    faceMeshOverlayEnabled: Boolean,
    isPowerSaveActive: Boolean,
    keepMeshOverlayInPowerSave: Boolean,
): Boolean {
    return if (isPowerSaveActive) faceMeshOverlayEnabled && keepMeshOverlayInPowerSave else faceMeshOverlayEnabled
}
