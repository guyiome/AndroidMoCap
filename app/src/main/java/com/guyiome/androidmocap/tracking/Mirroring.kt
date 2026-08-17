package com.guyiome.androidmocap.tracking

/**
 * Mode miroir : permute les scores des blendshapes gauche/droite entre
 * eux, à appliquer **toujours en même temps** que [RotationMath.mirrorEulerDegrees] sur la tête --
 * jamais l'un sans l'autre, c'est justement le déséquilibre entre les deux (tête mirrorée,
 * blendshapes pas mirrorés) qui causait l'incohérence découverte sur device le 10 août 2026.
 *
 * Dérivé du suffixe du nom (`Left`/`Right`), pas d'une liste tenue à la main -- s'applique
 * automatiquement à toute paire présente dans [BlendshapeCatalog.all] (yeux, sourcils, bouche,
 * joues, nez, mâchoire) sans risque d'en oublier une si le catalogue évolue. Les noms de sortie
 * restent inchangés (toujours `eyeBlinkLeft`/`eyeBlinkRight`, jamais renommés) -- seules les
 * *valeurs* sont échangées, pour rester compatibles avec ce qu'attendent VMC/iFacialMocap/VTube
 * Studio côté récepteur.
 */
internal fun mirrorBlendshapeName(name: String): String = when {
    name.endsWith("Left") -> name.removeSuffix("Left") + "Right"
    name.endsWith("Right") -> name.removeSuffix("Right") + "Left"
    else -> name
}

/**
 * Renvoie [blendshapes] avec, pour chaque entrée ayant une contrepartie gauche/droite, le score
 * échangé avec celui de sa contrepartie -- inchangé si la contrepartie est absente de la liste (ne
 * devrait pas arriver en pratique, MediaPipe renvoie toujours les 52) ou si le blendshape n'a pas de
 * variante gauche/droite (ex. `jawOpen`, `browInnerUp`, `tongueOut`).
 */
internal fun mirrorBlendshapes(blendshapes: List<BlendshapeScore>): List<BlendshapeScore> {
    val scoreByName = blendshapes.associate { it.name to it.score }
    return blendshapes.map { score ->
        val counterpartScore = scoreByName[mirrorBlendshapeName(score.name)] ?: score.score
        score.copy(score = counterpartScore)
    }
}

/**
 * Mirrore un [FaceTrackingResult] complet -- blendshapes ([mirrorBlendshapes]), tête
 * ([RotationMath.mirrorEulerDegrees]), et regard par œil : les deux tableaux gauche/droite sont à
 * la fois échangés entre eux (l'œil qui était à gauche est maintenant à droite) ET mirrorés
 * individuellement (la direction du regard elle-même s'inverse), même logique double que pour les
 * blendshapes gauche/droite. Seul point d'entrée destiné à être appelé depuis `MainViewModel`.
 */
internal fun mirrorFaceTrackingResult(result: FaceTrackingResult): FaceTrackingResult = result.copy(
    blendshapes = mirrorBlendshapes(result.blendshapes),
    headEulerDegrees = RotationMath.mirrorEulerDegrees(result.headEulerDegrees),
    leftEyeEulerDegrees = RotationMath.mirrorEulerDegrees(result.rightEyeEulerDegrees),
    rightEyeEulerDegrees = RotationMath.mirrorEulerDegrees(result.leftEyeEulerDegrees),
)
