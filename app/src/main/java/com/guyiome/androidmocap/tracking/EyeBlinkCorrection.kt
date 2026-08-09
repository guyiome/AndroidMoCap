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
 * fuit beaucoup moins dans les mêmes conditions.
 *
 * Principe retenu : ne pas remplacer le blendshape par l'EAR (l'EAR seul a son propre défaut, un
 * plissement des yeux le fait aussi chuter), seulement **atténuer** le score du blendshape quand
 * l'EAR indique clairement que l'œil est encore largement ouvert.
 *
 * **Deuxième problème mesuré, cette fois sur une fermeture tenue plusieurs secondes** (toujours le
 * 9 août 2026) : le blendshape brut restait stable (~0,6) pendant toute la tenue, mais la
 * correction s'effondrait progressivement vers 0 au fil des secondes -- le suivi de landmarks de
 * MediaPipe semble "relâcher" progressivement le contour de la paupière sur une pose tenue,
 * faisant remonter l'EAR calculé même œil toujours fermé. Une correction frame-à-frame sans
 * mémoire confondait cette dérive lente avec une vraie réouverture. D'abord corrigé par un
 * lissage asymétrique bricolé sur mesure (`EyeOpennessSmoother`, retiré le 9 août 2026), puis
 * remplacé par [OneEuroFilter] (revue technique, point 46) : sa coupure adaptative fait
 * naturellement la même chose -- une dérive lente (vitesse faible) reçoit une forte coupure basse
 * donc beaucoup de lissage, un vrai clignement (vitesse élevée) reçoit une coupure plus haute donc
 * peu de retard -- avec un mécanisme général réutilisable plutôt qu'un hack spécifique aux yeux.
 *
 * Seuils/constantes volontairement **fixes, pas calibrés par utilisateur** -- cohérent avec la
 * conception "mode léger actif sur tous les paliers" discutée pour ce point : une calibration
 * personnalisée reste une piste pour un futur mode expérimental, pas un prérequis ici.
 */

/** EAR observé pour un œil bien ouvert sur le device de test (plage mesurée ~0,35-0,42). */
internal const val EAR_OPEN_REFERENCE = 0.35f

/** EAR observé pour un œil fermé sur le device de test (plage mesurée ~0,06-0,12, marge incluse). */
internal const val EAR_CLOSED_REFERENCE = 0.10f

/**
 * Au-delà de ce niveau d'ouverture EAR lissée (0=fermé, 1=aussi ouvert que [EAR_OPEN_REFERENCE]),
 * le blendshape est considéré comme une fuite probable et atténué -- calé pour couvrir la plage de
 * fuite mesurée (~0,8-1,0 d'ouverture EAR pendant un clin d'œil de l'autre côté) sans toucher aux
 * fermetures partielles réelles.
 */
internal const val EAR_DAMPING_THRESHOLD = 0.7f

/**
 * Paramètres du [OneEuroFilter] appliqué à l'ouverture EAR (voir kdoc du fichier) -- point de
 * départ raisonné, pas encore affiné par mesure device comme l'ont été les constantes ci-dessus :
 * - [EAR_OPENNESS_MIN_CUTOFF] assez bas pour absorber fortement une dérive lente (mesurée à
 *   ~0,01-0,03 unité d'ouverture/s pendant une fermeture tenue) même au repos.
 * - [EAR_OPENNESS_BETA] assez élevé pour qu'un vrai clignement (mesuré à une vitesse bien plus
 *   grande, l'ouverture variant sur ~100-400 ms) fasse rapidement remonter la coupure adaptative et
 *   traverse sans retard perceptible.
 * À réévaluer avec le même protocole de mesure device (log `EarDiag`) que le reste du point 28 si
 * un comportement encore trop mou ou trop nerveux est observé.
 */
internal const val EAR_OPENNESS_MIN_CUTOFF = 0.5f
internal const val EAR_OPENNESS_BETA = 5f

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
 * (déjà lissée par [OneEuroFilter]) indique que l'œil est encore largement ouvert -- sinon renvoie
 * [rawScore] inchangé (les fermetures réelles, même partielles, traversent sans correction).
 * Fonction pure.
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
 * État porté d'une frame à l'autre par [correctEyeBlinkScores] -- un [OneEuroFilter] par œil,
 * initialisé avec [EAR_OPENNESS_MIN_CUTOFF]/[EAR_OPENNESS_BETA].
 */
internal data class EyeBlinkCorrectionState(
    val left: OneEuroFilter = OneEuroFilter(minCutoff = EAR_OPENNESS_MIN_CUTOFF, beta = EAR_OPENNESS_BETA),
    val right: OneEuroFilter = OneEuroFilter(minCutoff = EAR_OPENNESS_MIN_CUTOFF, beta = EAR_OPENNESS_BETA),
)

/**
 * Applique [correctBlinkScore] aux entrées `eyeBlinkLeft`/`eyeBlinkRight` de [blendshapes],
 * d'après l'EAR calculé sur [landmarks] et lissé dans le temps ([OneEuroFilter], voir [elapsedMs]
 * = temps écoulé depuis la frame précédente) -- laisse tous les autres blendshapes inchangés.
 * Renvoie [blendshapes] et [state] tels quels si [landmarks] est vide (pas de correction possible
 * sans landmarks). Fonction pure, seul point d'entrée destiné à être appelé depuis `MainViewModel`
 * (qui porte [EyeBlinkCorrectionState] d'une frame à l'autre).
 */
internal fun correctEyeBlinkScores(
    blendshapes: List<BlendshapeScore>,
    landmarks: List<Pair<Float, Float>>,
    state: EyeBlinkCorrectionState,
    elapsedMs: Long,
): Pair<List<BlendshapeScore>, EyeBlinkCorrectionState> {
    if (landmarks.isEmpty()) return blendshapes to state

    val leftInstant = earOpenness(eyeAspectRatioFromLandmarks(landmarks, EyeLandmarkIndices.LEFT_EYE))
    val rightInstant = earOpenness(eyeAspectRatioFromLandmarks(landmarks, EyeLandmarkIndices.RIGHT_EYE))
    val nextLeft = state.left.next(leftInstant, elapsedMs)
    val nextRight = state.right.next(rightInstant, elapsedMs)
    // .value ne peut être nul qu'avant le tout premier appel à .next() -- on vient juste d'en faire
    // un, donc jamais nul ici en pratique ; repli sur la valeur instantanée par pure prudence de
    // type plutôt que de forcer un !! qui planterait si ce n'était pas le cas.
    val leftOpenness = nextLeft.value ?: leftInstant
    val rightOpenness = nextRight.value ?: rightInstant

    val corrected = blendshapes.map { score ->
        when (score.name) {
            "eyeBlinkLeft" -> score.copy(score = correctBlinkScore(score.score, leftOpenness))
            "eyeBlinkRight" -> score.copy(score = correctBlinkScore(score.score, rightOpenness))
            else -> score
        }
    }
    return corrected to EyeBlinkCorrectionState(nextLeft, nextRight)
}
