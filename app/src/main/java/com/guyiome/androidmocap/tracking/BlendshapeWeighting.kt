package com.guyiome.androidmocap.tracking

/** Amplitude du réglage (voir `BlendshapesScreen`, réglable par pas continus via un `Slider`). */
const val BLENDSHAPE_WEIGHT_MIN = 0.5f
const val BLENDSHAPE_WEIGHT_MAX = 2f

/** Poids neutre -- valeur par défaut pour tout blendshape jamais réglé. */
const val BLENDSHAPE_WEIGHT_DEFAULT = 1f

/**
 * Applique un poids par blendshape (réglage utilisateur, `AppSettingsStore.blendshapeWeights`)
 * -- multiplie chaque score par son poids ([BLENDSHAPE_WEIGHT_DEFAULT] pour toute entrée
 * absente de [weights]), puis borne à `[0,1]` pour rester dans la plage documentée d'un
 * blendshape ARKit : un poids > 1 sur un score déjà élevé dépasserait sinon 1 sans cette borne.
 * Appelée dans `MainViewModel.handleTrackingResult()` juste après le mode miroir -- le panneau de
 * debug local et tout ce qui part sur le réseau (bruts, formules VBridger) partagent donc la même
 * valeur déjà pondérée, même principe que la correction eyeBlink/le mode miroir/l'injection
 * tongueOut avant ce point.
 */
fun applyBlendshapeWeights(blendshapes: List<BlendshapeScore>, weights: Map<String, Float>): List<BlendshapeScore> =
    blendshapes.map { score ->
        val weight = weights[score.name] ?: BLENDSHAPE_WEIGHT_DEFAULT
        if (weight == BLENDSHAPE_WEIGHT_DEFAULT) {
            score
        } else {
            score.copy(score = (score.score * weight).coerceIn(0f, 1f))
        }
    }
