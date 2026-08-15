package com.guyiome.androidmocap.tracking

/**
 * Échange les scores de `mouthDimpleLeft`/`mouthDimpleRight` (revue technique, point 41 -- retour
 * utilisateur du 15 août 2026, en marge du travail sur la traduction VBridger).
 *
 * Contrairement au reste des blendshapes de ce fichier (transmis tels quels depuis MediaPipe, jamais
 * retouchés -- voir [FaceLandmarkerHelper.onLiveStreamResult]), ce couple précis semble mal étiqueté
 * à la source : constaté par l'utilisateur en envoyant les données brutes à un vrai VBridger (pas la
 * traduction de cette app) et en croisant la valeur suivie en direct avec le rendu sur l'avatar 3D --
 * la fossette qui bouge à l'écran est celle du côté opposé à celui qui bouge réellement. Pas une
 * confusion avec le mode miroir de l'app (déjà pris en compte, testé miroir activé) : c'est un
 * problème indépendant, à la source des deux valeurs elles-mêmes.
 *
 * Appliqué une seule fois, au plus tôt (juste après la lecture depuis MediaPipe, avant toute autre
 * transformation -- correction EAR, mode miroir, etc.), pour que tout ce qui consomme
 * [FaceTrackingResult.blendshapes] en aval (envoi ARKit brut, VMC, formules composites VBridger)
 * reçoive la bonne valeur sans avoir à connaître cette particularité.
 */
internal fun correctMouthDimpleLeftRight(blendshapes: List<BlendshapeScore>): List<BlendshapeScore> {
    val left = blendshapes.firstOrNull { it.name == "mouthDimpleLeft" } ?: return blendshapes
    val right = blendshapes.firstOrNull { it.name == "mouthDimpleRight" } ?: return blendshapes
    return blendshapes.map { score ->
        when (score.name) {
            "mouthDimpleLeft" -> score.copy(score = right.score)
            "mouthDimpleRight" -> score.copy(score = left.score)
            else -> score
        }
    }
}
