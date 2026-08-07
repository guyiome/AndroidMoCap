# AndroidMoCap

Capture de mouvement facial sur Android, transmise en direct à VTube Studio, Blender, Unity ou
VBridger sur le réseau local. Le téléphone devient un tracker facial autonome : aucune app tierce,
aucun service cloud, juste la caméra frontale et un flux de blendshapes envoyé au PC.

Né du constat qu'il n'existe aujourd'hui aucune alternative Android maintenue à MeowFace (abandonné
depuis, sa librairie de tracking sous-jacente ayant été dépréciée) : ce projet vise à combler ce
vide, avec une contrainte propre à Android qu'aucune app du secteur ne peut totalement effacer --
l'absence de capteur de profondeur dédié (contrairement à l'iPhone/TrueDepth) plafonne la précision
atteignable, quelle que soit la qualité du logiciel.

Projet personnel, développé et maintenu par une seule personne, en évolution active.

## Fonctionnalités

- **Sélection automatique du meilleur pipeline** selon les capacités de l'appareil (GPU/CPU, RAM,
  cœurs, support ARCore) -- rien à configurer, l'app s'adapte du haut de gamme à l'entrée de gamme,
  avec repli automatique GPU → CPU si le délégué GPU échoue.
- **52 blendshapes ARKit** via MediaPipe Face Landmarker, plus une estimation de la direction du
  regard (non fournie nativement par MediaPipe, reconstruite à partir des blendshapes oculaires).
- **Double sortie réseau** : protocole VMC/OSC (VTube Studio, Blender, Unity) et protocole
  iFacialMocap/UDP (VBridger).
- **Calibrage de pose neutre** à la demande, avec compte à rebours.
- HUD minimal et lisible quelle que soit l'orientation du téléphone, réglages détaillés (sélection
  des blendshapes affichés, seuil de batterie faible, mode économie d'énergie, overlay de debug du
  mesh de tracking à 478 points).
- **Mode économie d'énergie** : assombrit l'écran et coupe l'aperçu caméra après inactivité sans
  interrompre le tracking ni l'envoi -- pensé pour les sessions de stream longues, téléphone posé
  loin de l'utilisateur.

À l'étude, pas encore implémenté : détection expérimentale de la langue tirée et des joues gonflées
(blendshapes que MediaPipe ne restitue pas de façon fiable), voir la feuille de route plus bas.

## Prérequis

- Android 11 (API 30) ou plus.
- Un **appareil physique** avec caméra frontale -- l'émulateur Android ne fournit pas de flux
  caméra exploitable pour le tracking.
- Téléphone et PC receveur sur le **même réseau Wi-Fi local**.

## Installation

L'app n'est pas distribuée sur le Play Store. Télécharger le dernier APK depuis les
[Releases GitHub](../../releases) et l'installer directement. Android affichera un avertissement
"source inconnue" à l'installation -- normal pour un APK distribué hors store, à autoriser
ponctuellement dans les réglages au moment de l'installation.

## Connexion côté PC

**VTube Studio** : Settings → VTube Studio → VirtualMotionCapture → activer la réception, port
`39539` par défaut (modifiable, doit correspondre à celui utilisé côté app).

**Blender / Unity** : utiliser un addon/package compatible VMC, configuré pour écouter sur le même
port.

**VBridger** : sélectionner le protocole iFacialMocap dans les réglages de l'app, puis suivre les
instructions VBridger en pointant vers l'IP affichée sur le téléphone -- c'est VBridger qui vient se
connecter à l'app, aucune IP à saisir côté téléphone pour ce chemin.

## Vie privée et réseau

L'app ne communique qu'avec la cible choisie dans les réglages, sur le réseau local -- aucun
service tiers, aucune télémétrie, aucune donnée envoyée en dehors de ce flux volontaire vers le
PC receveur.

## Compiler depuis les sources

1. **Télécharger le modèle MediaPipe** (obligatoire, trop volumineux pour être versionné) et le
   placer dans `app/src/main/assets/face_landmarker.task` :
   `https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task`
   Si le lien a changé, repartir de la page officielle
   [Face landmark detection guide for Android](https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker/android)
   (section "Model").
2. Ouvrir le dossier dans Android Studio (`File > Open`). Le premier sync Gradle télécharge AGP,
   Kotlin et les dépendances ARCore/CameraX/MediaPipe/JavaOSC listées dans
   `gradle/libs.versions.toml`.
3. Builder et lancer sur un **appareil physique** (voir Prérequis) -- pas d'émulateur possible pour
   tester le tracking.

## Structure du projet

```
app/src/main/java/com/guyiome/androidmocap/
  MainActivity.kt              Permission caméra + point d'entrée Compose
  capabilities/                Détection des capacités de l'appareil (ARCore, GPU, RAM, thermal)
  tracking/                    Sélection de palier + wrapper MediaPipe Face Landmarker + maths de rotation
  camera/                      Pilotage CameraX (caméra frontale -> MPImage, pool de bitmaps)
  sensors/                     Orientation du téléphone, icônes HUD, batterie
  network/                     Envoi OSC/UDP (VMC) et UDP (iFacialMocap)
  settings/                    Persistance des réglages (DataStore)
  ui/                          ViewModel + écrans Compose (HUD, réglages, overlay mesh)
```

## Tests

Suite de tests unitaires JVM pur (aucune dépendance Android/Robolectric) sous `app/src/test/`.
Détail fonction par fonction, avec les raisons explicites de ce qui n'est volontairement pas
couvert, dans `AndroidMoCap_tests_unitaires.md`. Lancer avec :

```
./gradlew testDebugUnitTest
```

## Documentation

- `AndroidMoCap_spec_fonctionnelle.md` -- ce que l'app fait aujourd'hui, côté utilisateur.
- `AndroidMoCap_spec_technique.md` -- architecture, pipeline de capture, protocoles réseau,
  contraintes non-fonctionnelles.
- `AndroidMoCap_revue_technique.md` -- journal de revue et backlog : raisonnement détaillé derrière
  chaque décision, chantiers en cours ou en réflexion.
- `AndroidMoCap_tests_unitaires.md` -- détail de la couverture de tests.

## Feuille de route

Suivi détaillé dans `AndroidMoCap_revue_technique.md`. Points principaux :

- **Fusion ARCore** (pose de tête, palier `OPTIMAL`) : intégrée sur `main` et fonctionnelle sur
  device (bascule de la source caméra vers ARCore pour ce palier) -- voir le point 13 de la revue
  technique pour le détail et les points mineurs encore ouverts.
- Throttling thermique dynamique : débit cible réduit de moitié pendant une chauffe détectée
  (`DeviceCapabilityDetector.isThermalThrottling`, sondée en continu pendant la capture), remonte
  automatiquement une fois la chauffe retombée -- implémenté, pas encore confirmé sur device, voir
  le point 34 de la revue technique.
- Lissage temporel (One Euro Filter) sur les blendshapes.
- Détection expérimentale de la langue tirée et des joues gonflées, derrière un interrupteur
  "Fonctionnalités expérimentales" dédié -- encore au stade de conception, voir les points 15 et 16.
- Vérification de mise à jour semi-automatique (comparaison au dernier tag GitHub Releases, lien
  direct plutôt qu'installation silencieuse -- impossible hors store).
- R8/minify sur le build release, une fois les tests sur device de nouveau possibles.

## Licence

Distribué sous licence [PolyForm Shield 1.0.0](https://polyformproject.org/licenses/shield/1.0.0)
(voir `LICENSE`) : usage libre, y compris commercial, à l'exception de la construction d'un produit
qui concurrencerait le logiciel lui-même. Ce n'est pas une licence "open source" au sens strict
(OSI) -- code source visible et modifiable pour un usage personnel, mais pas librement
redistribuable comme produit concurrent.

## Contribuer

Voir `CONTRIBUTING.md` avant d'ouvrir une pull request -- toute contribution suppose l'acceptation
de l'accord de licence contributeur (`CLA.md`).

## Publier une version (mainteneur)

Signature configurée via des variables d'environnement (`RELEASE_KEYSTORE_BASE64`,
`RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`), lues en local ou depuis
les secrets GitHub Actions -- jamais commitées. Pousser un tag déclenche la publication :

```
git tag v0.2.0
git push origin v0.2.0
```

Le workflow (`.github/workflows/release.yml`) construit l'APK (téléchargement du modèle MediaPipe
inclus), le signe, et crée une Release GitHub avec l'APK en pièce jointe.
