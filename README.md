# AndroidMoCap — PoC

Capture de mouvement facial sur Android, avec sélection automatique du meilleur pipeline
disponible selon l'appareil, et diffusion des blendshapes vers VTube Studio / Blender / Unity
via le protocole VMC (OSC/UDP).

Ce dépôt est le **squelette de la phase 1** (voir `AndroidMoCap_etude_options.md`) : CameraX +
MediaPipe Face Landmarker, détection de palier, overlay de diagnostic, envoi VMC basique.
L'intégration ARCore (fusion de pose de tête, phase 2) est préparée dans le code (`TrackingTier`,
`DeviceCapabilities`) mais pas encore branchée.

## Avant d'ouvrir le projet

### 1. Télécharger le modèle MediaPipe (obligatoire)

Le modèle `.task` n'est pas versionné (trop volumineux, exclu par `.gitignore`). À télécharger
manuellement et à placer dans :

```
app/src/main/assets/face_landmarker.task
```

URL du modèle (float16, ~modèle recommandé pour mobile) :
`https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task`

Si le lien a changé, repartir de la page officielle : [Face landmark detection guide for Android](https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker/android) (section "Model").

### 2. Ouvrir dans Android Studio

Ouvrir ce dossier directement dans Android Studio (`File > Open`). Le premier sync Gradle
téléchargera AGP 9.2 / Gradle 9.4.1 / Kotlin 2.3.20 ainsi que les dépendances ARCore, CameraX,
MediaPipe et JavaOSC listées dans `gradle/libs.versions.toml`.

Si Android Studio propose une mise à jour d'AGP/Gradle au moment du sync, tu peux accepter --
les versions du projet datent d'avril 2026, il est normal qu'elles bougent.

### 3. Builder sur un appareil physique

**Important : l'émulateur Android ne fournit pas de vraie image caméra frontale**, donc pas de
tracking facial réel. Il faut un téléphone physique connecté en USB (débogage USB activé) avec
Android 11 (API 30) ou plus.

Au premier lancement, l'app demande la permission caméra puis affiche :

- un aperçu caméra plein écran ;
- en haut à gauche : le palier de tracking choisi automatiquement (`OPTIMAL` / `STANDARD` /
  `COMPATIBLE`), le délégué actif (GPU/CPU), et les valeurs des blendshapes les plus actifs ;
- en bas : un champ pour entrer l'IP du PC qui fait tourner VTube Studio / Blender / Unity, et
  un bouton pour démarrer l'envoi VMC.

## Recevoir le flux côté PC

**VTube Studio** : Settings → VTube Studio → VirtualMotionCapture → activer la réception, port
`39539` par défaut (modifiable, doit correspondre à celui utilisé côté app).

**Blender / Unity** : utiliser un addon/package compatible VMC (voir `AndroidMoCap_etude_options.md`
section 2) configuré pour écouter sur le même port.

Téléphone et PC doivent être sur le **même réseau Wi-Fi local**.

## Structure du projet

```
app/src/main/java/com/guyiome/androidmocap/
  MainActivity.kt              Permission caméra + point d'entrée Compose
  MoCapApplication.kt
  capabilities/                Détection des capacités de l'appareil (ARCore, GPU, RAM, thermal)
  tracking/                    Sélection de palier + wrapper MediaPipe Face Landmarker
  camera/                      Pilotage CameraX (caméra frontale -> MPImage)
  network/                     Envoi OSC/UDP au format VMC
  ui/                          ViewModel + écran Compose (overlay diagnostic, contrôles VMC)
```

## Prochaines étapes (voir l'étude pour le détail)

1. Valider fréquence/latence réelle sur quelques appareils de test.
2. Brancher ARCore (pose de tête) en fusion avec MediaPipe pour le palier `OPTIMAL`.
3. Ajouter le lissage temporel (One Euro Filter) sur les blendshapes.
4. Écran de calibrage (offset caméra, sensibilité, persistance des réglages via DataStore --
   la dépendance est déjà dans `build.gradle.kts`, pas encore utilisée).
5. Rétrogradation dynamique de palier en cas de throttling thermique (`DeviceCapabilityDetector.isThermalThrottling`
   existe déjà, pas encore appelé en continu pendant la capture).
