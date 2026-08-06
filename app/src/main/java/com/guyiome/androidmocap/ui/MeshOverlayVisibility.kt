package com.guyiome.androidmocap.ui

/**
 * Décide si l'overlay du mesh de tracking ([FaceMeshOverlay]) doit être affiché sur l'écran
 * principal ([MainScreen]) -- fonction pure, aucune dépendance Android/Compose, testable en JVM
 * (même principe que [LandmarkProjection]).
 *
 * Deux raisons de l'afficher, indépendantes l'une de l'autre :
 * - [faceMeshOverlayEnabled] : réglage diagnostic optionnel (Affichage & confort), pour tous les
 *   paliers.
 * - [usingArCoreCameraSource] : au palier `OPTIMAL` avec ARCore comme source caméra active, il n'y
 *   a pas d'aperçu caméra live (voir `ArCoreHeadPoseTracker`) -- le mesh devient alors le seul
 *   retour visuel, affiché qu'il soit ou non explicitement activé dans les réglages.
 *
 * Le mode économie d'énergie garde la priorité par défaut : l'overlay (forcé ou non) disparaît en
 * mode éco, sauf si [keepMeshOverlayInPowerSave] (réglage dédié, tous paliers confondus) est actif.
 */
internal fun computeMeshOverlayVisible(
    usingArCoreCameraSource: Boolean,
    faceMeshOverlayEnabled: Boolean,
    isPowerSaveActive: Boolean,
    keepMeshOverlayInPowerSave: Boolean,
): Boolean {
    val baseVisible = usingArCoreCameraSource || faceMeshOverlayEnabled
    return if (isPowerSaveActive) baseVisible && keepMeshOverlayInPowerSave else baseVisible
}
