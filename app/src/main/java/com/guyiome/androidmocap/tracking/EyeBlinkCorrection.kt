package com.guyiome.androidmocap.tracking

/**
 * Correction du blendshape `eyeBlinkLeft`/`eyeBlinkRight` par l'Eye Aspect Ratio (EAR, voir
 * `EyeAspectRatio.kt`).
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
 * remplacé par [OneEuroFilter] : sa coupure adaptative fait
 * naturellement la même chose -- une dérive lente (vitesse faible) reçoit une forte coupure basse
 * donc beaucoup de lissage, un vrai clignement (vitesse élevée) reçoit une coupure plus haute donc
 * peu de retard -- avec un mécanisme général réutilisable plutôt qu'un hack spécifique aux yeux.
 *
 * **Troisième problème mesuré, cette fois lié à l'angle de caméra** (9 août 2026, téléphone posé
 * sous l'écran donc caméra en légère contre-plongée) : un clin d'œil droit réel et volontaire
 * (brut ~0,55) n'était corrigé qu'à hauteur de ~0,03-0,07 -- la correction anti-fuite écrasait
 * l'essentiel d'une vraie fermeture. Cause : [EAR_CLOSED_REFERENCE] est une constante mesurée face
 * caméra ; à cet angle, l'EAR d'un œil réellement fermé ne descend pas aussi bas (le contour de la
 * paupière reste visible sous un angle différent), donc l'ouverture calculée contre cette référence
 * fixe restait au-dessus de [EAR_DAMPING_THRESHOLD] même œil fermé -- **ici la vraie référence
 * "fermé" est plus haute que la constante historique**, pas plus basse : un simple suivi de minimum
 * (qui ne peut que descendre vite et remonter lentement) ne l'aurait jamais découverte, puisque
 * l'EAR à cet angle ne descend jamais sous la constante fixe pour se faire remarquer comme nouveau
 * minimum.
 *
 * Comme le téléphone ne sera pas toujours dans une position idéale, la référence "fermé" est
 * maintenant suivie dynamiquement par œil ([AdaptiveEarFloor]), calée sur le score brut du
 * blendshape (signal indépendant de l'EAR) plutôt que sur l'EAR seul : quand ce score dépasse
 * [EAR_FLOOR_ACTIVITY_THRESHOLD] (un clignement, réel ou fuite, est probablement en cours), l'EAR
 * minimum observé durant cet "épisode" est retenu, puis mélangé dans la référence une fois
 * l'épisode terminé ([EAR_FLOOR_EPISODE_BLEND]). Prendre le minimum sur tout l'épisode (et non
 * l'EAR de la dernière frame) protège naturellement du deuxième problème ci-dessus : sur une
 * fermeture tenue où l'EAR dérive vers "ouvert" en cours d'épisode, c'est la valeur basse du tout
 * début de l'épisode qui est retenue, pas la valeur déjà remontée. La référence "ouvert"
 * ([EAR_OPEN_REFERENCE]) reste fixe : aucune mesure device n'indique qu'elle soit en cause, seule
 * la référence "fermé" l'était.
 *
 * Seuils/constantes volontairement **fixes, pas calibrés par utilisateur** (sauf la référence
 * "fermé" ci-dessus, désormais auto-adaptative) -- cohérent avec la conception "mode léger actif
 * sur tous les paliers" discutée pour ce point : une calibration manuelle complète reste une piste
 * pour un futur mode expérimental, pas un prérequis ici.
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
 * À réévaluer avec le même protocole de mesure device (log `EarDiag`) que le reste de ce fichier si
 * un comportement encore trop mou ou trop nerveux est observé.
 */
internal const val EAR_OPENNESS_MIN_CUTOFF = 0.5f
internal const val EAR_OPENNESS_BETA = 5f

/**
 * Score brut `eyeBlinkLeft`/`eyeBlinkRight` au-delà duquel [AdaptiveEarFloor] considère qu'un
 * clignement (réel ou fuite -- peu importe pour la calibration, voir plus bas) est probablement en
 * cours sur cet œil. Calé au-dessus du gros de la plage de fuite mesurée (~0,26-0,49)
 * pour limiter les épisodes déclenchés par une pure fuite, sans exiger une
 * fermeture aussi franche que le maximum mesuré (~0,55-0,8) -- pas besoin de capter chaque
 * clignement, seulement d'en échantillonner assez régulièrement pour calibrer.
 */
internal const val EAR_FLOOR_ACTIVITY_THRESHOLD = 0.45f

/**
 * Poids du minimum d'un épisode de clignement dans la mise à jour d'[AdaptiveEarFloor] une fois cet
 * épisode terminé -- volontairement partiel (pas un remplacement pur) pour qu'un épisode isolé
 * pollué (ex. fuite passée au-dessus du seuil d'activité par erreur) ne redéfinisse pas la
 * référence à lui seul ; elle converge sur plusieurs épisodes plutôt que sur le dernier vu.
 */
internal const val EAR_FLOOR_EPISODE_BLEND = 0.3f

/**
 * Référence EAR "fermé" suivie dynamiquement par œil, remplaçant la constante fixe
 * [EAR_CLOSED_REFERENCE] quand l'angle de caméra réel s'en écarte (voir kdoc du fichier -- clin
 * d'œil droit réel mesuré sur device le 9 août 2026, écrasé par une référence fixe trop basse pour
 * l'angle réel, un simple suivi de minimum n'aurait pas pu la découvrir). [episodeMin] mémorise
 * l'EAR minimum observé depuis le début du clignement en cours ([EAR_FLOOR_ACTIVITY_THRESHOLD]) --
 * `null` en dehors d'un épisode. À la fin de l'épisode (score repassé sous le seuil), ce minimum est
 * mélangé dans [value] via [EAR_FLOOR_EPISODE_BLEND]. Initialisée à [EAR_CLOSED_REFERENCE], qui sert
 * de point de départ raisonnable avant qu'un vrai clignement ne soit observé, pas de vérité
 * absolue. Fonction pure, testable en JVM.
 */
internal data class AdaptiveEarFloor(
    val value: Float = EAR_CLOSED_REFERENCE,
    val episodeMin: Float? = null,
) {
    fun next(ear: Float, rawBlinkScore: Float): AdaptiveEarFloor {
        val active = rawBlinkScore >= EAR_FLOOR_ACTIVITY_THRESHOLD
        return when {
            active -> copy(episodeMin = minOf(episodeMin ?: ear, ear))
            episodeMin != null -> AdaptiveEarFloor(value = value + EAR_FLOOR_EPISODE_BLEND * (episodeMin - value))
            else -> this
        }
    }
}

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
 * État porté d'une frame à l'autre par [correctEyeBlinkScores] -- un [OneEuroFilter] et un
 * [AdaptiveEarFloor] par œil, le premier initialisé avec [EAR_OPENNESS_MIN_CUTOFF]/[EAR_OPENNESS_BETA].
 */
internal data class EyeBlinkCorrectionState(
    val left: OneEuroFilter = OneEuroFilter(minCutoff = EAR_OPENNESS_MIN_CUTOFF, beta = EAR_OPENNESS_BETA),
    val right: OneEuroFilter = OneEuroFilter(minCutoff = EAR_OPENNESS_MIN_CUTOFF, beta = EAR_OPENNESS_BETA),
    val leftFloor: AdaptiveEarFloor = AdaptiveEarFloor(),
    val rightFloor: AdaptiveEarFloor = AdaptiveEarFloor(),
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

    val rawLeft = blendshapes.firstOrNull { it.name == "eyeBlinkLeft" }?.score ?: 0f
    val rawRight = blendshapes.firstOrNull { it.name == "eyeBlinkRight" }?.score ?: 0f
    val leftEar = eyeAspectRatioFromLandmarks(landmarks, EyeLandmarkIndices.LEFT_EYE)
    val rightEar = eyeAspectRatioFromLandmarks(landmarks, EyeLandmarkIndices.RIGHT_EYE)
    val nextLeftFloor = state.leftFloor.next(leftEar, rawLeft)
    val nextRightFloor = state.rightFloor.next(rightEar, rawRight)

    val leftInstant = earOpenness(leftEar, closedReference = nextLeftFloor.value)
    val rightInstant = earOpenness(rightEar, closedReference = nextRightFloor.value)
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
    return corrected to EyeBlinkCorrectionState(nextLeft, nextRight, nextLeftFloor, nextRightFloor)
}
