package com.guyiome.androidmocap.network

import com.guyiome.androidmocap.tracking.BlendshapeCatalog
import com.guyiome.androidmocap.tracking.BlendshapeScore
import com.guyiome.androidmocap.tracking.FaceTrackingResult

/**
 * Traduction des blendshapes ARKit vers les paramètres par défaut de VTube Studio (point 41 de la
 * revue technique), en reprenant telles quelles les formules du panneau `AdvancedARKitSettings` de
 * VBridger -- capturées et vérifiées (calcul à zéro conforme à VBridger) dans
 * `E:\WorkSpaces\VTuberApp\Docs\VBridgerFormulaReference.md` (projet "VTuberApp", même traduction
 * déjà testée côté réception PC). But : qu'un streamer déjà riggé pour VBridger/VTS puisse se passer
 * de VBridger, sans revoir son mapping -- la sortie doit donc être une parité exacte avec ce que
 * VBridger lui-même produirait.
 *
 * ⚠️ **Mode exclusif, pas additif** (correction du 15 août 2026 -- la première version envoyait les
 * 52 blendshapes ARKit bruts EN PLUS des formules composites en pensant reproduire VBridger, ce qui
 * était faux : VBridger n'envoie à VTS que ses paramètres composites, jamais les blendshapes ARKit
 * bruts en paramètres personnalisés à côté). [activeFormulas] retourne donc soit [rawArkitFormulas]
 * (comportement historique de l'app, réglage désactivé), soit [vbridgerCompositeFormulas] seules
 * (réglage activé) -- jamais les deux à la fois.
 *
 * **Modèle unifié** : les 52 blendshapes ARKit bruts sont eux-mêmes modélisés comme des formules
 * triviales (`ParamètreEnvoyé = ParamètreBrut`, [rawArkitFormulas]), au même titre que les formules
 * composites VBridger ([vbridgerCompositeFormulas]) -- une seule structure de formule nommée
 * ([VtsParameterFormula]), pas deux types séparés, même si un seul des deux groupes est actif à la
 * fois côté envoi. Prépare le futur mode d'édition (activer/désactiver ou ajouter une formule, y
 * compris une identité) sans restructurer le code plus tard -- ce que ce mode d'édition ne couvre PAS
 * encore : réassigner quels blendshapes alimentent une formule composite (VBridger le permet dans son
 * propre panneau) -- demanderait de remplacer les lambdas [VtsParameterFormula.compute] par une
 * structure de données générique (liste pondérée d'entrées), un vrai chantier à part vu que certaines
 * formules ne sont pas de simples combinaisons linéaires (ex. [mouthX], un produit de deux termes).
 *
 * [VtsParameterFormula.requiresParameterCreation] reste utile malgré le mode exclusif : les 52 brutes
 * sont des paramètres personnalisés (besoin de `ParameterCreationRequest` avant usage), les
 * composites visent des paramètres déjà natifs à VTS (`InjectParameterDataRequest` accepte d'écrire
 * directement dedans, vérifié sur la doc officielle) -- `VTubeStudioSender` crée donc toujours les 52
 * noms bruts (les seuls à en avoir besoin, un jour ou l'autre si le réglage est basculé en cours de
 * connexion) indépendamment du réglage courant, voir son kdoc.
 *
 * ⚠️ **`EyeLeftX`/`EyeLeftY` ne sont PAS transcrits depuis la capture d'écran source** -- seuls
 * `EyeRightX/Y` étaient visibles. Reconstruits par symétrie miroir (substituer `_L` par `_R` dans la
 * même forme de formule) en s'appuyant sur la convention de paires déjà cohérente partout ailleurs
 * dans le référentiel (`EyeOpenLeft`←`_L`/`EyeOpenRight`←`_R`, `BrowLeftY`←`_L`/`BrowRightY`←`_R`...).
 * Inclus quand même plutôt qu'omis (décision explicite : les omettre créerait aussi un écart de
 * parité, juste dans l'autre sens) -- à vérifier contre un vrai VBridger si l'exactitude devient
 * critique, corrigible via le futur éditeur de formules.
 *
 * **Omissions délibérées, pas des oublis** :
 * - `VoiceVolumePlusMouthOpen`/`VoiceFrequencyPlusMouthSmile` -- VBridger les distingue de
 *   `MouthOpen`/`MouthSmile` uniquement pour y mélanger un signal micro live ; cette app n'a aucun
 *   canal audio, ils seraient de purs doublons.
 * - `FacePositionX/Y/Z`/`BodyPositionX/Y/Z` -- la position de tête n'est jamais calculée côté app
 *   (`headPosX/Y/Z` toujours à 0, même limite déjà documentée côté VBridger), ces formules seraient
 *   inertes. Ne pas les envoyer plutôt qu'envoyer un 0 qui pourrait figer une pose côté modèle.
 */
object VBridgerFormulas {

    /**
     * Une formule nommée -- [outputName] est le paramètre VTS ciblé, [requiresParameterCreation]
     * indique s'il faut passer par `ParameterCreationRequest` avant de pouvoir l'injecter (voir
     * kdoc de tête du fichier), [compute] lit la frame courante (map nom->score + rotation de tête
     * en degrés, `(pitch, yaw, roll)`) et produit la valeur à envoyer.
     */
    data class VtsParameterFormula(
        val outputName: String,
        val requiresParameterCreation: Boolean,
        val compute: (blendshapes: Map<String, Float>, headEulerDegrees: FloatArray) -> Float,
    )

    /**
     * Les 52 blendshapes ARKit, en formules identité -- depuis [BlendshapeCatalog.all] (inclut déjà
     * `tongueOut`) plutôt qu'une liste séparée codée en dur. Corrige au passage un cas limite de
     * l'ancien mécanisme dynamique de `VTubeStudioSender` (noms lus depuis la toute première frame
     * reçue) : `tongueOut` n'est ajouté à `FaceTrackingResult.blendshapes` qu'une fois la cascade de
     * détection langue tirée effectivement déclenchée -- absent de cette première frame, il n'était
     * jusqu'ici jamais créé côté VTS.
     */
    val rawArkitFormulas: List<VtsParameterFormula> = BlendshapeCatalog.all.map { (_, name) ->
        VtsParameterFormula(name, requiresParameterCreation = true) { b, _ -> b[name] ?: 0f }
    }

    /** Les formules composites VBridger (Face/Eyes/Mouth/Brows/Body) -- voir kdoc de tête du fichier. */
    val vbridgerCompositeFormulas: List<VtsParameterFormula> = listOf(
        // --- Face (tête, réaction 1:1) ---
        VtsParameterFormula("FaceAngleX", requiresParameterCreation = false) { _, head -> faceAngleX(head) },
        VtsParameterFormula("FaceAngleY", requiresParameterCreation = false) { b, head -> faceAngleY(b, head) },
        VtsParameterFormula("FaceAngleZ", requiresParameterCreation = false) { _, head -> faceAngleZ(head) },

        // --- Eyes ---
        VtsParameterFormula("EyeOpenLeft", requiresParameterCreation = false) { b, _ -> eyeOpen(b, "eyeBlinkLeft", "eyeWideLeft") },
        VtsParameterFormula("EyeOpenRight", requiresParameterCreation = false) { b, _ -> eyeOpen(b, "eyeBlinkRight", "eyeWideRight") },
        VtsParameterFormula("Eye_Squint_L", requiresParameterCreation = false) { b, _ -> b["eyeSquintLeft"] ?: 0f },
        VtsParameterFormula("Eye_Squint_R", requiresParameterCreation = false) { b, _ -> b["eyeSquintRight"] ?: 0f },
        VtsParameterFormula("EyeRightX", requiresParameterCreation = false) { b, _ -> eyeGazeX(b, "eyeLookInLeft", "eyeLookOutLeft") },
        VtsParameterFormula("EyeRightY", requiresParameterCreation = false) { b, _ -> eyeGazeY(b, "eyeLookUpLeft", "eyeLookDownLeft") },
        // Reconstruits par symétrie miroir, pas transcrits depuis la capture source -- voir kdoc de tête du fichier.
        VtsParameterFormula("EyeLeftX", requiresParameterCreation = false) { b, _ -> eyeGazeX(b, "eyeLookInRight", "eyeLookOutRight") },
        VtsParameterFormula("EyeLeftY", requiresParameterCreation = false) { b, _ -> eyeGazeY(b, "eyeLookUpRight", "eyeLookDownRight") },

        // --- Mouth ---
        VtsParameterFormula("JawOpen", requiresParameterCreation = false) { b, _ -> b["jawOpen"] ?: 0f },
        VtsParameterFormula("MouthOpen", requiresParameterCreation = false) { b, _ -> mouthOpen(b) },
        VtsParameterFormula("MouthSmile", requiresParameterCreation = false) { b, _ -> mouthSmile(b) },
        VtsParameterFormula("MouthX", requiresParameterCreation = false) { b, _ -> mouthX(b) },
        VtsParameterFormula("MouthPucker", requiresParameterCreation = false) { b, _ -> mouthPuckerOut(b) },
        VtsParameterFormula("MouthFunnel", requiresParameterCreation = false) { b, _ -> b["mouthFunnel"] ?: 0f },
        VtsParameterFormula("MouthShrug", requiresParameterCreation = false) { b, _ -> mouthShrug(b) },
        VtsParameterFormula("MouthPressLipOpen", requiresParameterCreation = false) { b, _ -> mouthPressLipOpen(b) },
        VtsParameterFormula("TongueOut", requiresParameterCreation = false) { b, _ -> b["tongueOut"] ?: 0f },
        VtsParameterFormula("CheekPuff", requiresParameterCreation = false) { b, _ -> b["cheekPuff"] ?: 0f },

        // --- Brows ---
        VtsParameterFormula("BrowLeftY", requiresParameterCreation = false) { b, _ ->
            browY(b, outerUpName = "browOuterUpLeft", downName = "browDownLeft", mouthPlusName = "mouthRight", mouthMinusName = "mouthLeft")
        },
        VtsParameterFormula("BrowRightY", requiresParameterCreation = false) { b, _ ->
            browY(b, outerUpName = "browOuterUpRight", downName = "browDownRight", mouthPlusName = "mouthLeft", mouthMinusName = "mouthRight")
        },
        VtsParameterFormula("Brows", requiresParameterCreation = false) { b, _ -> brows(b) },
        VtsParameterFormula("BrowInnerUp", requiresParameterCreation = false) { b, _ -> b["browInnerUp"] ?: 0f },

        // --- Body (copie secondaire de Face, coefficient d'angle x1.5, position omise) ---
        VtsParameterFormula("BodyAngleX", requiresParameterCreation = false) { _, head -> bodyAngleX(head) },
        VtsParameterFormula("BodyAngleY", requiresParameterCreation = false) { b, head -> bodyAngleY(b, head) },
        VtsParameterFormula("BodyAngleZ", requiresParameterCreation = false) { _, head -> bodyAngleZ(head) },
    )

    /**
     * Formules actives compte tenu du réglage -- exclusif, pas additif (voir kdoc de tête du
     * fichier) : les 52 brutes si désactivé, les formules composites VBridger seules si activé.
     */
    fun activeFormulas(useVBridgerTranslation: Boolean): List<VtsParameterFormula> =
        if (useVBridgerTranslation) vbridgerCompositeFormulas else rawArkitFormulas

    /** Évalue [formulas] contre [result] -- construit la map de lookup une seule fois pour tout [formulas]. */
    fun evaluate(formulas: List<VtsParameterFormula>, result: FaceTrackingResult): List<BlendshapeScore> {
        val b = result.blendshapes.associate { it.name to it.score }
        return formulas.map { BlendshapeScore(it.outputName, it.compute(b, result.headEulerDegrees)) }
    }

    // --- Face / Body : headEulerDegrees = (pitch, yaw, roll), même convention que
    // IFacialMocapSender.buildMessage (déjà destiné à nourrir VBridger via le protocole
    // iFacialMocap) -- headRotX=pitch, headRotY=yaw, headRotZ=roll dans le référentiel.
    private const val FACE_ANGLE_RANGE_MIN = -50f
    private const val FACE_ANGLE_RANGE_MAX = 50f
    private const val FACE_ANGLE_EYE_BLINK_COEFFICIENT = -1f
    private const val BODY_ANGLE_COEFFICIENT = 1.5f

    private fun faceAngleX(head: FloatArray): Float =
        (-head[1]).coerceIn(FACE_ANGLE_RANGE_MIN, FACE_ANGLE_RANGE_MAX)

    private fun faceAngleY(b: Map<String, Float>, head: FloatArray): Float {
        val blink = (b["eyeBlinkLeft"] ?: 0f) + (b["eyeBlinkRight"] ?: 0f)
        return (-head[0] + blink * FACE_ANGLE_EYE_BLINK_COEFFICIENT).coerceIn(FACE_ANGLE_RANGE_MIN, FACE_ANGLE_RANGE_MAX)
    }

    private fun faceAngleZ(head: FloatArray): Float =
        head[2].coerceIn(FACE_ANGLE_RANGE_MIN, FACE_ANGLE_RANGE_MAX)

    private fun bodyAngleX(head: FloatArray): Float =
        (-head[1] * BODY_ANGLE_COEFFICIENT).coerceIn(FACE_ANGLE_RANGE_MIN, FACE_ANGLE_RANGE_MAX)

    private fun bodyAngleY(b: Map<String, Float>, head: FloatArray): Float {
        val blink = (b["eyeBlinkLeft"] ?: 0f) + (b["eyeBlinkRight"] ?: 0f)
        return (-head[0] * BODY_ANGLE_COEFFICIENT + blink * FACE_ANGLE_EYE_BLINK_COEFFICIENT)
            .coerceIn(FACE_ANGLE_RANGE_MIN, FACE_ANGLE_RANGE_MAX)
    }

    private fun bodyAngleZ(head: FloatArray): Float =
        (head[2] * BODY_ANGLE_COEFFICIENT).coerceIn(FACE_ANGLE_RANGE_MIN, FACE_ANGLE_RANGE_MAX)

    // --- Eyes ---
    private const val EYE_OPEN_BASELINE = 0.5f
    private const val EYE_OPEN_BLINK_COEFFICIENT = -0.8f
    private const val EYE_OPEN_WIDE_COEFFICIENT = 0.8f
    private const val EYE_GAZE_RANGE_MIN = -0.6f
    private const val EYE_GAZE_RANGE_MAX = 0.6f
    private const val EYE_GAZE_IN_OFFSET = -0.1f

    private fun eyeOpen(b: Map<String, Float>, blinkName: String, wideName: String): Float {
        val blink = b[blinkName] ?: 0f
        val wide = b[wideName] ?: 0f
        return (EYE_OPEN_BASELINE + blink * EYE_OPEN_BLINK_COEFFICIENT + wide * EYE_OPEN_WIDE_COEFFICIENT).coerceIn(0f, 1f)
    }

    private fun eyeGazeX(b: Map<String, Float>, lookInName: String, lookOutName: String): Float {
        val lookIn = b[lookInName] ?: 0f
        val lookOut = b[lookOutName] ?: 0f
        return ((lookIn + EYE_GAZE_IN_OFFSET) - lookOut).coerceIn(EYE_GAZE_RANGE_MIN, EYE_GAZE_RANGE_MAX)
    }

    private fun eyeGazeY(b: Map<String, Float>, lookUpName: String, lookDownName: String): Float {
        val lookUp = b[lookUpName] ?: 0f
        val lookDown = b[lookDownName] ?: 0f
        return (lookUp - lookDown).coerceIn(EYE_GAZE_RANGE_MIN, EYE_GAZE_RANGE_MAX)
    }

    // --- Mouth ---
    private const val MOUTH_OPEN_ROLL_UPPER_COEFFICIENT = 0.1f
    private const val PLUS_MINUS_ONE_RANGE_MIN = -1f
    private const val PLUS_MINUS_ONE_RANGE_MAX = 1f
    private const val MOUTH_SHRUG_DIVISOR = 4f
    private const val MOUTH_PRESS_LIP_OPEN_UP_DIVISOR = 1.2f
    private const val MOUTH_PRESS_LIP_OPEN_RANGE_MIN = -1.3f
    private const val MOUTH_PRESS_LIP_OPEN_RANGE_MAX = 1.3f

    private fun mouthOpen(b: Map<String, Float>): Float {
        val jawOpen = b["jawOpen"] ?: 0f
        val mouthClose = b["mouthClose"] ?: 0f
        val rollUpper = b["mouthRollUpper"] ?: 0f
        return ((jawOpen - mouthClose) - rollUpper * MOUTH_OPEN_ROLL_UPPER_COEFFICIENT).coerceIn(0f, 1f)
    }

    private fun mouthSmile(b: Map<String, Float>): Float {
        val frown = (b["mouthFrownLeft"] ?: 0f) + (b["mouthFrownRight"] ?: 0f) + (b["mouthPucker"] ?: 0f)
        val smile = (b["mouthSmileRight"] ?: 0f) + (b["mouthSmileLeft"] ?: 0f) +
            ((b["mouthDimpleLeft"] ?: 0f) + (b["mouthDimpleRight"] ?: 0f)) / 2f
        return ((2f - frown + smile) / 4f).coerceIn(PLUS_MINUS_ONE_RANGE_MIN, PLUS_MINUS_ONE_RANGE_MAX)
    }

    private fun mouthX(b: Map<String, Float>): Float {
        val left = b["mouthLeft"] ?: 0f
        val right = b["mouthRight"] ?: 0f
        val tongueOut = b["tongueOut"] ?: 0f
        return ((left - right) * (1f - tongueOut)).coerceIn(PLUS_MINUS_ONE_RANGE_MIN, PLUS_MINUS_ONE_RANGE_MAX)
    }

    private fun mouthPuckerOut(b: Map<String, Float>): Float {
        val dimple = ((b["mouthDimpleRight"] ?: 0f) + (b["mouthDimpleLeft"] ?: 0f)) / 2f
        val pucker = b["mouthPucker"] ?: 0f
        val tongueOut = b["tongueOut"] ?: 0f
        return ((dimple - pucker) * (1f - tongueOut)).coerceIn(PLUS_MINUS_ONE_RANGE_MIN, PLUS_MINUS_ONE_RANGE_MAX)
    }

    private fun mouthShrug(b: Map<String, Float>): Float {
        val sum = (b["mouthShrugUpper"] ?: 0f) + (b["mouthShrugLower"] ?: 0f) +
            (b["mouthPressRight"] ?: 0f) + (b["mouthPressLeft"] ?: 0f)
        return (sum / MOUTH_SHRUG_DIVISOR).coerceIn(0f, 1f)
    }

    private fun mouthPressLipOpen(b: Map<String, Float>): Float {
        val up = (b["mouthUpperUpRight"] ?: 0f) + (b["mouthUpperUpLeft"] ?: 0f) +
            (b["mouthLowerDownRight"] ?: 0f) + (b["mouthLowerDownLeft"] ?: 0f)
        val roll = (b["mouthRollLower"] ?: 0f) + (b["mouthRollUpper"] ?: 0f)
        return (up / MOUTH_PRESS_LIP_OPEN_UP_DIVISOR - roll)
            .coerceIn(MOUTH_PRESS_LIP_OPEN_RANGE_MIN, MOUTH_PRESS_LIP_OPEN_RANGE_MAX)
    }

    // --- Brows ---
    private const val BROW_BASELINE = 0.5f
    private const val BROW_MOUTH_CROSS_TALK_DIVISOR = 8f
    private const val BROWS_DIVISOR = 4f

    private fun browY(
        b: Map<String, Float>,
        outerUpName: String,
        downName: String,
        mouthPlusName: String,
        mouthMinusName: String,
    ): Float {
        val outerUp = b[outerUpName] ?: 0f
        val down = b[downName] ?: 0f
        val mouthPlus = b[mouthPlusName] ?: 0f
        val mouthMinus = b[mouthMinusName] ?: 0f
        return (BROW_BASELINE + (outerUp - down) + (mouthPlus - mouthMinus) / BROW_MOUTH_CROSS_TALK_DIVISOR).coerceIn(0f, 1f)
    }

    private fun brows(b: Map<String, Float>): Float {
        val outerUp = (b["browOuterUpRight"] ?: 0f) + (b["browOuterUpLeft"] ?: 0f)
        val down = (b["browDownLeft"] ?: 0f) + (b["browDownRight"] ?: 0f)
        return (BROW_BASELINE + (outerUp - down) / BROWS_DIVISOR).coerceIn(0f, 1f)
    }
}
