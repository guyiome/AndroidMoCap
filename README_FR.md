# AndroidMoCap

*🇬🇧 English: [README.md](README.md) · 🇨🇳 简体中文: [README_ZH.md](README_ZH.md) · 🇯🇵 日本語: [README_JA.md](README_JA.md)*

Capture de mouvement facial sur Android, transmise en direct à Blender, Unity, VBridger ou
directement à VTube Studio sur le réseau local. Le téléphone devient un tracker facial autonome :
aucune app tierce, aucun service cloud, juste la caméra frontale et un flux de blendshapes envoyé
au PC.

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
- **Triple sortie réseau, toutes confirmées fonctionnelles sur device** : protocole VMC/OSC
  (Blender, Unity), protocole iFacialMocap/UDP (VBridger), et intégration directe VTube Studio via
  son API Plugin propriétaire (VTube Studio ne reçoit pas VMC/OSC en entrée).
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

**Blender / Unity** : utiliser un addon/package compatible VMC, configuré pour écouter sur le même
port (`39539` par défaut, modifiable côté app).

**VTube Studio** : ne reçoit pas VMC/OSC nativement -- pas d'option de ce genre dans ses réglages.
L'app propose une intégration directe via l'API Plugin propriétaire de VTube Studio (choisir
"VTube Studio" dans les réglages de connexion, IP du PC + port 8001 par défaut) : un popup
d'autorisation apparaît dans VTube Studio à la première connexion, puis les paramètres créés
doivent être mappés une fois dans l'éditeur de paramètres de VTube Studio pour animer le modèle.
Confirmé fonctionnel sur device.

**VBridger** : sélectionner le protocole iFacialMocap dans les réglages de l'app, puis suivre les
instructions VBridger en pointant vers l'IP affichée sur le téléphone -- c'est VBridger qui vient se
connecter à l'app, aucune IP à saisir côté téléphone pour ce chemin.

## Vie privée et réseau

L'app ne communique qu'avec la cible choisie dans les réglages, sur le réseau local -- aucun
service tiers, aucune télémétrie, aucune donnée envoyée en dehors de ce flux volontaire vers le
PC receveur.

**Logs** : conservés localement (fichier privé à l'app, jamais transmis automatiquement), niveau
"Erreur" par défaut, réglable dans Réglages > Journalisation. Peuvent contenir des informations
techniques (erreurs, statut de connexion) et l'adresse IP locale configurée -- jamais de données de
suivi du visage. Les adresses IP sont masquées automatiquement en dehors des builds de
développement. Un bouton "Partager les logs" permet d'envoyer ce fichier (ex. pour signaler un
problème) -- entièrement à l'initiative de l'utilisateur, qui choisit la destination.

## Compiler depuis les sources

1. **Télécharger le modèle MediaPipe** (obligatoire, trop volumineux pour être versionné) et le
   placer dans `app/src/main/assets/face_landmarker.task` :
   `https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task`
   Si le lien a changé, repartir de la page officielle
   [Face landmark detection guide for Android](https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker/android)
   (section "Model").
   - **Optionnel** : pour la détection expérimentale de la langue tirée (étage 3, désactivée par
     défaut), un second modèle, `app/src/main/assets/image_embedder.tflite` :
     `https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_small/float32/latest/mobilenet_v3_small.tflite`.
     L'app compile et fonctionne normalement sans -- seule cette fonctionnalité expérimentale en a besoin.
2. Ouvrir le dossier dans Android Studio (`File > Open`). Le premier sync Gradle télécharge AGP,
   Kotlin et les dépendances ARCore/CameraX/MediaPipe/JavaOSC/nv-websocket-client/kotlinx.serialization
   listées dans `gradle/libs.versions.toml`.
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
  network/                     Envoi OSC/UDP (VMC), UDP (iFacialMocap), WebSocket (API Plugin VTube Studio)
  settings/                    Persistance des réglages (DataStore)
  ui/                          ViewModel + écrans Compose (HUD, réglages, overlay mesh)
```

## Tests

Suite de tests unitaires JVM pur (aucune dépendance Android/Robolectric) sous `app/src/test/`.
Détail fonction par fonction, avec les raisons explicites de ce qui n'est volontairement pas
couvert, dans `docs/AndroidMoCap_unit_tests_FR.md`. Lancer avec :

```
./gradlew testDebugUnitTest
```

## Documentation

- `docs/AndroidMoCap_functional_spec_FR.md` -- ce que l'app fait aujourd'hui, côté utilisateur.
- `docs/AndroidMoCap_technical_spec_FR.md` -- architecture, pipeline de capture, protocoles réseau,
  contraintes non-fonctionnelles.
- `docs/AndroidMoCap_unit_tests_FR.md` -- détail de la couverture de tests.

## Feuille de route

Points principaux encore ouverts :

- Détection expérimentale des joues gonflées (`cheekPuff`) -- même famille que la détection de
  langue tirée déjà implémentée, encore au stade de conception.
- Vérification de mise à jour semi-automatique (comparaison au dernier tag GitHub Releases, lien
  direct plutôt qu'installation silencieuse -- impossible hors store).
- Adaptation des écrans de réglages à l'orientation système sur grand écran (tablette).
- Ajustement de poids/gain par blendshape (+ lissage réglable).

## Licence

Distribué sous licence [PolyForm Shield 1.0.0](https://polyformproject.org/licenses/shield/1.0.0)
(voir `LICENSE` -- seule version faisant foi ; des traductions automatiques non officielles et non
relues existent à titre indicatif dans [LICENSE_ZH.md](LICENSE_ZH.md) et
[LICENSE_JA.md](LICENSE_JA.md)) : usage libre, y compris commercial, à l'exception de la
construction d'un produit qui concurrencerait le logiciel lui-même. Ce n'est pas une licence
"open source" au sens strict (OSI) -- code source visible et modifiable pour un usage personnel,
mais pas librement redistribuable comme produit concurrent.

## Contribuer

Voir `docs/CONTRIBUTING_FR.md` avant d'ouvrir une pull request -- toute contribution
suppose l'acceptation de l'accord de licence contributeur (`docs/CLA_FR.md`).

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

**Canal beta** : un tag contenant `-beta` (ex. `v0.3.0-beta.1`) suit exactement le même chemin, mais
la Release est publiée comme *prerelease* GitHub -- ignorée par défaut par les outils de suivi de
mise à jour (Obtainium et similaires) sauf activation explicite du côté de qui l'installe. Pratique
pour partager un build à tester sans que ça remonte comme "mise à jour recommandée".

```
git tag v0.3.0-beta.1
git push origin v0.3.0-beta.1
```
