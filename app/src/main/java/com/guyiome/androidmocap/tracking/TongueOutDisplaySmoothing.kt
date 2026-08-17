package com.guyiome.androidmocap.tracking

/**
 * Lissage du signal `tongueOut` : maintient l'état à "TONGUE_OUT" pendant [holdMs] après la
 * dernière classification positive, plutôt que de retomber à 0 dès la frame suivante. Motivé par
 * un retour utilisateur (11 août 2026) : pendant une tenue réelle de langue tirée, l'étage 3 alterne
 * souvent `TONGUE_OUT`/`UNDECIDED` d'une frame à l'autre (jamais `TONGUE_IN` à tort dans les données
 * collectées ce jour-là -- juste "confiant" vs "pas sûr"), ce qui se traduisait par un clignotement
 * 1/0 rapide et gênant à l'écran plutôt qu'un signal stable. Ne touche à aucune décision de la
 * cascade elle-même (`etage3`/`simOut`/`simIn` restent inchangés, purement diagnostiques dans les
 * logs) -- lisse uniquement la valeur affichée.
 *
 * ⚠️ **Piloté à l'origine le panneau de test LOCAL uniquement, plus depuis le 13 août 2026** : une
 * fois l'injection réseau activée, `MainViewModel.handleTrackingResult`
 * réutilise directement la sortie de [TongueOutDisplayState.next] comme valeur `tongueOut` envoyée à
 * VMC/iFacialMocap/VTube Studio -- ce n'est plus un lissage purement cosmétique, une modification ici
 * change aussi ce qui part sur le réseau.
 */
internal const val DEFAULT_TONGUE_OUT_DISPLAY_HOLD_MS = 300L

internal data class TongueOutDisplayState(
    val displayedAsOut: Boolean = false,
    // Long.MAX_VALUE / 2 plutôt que MAX_VALUE seul : évite tout risque de dépassement (overflow) si
    // next() additionne encore un elapsedMs avant qu'une classification positive ne survienne.
    val msSinceLastPositive: Long = Long.MAX_VALUE / 2,
)

internal fun TongueOutDisplayState.next(
    classification: TongueEmbeddingClassification?,
    elapsedMs: Long,
    holdMs: Long = DEFAULT_TONGUE_OUT_DISPLAY_HOLD_MS,
): TongueOutDisplayState {
    if (classification == TongueEmbeddingClassification.TONGUE_OUT) {
        return TongueOutDisplayState(displayedAsOut = true, msSinceLastPositive = 0L)
    }
    val elapsedSincePositive = msSinceLastPositive + elapsedMs.coerceAtLeast(0L)
    return TongueOutDisplayState(
        displayedAsOut = elapsedSincePositive < holdMs,
        msSinceLastPositive = elapsedSincePositive,
    )
}
