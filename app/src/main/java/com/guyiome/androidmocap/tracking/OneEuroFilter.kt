package com.guyiome.androidmocap.tracking

import kotlin.math.PI
import kotlin.math.abs

/**
 * One Euro Filter (Casiez, Roussel & Vogel, "1€ Filter: A Simple Speed-based Low-pass Filter for
 * Noisy Input in Interactive Systems", CHI 2012) -- filtre passe-bas à fréquence de coupure
 * *adaptative* : à vitesse de variation faible, coupure basse (lisse fort, tue le bruit/tremblement
 * au repos) ; à vitesse élevée, la coupure remonte (lisse moins, réduit le retard sur un mouvement
 * rapide). Généralisation du bricolage ponctuel [EyeOpennessSmoother] (revue technique, point
 * 28/45) -- outil réutilisable pour n'importe quel signal continu bruité (blendshapes, futures
 * cascades géométriques...), pas spécifique aux yeux.
 *
 * Deux paramètres à régler par usage :
 * - [minCutoff] (Hz) : plus bas = moins de tremblement au repos, mais plus de retard.
 * - [beta] : plus haut = moins de retard sur un mouvement rapide, au prix d'un peu plus de bruit
 *   laissé passer pendant ce mouvement.
 * - [dCutoff] (Hz) : coupure fixe utilisée pour lisser l'estimation de la dérivée elle-même avant
 *   qu'elle ne pilote la coupure adaptative -- 1,0 est la valeur par défaut recommandée par les
 *   auteurs, rarement besoin d'y toucher.
 *
 * Fonction pure, testable en JVM, basée sur le temps réel écoulé entre appels ([elapsedMs], comme
 * [EyeOpennessSmoother]) plutôt qu'un compte de frames -- comportement cohérent quel que soit le
 * palier/débit courant. [value] est `null` tant qu'aucun échantillon n'a été reçu (contrairement à
 * [EyeOpennessSmoother], cet outil générique n'assume aucune valeur de repos propre à un domaine
 * particulier -- à l'appelant de décider quoi faire avant le premier échantillon).
 */
internal data class OneEuroFilter(
    val minCutoff: Float = 1f,
    val beta: Float = 0f,
    val dCutoff: Float = 1f,
    val value: Float? = null,
    val derivative: Float = 0f,
) {
    fun next(newValue: Float, elapsedMs: Long): OneEuroFilter {
        val previous = value
        if (previous == null || elapsedMs <= 0L) {
            // Premier échantillon (ou timestamp invalide) : rien à lisser, la dérivée part de
            // zéro -- comme EyeOpennessSmoother, on ne retarde pas la toute première lecture.
            return copy(value = newValue, derivative = 0f)
        }
        val elapsedSeconds = elapsedMs / 1000f

        // Dérivée instantanée, elle-même lissée (coupure fixe dCutoff) avant de servir à adapter
        // la coupure principale -- évite qu'un bruit sur la dérivée fasse elle-même trembler la
        // coupure adaptative.
        val instantDerivative = (newValue - previous) / elapsedSeconds
        val derivativeAlpha = smoothingFactor(elapsedSeconds, dCutoff)
        val smoothedDerivative = derivativeAlpha * instantDerivative + (1f - derivativeAlpha) * derivative

        val adaptiveCutoff = minCutoff + beta * abs(smoothedDerivative)
        val alpha = smoothingFactor(elapsedSeconds, adaptiveCutoff)
        val smoothedValue = alpha * newValue + (1f - alpha) * previous

        return copy(value = smoothedValue, derivative = smoothedDerivative)
    }

    private companion object {
        /** `r/(r+1)` avec `r = 2π·coupure·Δt` -- coefficient de lissage standard associé à une
         * fréquence de coupure donnée pour un filtre passe-bas exponentiel. Tend vers 1 (aucun
         * lissage) quand la coupure devient grande, vers 0 (figé) quand elle tend vers zéro. */
        fun smoothingFactor(elapsedSeconds: Float, cutoffHz: Float): Float {
            val r = 2f * PI.toFloat() * cutoffHz * elapsedSeconds
            return r / (r + 1f)
        }
    }
}
