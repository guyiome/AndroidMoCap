package com.guyiome.androidmocap.tracking

/**
 * Référence "sans langue" du ratio couleur ([tonguePixelRatio]) suivie dynamiquement, remplaçant un
 * seuil absolu fixe -- même principe que [AdaptiveEarFloor] (point 48) : une constante mesurée une
 * fois dérive trop d'une session à l'autre (mesuré sur device le 11 août 2026 -- même geste "bouche
 * ouverte, langue rentrée", deux essais à quelques minutes d'écart : ratio 0,81-0,93 puis 0,95-0,99,
 * plages qui se chevauchent avec celles "langue tirée" mesurées séparément -- un seuil fixe ne
 * survit pas à ce genre de dérive, probablement exposition auto/angle caméra plutôt qu'un vrai
 * changement physique).
 *
 * Contrairement à [AdaptiveEarFloor] (qui suit un minimum pendant un épisode d'activité connu par un
 * signal indépendant, le blendshape brut), pas de signal indépendant équivalent ici pour repérer un
 * "épisode langue tirée" -- c'est justement ce qu'on cherche à détecter. [next] suit donc la
 * référence en continu, mais **ralentit/arrête de suivre quand le ratio courant est déjà nettement
 * au-dessus d'elle** ([elevatedMargin]) : un pic isolé (candidat langue tirée) ne traîne pas la
 * référence vers le haut avec lui, seul le niveau "normal" (bouche fermée/ouverte sans langue, la
 * majorité du temps) la fait dériver.
 */
internal data class TongueColorBaseline(val value: Float = 0f, val sampleCount: Int = 0)

/**
 * Intègre un nouvel échantillon de ratio. Premier appel : la référence part directement de ce
 * ratio (pas de raison de retarder la première lecture, même convention que [OneEuroFilter]).
 * [alpha] contrôle la vitesse d'adaptation (EMA classique) quand le ratio est dans la zone "normale"
 * (`<= value + elevatedMargin`) ; au-dessus, la référence reste inchangée pour cet échantillon.
 */
internal fun TongueColorBaseline.next(ratio: Float, elevatedMargin: Float = 0.02f, alpha: Float = 0.05f): TongueColorBaseline {
    if (sampleCount == 0) return TongueColorBaseline(value = ratio, sampleCount = 1)
    return if (ratio <= value + elevatedMargin) {
        copy(value = value + alpha * (ratio - value), sampleCount = sampleCount + 1)
    } else {
        copy(sampleCount = sampleCount + 1)
    }
}

/**
 * `true` si [ratio] dépasse la référence courante d'au moins [margin] -- `false` tant qu'aucun
 * échantillon n'a encore été intégré (pas de comparaison possible avant [next]).
 *
 * Marge relevée 0,02 -> 0,06 après un premier test device (11 août 2026, palier ARCore) : à 0,02,
 * de simples pics de bruit frame-à-frame en "langue rentrée" (jusqu'à 0,9489 mesuré, largement
 * au-dessus de la référence ~0,84-0,85) déclenchaient de faux positifs (32% des frames en phase de
 * rodage, 8% une fois la référence stabilisée) -- la marge doit couvrir ce bruit sans manquer les
 * frames "langue tirée" les plus faibles (minimum mesuré 0,8147 sur ce même essai, donc pas de
 * marge illimitée non plus). Valeur encore provisoire, un seul essai.
 */
internal fun TongueColorBaseline.isElevated(ratio: Float, margin: Float = 0.06f): Boolean =
    sampleCount > 0 && ratio > value + margin
