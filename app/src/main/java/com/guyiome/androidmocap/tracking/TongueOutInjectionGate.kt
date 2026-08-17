package com.guyiome.androidmocap.tracking

/**
 * Anti-rebond avant d'injecter réellement `tongueOut` dans les données envoyées au réseau
 * (activé le 13 août 2026 après deux sessions de test device avec la
 * calibration élargie au mouvement de mâchoire, voir `TongueCalibrationRecordingState.kt`).
 *
 * Ces deux sessions ont confirmé une nette amélioration (5/8 faux `TONGUE_OUT` sur l'ancienne
 * calibration statique, contre 0/98 puis 3/103 sur la nouvelle) mais **pas un zéro absolu** -- le
 * résidu observé est fait d'erreurs isolées (une frame seule, ou deux consécutives au maximum),
 * jamais une tenue soutenue.
 *
 * ⚠️ **Seuil abaissé de 3 à 2 le 13 août 2026, retour utilisateur immédiat** : à 3, la détection
 * devenait très difficile à déclencher en usage normal (retour direct : "aucune différence de mon
 * côté", confirmé dans les logs -- un geste "langue dehors" déclaré tenu ~9s n'a confirmé qu'une
 * seule fois sur toute la fenêtre, la tenue soutenue n'étant pas aussi ininterrompue en pratique
 * que ce qu'avaient laissé penser les deux sessions de calibrage). Exiger seulement
 * [DEFAULT_TONGUE_OUT_INJECTION_CONSECUTIVE_FRAMES] classements consécutifs **rouvre
 * explicitement** le résidu "paire consécutive" observé lors du calage de la calibration (1 cas sur
 * 103 échantillons) -- seule la frame isolée reste filtrée. Compromis assumé : mieux vaut une
 * détection réellement utilisable avec un risque résiduel connu et faible, qu'un filtre trop
 * strict pour être exploitable au quotidien.
 */
internal const val DEFAULT_TONGUE_OUT_INJECTION_CONSECUTIVE_FRAMES = 2

internal data class TongueOutInjectionState(val consecutiveOutFrames: Int = 0)

/** Incrémente sur `TONGUE_OUT`, remet à zéro sur tout le reste (`TONGUE_IN`, `UNDECIDED`, `null`
 *  -- pas de distinction entre eux, aucun n'est un indice positif). */
internal fun TongueOutInjectionState.next(classification: TongueEmbeddingClassification?): TongueOutInjectionState =
    TongueOutInjectionState(
        consecutiveOutFrames = if (classification == TongueEmbeddingClassification.TONGUE_OUT) consecutiveOutFrames + 1 else 0,
    )

internal fun TongueOutInjectionState.confirmed(
    requiredConsecutiveFrames: Int = DEFAULT_TONGUE_OUT_INJECTION_CONSECUTIVE_FRAMES,
): Boolean = consecutiveOutFrames >= requiredConsecutiveFrames

/**
 * `true` si cette frame apporte une information exploitable pour l'anti-rebond -- auquel cas
 * l'appelant doit appeler [next] ; `false` si l'appelant doit laisser l'état inchangé plutôt que
 * de le réinitialiser à tort.
 *
 * ⚠️ **Ajouté le 13 août 2026** (revue de code) : avant cette
 * fonction, `next()` était appelé à *chaque frame caméra traitée*, y compris celles où aucune
 * classification n'avait été tentée -- une frame où l'étage 1 (`jawOpenGateOpen`) est ouvert mais
 * où `embed()` a été sauté par la contre-pression anti-ANR
 * (`MainViewModel.TONGUE_EMBEDDING_MIN_INTERVAL_MS`, ~150ms) passait alors `null` à `next()`,
 * remettant [TongueOutInjectionState.consecutiveOutFrames] à zéro exactement comme une vraie
 * classification négative. Or les frames caméra arrivent à 20-60Hz (16-50ms) tandis qu'une
 * classification fraîche n'est disponible que toutes les ~150-230ms (mesuré sur device) --
 * presque toutes les frames entre deux tentatives réussies remettaient donc le compteur à zéro
 * avant qu'il puisse jamais atteindre [DEFAULT_TONGUE_OUT_INJECTION_CONSECUTIVE_FRAMES], rendant
 * la confirmation quasi impossible en pratique malgré une classification correcte et répétée.
 *
 * Distinction retenue : [stage1Open] `false` (bouche géométriquement trop fermée pour qu'une
 * langue soit visible) est un vrai signal négatif -- doit réinitialiser, comme avant. [stage1Open]
 * `true` mais [classification] `null` (throttle, pas encore calibré, échec de recadrage/bitmap)
 * n'apporte aucune information -- ne doit pas réinitialiser, l'état doit attendre la prochaine
 * vraie tentative.
 */
internal fun tongueOutInjectionShouldUpdate(
    stage1Open: Boolean,
    classification: TongueEmbeddingClassification?,
): Boolean = !stage1Open || classification != null
