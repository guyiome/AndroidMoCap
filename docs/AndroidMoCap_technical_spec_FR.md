# AndroidMoCap — Spécification technique

*🇬🇧 English version: [AndroidMoCap_technical_spec.md](AndroidMoCap_technical_spec.md)*

*Document de référence sur l'architecture et l'implémentation courantes. Décrit comment l'app est
construite, avec le niveau de détail technique -- pas un journal, pas d'historique de décisions, pas
de feuille de route. Pour le périmètre fonctionnel côté utilisateur, voir
`AndroidMoCap_functional_spec_FR.md`.*

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
  network/               Envoi OSC/UDP (VMC), UDP (iFacialMocap), WebSocket (API Plugin VTube
                          Studio) + le registre de formules ARKit -> VBridger
  settings/              Persistance des réglages (DataStore)
  ui/                    ViewModel + écrans Compose (HUD, réglages, overlay mesh)
```

Stack : Kotlin, AGP 9.x, Jetpack Compose, CameraX, MediaPipe Tasks Vision (Face Landmarker),
ARCore (Augmented Faces, palier `OPTIMAL` seulement), JavaOSC (protocole VMC), nv-websocket-client
+ kotlinx.serialization (API Plugin VTube Studio), AndroidX DataStore (Preferences).

`minSdk = 30` (Android 11), `compileSdk = targetSdk = 37` (imposé par les dépendances AndroidX
récentes).

## 2. Pipeline de capture

`CameraController` pilote CameraX en deux usages séparés : `ImageAnalysis` (lié en permanence dès
le démarrage, jamais débindé) fournit les frames à MediaPipe ; `Preview` (l'aperçu affiché) est
ajouté/retiré indépendamment selon le mode économie d'énergie, sans jamais interrompre l'analyse.

Par frame acceptée (après filtrage du débit cible, voir §3) : conversion en `Bitmap` via un pool
réutilisé (évite une allocation par frame), rotation appliquée si nécessaire (matrice mise en cache,
l'angle étant constant tant que l'app reste verrouillée portrait -- voir §7), puis passage à
`FaceLandmarkerHelper.detectAsync` en mode `LIVE_STREAM`. Le bitmap est suivi par timestamp de
frame (pas par référence d'objet, MediaPipe pouvant envelopper l'image en interne) et repris dans
le pool via `releaseFrame()` une fois le résultat MediaPipe reçu pour ce timestamp précis.

`FaceLandmarkerHelper` tente d'abord le délégué GPU si `TierConfig.preferGpuDelegate`, et retombe
sur CPU en cas d'échec d'initialisation. Sortie par frame (`FaceTrackingResult`) : liste des
blendshapes (`BlendshapeScore`), matrice/angles de rotation de tête, angles de regard par œil
(calculés en fonction pure, `computeEyeGazeDegrees`, testable en JVM), et le mesh de 478 points
(consommé aussi par la correction du clignement par EAR ci-dessous).

**Fiabilisation du clignement** : `tracking/EyeAspectRatio.kt` calcule un Eye Aspect Ratio
géométrique par œil à partir du mesh. Le blendshape `eyeBlink` de MediaPipe fuit d'un œil vers
l'autre (un clin d'œil délibéré remonte aussi le score de l'autre œil) -- `tracking/EyeBlinkCorrection.kt`
atténue le score d'un œil quand son EAR indique qu'il est encore largement ouvert, appliqué dans
`MainViewModel.handleTrackingResult()` avant tout envoi réseau ou affichage. `EyeOpennessSmoother`
lisse la remontée de l'EAR (attaque instantanée, relâchement sur ~3s) pour ne pas confondre une
dérive du suivi de landmarks pendant une fermeture tenue avec une vraie réouverture. La référence
"fermé" (`AdaptiveEarFloor`) est auto-adaptative par œil plutôt qu'une constante fixe : un seuil
mesuré face caméra ne tient pas à d'autres angles de prise de vue.

Le bouton de calibrage (`MainHud`) se teinte en rouge si une dérive de la pose calibrée est détectée
(visage au repos mais pose qui ne revient pas près de zéro, ou perte de détection du visage puis
redétection) -- purement informatif, jamais d'action automatique, se résout uniquement par un
nouveau calibrage explicite. Logique pure et testée en JVM :
`tracking/CalibrationAnomaly.kt`/`BlendshapeStability.kt`.

## 3. Sélection de palier (`TrackingTier`)

`DeviceCapabilityDetector.detect()` établit une photo des capacités de l'appareil (support ARCore,
classe de performance officielle Android si disponible, nombre de cœurs, RAM totale) au démarrage.
`TrackingTierSelector` en déduit un palier :

- **`COMPATIBLE`** -- appareils d'entrée de gamme, débit cible le plus bas, délégué CPU.
- **`STANDARD`** -- profil intermédiaire.
- **`OPTIMAL`** -- appareils haut de gamme avec support ARCore, débit le plus élevé, utilise la pose
  de tête ARCore (`useArCorePose`), voir §4.

Le débit cible (`targetFps`) par palier est appliqué dans `CameraController.processFrame()` : les
frames en trop sont ignorées avant toute allocation/rotation, avant même d'atteindre MediaPipe.

`DeviceCapabilityDetector.isThermalThrottling()` (surveille `PowerManager.currentThermalStatus`) est
sondée en continu pendant la capture (`MainViewModel.startThermalPolling()`, toutes les 5 secondes,
actif entre `ON_START`/`ON_STOP`) : en cas de chauffe, le débit cible est réduit de moitié (plancher
10 fps) via les mêmes points d'ajustement à chaud que le débit par palier ci-dessus
(`CameraController`/`ArCoreHeadPoseTracker.setTargetFps`), et remonte automatiquement dès que la
chauffe retombe. Volontairement limité au débit cible -- ni le délégué GPU/CPU ni la source caméra
(ARCore/CameraX) ne changent à chaud, seul un changement de palier complet (jamais fait en cours de
session) y toucherait. Logique pure et testée en JVM : `tracking/ThermalThrottle.kt`
(`ThermalThrottleState.next()`).

À côté du sélecteur de palier manuel (`tierOverride`, diagnostic uniquement), un panneau de mocks de
debug caché (`AdvancedSettingsScreen`, déverrouillé par tap multiple sur la ligne de version de l'app)
permet de forcer trois comportements difficiles à déclencher naturellement sur un appareil donné :
throttling thermique (en direct), ARCore indisponible et délégué GPU indisponible (ces deux derniers
au prochain lancement, même contrainte que `tierOverride` -- voir §6).

## 4. Fusion ARCore (palier `OPTIMAL`)

ARCore Augmented Faces gère lui-même l'accès caméra en interne et ne peut pas coexister avec
`ImageAnalysis` de CameraX sur la même caméra frontale (un seul client Camera2 actif à la fois).
Pour ce palier uniquement, `Session` (ARCore) pilote la caméra via `ArCoreHeadPoseTracker`, les
frames sont récupérées via `Frame.acquireCameraImage()` (toujours en YUV_420_888 côté ARCore,
aucune option RGBA) puis converties manuellement en `Bitmap` ARGB_8888 (`yuv420ToBitmap()`) avant
`BitmapImageBuilder` -- pas `MediaImageBuilder` directement, qui exige du RGBA -- pour continuer à
nourrir MediaPipe côté blendshapes ; la pose de tête utilise `AugmentedFace.centerPose` (récupérée
via un callback dédié dans `MainViewModel`, thread GL) à la place de
`facialTransformationMatrixes()`. Repli automatique et silencieux vers CameraX+`CameraController`
(comme le palier `STANDARD`) si Augmented Faces s'avère indisponible à l'usage, décidé synchrone dès
`MainViewModel.initializeTracking()`.

**Fond caméra live** : le `GLSurfaceView` de ce palier affiche un quad plein écran texturé avec la
texture OES qu'ARCore alimente déjà via `Session#setCameraTextureName`, comblant le manque d'aperçu
live de ce palier par rapport à `PreviewView` sur CameraX -- le mesh overlay suit le même réglage
(`faceMeshOverlayEnabled`) que CameraX (`ui/MeshOverlayVisibility.kt`), il n'est pas forcé sur ce
palier. Masqué en mode économie d'énergie comme l'aperçu CameraX
(`ArCoreHeadPoseTracker.setBackgroundRenderingEnabled`). Rotation du quad fixe, indépendante de la
tenue physique du téléphone, exactement comme `cameraRotationDegrees` côté CPU ; latéralité
volontairement non mirorée (comportement natif/anatomique, ce palier n'a pas de `PreviewView`
équivalent à qui rester cohérent, contrairement au mesh qui reste mirroré pour CameraX).

Classes dédiées : `ArCoreHeadPoseTracker`, `ArCoreFaceSelector` (sélection du visage principal si
plusieurs détectés, fonction pure testée en JVM : `pickPrimary`). Le miroir n'est volontairement
jamais appliqué à l'image envoyée à MediaPipe, seulement à l'affichage, même convention que
`CameraController`.

**Limitations connues** : `DeviceCapabilityDetector.detect()` lit
`ArCoreApk.checkAvailability().isSupported` de façon synchrone au démarrage (détaillé dans le kdoc
d'`ArCoreHeadPoseTracker.kt`) ; pas de pool de bitmaps pour le chemin ARCore (contrairement à
`CameraController` côté CameraX).

## 5. Protocoles réseau

### VMC/OSC (`VmcOscSender`)

Cible : Blender, Unity -- pas VTube Studio (qui ne reçoit pas le protocole VMC/OSC en entrée). Un
`OSCBundle` unique par frame envoyée, regroupant un message `/VMC/Ext/Blend/Val` par blendshape
(~52) suivi d'un `/Apply` -- un seul appel réseau par frame plutôt qu'un paquet UDP par blendshape.
IP/port cible saisis manuellement côté app (le téléphone est l'émetteur, il doit connaître sa
destination).

Détection de déconnexion best-effort (socket `connect()`-é + sonde `receive()` dédiée) : repose sur
la délivrance d'un paquet ICMP "port injoignable", non garantie selon la configuration réseau --
l'icône "connecté" peut donc rester allumée même sans récepteur actif sur certains réseaux.
N'affecte jamais l'envoi lui-même (best-effort, jamais bloquant).

### iFacialMocap/UDP (`IFacialMocapSender`)

Cible : VBridger. Modèle inversé : le téléphone écoute passivement et affiche sa propre IP locale
dans les réglages ; c'est VBridger qui initie la connexion vers cette IP. Aucune saisie réseau
requise côté téléphone pour ce chemin. Même détection de déconnexion best-effort que VMC ci-dessus,
même limite.

### VTube Studio Plugin API (`VTubeStudioSender`)

Cible : VTube Studio, en direct, via son API Plugin propriétaire (WebSocket JSON, port 8001 par
défaut) plutôt que VMC/OSC. Le téléphone est client WebSocket vers l'IP/port du PC (comme VMC, pas
comme iFacialMocap). Cycle de connexion en plusieurs étapes asynchrones (`VTubeStudioConnectionState`,
pur et testé) : ouverture du socket, authentification (jeton persisté après un premier popup
d'autorisation utilisateur dans VTube Studio, réutilisé aux connexions suivantes -- avec retry
automatique via un nouveau popup si le jeton stocké est refusé/révoqué, plus un bouton manuel
"Oublier le jeton" dans l'écran Connexion), création d'un paramètre personnalisé par formule du
groupe actif (voir plus bas -- une collision de nom avec un autre plugin déjà connecté, ex.
VBridger, n'interrompt pas toute la connexion, seul ce paramètre est perdu), puis injection des
valeurs à chaque frame (`InjectParameterDataRequest`). Encodage/décodage JSON pur et testé
(`VTubeStudioProtocol.kt`) via kotlinx.serialization (`encodeDefaults = true` impératif -- voir son
kdoc) ; transport WebSocket via **nv-websocket-client** (`VTubeStudioSender.kt`, seule pièce non
testable en JVM) -- pas OkHttp, qui propose systématiquement l'extension `permessage-deflate` sans
réglage public pour la désactiver, incompatible avec le serveur `websocket-sharp` de VTube Studio.
Les paramètres créés ne sont pas automatiquement reconnus par un modèle Live2D existant --
l'utilisateur doit les mapper une fois dans l'éditeur de paramètres de VTube Studio.

**Traduction des paramètres équivalents à VBridger** (`network/VBridgerFormulas.kt`, optionnelle via
`ConnectionSettingsStore.vtsUseVBridgerTranslation`, désactivée par défaut). Une seule liste
unifiée de formules nommées (`VtsParameterFormula` : nom du paramètre de sortie, besoin ou non de
`ParameterCreationRequest`, et une fonction de calcul `(blendshapes, headEulerDegrees) -> Float`)
modélise à la fois les 52 blendshapes ARKit bruts (en formules identité triviales) et 28 formules
composites reproduisant le panneau `AdvancedARKitSettings` de VBridger lui-même (`FaceAngleX/Y/Z`,
`MouthSmile`, `EyeOpenLeft/Right`...) -- un seul mécanisme plutôt que deux, pour qu'un futur écran
d'édition des formules (pas encore construit) n'impose pas de restructuration. `activeFormulas
(useVBridgerTranslation)` est **exclusif, pas additif** : soit les 52 formules brutes, soit les 28
composites, jamais les deux, cohérent avec ce que VBridger lui-même envoie à VTube Studio (son
propre jeu composite seul, jamais les blendshapes bruts en plus). Chaque formule du groupe actif
demande une création de paramètre de façon uniforme, y compris les composites -- la répartition
exacte native/plugin des paramètres VTube Studio n'a pas pu être établie de façon fiable depuis la
documentation disponible, donc une tentative de création est faite pour toutes ; un échec sur un
paramètre déjà natif est toléré de la même façon qu'une collision de nom (voir ci-dessus). Les
formules composites ne portent aucune logique de miroir propre -- `mirrorModeEnabled` (appliqué une
seule fois, en amont, dans `MainViewModel`) décide de façon uniforme pour tous les champs envoyés à
tous les protocoles réseau, mode traduction compris. Un écran en lecture seule
(`ui/VtsFormulasScreen.kt`) liste précisément les noms de paramètres actuellement envoyés, reflétant
le groupe actif.

Nécessite `res/xml/network_security_config.xml` (`<base-config cleartextTrafficPermitted="true">`,
référencé depuis `AndroidManifest.xml`) : les bibliothèques WebSocket respectent la politique de
sécurité réseau d'Android (trafic non chiffré bloqué par défaut depuis l'API 28), contrairement au
`DatagramSocket` brut utilisé par VMC/iFacialMocap, qui n'y est pas soumis.

Les trois protocoles (VMC, iFacialMocap, VTube Studio) sont mutuellement exclusifs à l'exécution
(un seul `ConnectionType` actif), choix persisté via `ConnectionSettingsStore`.

## 6. Persistance des réglages

`AppSettingsStore` et `ConnectionSettingsStore` (DataStore Preferences) : seuil de batterie faible,
mode économie d'énergie (activation + délai), overlay du mesh, mode miroir, type de connexion et
cible réseau (IP VMC, IP + jeton d'authentification VTube Studio + `vtsUseVBridgerTranslation`, voir
§5), et -- optionnellement -- la sélection de blendshapes affichés sur l'écran principal. Cette
dernière reste non persistée par défaut (remise à zéro à chaque lancement), mais un réglage dédié
(`persistBlendshapeSelectionEnabled`) permet de la conserver d'une session à l'autre.

`AppSettingsStore.blendshapeWeights` (une `floatPreferencesKey` par blendshape, défaut 1.0 pour
toute entrée absente) est persisté mais **pas encore branché sur le pipeline de tracking** --
préparation pour un futur ajustement de gain par blendshape, pas encore une fonctionnalité active.
`resetBlendshapeWeights()` supprime les clés plutôt que de les réécrire, donc une entrée jamais
réglée et une entrée juste réinitialisée sont indiscernables.

Deux mocks de debug persistés (`debugForceArCoreUnavailable`, `debugForceGpuUnavailable` -- voir §3)
suivent exactement le même patron que `tierOverride` : lus une seule fois au lancement, un
changement ne s'applique qu'au redémarrage suivant. Le troisième mock (débit thermique) est
volontairement absent de ce store -- bascule de session active en direct, pas un réglage de
lancement.

## 7. Contraintes non-fonctionnelles

**Portrait verrouillé** -- l'app est verrouillée en orientation portrait, ce qui permet de mettre
en cache la matrice de rotation caméra pour toute la session (calculée une seule fois plutôt qu'à
chaque frame). Limite connue : à partir d'Android 16 (API 36), les restrictions d'orientation
déclarées par une app sont ignorées par défaut sur les écrans de largeur minimale ≥ 600dp
(tablettes) ; Android 17 (API 37) supprime l'option de désactivation manifeste -- affecte la mise en
page des réglages sur tablette récente, confirmé sans crash ni casse fonctionnelle sur une vraie
tablette (15 août 2026), seulement pas encore adapté visuellement à l'orientation système. La
rotation caméra fixe elle-même fonctionne correctement dans toutes les orientations physiques
testées (portrait, paysage dans les deux sens). Un
changement significatif de tenue physique en cours de session nécessite un nouveau calibrage
explicite pour que la pose de tête reste cohérente.

**Budget batterie/chauffe** -- préoccupation centrale de plusieurs choix d'implémentation : pool de
bitmaps, throttling du débit par palier, mode économie d'énergie qui coupe l'aperçu affiché sans
couper le tracking. Le mesh de 478 points est extrait à chaque frame que l'overlay soit actif ou
non -- coût jugé négligeable, MediaPipe calculant ces points en interne de toute façon. Toute
fonctionnalité ajoutée (notamment les cascades de détection expérimentales) est évaluée à cette
aune, avec un avertissement utilisateur si l'appareil montre des signes de throttling.

**Cycle de vie** -- capteurs (orientation, batterie) et initialisation du tracking alignés sur le
cycle de vie réel de l'Activity (`ON_START`/`ON_STOP`), pas seulement sur la composition Compose,
pour éviter une écoute inutile quand l'app passe en arrière-plan sans être détruite.

**Découplage état chaud / état froid** -- `MainUiState` (réglages, connexion, calibration -- change
rarement) est séparé du flux `trackingFrame` (blendshapes, latence, détection -- change à 20-60 Hz)
pour limiter la recomposition Compose aux seuls composants qui ont réellement besoin des mises à
jour par frame.

**Écran de chargement au démarrage** -- `MainUiState.isInitializing` (défaut `true`, remis à `false`
en toute dernière instruction d'`initializeTracking()`) pilote `ui/LoadingScreen.kt` dans
`MainScreen`, affiché à la place de l'aperçu caméra/HUD le temps que le palier soit choisi, que le
modèle MediaPipe charge, et que la source caméra soit construite. Barre de progression
volontairement indéterminée -- aucun jalon intermédiaire réel n'existe pour en nourrir une
déterminée.

**Empilement des écrans de réglages et clic-traversant** -- chaque écran de réglages et
sous-catégorie reste composé en dessous de l'écran actif (booléens dans `MainScreen`, jamais
démonté) plutôt que de le remplacer, pour que le retour révèle l'écran du dessous sans le
reconstruire. La racine plein écran de chaque écran superposé consomme les événements tactiles via
`Modifier.blockClicksBehind()` (`ui/ClickBlocking.kt`) -- sans ça, un tap sur une zone vide d'un
sous-écran retombait sur l'écran empilé en dessous.

## 8. Dépendances externes et limites connues

**MediaPipe Face Landmarker** -- modèle `.task` téléchargé manuellement (non versionné, trop
volumineux), mode `LIVE_STREAM`, sortie de 52 blendshapes ARKit. Limitation structurelle du modèle :
`tongueOut` n'est jamais restitué de façon fiable (la langue n'existe pas dans la topologie du mesh
de landmarks, qui ne modélise que la surface visible du visage) ; `cheekPuff` est également peu
fiable en pratique mais pour une raison différente (déformation de surface bien présente dans le
mesh, manque de couverture du modèle plutôt qu'impossibilité structurelle). `tongueOut` a une
mitigation applicative construite en dehors du mesh : une porte grossière sur le blendshape
`jawOpen` existant, suivie d'une classification par similarité cosinus contre une calibration
personnelle par embedding (`ImageEmbedder` MediaPipe, écran de calibration à faire une fois par
utilisateur) -- une heuristique de recadrage/couleur pixel existe aussi dans le pipeline
(`tracking/MouthColorAnalysis.kt`, `LipLandmarks.kt`) mais reste purement diagnostique, elle ne
conditionne plus la classification. Envoyée aux protocoles réseau une fois calibrée, avec un
anti-rebond exigeant plusieurs classifications consécutives avant confirmation, et un risque
résiduel de faux positif isolé assumé ; `cheekPuff` n'a pas de mitigation.

**ARCore** -- utilisé uniquement pour Augmented Faces au palier `OPTIMAL` (voir §4), pas pour un
usage de réalité augmentée classique.

**JavaOSC** -- implémentation du protocole OSC utilisé pour VMC.

**nv-websocket-client** -- client WebSocket utilisé par `VTubeStudioSender` pour l'API Plugin de
VTube Studio, plutôt qu'OkHttp : OkHttp propose systématiquement l'extension `permessage-deflate`,
sans réglage public pour la désactiver, incompatible avec le serveur `websocket-sharp` de VTube
Studio.

**kotlinx.serialization** -- encodage/décodage JSON pur pour le protocole de l'API Plugin VTube
Studio (`VTubeStudioProtocol.kt`), préféré à `org.json` (déjà présent dans le SDK Android) car ce
dernier n'est qu'un stub sous le runner de tests JVM de ce projet (pas de Robolectric configuré) --
casserait la convention "fonctions pures testables en JVM" suivie partout ailleurs.

**androidx.appcompat** -- requis uniquement pour `AppCompatActivity`/`AppCompatDelegate`, le
sélecteur de langue en-app (voir §12) ; le reste de l'UI (Compose/Material3) ne s'appuie sur aucun
composant de support classique.

## 9. Tests

Suite de tests unitaires JVM pur (`app/src/test/`, aucune dépendance Android/Robolectric),
`./gradlew testDebugUnitTest`. Philosophie : extraire en fonctions pures et testables tout calcul
qui ne dépend pas directement du framework Android/MediaPipe/CameraX -- exemples : `RotationMath`
(conversions matrice/quaternion/Euler), `computeEyeGazeDegrees`, `CameraController.rotatedDimensions`,
`ArCoreFaceSelector.pickPrimary`, `VBridgerFormulas` (le registre de formules ARKit -> VBridger, §5).
Détail fonction par fonction, avec les raisons explicites de ce qui n'est volontairement pas couvert,
dans `AndroidMoCap_unit_tests_FR.md`.

## 10. Build et distribution

Signature release lue depuis des variables d'environnement (jamais commitées), workflow GitHub
Actions (`.github/workflows/release.yml`) déclenché par un tag `vX.Y.Z` : build, téléchargement du
modèle MediaPipe, signature, publication d'une Release GitHub avec l'APK en pièce jointe. Pas de
Play Store. `isMinifyEnabled = true` en configuration release (R8 activé) : shrinking + renommage
actifs, `-dontoptimize` conservé délibérément (voir `proguard-rules.pro`) -- l'optimiseur R8 perturbe
la détection d'appelant de Guava Flogger (dépendance transitive de MediaPipe), source d'un crash au
lancement sans cette option. Sans variables d'environnement de signature release, `assembleRelease`
retombe sur la signature debug -- seulement pour permettre de tester un build release minifié en
local (`adb install`), le workflow de publication a toujours les vraies variables.

Second workflow (`.github/workflows/ci.yml`) déclenché sur chaque pull request et chaque push sur
`main` : build debug (non signé, aucun secret nécessaire) + tests unitaires, pour donner un signal
objectif avant toute décision de merge -- notamment sur les PR Dependabot.

Licence : PolyForm Shield 1.0.0 (voir `../LICENSE`), CLA en place pour les contributions externes
(`CLA_FR.md`, `CONTRIBUTING_FR.md`).

## 11. Dette technique et limites connues

Les limitations connues sont documentées directement dans la section concernée plutôt que dans une
liste séparée : vérification de mise à jour semi-automatique non implémentée (§10), comportement non
défini sur grand écran/tablette (§7), pas de pool de bitmaps pour le chemin ARCore (§4), traductions
FR/EN pas encore validées par un locuteur natif (§12).

## 12. Localisation

Texte utilisateur externalisé en ressources : `res/values/strings.xml` contient l'anglais et sert de
repli par défaut (toute langue système sans dossier dédié, y compris ni français ni anglais, retombe
sur l'anglais) ; `res/values-fr/strings.xml` contient le français, utilisé explicitement quand la
langue système (ou le choix fait via le sélecteur par app) est le français. `android:localeConfig`
déclaré dans `AndroidManifest.xml` (`res/xml/locales_config.xml`, ordre en/fr) pour le sélecteur de
langue par app natif du système (réglages système, Android 13+ seulement). Sur les versions
antérieures, un sélecteur en-app couvre le même besoin (`ComfortSettingsScreen`, section "Langue de
l'app") : `MainActivity` étend `AppCompatActivity` (thème `Theme.AppCompat.DayNight.NoActionBar`,
requis -- `android:Theme.Material.NoActionBar` seul empêcherait
`AppCompatDelegate.setApplicationLocales()` de fonctionner sous Compose), et le manifeste déclare
`AppLocalesMetadataHolderService` (`autoStoreLocales="true"`) pour que le choix persiste
automatiquement d'un lancement à l'autre sur toutes les versions (stockage propre à AppCompat sous
API 33, délégué au `LocaleManager` plateforme au-delà) -- sans DataStore ni code de persistance
propre à ce projet. Changer la langue recrée l'Activity (comportement AppCompat documenté) : un
écran de réglages ouvert au moment du changement se ferme et revient à l'écran principal, cosmétique
mineur plutôt qu'un bug. `MainViewModel.rebindTrackingPipeline()` relie la source caméra et ses
compagnons pilotés par le cycle de vie au nouveau `LifecycleOwner` de l'Activity quand ça arrive (le
ViewModel, lui, survit à la recréation) -- sans ça, l'aperçu/l'overlay de mesh gelait pour de bon
(voir revue technique, point 63). Les 52 noms de blendshapes ARKit (`jawOpen`, `mouthSmileLeft`...) et les
identifiants techniques de protocole (`ConnectionType.IFACIALMOCAP`...) ne sont volontairement pas
traduits -- vocabulaire de protocole, pas texte d'affichage. Les messages d'erreur émis hors
`@Composable` (`MainViewModel`, `CameraController`, `FaceLandmarkerHelper`) sont résolus via
`Context.getString()`, chacune de ces classes ayant déjà accès à un `Context`/`Application`.
Traductions FR/EN pas encore validées par un locuteur natif.
