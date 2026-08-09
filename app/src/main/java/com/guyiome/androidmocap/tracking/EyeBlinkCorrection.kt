package com.guyiome.androidmocap.tracking

/**
 * Correction du blendshape `eyeBlinkLeft`/`eyeBlinkRight` par l'Eye Aspect Ratio (EAR, voir
 * `EyeAspectRatio.kt`) -- revue technique, point 28.
 *
 * Problème mesuré sur device (9 août 2026), pas supposé : le blendshape `eyeBlink` de MediaPipe
 * **fuit d'un œil vers l'autre** -- pendant un clin d'œil volontaire d'un seul côté, le score de
 * l'œil censé rester ouvert monte quand même à ~0,26-0,49 (contre ~0,43-0,49 pour un vrai clin
 * d'œil de ce côté-là) -- des plages qui se chevauchent trop pour être séparées par un simple
 * seuil côté récepteur (VBridger). L'EAR, mesure géométrique indépendante de ce classifieur ML,
 * fuit beaucoup moins dans les mêmes conditions (l'œil "au repos" ne descend qu'à ~0,29-0,35 contre
 * une base ouverte ~0,35-0,42, loin des ~0,06-0,12 d'une vraie fermeture) -- assez net pour servir
 * de garde-fou.
 *
 * Principe retenu : ne pas remplacer le blendshape par l'EAR (l'EAR seul seul a son propre défaut,
 * un plissement des yeux le fait aussi chuter), seulement **atténuer** le score du blendshape
 * quand l'EAR indique clairement que l'œil est encore largement ouvert -- la fuite typique reste
 * en dessous du seuil [EAR_DAMPING_THRESHOLD] côté "ouverture EAR", donc atténuée, tandis qu'une
 * vraie fermeture (EAR nettement plus bas) traverse sans y toucher.
 *
 * Seuils volontairement **fixes, pas calibrés par utilisateur** -- cohérent avec la conception
 * "mode léger actif sur tous les paliers" discutée pour ce point : une calibration personnalisée
 * (EAR ouvert/fermé propre à l'utilisateur) reste une piste pour un futur mode expérimental, pas
 * un prérequis ici.
 */

/** EAR observé pour un œil bien ouvert sur le device de test (plage mesurée ~0,35-0,42). */
internal const val EAR_OPEN_REFERENCE = 0.35f

/** EAR observé pour un œil fermé sur le device de test (plage mesurée ~0,06-0,12, marge incluse). */
internal const val EAR_CLOSED_REFERENCE = 0.10f

/**
 * Au-delà de ce niveau d'ouverture EAR (0=fermé, 1=aussi ouvert que [EAR_OPEN_REFERENCE]), le
 * blendshape est considéré comme une fuite probable et atténué -- calé pour couvrir la plage de
 * fuite mesurée (~0,8-1,0 d'ouverture EAR pendant un clin d'œil de l'autre côté) sans toucher aux
 * fermetures partielles réelles.
 */
internal const val EAR_DAMPING_THRESHOLD = 0.7f

/**
 * Ouverture normalisée de l'œil d'après son EAR -- 0 = aussi fermé que [closedReference] (ou plus),
 * 1 = aussi ouvert que [openReference] (ou plus). Fonction pure, testable en JVM.
 */
internal fun earOpenness(
    ear: Float,
    openReference: Float = EAR_OPEN_REFERENCE,
    closedReference: Float = EAR_CLOSED_REFERENCE,
): Float {
    if (openReference <= closedReference) return 0f
    return ((ear - closedReference) / (openReference - closedReference)).coerceIn(0f, 1f)
}

/**
 * Atténue [rawScore] (score brut `eyeBlinkLeft`/`eyeBlinkRight` de MediaPipe) quand [openness]
 * (voir [earOpenness]) indique que l'œil est encore largement ouvert -- sinon renvoie [rawScore]
 * inchangé (les fermetures réelles, même partielles, traversent sans correction). Fonction pure.
 */
internal fun correctBlinkScore(
    rawScore: Float,
    openness: Float,
    dampingThreshold: Float = EAR_DAMPING_THRESHOLD,
): Float {
    if (openness <= dampingThreshold) return rawScore
    return rawScore * (1f - openness)
}

/**
 * Applique [correctBlinkScore] aux entrées `eyeBlinkLeft`/`eyeBlinkRight` de [blendshapes],
 * d'après l'EAR calculé sur [landmarks] -- laisse tous les autres blendshapes inchangés. Renvoie
 * [blendshapes] tel quel si [landmarks] est vide (pas de correction possible sans landmarks, ex.
 * aucune frame traitée pour l'instant). Fonction pure, orchestre [eyeAspectRatioFromLandmarks] +
 * [earOpenness] + [correctBlinkScore] -- le seul point d'entrée destiné à être appelé depuis
 * `MainViewModel`.
 */
internal fun correctEyeBlinkScores(
    blendshapes: List<BlendshapeScore>,
    landmarks: List<Pair<Float, Float>>,
): List<BlendshapeScore> {
    if (landmarks.isEmpty()) return blendshapes
    val leftOpenness = earOpenness(eyeAspectRatioFromLandmarks(landmarks, EyeLandmarkIndices.LEFT_EYE))
    val rightOpenness = earOpenness(eyeAspectRatioFromLandmarks(landmarks, EyeLandmarkIndices.RIGHT_EYE))
    return blendshapes.map { score ->
        when (score.name) {
            "eyeBlinkLeft" -> score.copy(score = correctBlinkScore(score.score, leftOpenness))
            "eyeBlinkRight" -> score.copy(score = correctBlinkScore(score.score, rightOpenness))
            else -> score
        }
    }
}
