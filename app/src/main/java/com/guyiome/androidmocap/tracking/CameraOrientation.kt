package com.guyiome.androidmocap.tracking

/**
 * Rotation caméra selon l'orientation physique réelle du téléphone (revue technique, points 1/15/
 * 20) -- pur, aucune dépendance Android, volontairement séparé de [RotationMath] qui reste dédié à
 * l'espace matriciel (composition de poses), pas à ce genre de logique en degrés/paliers.
 *
 * Contexte : l'app est verrouillée portrait (`AndroidManifest.xml`), mais rien dans le code ne lit
 * l'orientation *physique* réelle du téléphone avant ce correctif -- les deux chemins caméra
 * (`CameraController`/CameraX, `ArCoreHeadPoseTracker`/ARCore) supposaient à tort que "fenêtre
 * verrouillée portrait" impliquait "téléphone tenu portrait", ce qui casse dès que l'utilisateur
 * tient le téléphone en paysage (son cas d'usage le plus fréquent, confirmé 12 août 2026) ou sur
 * une tablette Android 16/17 où l'OS ignore le verrouillage (point 20).
 *
 * Piège Android à connaître (vérifié dans la doc officielle, recoupé avec le kdoc existant
 * d'`ArCoreHeadPoseTracker` qui référence déjà `CameraOrientationUtil`/"destination fixe 0°") :
 * `OrientationEventListener` (utilisé par `IconOrientationTracker`) est **horaire**,
 * `Surface.ROTATION_*` (attendu par `ImageAnalysis.targetRotation` et la formule caméra frontale
 * ci-dessous) est **anti-horaire** -- deux conventions opposées, jamais mélangées sans conversion
 * explicite ([surfaceRotationEquivalentDegrees]).
 */

/**
 * Convention `OrientationEventListener` (0-359°, horaire) -> un des 4 paliers {0,90,180,270},
 * fenêtres de ±45° centrées sur chaque palier. Corrige au passage un trou de la logique ad hoc
 * précédente de `MainScreen.kt` (le cas 135-225° tombait à tort sur 0° au lieu de 180°).
 */
internal fun snapToRotationBucket(rawDegrees: Int): Int {
    val normalized = ((rawDegrees % 360) + 360) % 360
    return when {
        normalized < 45 || normalized >= 315 -> 0
        normalized < 135 -> 90
        normalized < 225 -> 180
        else -> 270
    }
}

/**
 * Inverse la convention horaire ([snapToRotationBucket]/`OrientationEventListener`) vers la
 * convention anti-horaire `Surface.ROTATION_*` qu'attendent à la fois `ImageAnalysis.targetRotation`
 * et [frontCameraRotationDegrees] ci-dessous. 0° et 180° sont des points fixes (symétriques), 90°
 * et 270° s'échangent. Précondition : [bucketDegrees] déjà un des 4 paliers (sortie de
 * [snapToRotationBucket]), pas un angle brut.
 */
internal fun surfaceRotationEquivalentDegrees(bucketDegrees: Int): Int = (360 - bucketDegrees) % 360

/**
 * Formule de compensation de rotation pour caméra frontale, documentée par Google (ML Kit /
 * `CameraOrientationUtil`, cas "opposite facing") : `(sensorOrientation + destination) % 360`.
 * [sensorOrientationDegrees] est la constante matérielle fixe lue via
 * `CameraCharacteristics.SENSOR_ORIENTATION` (ne dépend pas de la tenue physique du téléphone).
 * [destinationRotationDegrees] doit déjà être en convention `Surface.ROTATION_*` -- c'est-à-dire la
 * sortie de [surfaceRotationEquivalentDegrees], **jamais** un palier brut de [snapToRotationBucket].
 * Déjà utilisée implicitement et confirmée sur device pour le cas `destination=0`
 * (`ArCoreHeadPoseTracker`, avant ce correctif) -- ceci en est la généralisation à une destination
 * dynamique, pas une nouvelle hypothèse. Précondition : les deux paramètres sont des multiples de
 * 90° dans [0,270].
 */
internal fun frontCameraRotationDegrees(sensorOrientationDegrees: Int, destinationRotationDegrees: Int): Int =
    (sensorOrientationDegrees + destinationRotationDegrees) % 360
