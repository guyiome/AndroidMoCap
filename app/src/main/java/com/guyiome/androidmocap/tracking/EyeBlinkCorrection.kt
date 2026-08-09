package com.guyiome.androidmocap.tracking

import kotlin.math.exp

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
 * mémoire confondait cette dérive lente avec une vraie réouverture. Corrigé par
 * [EyeOpennessSmoother] : la fermeture reste suivie instantanément, une remontée de l'ouverture
 * EAR est lissée dans le temps avant d'être crue.
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
 * Constante de temps (ms) du relâchement de l'ouverture lissée -- voir [EyeOpennessSmoother] et le
 * kdoc de ce fichier sur la dérive de landmarks mesurée pendant une fermeture tenue (effondrement
 * complet en ~9 s dans le log incriminé). 3000 ms absorbe largement cette dérive dans sa fenêtre
 * critique (les premières secondes d'une tenue) tout en laissant une vraie réouverture se
 * confirmer en quelques secondes plutôt que de rester bloquée indéfiniment -- valeur de départ à
 * ajuster si le test device montre encore un relâchement trop rapide ou trop lent.
 */
internal const val EAR_OPENNESS_RELEASE_TIME_CONSTANT_MS = 3000f

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
 * (déjà lissée, voir [EyeOpennessSmoother]) indique que l'œil est encore largement ouvert -- sinon
 * renvoie [rawScore] inchangé (les fermetures réelles, même partielles, traversent sans
 * correction). Fonction pure.
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
 * Lissage asymétrique de l'ouverture EAR d'un œil -- "attaque rapide, relâchement lent" (filtre
 * passe-bas exponentiel classique, même famille que ceux utilisés en audio/éclairage). Une
 * fermeture (ouverture qui baisse) est suivie **instantanément**, aucun lissage : on veut réagir
 * tout de suite à un vrai clignement. Une remontée est lissée sur [EAR_OPENNESS_RELEASE_TIME_CONSTANT_MS]
 * -- absorbe la dérive de landmarks mesurée pendant une fermeture tenue (voir kdoc du fichier) sans
 * retarder excessivement une vraie réouverture. Valeur par défaut 1 (œil ouvert) : au départ d'une
 * session, avant toute donnée, mieux vaut ne rien atténuer (comportement neutre, comme avant cette
 * correction) qu'assumer un œil fermé et retarder la toute première lecture d'un œil réellement
 * ouvert. Fonction pure, testable en JVM ; l'appelant ([MainViewModel]) porte l'état d'une frame à
 * l'autre.
 */
internal data class EyeOpennessSmoother(val value: Float = 1f) {
    fun next(
        instantOpenness: Float,
        elapsedMs: Long,
        releaseTimeConstantMs: Float = EAR_OPENNESS_RELEASE_TIME_CONSTANT_MS,
    ): EyeOpennessSmoother {
        if (instantOpenness <= value || elapsedMs <= 0L || releaseTimeConstantMs <= 0f) {
            return EyeOpennessSmoother(instantOpenness)
        }
        val alpha = 1f - exp(-elapsedMs / releaseTimeConstantMs)
        return EyeOpennessSmoother(value + alpha * (instantOpenness - value))
    }
}

/** État porté d'une frame à l'autre par [correctEyeBlinkScores] -- un [EyeOpennessSmoother] par œil. */
internal data class EyeBlinkCorrectionState(
    val left: EyeOpennessSmoother = EyeOpennessSmoother(),
    val right: EyeOpennessSmoother = EyeOpennessSmoother(),
)

/**
 * Applique [correctBlinkScore] aux entrées `eyeBlinkLeft`/`eyeBlinkRight` de [blendshapes],
 * d'après l'EAR calculé sur [landmarks] et lissé dans le temps ([EyeOpennessSmoother], voir
 * [elapsedMs] = temps écoulé depuis la frame précédente) -- laisse tous les autres blendshapes
 * inchangés. Renvoie [blendshapes] et [state] tels quels si [landmarks] est vide (pas de correction
 * possible sans landmarks). Fonction pure, seul point d'entrée destiné à être appelé depuis
 * `MainViewModel` (qui porte [EyeBlinkCorrectionState] d'une frame à l'autre).
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

    val corrected = blendshapes.map { score ->
        when (score.name) {
            "eyeBlinkLeft" -> score.copy(score = correctBlinkScore(score.score, nextLeft.value))
            "eyeBlinkRight" -> score.copy(score = correctBlinkScore(score.score, nextRight.value))
            else -> score
        }
    }
    return corrected to EyeBlinkCorrectionState(nextLeft, nextRight)
}
