package com.guyiome.androidmocap.settings

import com.guyiome.androidmocap.logging.AppLog
import com.guyiome.androidmocap.tracking.TongueCalibrationResult
import java.io.File

/**
 * Stockage des deux vecteurs de référence de l'étage 3 de la cascade langue tirée (revue technique,
 * point 15) -- `filesDir` plutôt que DataStore (pensé pour des préférences scalaires, pas des
 * tableaux de floats). Encodage CSV texte (une ligne par vecteur, valeurs séparées par des
 * virgules, `"%.8f"`) plutôt que binaire : un embedding MobileNetV3 fait au plus quelques Ko en
 * texte, largement en dessous de tout souci de taille -- le texte reste inspectable à l'œil (ouvrir
 * le fichier suffit à vérifier que la calibration n'est pas vide/corrompue), même arbitrage
 * simplicité > compacité que le reste du stockage `filesDir` de ce projet (`AppLog`, texte plat).
 * Pas de JSON malgré kotlinx.serialization déjà présent (point 39) : deux tableaux de floats nommés
 * ne justifient pas cet overhead ici, le CSV est strictement plus simple pour ce cas précis. Suit
 * exactement le patron `AppLog.init(filesDir)` : `File` brut, `runCatching`, aucune exception ne
 * doit remonter jusqu'à l'appelant.
 */
internal class TongueCalibrationStore(private val filesDir: File) {

    private companion object {
        private const val TAG = "TongueCalibrationStore"
        private const val FILE_NAME = "tongue_calibration.csv"
    }

    /** `null` si jamais calibré, fichier absent, ou fichier corrompu -- traité comme "jamais
     *  calibré" plutôt que de planter, cohérent avec la discipline "honnêteté du signal" du reste
     *  de la cascade : mieux vaut repartir de zéro que d'utiliser une référence à moitié lue. */
    fun load(): TongueCalibrationResult? = runCatching {
        val file = File(filesDir, FILE_NAME)
        if (!file.exists()) return null
        val lines = file.readLines()
        if (lines.size < 2) return null
        val tongueOut = lines[0].split(",").map { it.toFloat() }.toFloatArray()
        val tongueIn = lines[1].split(",").map { it.toFloat() }.toFloatArray()
        if (tongueOut.isEmpty() || tongueIn.isEmpty()) return null
        TongueCalibrationResult(tongueOut, tongueIn)
    }.onFailure {
        AppLog.w(TAG, "Échec lecture calibration langue, traitée comme absente", it)
    }.getOrNull()

    fun save(result: TongueCalibrationResult) {
        runCatching {
            filesDir.mkdirs()
            val file = File(filesDir, FILE_NAME)
            val text = buildString {
                appendLine(result.tongueOutReference.joinToString(",") { "%.8f".format(it) })
                appendLine(result.tongueInReference.joinToString(",") { "%.8f".format(it) })
            }
            file.writeText(text)
        }.onFailure {
            AppLog.e(TAG, "Échec écriture calibration langue", it)
        }
    }

    fun clear() {
        runCatching { File(filesDir, FILE_NAME).delete() }
            .onFailure { AppLog.w(TAG, "Échec suppression calibration langue", it) }
    }
}
