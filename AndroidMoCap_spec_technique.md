# AndroidMoCap — Spécification technique

*Document de référence sur l'architecture et l'implémentation courantes -- pas un journal, pas
d'historique de décisions. Pour le raisonnement derrière chaque choix, les alternatives écartées et
les évolutions en cours de réflexion, voir `AndroidMoCap_revue_technique.md`. Pour le périmètre
fonctionnel côté utilisateur, voir `AndroidMoCap_spec_fonctionnelle.md`. Dernière mise à jour :
6 août 2026.*

## 1. Vue d'ensemble

Application Android native (Kotlin, Jetpack Compose), architecture en couches, un seul écran
(caméra plein écran + overlays Compose), MVVM avec un `MainViewModel`/`MainUiState` central.

```
app/src/main/java/com/guyiome/androidmocap/
  MainActivity.kt      Permission caméra, point d'entrée Compose
  capabilities/         Détection des capacités de l'appareil (ARCore, GPU, RAM, thermal)
  tracking/             Sélection de palier, wrapper MediaPipe Face Landmarker, maths de rotation
  camera/                Pilotage CameraX (caméra frontale -> MPImage, pool de bitmaps)
  sensors/               Orientation du téléphone, icônes HUD, batterie
  network/               Envoi OSC/UDP (VMC) et UDP (iFacialMocap)
  settings/              Persistance des réglages (DataStore)
  ui/                    ViewModel + écrans Compose (HUD, réglages, overlay mesh)
```

Stack : Kotlin 2.4.10, AGP 9.x, Jetpack Compose, CameraX, MediaPipe Tasks Vision 1.0.0 (Face Landmarker),
ARCore (Augmented Faces, palier `OPTIMAL` seulement, non actif dans `main`), JavaOSC (protocole
VMC), AndroidX DataStore (Preferences).

`minSdk = 30` (Android 11, couvre ~87 % du parc actif), `compileSdk = targetSdk = 37` (imposé par
les dépendances AndroidX récentes).

## 2. Pipeline de capture

`CameraController` pilote CameraX en deux usages séparés : `ImageAnalysis` (lié en permanence dès
le démarrage, jamais débindé) fournit les frames à MediaPipe ; `Preview` (l'aperçu affiché) est
ajouté/retiré indépendamment selon le mode économie d'énergie, sans jamais interrompre l'analyse.

Par frame acceptée (après filtrage du débit cible, voir §3) : conversion en `Bitmap` via un pool
réutilisé (évite une allocation par frame, point chaud historique du pipeline), rotation appliquée
si nécessaire (matrice mise en cache, l'angle étant constant tant que l'app reste verrouillée
portrait -- voir §7 pour la réserve sur ce point), puis passage à `FaceLandmarkerHelper.detectAsync`
en mode `LIVE_STREAM`. Le bitmap est suivi par timestamp de frame (pas par référence d'objet,
MediaPipe pouvant envelopper l'image en interne) et repris dans le pool via `releaseFrame()` une
fois le résultat MediaPipe reçu pour ce timestamp précis.

`FaceLandmarkerHelper` tente d'abord le délégué GPU si `TierConfig.preferGpuDelegate`, et retombe
sur CPU en cas d'échec d'initialisation. Sortie par frame (`FaceTrackingResult`) : liste des
blendshapes (`BlendshapeScore`), matrice/angles de rotation de tête, angles de regard par œil
(calculés en fonction pure, `computeEyeGazeDegrees`, testable en JVM), et le mesh de 478 points
(extrait uniquement si un overlay en a besoin -- voir `setLandmarksNeeded`, coût évité sinon).

Le bouton de calibrage (`MainHud`) se teinte en rouge si une dérive de la pose calibrée est détectée
(visage au repos mais pose qui ne revient pas près de zéro, ou perte de détection du visage puis
redétection) -- purement informatif, jamais d'action automatique, se résout uniquement par un
nouveau calibrage explicite. Logique pure et testée en JVM :
`tracking/CalibrationAnomaly.kt`/`BlendshapeStability.kt`. Voir revue technique, point 19.

## 3. Sélection de palier (`TrackingTier`)

`DeviceCapabilityDetector.detect()` établit une photo des capacités de l'appareil (support ARCore,
classe de performance officielle Android si disponible, nombre de cœurs, RAM totale) au démarrage.
`TrackingTierSelector` en déduit un palier :

- **`COMPATIBLE`** -- appareils d'entrée de gamme, débit cible le plus bas, délégué CPU.
- **`STANDARD`** -- profil intermédiaire.
- **`OPTIMAL`** -- appareils haut de gamme avec support ARCore, débit le plus élevé, prévu pour
  utiliser la pose de tête ARCore (`useArCorePose`) -- non actif dans `main` à ce jour, voir §4.

Le débit cible (`targetFps`) par palier est appliqué dans `CameraController.processFrame()` : les
frames en trop sont ignorées avant toute allocation/rotation, avant même d'atteindre MediaPipe --
c'est le point où le filtrage coûte le moins cher.

`DeviceCapabilityDetector.isThermalThrottling()` (surveille `PowerManager.currentThermalStatus`) est
sondée en continu pendant la capture (`MainViewModel.startThermalPolling()`, toutes les 5 secondes,
actif entre `ON_START`/`ON_STOP`) : en cas de chauffe, le débit cible est réduit de moitié (plancher
10 fps) via les mêmes points d'ajustement à chaud que le débit par palier ci-dessus
(`CameraController`/`ArCoreHeadPoseTracker.setTargetFps`), et remonte automatiquement dès que la
chauffe retombe. Volontairement limité au débit cible -- ni le délégué GPU/CPU ni la source caméra
(ARCore/CameraX) ne changent à chaud, seul un changement de palier complet (jamais fait en cours de
session, voir §4 et le sélecteur de palier manuel) y toucherait. Logique pure et testée en JVM :
`tracking/ThermalThrottle.kt` (`ThermalThrottleState.next()`). Voir revue technique, point 34.

À côté du sélecteur de palier manuel (`tierOverride`, diagnostic uniquement), un panneau de mocks de
debug caché (`DiagnosticsScreen`, déverrouillé par tap multiple sur la ligne de version de l'app)
permet de forcer trois comportements difficiles à déclencher naturellement sur un appareil donné :
throttling thermique (en direct), ARCore indisponible et délégué GPU indisponible (ces deux derniers
au prochain lancement, même contrainte que `tierOverride` -- voir §6). Voir revue technique, point 35.

## 4. Fusion ARCore (palier `OPTIMAL`) -- intégrée sur `main`, non testée sur device

Intégrée directement sur `main` le 6 août 2026 (réimplémentée à neuf plutôt que fusionnée depuis
`feature/arcore-fusion`, devenue trop divergente -- voir revue technique, point 13, pour le
raisonnement complet), **compile et teste en JVM, non testée sur device**. Contrainte technique
identifiée : ARCore Augmented Faces gère lui-même l'accès caméra en interne et ne peut pas coexister
avec `ImageAnalysis` de CameraX sur la même caméra frontale (un seul client Camera2 actif à la
fois). Solution retenue : pour ce palier uniquement, `Session` (ARCore) pilote la caméra via
`ArCoreHeadPoseTracker`, les frames sont récupérées via `Frame.acquireCameraImage()` et converties
en `MPImage` (`MediaImageBuilder`) pour continuer à nourrir MediaPipe côté blendshapes ; la pose de
tête utilise `AugmentedFace.centerPose` (récupérée via un callback dédié dans `MainViewModel`, thread
GL) à la place de `facialTransformationMatrixes()`. Repli automatique et silencieux vers
CameraX+`CameraController` (comme le palier `STANDARD`) si Augmented Faces s'avère indisponible à
l'usage, décidé synchrone dès `MainViewModel.initializeTracking()`. Pas d'aperçu caméra live pour ce
palier dans cette passe (l'overlay du mesh sert de retour visuel à la place, forcé visible dans ce
cas -- voir `ui/MeshOverlayVisibility.kt`) -- choix assumé, conçu pour ne pas empêcher un futur mode
"rendu live" (backlog, revue technique point 13).

Classes dédiées : `ArCoreHeadPoseTracker`, `ArCoreFaceSelector` (sélection du visage principal si
plusieurs détectés, fonction pure testée en JVM : `pickPrimary`). Risques connus non résolus sans
device (rotation/miroir de l'image caméra ARCore non gérée, vérification `ArCoreApk` synchrone au
démarrage) : détaillés dans le kdoc d'`ArCoreHeadPoseTracker.kt` et la revue technique, point 13.

## 5. Protocoles réseau

### VMC/OSC (`VmcOscSender`)

Cible : Blender, Unity -- **pas VTube Studio** (voir revue technique point 39 : VTube Studio ne
reçoit vraisemblablement pas le protocole VMC/OSC en entrée, contrairement à l'hypothèse initiale du
projet). Un `OSCBundle` unique par frame envoyée, regroupant un message `/VMC/Ext/Blend/Val` par
blendshape (~52) suivi d'un `/Apply` -- un seul appel réseau par frame plutôt qu'un paquet UDP par
blendshape (correctif appliqué, voir revue technique point 4). IP/port cible saisis manuellement
côté app (le téléphone est l'émetteur, il doit connaître sa destination).

### iFacialMocap/UDP (`IFacialMocapSender`)

Cible : VBridger. Modèle inversé : le téléphone écoute passivement et affiche sa propre IP locale
dans les réglages ; c'est VBridger qui initie la connexion vers cette IP. Aucune saisie réseau
requise côté téléphone pour ce chemin.

### VTube Studio Plugin API (`VTubeStudioSender`) -- point 39

Cible : VTube Studio, en direct, via son API Plugin propriétaire (WebSocket JSON, port 8001 par
défaut) plutôt que VMC/OSC. Le téléphone est client WebSocket vers l'IP/port du PC (comme VMC, pas
comme iFacialMocap). Cycle de connexion en plusieurs étapes asynchrones (`VTubeStudioConnectionState`,
pur et testé) : ouverture du socket, authentification (jeton persisté après un premier popup
d'autorisation utilisateur dans VTube Studio, réutilisé aux connexions suivantes), création d'un
paramètre personnalisé par blendshape (noms ARKit, découverts dynamiquement à la première frame
plutôt qu'une liste codée en dur), puis injection des valeurs à chaque frame
(`InjectParameterDataRequest`). Encodage/décodage JSON pur et testé (`VTubeStudioProtocol.kt`) via
kotlinx.serialization ; transport WebSocket via OkHttp (`VTubeStudioSender.kt`, seule pièce non
testable en JVM). Les paramètres créés ne sont pas automatiquement reconnus par un modèle Live2D
existant -- l'utilisateur doit les mapper une fois dans l'éditeur de paramètres de VTube Studio.

⚠️ Non vérifié sur device à ce stade (prêt pour premier test) : en particulier, si le serveur
WebSocket de VTube Studio écoute au-delà de `127.0.0.1` -- indispensable ici puisque le téléphone
est un autre appareil du LAN, pas un plugin tournant sur la même machine que VTube Studio. Voir
revue technique point 39 pour le détail du risque et la méthode de vérification recommandée.

Les trois protocoles (VMC, iFacialMocap, VTube Studio) sont mutuellement exclusifs à l'exécution
(un seul `ConnectionType` actif), choix persisté via `ConnectionSettingsStore`.

## 6. Persistance des réglages

`AppSettingsStore` et `ConnectionSettingsStore` (DataStore Preferences) : seuil de batterie faible,
mode économie d'énergie (activation + délai), overlay du mesh, type de connexion et cible réseau
(IP VMC, IP + jeton d'authentification VTube Studio -- point 39), et -- optionnellement -- la
sélection de blendshapes affichés sur l'écran principal. Cette dernière
reste **non persistée par défaut** (remise à zéro à chaque lancement, comportement historique
inchangé), mais un réglage dédié (`persistBlendshapeSelectionEnabled`) permet de la conserver d'une
session à l'autre -- voir revue technique point 25.

Deux mocks de debug persistés (`debugForceArCoreUnavailable`, `debugForceGpuUnavailable` -- voir §3
et revue technique point 35) suivent exactement le même patron que `tierOverride` : lus une seule
fois au lancement, un changement ne s'applique qu'au redémarrage suivant. Le troisième mock (débit
thermique) est volontairement **absent** de ce store -- bascule de session active en direct, pas un
réglage de lancement.

## 7. Contraintes non-fonctionnelles

**Portrait verrouillé** -- l'app est verrouillée en orientation portrait, ce qui permet de mettre
en cache la matrice de rotation caméra pour toute la session (calculée une seule fois plutôt qu'à
chaque frame). Réserve documentée (revue technique point 20) : à partir d'Android 16 (API 36), les
restrictions d'orientation déclarées par une app sont ignorées par défaut sur les écrans de largeur
minimale ≥ 600dp (tablettes) ; Android 17 (API 37) supprime l'option de désactivation manifeste.
Le projet ciblant déjà `targetSdk = 37`, cette hypothèse ne tient donc plus sur tablette récente
-- risque identifié sur la mise en page des réglages et, potentiellement, sur la justesse de la
rotation caméra elle-même. Aucune décision de mise en œuvre prise à ce jour.

**Budget batterie/chauffe** -- préoccupation centrale de plusieurs choix d'implémentation : pool de
bitmaps, throttling du débit par palier, mode économie d'énergie qui coupe l'aperçu affiché sans
couper le tracking, extraction du mesh 478 points évitée quand l'overlay est inactif. Toute
fonctionnalité ajoutée (notamment les cascades de détection expérimentales, revue technique points
15/16) doit être évaluée à cette aune, avec un avertissement utilisateur prévu si l'appareil montre
des signes de throttling.

**Cycle de vie** -- capteurs (orientation, batterie) et initialisation du tracking alignés sur le
cycle de vie réel de l'Activity (`ON_START`/`ON_STOP`), pas seulement sur la composition Compose,
pour éviter une écoute inutile quand l'app passe en arrière-plan sans être détruite.

**Découplage état chaud / état froid** -- `MainUiState` (réglages, connexion, calibration -- change
rarement) est séparé du flux `trackingFrame` (blendshapes, latence, détection -- change à 20-60 Hz)
pour limiter la recomposition Compose aux seuls composants qui ont réellement besoin des mises à
jour par frame.

## 8. Dépendances externes et limites connues

**MediaPipe Face Landmarker** -- modèle `.task` téléchargé manuellement (non versionné, trop
volumineux), mode `LIVE_STREAM`, sortie de 52 blendshapes ARKit. Limitation documentée et
structurelle : `tongueOut` n'est jamais restitué de façon fiable (la langue n'existe pas dans la
topologie du mesh de landmarks, qui ne modélise que la surface visible du visage) ; `cheekPuff` est
également peu fiable en pratique mais pour une raison différente (déformation de surface bien
présente dans le mesh, vraisemblablement un manque de couverture du modèle officiel plutôt qu'une
impossibilité structurelle) -- distinction qui conditionne les pistes de correction envisagées
(revue technique points 15/16).

**ARCore** -- utilisé uniquement pour Augmented Faces au palier `OPTIMAL` (voir §4), pas pour un
usage de réalité augmentée classique.

**JavaOSC** -- implémentation du protocole OSC utilisé pour VMC.

**OkHttp** -- client WebSocket utilisé par `VTubeStudioSender` (point 39) pour l'API Plugin de
VTube Studio.

**kotlinx.serialization** -- encodage/décodage JSON pur pour le protocole de l'API Plugin VTube
Studio (`VTubeStudioProtocol.kt`), préféré à `org.json` (déjà présent dans le SDK Android) car ce
dernier n'est qu'un stub sous le runner de tests JVM de ce projet (pas de Robolectric configuré) --
casserait la convention "fonctions pures testables en JVM" suivie partout ailleurs.

## 9. Tests

Suite de tests unitaires JVM pur (`app/src/test/`, aucune dépendance Android/Robolectric),
`./gradlew testDebugUnitTest`. Philosophie : extraire en fonctions pures et testables tout calcul
qui ne dépend pas directement du framework Android/MediaPipe/CameraX -- exemples : `RotationMath`
(conversions matrice/quaternion/Euler), `computeEyeGazeDegrees`, `CameraController.rotatedDimensions`,
`ArCoreFaceSelector.pickPrimary`. Détail fonction par fonction, avec les raisons explicites de ce
qui n'est volontairement pas couvert, dans `AndroidMoCap_tests_unitaires.md`.

## 10. Build et distribution

Signature release lue depuis des variables d'environnement (jamais commitées), workflow GitHub
Actions (`.github/workflows/release.yml`) déclenché par un tag `vX.Y.Z` : build, téléchargement du
modèle MediaPipe, signature, publication d'une Release GitHub avec l'APK en pièce jointe. Pas de
Play Store à ce jour. `isMinifyEnabled = false` en configuration release (R8/ProGuard désactivés
volontairement tant que les tests sur device ne sont pas de nouveau possibles -- revue technique
point 8).

Second workflow (`.github/workflows/ci.yml`) déclenché sur chaque pull request et chaque push sur
`main` : build debug (non signé, aucun secret nécessaire) + tests unitaires, pour donner un signal
objectif avant toute décision de merge -- notamment sur les PR Dependabot. Reste **informatif, pas
contraignant** : le plan GitHub Free ne permet les "required status checks" bloquants que sur les
dépôts publics, pas privés -- voir revue technique point 17 (le second, celui de la CI -- collision
de numérotation documentée dans l'index de la revue technique).

Licence : PolyForm Shield 1.0.0 (voir `LICENSE`), CLA en place pour les contributions externes
(`CLA.md`, `CONTRIBUTING.md`) -- détail du raisonnement au point 22 de la revue technique.

## 11. Dette technique et limites connues

Liste vivante tenue dans `AndroidMoCap_revue_technique.md`, pas dupliquée ici. Points ouverts
principaux au moment de la rédaction : minify désactivé (point 8), vérification de mise à jour non
implémentée (point 14), comportement non défini sur grand écran/tablette (point 20). La
localisation de l'UI (point 23) est traitée -- voir §12. La fusion ARCore (point 13) est intégrée
sur `main` mais non testée sur device -- voir §4. Le throttling thermique dynamique (point 3/13, 34)
est branché et vérifié sur device via mock (point 35) -- le capteur thermique réel reste non exercé,
l'appareil de test ne chauffant pas suffisamment en usage normal, voir §3.

## 12. Localisation

Texte utilisateur externalisé en ressources : `res/values/strings.xml` contient l'anglais et sert de
**repli par défaut** (toute langue système sans dossier dédié, y compris ni français ni anglais,
retombe sur l'anglais -- plus universel) ; `res/values-fr/strings.xml` contient le français, utilisé
explicitement quand la langue système (ou le choix fait via le sélecteur par app) est le français.
`android:localeConfig` déclaré dans `AndroidManifest.xml` (`res/xml/locales_config.xml`, ordre
en/fr) pour le sélecteur de langue par app (réglages système, **Android 13+ seulement** -- sur
Android 11/12, l'app suit uniquement la langue système, sans possibilité de la forcer autrement dans
l'app elle-même ; voir revue technique, point 30). Les 52 noms de blendshapes ARKit (`jawOpen`,
`mouthSmileLeft`...) et les identifiants techniques de protocole (`ConnectionType.IFACIALMOCAP`...)
ne sont volontairement pas traduits -- vocabulaire de protocole, pas texte d'affichage. Les messages
d'erreur émis hors `@Composable` (`MainViewModel`, `CameraController`, `FaceLandmarkerHelper`) sont
résolus via `Context.getString()`, chacune de ces classes ayant déjà accès à un `Context`/
`Application`. Détail du travail d'extraction : revue technique, point 23. Traductions FR/EN pas
encore validées par un locuteur natif -- revue technique, point 29.
