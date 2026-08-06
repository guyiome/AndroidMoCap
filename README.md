# AndroidMoCap — PoC

Capture de mouvement facial sur Android, avec sélection automatique du meilleur pipeline
disponible selon l'appareil, et diffusion des blendshapes vers VTube Studio / Blender / Unity
(protocole VMC/OSC) ou vers VBridger (iFacialMocap/UDP).

Ce dépôt couvre la **phase 1** (voir `AndroidMoCap_etude_options.md`) : CameraX + MediaPipe Face
Landmarker, détection de palier, mode économie d'énergie, envoi VMC/iFacialMocap. L'intégration
ARCore (fusion de pose de tête, phase 2, palier `OPTIMAL`) est préparée dans le code (`TrackingTier`,
`DeviceCapabilities`) mais pas encore branchée -- voir `AndroidMoCap_revue_technique.md` point 3
pour l'état de la réflexion.

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

Au premier lancement, l'app demande la permission caméra puis affiche l'aperçu caméra plein écran
avec, par-dessus, un bandeau d'icônes minimal (visage détecté, calibrage, connexion, mode éco,
réglages -- ce dernier toujours en dernière position) et, sur simple appui, un écran de réglages
dédié regroupant : type de connexion (VMC / iFacialMocap) et cible réseau, sélection des
blendshapes affichés, seuil de batterie faible, mode économie d'énergie (délai d'activation,
sortie au toucher), et overlay optionnel du mesh de tracking (478 points, désactivé par défaut).

## Recevoir le flux côté PC

**VTube Studio** : Settings → VTube Studio → VirtualMotionCapture → activer la réception, port
`39539` par défaut (modifiable, doit correspondre à celui utilisé côté app).

**Blender / Unity** : utiliser un addon/package compatible VMC (voir `AndroidMoCap_etude_options.md`
section 2) configuré pour écouter sur le même port.

**VBridger** : sélectionner le protocole iFacialMocap dans les réglages de l'app et suivre les
instructions VBridger pour pointer vers l'IP/port du téléphone.

Téléphone et PC doivent être sur le **même réseau Wi-Fi local**.

## Structure du projet

```
app/src/main/java/com/guyiome/androidmocap/
  MainActivity.kt              Permission caméra + point d'entrée Compose
  MoCapApplication.kt
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
Voir `AndroidMoCap_tests_unitaires.md` pour le détail fonction par fonction, avec les raisons
explicites de ce qui n'est volontairement pas couvert. Lancer avec :

```
./gradlew testDebugUnitTest
```

## Distribution

Le projet est distribué en open source via les **Releases GitHub** (pas de Play Store pour
l'instant) : `.github/workflows/release.yml` build, signe et publie automatiquement un APK dès
qu'un tag `vX.Y.Z` est poussé.

### Mettre en place la signature (une seule fois)

1. Générer une clé de signature en local (à garder précieusement, jamais commitée) :
   ```
   keytool -genkeypair -v -keystore release.keystore -alias androidmocap \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Dans les réglages GitHub du dépôt (`Settings > Secrets and variables > Actions`), créer 4 secrets :
   - `RELEASE_KEYSTORE_BASE64` : `base64 -w0 release.keystore` (le contenu du fichier encodé)
   - `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` (`androidmocap` si tu as suivi la commande
     ci-dessus), `RELEASE_KEY_PASSWORD`
3. Conserver `release.keystore` en lieu sûr en dehors du dépôt : le perdre empêche de publier une
   mise à jour signée avec la même clé (les utilisateurs devraient alors désinstaller/réinstaller).

### Publier une version

```
git tag v0.2.0
git push origin v0.2.0
```

Le workflow construit l'APK (téléchargement du modèle MediaPipe inclus), le signe avec la clé du
secret, et crée une Release GitHub avec l'APK en pièce jointe.

### Installer l'APK (côté utilisateur)

Comme l'app n'est pas sur le Play Store, Android affiche un avertissement "source inconnue" à
l'installation -- normal pour un APK distribué hors store, à activer ponctuellement dans les
réglages au moment de l'installation.

## Prochaines étapes (voir l'étude et `AndroidMoCap_revue_technique.md` pour le détail)

1. Valider fréquence/latence réelle sur quelques appareils de test.
2. Brancher ARCore (pose de tête) en fusion avec MediaPipe pour le palier `OPTIMAL` -- voir
   `AndroidMoCap_revue_technique.md` point 3 pour la piste retenue et le point d'architecture
   ouvert (accès caméra partagé entre ARCore et CameraX).
3. Ajouter le lissage temporel (One Euro Filter) sur les blendshapes.
4. Rétrogradation dynamique de palier en cas de throttling thermique (`DeviceCapabilityDetector.isThermalThrottling`
   existe déjà, pas encore appelé en continu pendant la capture).
5. Activer R8/minify sur le build release une fois la vérification sur device de nouveau possible
   (règles ProGuard MediaPipe/OSC à valider une par une).
