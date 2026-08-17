package com.guyiome.androidmocap.network

import com.guyiome.androidmocap.tracking.BlendshapeCatalog
import com.guyiome.androidmocap.tracking.BlendshapeScore
import com.guyiome.androidmocap.tracking.FaceTrackingResult

/**
 * Traduction des blendshapes ARKit vers les paramètres par défaut de VTube Studio, en reprenant
 * telles quelles les formules du panneau `AdvancedARKitSettings` de
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
 * ⚠️ **Toutes les formules composites demandent aussi `ParameterCreationRequest`** (correction du
 * même jour -- la version précédente supposait qu'elles visaient toutes des paramètres déjà natifs à
 * VTS, donc jamais besoin de création). Recherché sur la doc officielle et sur un plugin tiers
 * comparable (`VTube-IFacial-Link`, même pont ARKit -> VTS) : VTS a bien un jeu de paramètres natifs
 * (`FaceAngleX`, `MouthSmile`, `EyeOpenLeft`...), mais les sources disponibles se contredisent sur le
 * détail exact de ce jeu -- certaines formules composites d'ici (`EyeSquintL/R`, `MouthPucker`,
 * `BrowInnerUp`, le groupe `Body*`...) sont très probablement des ajouts propres à VBridger, pas des
 * natifs VTS, et ont donc réellement besoin d'être créées -- **confirmé sur device pour
 * `EyeSquintL/R`** (voir capture d'écran de l'utilisateur, éditeur de paramètres VTS : deux entrées
 * distinctes `EyeSquintL`/`EyeSquintR` marquées "VBridger", bien séparées des nôtres). Plutôt que de
 * deviner laquelle des 28 est native et laquelle ne l'est pas depuis une doc tierce non fiable,
 * [VtsParameterFormula.requiresParameterCreation] vaut `true` pour les 80 formules (52 brutes + 28
 * composites) sans distinction. Pour les formules qui s'avèrent réellement déjà natives à VTS, une
 * tentative de création est sans conséquence -- déjà géré avec tolérance par le code existant
 * (`APIError` pendant `Authenticated` : le paramètre concerné est simplement journalisé puis ignoré,
 * sans faire échouer la connexion, voir `VTubeStudioSender.onTextMessage`). `VTubeStudioSender` ne
 * crée toutefois que le groupe **actuellement actif** (pas les deux systématiquement, essayé un temps
 * puis abandonné le même jour -- ça laissait des paramètres personnalisés inutilisés visibles côté VTS
 * pour le groupe inactif, gonflant le compte de paramètres constaté sur device), voir son kdoc.
 *
 * ⚠️ **Coquille de transcription trouvée et corrigée sur retour device (15 août 2026)** : le
 * référentiel donnait `Eye_Squint_L`/`Eye_Squint_R` (avec underscores) -- seuls noms de tout le jeu de
 * 28 formules à en avoir, alors que VBridger utilise en réalité `EyeSquintL`/`EyeSquintR` (confirmé
 * par la capture d'écran ci-dessus : le nom sans underscore, pas le nôtre, apparaît étiqueté
 * "VBridger"). Avec l'ancien nom, notre valeur partait dans un paramètre personnalisé distinct plutôt
 * que de rejoindre celui de VBridger -- corrigé, plus aucun autre nom du jeu ne contient d'underscore
 * (bon signe qu'il n'y a pas d'autre coquille du même genre, mais pas vérifié un par un contre un vrai
 * VBridger).
 *
 * ⚠️ **Croisement `_L`/`_R` du référentiel retiré de `EyeRightX/Y`/`EyeLeftX/Y`** (correction du 15
 * août 2026, retour utilisateur) : le référentiel `VBridgerFormulaReference.md` construit
 * délibérément `EyeRightX/Y` depuis les blendshapes `_L` (et vice versa), un miroir intégré à la
 * formule elle-même -- mais c'est la SEULE formule Eyes à le faire, `EyeOpenLeft/Right`,
 * `EyeSquintL/R` etc. lisent toutes leur propre côté directement, sans croisement. Combiné au fait
 * que ce fichier reçoit désormais toujours des données déjà mirrorées de façon uniforme par
 * `MainViewModel` (voir plus bas), garder ce croisement aurait fait subir un miroir supplémentaire
 * au seul regard, incohérent avec le reste des champs Eyes -- corrigé en lecture directe partout
 * (`EyeRightX/Y` lit `_R`, `EyeLeftX/Y` lit `_L`), même convention que tout le reste du fichier.
 *
 * ⚠️ **Aucun miroir ne doit être intégré dans ces formules -- c'est le réglage `mirrorModeEnabled`
 * de l'app, appliqué en amont par `MainViewModel`, qui décide, de façon uniforme pour tous les
 * champs (tête, blendshapes gauche/droite, regard).** Une première tentative avait fait évaluer ces
 * formules sur la donnée *avant* ce mode miroir, en pensant que les formules composites avaient
 * besoin d'un flux brut/anatomique -- source d'une incohérence détectée par l'utilisateur (le regard,
 * seul champ avec un miroir intégré à la formule d'origine, se retrouvait mirroré différemment du
 * reste). `MainViewModel` envoie donc à nouveau `finalToSend` (déjà mirroré de façon uniforme, comme
 * pour tous les autres envois réseau) à ce fichier, quel que soit le réglage de traduction -- voir son
 * commentaire au site d'appel.
 *
 * ⚠️ **`EyeLeftX`/`EyeLeftY` ne sont PAS transcrits depuis la capture d'écran source** -- seuls
 * `EyeRightX/Y` étaient visibles. Reconstruits par symétrie de paire (même forme de formule que
 * `EyeRightX/Y`, substituant simplement le côté lu) en s'appuyant sur la convention de paires déjà
 * cohérente partout ailleurs dans le référentiel (`EyeOpenLeft`/`EyeOpenRight`,
 * `BrowLeftY`/`BrowRightY`...). Inclus quand même plutôt qu'omis (décision explicite : les omettre
 * créerait aussi un écart de parité, juste dans l'autre sens) -- à vérifier contre un vrai VBridger si
 * l'exactitude devient
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
        VtsParameterFormula("FaceAngleX", requiresParameterCreation = true) { _, head -> faceAngleX(head) },
        VtsParameterFormula("FaceAngleY", requiresParameterCreation = true) { b, head -> faceAngleY(b, head) },
        VtsParameterFormula("FaceAngleZ", requiresParameterCreation = true) { _, head -> faceAngleZ(head) },

        // --- Eyes ---
        VtsParameterFormula("EyeOpenLeft", requiresParameterCreation = true) { b, _ -> eyeOpen(b, "eyeBlinkLeft", "eyeWideLeft") },
        VtsParameterFormula("EyeOpenRight", requiresParameterCreation = true) { b, _ -> eyeOpen(b, "eyeBlinkRight", "eyeWideRight") },
        VtsParameterFormula("EyeSquintL", requiresParameterCreation = true) { b, _ -> b["eyeSquintLeft"] ?: 0f },
        VtsParameterFormula("EyeSquintR", requiresParameterCreation = true) { b, _ -> b["eyeSquintRight"] ?: 0f },
        // Lecture directe (côté droit -> _R), pas le croisement _L du référentiel d'origine -- voir
        // kdoc de tête du fichier (cohérence du miroir avec le reste des formules Eyes).
        VtsParameterFormula("EyeRightX", requiresParameterCreation = true) { b, _ -> eyeGazeX(b, "eyeLookInRight", "eyeLookOutRight") },
        VtsParameterFormula("EyeRightY", requiresParameterCreation = true) { b, _ -> eyeGazeY(b, "eyeLookUpRight", "eyeLookDownRight") },
        // Reconstruits par symétrie de paire (pas transcrits depuis la capture source, voir kdoc de
        // tête du fichier) -- lecture directe côté gauche -> _L, même raisonnement que ci-dessus.
        VtsParameterFormula("EyeLeftX", requiresParameterCreation = true) { b, _ -> eyeGazeX(b, "eyeLookInLeft", "eyeLookOutLeft") },
        VtsParameterFormula("EyeLeftY", requiresParameterCreation = true) { b, _ -> eyeGazeY(b, "eyeLookUpLeft", "eyeLookDownLeft") },

        // --- Mouth ---
        VtsParameterFormula("JawOpen", requiresParameterCreation = true) { b, _ -> b["jawOpen"] ?: 0f },
        VtsParameterFormula("MouthOpen", requiresParameterCreation = true) { b, _ -> mouthOpen(b) },
        VtsParameterFormula("MouthSmile", requiresParameterCreation = true) { b, _ -> mouthSmile(b) },
        VtsParameterFormula("MouthX", requiresParameterCreation = true) { b, _ -> mouthX(b) },
        VtsParameterFormula("MouthPucker", requiresParameterCreation = true) { b, _ -> mouthPuckerOut(b) },
        VtsParameterFormula("MouthFunnel", requiresParameterCreation = true) { b, _ -> b["mouthFunnel"] ?: 0f },
        VtsParameterFormula("MouthShrug", requiresParameterCreation = true) { b, _ -> mouthShrug(b) },
        VtsParameterFormula("MouthPressLipOpen", requiresParameterCreation = true) { b, _ -> mouthPressLipOpen(b) },
        VtsParameterFormula("TongueOut", requiresParameterCreation = true) { b, _ -> b["tongueOut"] ?: 0f },
        VtsParameterFormula("CheekPuff", requiresParameterCreation = true) { b, _ -> b["cheekPuff"] ?: 0f },

        // --- Brows ---
        VtsParameterFormula("BrowLeftY", requiresParameterCreation = true) { b, _ ->
            browY(b, outerUpName = "browOuterUpLeft", downName = "browDownLeft", mouthPlusName = "mouthRight", mouthMinusName = "mouthLeft")
        },
        VtsParameterFormula("BrowRightY", requiresParameterCreation = true) { b, _ ->
            browY(b, outerUpName = "browOuterUpRight", downName = "browDownRight", mouthPlusName = "mouthLeft", mouthMinusName = "mouthRight")
        },
        VtsParameterFormula("Brows", requiresParameterCreation = true) { b, _ -> brows(b) },
        VtsParameterFormula("BrowInnerUp", requiresParameterCreation = true) { b, _ -> b["browInnerUp"] ?: 0f },

        // --- Body (copie secondaire de Face, coefficient d'angle x1.5, position omise) ---
        VtsParameterFormula("BodyAngleX", requiresParameterCreation = true) { _, head -> bodyAngleX(head) },
        VtsParameterFormula("BodyAngleY", requiresParameterCreation = true) { b, head -> bodyAngleY(b, head) },
        VtsParameterFormula("BodyAngleZ", requiresParameterCreation = true) { _, head -> bodyAngleZ(head) },
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
        head[1].coerceIn(FACE_ANGLE_RANGE_MIN, FACE_ANGLE_RANGE_MAX)

    private fun faceAngleY(b: Map<String, Float>, head: FloatArray): Float {
        val blink = (b["eyeBlinkLeft"] ?: 0f) + (b["eyeBlinkRight"] ?: 0f)
        return (-head[0] + blink * FACE_ANGLE_EYE_BLINK_COEFFICIENT).coerceIn(FACE_ANGLE_RANGE_MIN, FACE_ANGLE_RANGE_MAX)
    }

    // Signe inversé par rapport au référentiel d'origine (qui donnait `headRotZ * 1`) -- retour
    // utilisateur sur device (15 août 2026) : l'inclinaison de tête (roulis) bougeait bien avec le
    // miroir mais dans le mauvais sens. Convention d'axe VTS opposée à celle du référentiel pour cet
    // axe, indépendante de la logique de miroir elle-même (déjà correcte -- confirmé "ça bouge").
    private fun faceAngleZ(head: FloatArray): Float =
        (-head[2]).coerceIn(FACE_ANGLE_RANGE_MIN, FACE_ANGLE_RANGE_MAX)

    private fun bodyAngleX(head: FloatArray): Float =
        (head[1] * BODY_ANGLE_COEFFICIENT).coerceIn(FACE_ANGLE_RANGE_MIN, FACE_ANGLE_RANGE_MAX)

    private fun bodyAngleY(b: Map<String, Float>, head: FloatArray): Float {
        val blink = (b["eyeBlinkLeft"] ?: 0f) + (b["eyeBlinkRight"] ?: 0f)
        return (-head[0] * BODY_ANGLE_COEFFICIENT + blink * FACE_ANGLE_EYE_BLINK_COEFFICIENT)
            .coerceIn(FACE_ANGLE_RANGE_MIN, FACE_ANGLE_RANGE_MAX)
    }

    private fun bodyAngleZ(head: FloatArray): Float =
        (-head[2] * BODY_ANGLE_COEFFICIENT).coerceIn(FACE_ANGLE_RANGE_MIN, FACE_ANGLE_RANGE_MAX)

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
        // Signe inversé par rapport au référentiel d'origine -- retour utilisateur sur device
        // (15 août 2026) : le regard bougeait bien avec le miroir mais dans le mauvais sens
        // horizontalement, même après le retrait du croisement _L/_R. Convention d'axe VTS opposée à
        // celle du référentiel pour cet axe, indépendante de la logique de miroir elle-même (qui,
        // elle, était déjà correcte -- confirmé par l'utilisateur : "ça bouge" avec le réglage).
        return (-((lookIn + EYE_GAZE_IN_OFFSET) - lookOut)).coerceIn(EYE_GAZE_RANGE_MIN, EYE_GAZE_RANGE_MAX)
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

    // `mouthDimpleLeft`/`mouthDimpleRight` sont répertoriés peu fiables chez MediaPipe
    // (BlendshapeCatalog.unreliable, quasi immobiles ou très bruités) -- confirmé par l'utilisateur
    // le 15 août 2026 (plafonnent vers 0.5 même en forçant l'expression). Poids réduit plutôt que
    // retiré (décision explicite : garder une trace du référentiel VBridger d'origine) dans
    // [mouthSmile]/[mouthPuckerOut], les deux seules formules composites à s'appuyer dessus -- laisse
    // les composantes plus fiables de chaque formule (le sourire lui-même, `mouthPucker` brut)
    // dominer le résultat plutôt que d'hériter directement du bruit de ce blendshape précis.
    private const val MOUTH_DIMPLE_COEFFICIENT = 0.5f

    private fun mouthOpen(b: Map<String, Float>): Float {
        val jawOpen = b["jawOpen"] ?: 0f
        val mouthClose = b["mouthClose"] ?: 0f
        val rollUpper = b["mouthRollUpper"] ?: 0f
        return ((jawOpen - mouthClose) - rollUpper * MOUTH_OPEN_ROLL_UPPER_COEFFICIENT).coerceIn(0f, 1f)
    }

    private fun mouthSmile(b: Map<String, Float>): Float {
        val frown = (b["mouthFrownLeft"] ?: 0f) + (b["mouthFrownRight"] ?: 0f) + (b["mouthPucker"] ?: 0f)
        val dimple = ((b["mouthDimpleLeft"] ?: 0f) + (b["mouthDimpleRight"] ?: 0f)) / 2f
        val smile = (b["mouthSmileRight"] ?: 0f) + (b["mouthSmileLeft"] ?: 0f) + dimple * MOUTH_DIMPLE_COEFFICIENT
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
        return ((dimple * MOUTH_DIMPLE_COEFFICIENT - pucker) * (1f - tongueOut))
            .coerceIn(PLUS_MINUS_ONE_RANGE_MIN, PLUS_MINUS_ONE_RANGE_MAX)
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
