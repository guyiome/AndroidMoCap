# AndroidMoCap — Revue technique et pistes d'optimisation

*Passage complet sur l'ensemble du code (caméra, tracking, capteurs, réseau, UI, réglages), à date du 5 août 2026.*

## Rôle de ce document et lien avec les autres documents de suivi

Ce document reste un **journal chronologique** : état des lieux à un instant T, décisions actées
avec leur raisonnement complet (alternatives écartées, points d'attention), et liste de tâches
prévues -- son rôle ne change pas. Pour une vue de référence sur l'état courant, sans narration ni
historique, voir plutôt `AndroidMoCap_spec_fonctionnelle.md` (ce que l'app fait, côté utilisateur)
et `AndroidMoCap_spec_technique.md` (comment elle est construite). Les deux specs sont censées
rester synchronisées avec ce journal à mesure que les décisions actées ici sont effectivement
implémentées -- ce document reste la source du raisonnement, les specs la référence rapide.

**Protocole pour une session de travail technique (implémentation/code)** : avant de commencer,
relire l'index ci-dessous puis les sections concernées des deux specs. Après implémentation, mettre
à jour le "Statut" du ou des points concernés ici (ex. "idée actée, aucun code écrit" →
"implémenté, voir commit/branche X") et répercuter le changement dans les specs. C'est le mécanisme
d'échange entre cette conversation de réflexion (qui n'écrit jamais de code) et toute session
technique travaillant sur ce dépôt : les décisions prises ici n'ont d'effet que si elles sont lues
et reportées ici une fois traitées -- pas de synchronisation automatique entre conversations, le
dépôt de fichiers partagé est le seul canal.

**Convention de commit** : quand une demande touche plusieurs fonctionnalités à la fois, séparer les
commits pour n'en avoir qu'un par fonctionnalité (sauf dépendance forte entre les deux qui ferait
planter l'une sans l'autre, ou refactor global -- un seul commit "refactor" suffit alors). Titre
clair et concis, détails en corps de message.

**Traitement des PR (Dependabot ou autre), depuis le 6 août 2026** : ne pas utiliser le bouton
"Merge" de GitHub. À la place -- lire la PR (diff, changelog/release notes), reproduire la même
modification directement en local dans ce dépôt, tester/valider (dans la mesure du possible depuis
ce sandbox, sinon sur device), commit dédié comme n'importe quelle modif. Une fois poussé vers
GitHub depuis la machine de l'utilisateur, marquer la PR correspondante comme traitée (fermeture
manuelle avec commentaire renvoyant vers le commit, ou laisser Dependabot la fermer automatiquement
à son prochain passage s'il détecte que `main` satisfait déjà la version cible). Raison : un merge
fait côté serveur GitHub crée un commit que ce sandbox ne peut pas rapatrier (pas d'accès réseau
`api.github.com`/push ici, voir plus bas) alors que la copie locale accumule ses propres commits non
poussés -- ça a produit une divergence confuse lors du merge de la PR #6 (voir plus bas). Passer par
un commit local dès le départ élimine ce problème : la locale reste la source de vérité unique.

### Index des décisions actées en attente d'implémentation

**Collision de numérotation à connaître :** les points 15-21 ci-dessous reprennent la numérotation
des notes de conception rédigées sur la branche `feature/arcore-fusion` (non fusionnée à ce jour).
Or, indépendamment, `main` a déjà utilisé les numéros **15** (navigation retour des écrans
superposés), **16** (renommage du mode de connexion iFacialMocap) et **17** au sens ancien
(indicateur de fiabilité, voir plus bas) pour des sujets sans rapport -- voir leurs sections propres
plus loin dans ce document. Pas de renumérotation rétroactive ici (déjà référencée dans des messages
de commit et potentiellement dans d'autres conversations) : en cas d'ambiguïté, se fier au **sujet**
plutôt qu'au seul numéro. Les points 24-26 ci-dessous ont été ouverts pour lever l'ambiguïté sur les
trois qui se trouvent être déjà implémentés sur `main`.

| Point | Sujet | Statut |
| --- | --- | --- |
| 3 / 13 | Fusion ARCore (palier `OPTIMAL`) | **Intégrée sur `main` et testée sur device le 6 août 2026** (réimplémentée à neuf, pas mergée depuis `feature/arcore-fusion`) -- tracking fonctionnel confirmé par l'utilisateur (crash, rotation et perf corrigés en cours de session), voir sa section dédiée pour le détail et les points mineurs restants |
| 8 | Minify/R8 en release | Désactivé volontairement, en attente de tests device |
| 14 | Vérification de mise à jour semi-automatique | Backlog, aucun code écrit |
| 15 | Détection langue (cascade) | Conception actée, aucun code écrit -- prérequiert le throttling thermique continu (point 3/13). **Confirmé par observation device le 6 août 2026** : le mesh montre que `tongueOut` n'est pas du tout restitué par MediaPipe (pas juste peu fiable), cohérent avec sa présence dans `BlendshapeCatalog.unreliable`. |
| 16 | Détection joues (cascade allégée) | Conception actée, aucun code écrit. **Confirmé par observation device le 6 août 2026** : le mesh bouge très peu au gonflement des joues -- le signal géométrique disponible pour une cascade risque d'être faible/bruité, point d'attention à garder pour la conception détaillée. |
| 17 | Indicateur de fiabilité par blendshape | **Implémenté sur `main`, voir point 24** (l'index le disait encore "aucun code écrit" par erreur) |
| 18 | Persistance sélection blendshapes + valeur brute/ajustée | **Persistance implémentée sur `main`, voir point 25** -- le volet "valeur brute à côté de la valeur ajustée" reste en attente (dépend d'une pondération par blendshape jamais construite) |
| 19 | Détection d'anomalie de calibrage (bouton rouge) | Conception actée, aucun code écrit |
| 20 | Orientation grand écran / tablette | Constat documenté, aucune décision de mise en œuvre |
| 21 | Tri en sous-écrans des réglages | **Implémenté sur `main`, voir point 26** (l'index le disait encore "aucun code écrit" par erreur) |
| 28 | Fiabilisation du clignement des yeux avec lunettes | Idée de conception ouverte le 6 août 2026, voir section dédiée plus bas -- aucun code écrit |
| 29 | Validation des traductions FR/EN par locuteurs natifs | Backlog, voir point 23 -- texte rédigé sans relecture native, ni le français ni l'anglais (repli par défaut) n'ont été validés |
| 30 | Sélecteur de langue dans l'app pour Android 11/12 | Backlog, voir point 23 -- pas d'équivalent au sélecteur système Android 13+ sur ces versions, demanderait `androidx.appcompat` + `AppCompatDelegate` |
| 31 | CI cassée depuis le premier run (`gradlew` sans bit exécutable) | **✅ corrigé le 6 août 2026** (commit `df640a4`), voir section dédiée plus bas |

Points 1, 2, 4, 5, 6, 7, 9, 10, 11, 12, 22, 23 : traités ou correctement à l'état de backlog priorisé
(point 23), rien à corriger côté statut. Les sections détaillées des points 15/16/19/20, qui
n'existaient jusqu'ici que sur la copie du document présente sur `feature/arcore-fusion` (jamais
fusionnée), ont été rapatriées dans `main` le 6 août 2026 -- voir la section dédiée en fin de
document.

## Vue d'ensemble

L'app est structurée proprement en couches : `camera` (CameraX), `tracking` (MediaPipe + maths de rotation), `sensors` (gyroscope, orientation d'icônes, batterie), `network` (VMC/OSC et iFacialMocap/UDP), `settings` (DataStore) et `ui` (Compose MVVM avec un seul `MainViewModel`/`MainUiState`). L'architecture globale est saine et déjà éprouvée sur les points durs (calibration matricielle, paliers adaptatifs, repli GPU→CPU). Les points ci-dessous sont classés du plus impactant au plus cosmétique.

## 1. Le goulot d'étranglement principal : allocation d'un bitmap à chaque frame

`CameraController.processFrame()` crée un nouveau `Bitmap.createBitmap(...)` à **chaque frame caméra**, copie le buffer dedans, puis — si l'image n'est pas déjà à l'endroit — en crée un **second** via une rotation matricielle (`Bitmap.createBitmap(bitmapBuffer, ..., matrix, true)`, avec filtrage bilinéaire). À 30-60 fps ça représente potentiellement plus d'une centaine de bitmaps/seconde alloués puis jetés, tout ça sur l'unique thread `cameraExecutor` qui bloque aussi l'arrivée de la frame suivante pendant ce travail. C'est très probablement la plus grosse source de pression GC, de chauffe et de latence de tout le pipeline, et le point le plus rentable à corriger : réutiliser un bitmap tampon (recréé seulement si les dimensions changent) et, si la rotation est fixe pour toute la session (elle l'est : l'app est verrouillée portrait), la calculer une fois plutôt qu'à chaque frame, ou utiliser `Bitmap.Config.ARGB_8888` avec `postRotate` en filtrage `false` (nearest) puisqu'un léger crénelage n'affecte pas la détection MediaPipe.

## 2. Le palier de tracking ne throttle rien

`TrackingTierSelector` définit un `targetFps` par palier (60 / 30 / 20) mais cette valeur n'est **utilisée nulle part** : `ImageAnalysis` tourne au rythme natif du capteur quel que soit le palier, et `COMPATIBLE` (délégué CPU, appareil d'entrée de gamme) traite donc autant de frames qu'`OPTIMAL`. Sur les appareils visés par ce palier, c'est justement là que ça chauffe et consomme le plus. Un throttling simple (ignorer la frame si moins de `1000/targetFps` ms se sont écoulées depuis la dernière analyse acceptée, dans `processFrame` ou dans l'`analyzer`) donnerait un vrai gain sans toucher à MediaPipe.

## 3. Fonctionnalités documentées mais jamais branchées

`DeviceCapabilityDetector.isThermalThrottling()` existe, avec un commentaire explicite ("à appeler périodiquement pendant la capture... pour déclencher une rétrogradation dynamique de palier") mais n'est appelé nulle part dans le code : aucune surveillance thermique ni rétrogradation de palier en cours de session. `TierConfig.useArCorePose` est désormais branché (voir point 13, intégré le 6 août 2026) -- la résolution de capture, elle, n'est toujours pas adaptée au palier (`ImageAnalysis.Builder()` ne fixe aucune résolution cible, CameraX choisit son défaut quel que soit `COMPATIBLE` ou `OPTIMAL`). Rien d'urgent ici, mais ce sont des leviers d'optimisation "gratuits" déjà prévus dans l'architecture et pas encore tous exploités -- le throttling thermique en particulier reste d'autant plus utile maintenant que le palier `OPTIMAL` combine ARCore et MediaPipe simultanément (voir les risques listés au point 13).

## 4. Envoi réseau VMC : un paquet UDP par blendshape

`VmcOscSender.send()` boucle sur les ~52 blendshapes et envoie un `OSCMessage` (`/VMC/Ext/Blend/Val`) par un appel `out.send(...)` séparé, plus un dernier pour `/Apply` — soit une cinquantaine de paquets UDP distincts par frame envoyée, potentiellement 1500+ paquets/seconde en régime établi. Le protocole OSC supporte le regroupement en `OSCBundle` : construire un seul bundle avec tous les messages `Val` + le `Apply` et l'envoyer en un seul appel réduirait drastiquement le nombre de syscalls réseau, sans changer le format reçu côté VTube Studio/Blender/Unity.

## 5. Capteurs et récepteur batterie pas alignés sur le cycle de vie de l'app

`DeviceOrientationTracker`, `IconOrientationTracker` et `BatteryMonitor` sont démarrés/arrêtés via des `DisposableEffect` (ou dans `initializeTracking`) qui ne se redéclenchent que si la clé passée change ou si le composable quitte totalement la composition — pas quand l'utilisateur bascule sur une autre appli (l'Activity passe en arrière-plan sans être détruite). Contrairement à `CameraController` qui se coupe automatiquement grâce à `bindToLifecycle`, ces trois-là continuent d'écouter les capteurs/le broadcast batterie en arrière-plan, consommation inutile tant que l'app n'est pas relancée au premier plan. Les accrocher au cycle de vie réel de l'Activity (`LifecycleEventObserver` sur `ON_PAUSE`/`ON_RESUME`) réglerait ça proprement.

## 6. `initializeTracking()` n'est pas protégé contre un double appel

Rien n'empêche `MainViewModel.initializeTracking()` d'être invoqué deux fois (par exemple si `hasCameraPermission` redevient `false` puis `true` sans destruction de l'Activity) : chaque appel recrée un `FaceLandmarkerHelper` et un `CameraController` complets, écrasant les références précédentes sans jamais fermer l'ancien `FaceLandmarker` ni arrêter l'ancien exécuteur caméra — fuite de thread et de ressources natives MediaPipe. Un simple garde (`if (faceLandmarkerHelper != null) return`) sécuriserait ce point, peu probable en usage normal mais pas impossible.

## 7. Un seul `StateFlow` géant pour tout l'état UI

`MainUiState` mélange des données qui changent à chaque frame (`allBlendshapes`, `inferenceTimeMs`, `faceDetected`) avec des données qui ne changent presque jamais (réglages, connexion, calibration). Comme `MainScreen` collecte ce flux unique via un seul `collectAsState()`, toute mise à jour — y compris les 30-60 mises à jour de blendshapes par seconde — redéclenche une recomposition de tout l'écran, même quand le panneau de blendshapes est masqué (mode éco, aucune sélection). Séparer l'état "chaud" (par frame) de l'état "froid" (réglages/connexion) dans deux flux distincts limiterait la recomposition aux seuls composables qui en ont réellement besoin — gain surtout sensible sur les appareils `COMPATIBLE`, déjà les plus sollicités.

## 8. Build release non optimisé

`isMinifyEnabled = false` en configuration `release` : ni réduction de code (R8) ni obfuscation, donc un APK plus gros et potentiellement des méthodes non inlinées/non optimisées par rapport à ce qu'un build release permettrait. Pour un PoC personnel ce n'est pas bloquant, mais à activer avant toute distribution plus large (avec les règles ProGuard nécessaires pour MediaPipe/OSC, à vérifier une par une pour éviter de casser la réflexion utilisée par ces libs).

## Priorités suggérées (à l'origine)

Si l'objectif est de gagner en fluidité et en autonomie perçues sur le terrain, les points 1 et 2 (bitmap par frame + absence de throttling) sont ceux qui rapporteraient le plus, suivis du point 4 (paquets OSC groupés) qui est isolé et à faible risque de régression. Les points 5 et 6 sont des corrections de robustesse plutôt que de performance pure, utiles mais moins urgentes. Le point 7 est un investissement plus structurant (à faire si d'autres écrans/fonctionnalités per-frame s'ajoutent). Le point 8 peut attendre une vraie mise en distribution.

## Mise à jour -- 6 août 2026

Les points 1, 2, 4, 5, 6 et 7 sont traités (pool de bitmaps + throttling FPS par palier, bundle OSC unique, capteurs/init alignés sur le cycle de vie, état Compose chaud/froid séparé) -- voir l'historique de commits et `AndroidMoCap_tests_unitaires.md` pour le détail de chacun. Restent ouverts le point 3 (throttling thermique dynamique / pose ARCore phase 2 / résolution par palier, toujours des leviers non exploités) et le point 8 (build release non minifié), plus les points ci-dessous, apparus depuis.

### 9. L'extraction du mesh facial se fait à chaque frame même quand l'overlay est désactivé -- ✅ corrigé

`FaceLandmarkerHelper.onLiveStreamResult` construisait systématiquement la liste des 478 points, que l'overlay soit affiché ou non -- et il est **désactivé par défaut**. Corrigé : `FaceLandmarkerHelper.setLandmarksNeeded()` (appelé par `MainViewModel` à chaque changement du réglage, et à l'initialisation) court-circuite l'extraction tant que l'overlay n'est pas actif.

### 10. `README.md` décrit une UI qui n'existe plus -- ✅ résolu

Le README (section "Builder sur un appareil physique") mentionnait un "aperçu diagnostic en haut à gauche" et un "champ IP sur l'écran principal" -- toute cette UI a été remplacée depuis par le bandeau d'icônes minimal + l'écran de réglages dédié. La section "Prochaines étapes" listait aussi des choses déjà faites à côté de choses toujours vraies et d'idées jamais mentionnées ailleurs.

**Mise à jour :** README entièrement réécrit (6 août 2026) pour servir de page d'accueil du dépôt -- statut des fonctionnalités aligné sur le code réel (langue/joues explicitement notées "à l'étude, pas implémentées", ARCore correctement décrit comme non fusionné), référence morte vers `AndroidMoCap_etude_options.md` supprimée (fichier jamais présent dans le dépôt), nouvelles sections Installation, Vie privée et réseau, Licence, Contribuer, Documentation.

### 11. Pas de configuration de signature release, versionnage figé

`app/build.gradle.kts` n'a pas de bloc `signingConfigs` : un `assembleRelease` produirait un APK non signé, impossible à installer tel quel. `versionCode`/`versionName` sont toujours à `1`/`0.1.0-poc`, alors que l'app a beaucoup changé depuis. Sans conséquence tant que le build reste local (`installDebug`), mais bloquant dès qu'il s'agit de partager un APK -- voir la discussion sur le déploiement.

### 12. Distribution (open source / GitHub Releases) -- ✅ mis en place

Ajout d'un `signingConfigs.release` lu depuis des variables d'environnement (jamais commité) et d'un
workflow `.github/workflows/release.yml` : un tag `vX.Y.Z` poussé déclenche un build, télécharge le
modèle MediaPipe, signe l'APK avec la clé stockée en secret GitHub et publie une Release avec l'APK
attaché. Procédure complète (génération de la clé, secrets à créer) dans le README, section
"Distribution". `versionCode`/`versionName` passés à `2`/`0.2.0`. Le point 8 (minify/R8) reste
volontairement désactivé -- voir sa note mise à jour ci-dessus.

### 13. Fusion ARCore (palier `OPTIMAL`, phase 2) -- ✅ intégrée sur `main`, vérification device en attente

En creusant ce que "brancher ARCore" impliquerait concrètement, un point bloquant est apparu, qui
explique en partie pourquoi ce n'était encore qu'un commentaire dans le code : **ARCore Augmented
Faces et CameraX se disputent le même accès caméra**. `ArCoreApk`/`Session` en mode Augmented Faces
gère lui-même la capture caméra frontale en interne -- il ne consomme pas un flux `ImageAnalysis`
qu'on lui donnerait, contrairement à MediaPipe. Or Camera2 (sous CameraX comme sous ARCore) n'autorise
qu'un seul client actif sur une caméra donnée à la fois (l'API "shared camera" d'ARCore existe mais
cible l'usage caméra arrière + suivi du monde, pas ce cas de figure caméra frontale + Augmented Faces).
Impossible donc, tel quel, de laisser CameraX nourrir MediaPipe **et** de laisser ARCore tourner en
parallèle sur la même caméra pour le palier `OPTIMAL`.

Piste retenue pour la suite : basculer la source d'image côté `OPTIMAL` -- au lieu que CameraX
alimente MediaPipe, laisser `Session` piloter la caméra, récupérer les frames via
`Frame.acquireCameraImage()` (image YUV) pour les convertir en `MPImage` et continuer à nourrir
MediaPipe (blendshapes) avec ce flux, et utiliser `AugmentedFace.centerPose` (position + quaternion)
comme source de pose de tête à la place de `facialTransformationMatrixes()` -- CameraX ne serait alors
simplement plus utilisé pour ce palier (il resterait tel quel pour `STANDARD`/`COMPATIBLE`). C'est un
changement structurant côté `CameraController`/`FaceLandmarkerHelper`, pas un simple ajout.

En attendant de pouvoir vérifier ça sur device (disponibilité réelle d'Augmented Faces variable selon
les appareils, comportement de `acquireCameraImage()` à valider empiriquement), seule la brique de
maths pure et sans risque a été préparée en TDD : `RotationMath.rotation3x3FromQuaternion(x, y, z, w)`
convertit le quaternion `Pose#getRotationQuaternion()` d'ARCore vers le même format de matrice 3x3
row-major que `rotation3x3FromColumnMajor4x4`, pour pouvoir rejoindre `composeCalibratedEuler` sans le
dupliquer une fois le reste branché. Voir `AndroidMoCap_tests_unitaires.md`.

**Mise à jour (6 août 2026) : intégration à neuf sur `main`.** Le travail existait déjà sur la
branche `feature/arcore-fusion` (jamais fusionnée, jamais testée sur device), mais un test de merge
à blanc (fait puis annulé sans rien committer) a montré 13 fichiers en conflit -- l'UI/le ViewModel
ont trop divergé depuis (menu à 4 sous-écrans, état enrichi, localisation, correctif du mesh
overlay point 27) pour que fusionner soit raisonnable. Décision : récupérer uniquement les deux
fichiers purs et isolés de la branche (`ArCoreHeadPoseTracker.kt`, `ArCoreFaceSelector.kt` + test --
commit `28941c0`, compilent et testent sans aucun ajustement, dépendance ARCore déjà câblée,
`MediaImageBuilder` confirmé identique en MediaPipe 1.0.0 par décompilation `javap`), puis
réimplémenter l'intégration caméra/UI entièrement à neuf contre l'architecture actuelle.

Conception (décisions actées avec l'utilisateur) :
- Pas d'aperçu caméra live pour ce palier dans cette passe -- l'overlay du mesh de tracking (478
  points) est le seul retour visuel, **forcé visible** quand ARCore est la source caméra active
  (indépendamment du réglage "Overlay du mesh", qui reste un diagnostic optionnel pour les autres
  paliers). Conçu pour ne pas fermer la porte à un futur mode "rendu live" (toggle dédié, avec
  avertissement utilisateur si coûteux en ressources) -- pas construit maintenant.
- Le mode économie d'énergie garde la priorité par défaut (l'overlay, forcé ou non, y disparaît
  comme avant) ; nouveau réglage `keepMeshOverlayInPowerSave` (Affichage & confort, tous paliers
  confondus, défaut désactivé) pour le garder visible même en éco.
- Logique de visibilité extraite en fonction pure testée (`ui/MeshOverlayVisibility.kt` +
  `MeshOverlayVisibilityTest.kt`, 8 cas), même principe que `LandmarkProjection.kt`.

Câblage (`ui/MainViewModel.kt`) : `initializeTracking()` branche sur `TierConfig.useArCorePose` --
construit `ArCoreHeadPoseTracker` (jamais en même temps que `CameraController`, les deux sont
mutuellement exclusifs) avec repli automatique et silencieux sur `CameraController`/CameraX si
`onUnavailable` se déclenche (ARCore non installé, appareil incompatible, config caméra frontale
refusée...) -- testé synchrone dès `initializeTracking()` (pas seulement sur `ON_START`) pour que le
repli soit déjà effectif avant que `MainScreen` n'appelle `startCamera()`. La pose de tête reçue via
`onHeadPoseRotationMatrix` (thread GL, `@Volatile`) remplace celle de MediaPipe
(`facialTransformationMatrixes()`, toujours calculée mais ignorée dans ce cas) dans
`handleTrackingResult()`, avec repli sur la matrice MediaPipe tant qu'ARCore n'a pas encore émis de
pose. `ui/MainScreen.kt` héberge un `GLSurfaceView` (via `AndroidView`, même patron que le
`PreviewView` CameraX) à la place de l'aperçu caméra quand ARCore est actif. `DiagnosticsScreen`
affiche la source caméra effective (ARCore/CameraX) au palier `OPTIMAL`, utile pour repérer un repli
silencieux sur l'appareil de test.

Vérifié (sans device, comme le reste de cette session) : `testDebugUnitTest` (61 tests, 0 échec,
dont les 8 nouveaux `MeshOverlayVisibilityTest`) + `assembleDebug` → succès, compilation propre du
premier coup sans aucun ajustement.

**Risques connus, non résolus, à vérifier au premier accès device (pas deviner/corriger à
l'aveugle) :**
1. ~~`ArCoreHeadPoseTracker.emitCameraImage()` ne corrige ni la rotation ni le miroir~~ -- toujours
   vrai (voir plus bas), mais un problème plus fondamental a été trouvé en premier sur device (voir
   "Suivi device" ci-dessous) : le format d'image lui-même faisait planter l'app.
2. `DeviceCapabilityDetector.detect()` lit `ArCoreApk.checkAvailability().isSupported` de façon
   synchrone, un seul appel au démarrage -- un état transitoire `UNKNOWN_CHECKING` (tout premier
   appel ARCore jamais mis en cache sur l'appareil) serait interprété comme "non supporté" pour
   toute la session. Pré-existant, pas introduit par cette intégration.
3. Pas de bloc `<queries>` pour `com.google.ar.core` dans le manifeste -- sans impact tant que le
   repli reste silencieux (pas de flux `ArCoreApk.requestInstall()` prévu), à confirmer si un jour
   un flux d'installation est ajouté.
4. Coût thermique/batterie du duo GL (`RENDERMODE_CONTINUOUSLY`) + inférence MediaPipe simultanés,
   sur un palier qui ne bénéficie toujours pas du throttling thermique dynamique (jamais branché,
   voir plus haut dans ce même point).

**Suivi device (6 août 2026, même jour) : crash au premier lancement réel, corrigé.**
`frame.acquireCameraImage()` d'ARCore renvoie toujours du YUV_420_888 (aucune option RGBA côté
ARCore, contrairement à CameraX/`ImageAnalysis` que `CameraController` configure explicitement en
RGBA) -- passer ça tel quel à `MediaImageBuilder` faisait planter l'app dès la première frame
(`UnsupportedOperationException: Android media image must use RGBA_8888 config`, thread GL, logcat
fourni par l'utilisateur). Corrigé (commit `250e94c`) : conversion manuelle YUV_420_888 → `Bitmap`
ARGB_8888 (`ArCoreHeadPoseTracker.yuv420ToBitmap()`, coefficients BT.601) puis `BitmapImageBuilder`
au lieu de `MediaImageBuilder` -- même format que celui déjà produit par `CameraController`.
Simplification en cascade : l'`Image` ARCore n'a plus besoin d'être gardée ouverte jusqu'à la fin du
traitement MediaPipe (fermée immédiatement après conversion), donc le pool d'images en vol
(`inFlightImages`/`MAX_TRACKED_IMAGES`) n'a plus lieu d'être et a été supprimé ; `releaseFrame()`
devient un no-op documenté.

Deux nouveaux risques ouverts par ce correctif, non résolus, à vérifier/mesurer sur device plutôt
que deviner :
5. `yuv420ToBitmap()` alloue un nouveau `Bitmap` par frame et copie les pixels un par un (pas de
   pool de réutilisation comme `CameraController.acquirePooledBitmap`) -- potentiellement coûteux à
   60 fps (palier `OPTIMAL`). À mesurer avant d'optimiser.
6. Le point 1 ci-dessus (rotation/miroir non corrigés) reste entièrement d'actualité malgré ce
   correctif -- la conversion YUV→RGB ne fait que rendre l'image lisible par MediaPipe, elle ne dit
   rien de son orientation.

**Suivi device (6 août 2026, même jour, suite) : rotation corrigée, warning `Display geometry`
corrigé, perf déportée sur thread dédié -- tracking fonctionnel confirmé par l'utilisateur.**

- **`Session.setDisplayGeometry()` jamais appelé** (commit `22dae4b`) : omission par rapport au
  boilerplate ARCore standard, cause confirmée du warning natif répété `view_manager_utils.cc:
  Display geometry has an invalid width: 0`. `ArCoreHeadPoseTracker` mémorise maintenant les
  dimensions du `GLSurfaceView` (`onSurfaceChanged`) et appelle `setDisplayGeometry(ROTATION_0, ...)`
  dès qu'une session existe, quel que soit l'ordre d'arrivée (même patron que
  `maybeBindCameraTexture()`).
- **Rotation de l'image caméra** (commit `fdee8aa`) : lue via Camera2
  `CameraCharacteristics.SENSOR_ORIENTATION` pour la caméra choisie par ARCore -- même source que
  celle utilisée en interne par CameraX, pas une valeur devinée -- puis appliquée via
  `rotateBitmap()` qui réutilise `CameraController.rotatedDimensions()` (fonction pure déjà
  testée). **Confirmé fonctionnel par l'utilisateur** : tracking correctement orienté, et la
  latence perçue s'est nettement améliorée du même coup -- le warning `aimatter_landmarks_3Dmesh.cc:
  Not able to find preprocess rotation with expected timestamp` était vraisemblablement causé par
  la même absence de rotation (probablement résolu, pas encore reconfirmé explicitement dans un
  nouveau logcat).
- **Conversion déportée sur thread dédié** (commit `a247b95`), sur demande explicite de
  l'utilisateur : la conversion YUV→RGB + rotation (coûteuse, pixel par pixel) tournait en
  synchrone sur le thread GL (`onDrawFrame`), ralentissant à la fois le rendu et la cadence de
  `session.update()`. Déportée sur `imageProcessingExecutor` (thread unique dédié, même convention
  que `CameraController.cameraExecutor`) ; `acquireCameraImage()` reste sur le thread GL (lié à
  `session.update()`), seule la suite est déportée. Contre-pression explicite
  (`pendingConversions`/`MAX_PENDING_CONVERSIONS = 1`) : une frame est abandonnée plutôt que mise
  en file si le thread dédié a du retard -- **principe explicitement demandé par l'utilisateur** :
  les données envoyées au récepteur (blendshapes/pose) doivent rester prioritaires et à jour, une
  frame caméra sautée est préférable à un retard qui s'accumule.
- **Récepteur (VBridger)** : déjà validé séparément par l'utilisateur en palier `STANDARD`
  (CameraX) avant cette session -- pas encore revérifié spécifiquement avec la source caméra
  ARCore, mais rien dans le pipeline de données (blendshapes, protocole réseau) n'a changé entre
  les deux paliers, seule la source caméra/pose diffère.

Risques restants après cette série de correctifs (mise à jour de la liste ci-dessus) :
- Point 2 (vérification `ArCoreApk` synchrone) et point 3 (pas de `<queries>`) : inchangés, toujours
  ouverts.
- Point 4 (coût thermique du duo GL+MediaPipe) : partiellement atténué par le déport de thread
  ci-dessus (le thread GL est moins chargé), mais le throttling thermique dynamique reste non
  branché -- toujours pertinent pour une session longue.
- Nouveau point mineur : `yuv420ToBitmap()` alloue toujours un `Bitmap` par frame (pas de pool de
  réutilisation) -- non prioritaire vu le gain déjà obtenu par le déport de thread, à reconsidérer
  seulement si un nouveau signal de coût apparaît.

### 14. Vérification de mise à jour semi-automatique -- à faire (backlog)

Discuté suite au point 12 (distribution GitHub Releases) : pas de mise à jour automatique possible
hors store (Android exige une confirmation manuelle d'installation pour tout APK sideloadé, protection
système, pas une limite contournable côté code). Piste retenue : l'app interroge périodiquement
l'API GitHub (`GET /repos/guyiome/AndroidMoCap/releases/latest`), compare le `versionCode` reçu au
sien, et affiche une bannière non intrusive ("Mise à jour disponible") avec un lien direct vers l'APK
si une version plus récente existe -- l'utilisateur garde la main, un ou deux taps suffisent ensuite.
Logique de comparaison de version + parsing de la réponse JSON sont de bons candidats à extraire en
fonction pure et à couvrir en TDD le moment venu, avant de toucher à l'appel réseau/UI qui les entoure.

### 15. Navigation retour des écrans superposés -- ✅ corrigé

Retour de test sur émulateur : la croix de fermeture (`Icons.Filled.Close`) était difficilement
cliquable sur les six écrans superposés (`SettingsScreen` et ses quatre sous-écrans, plus
`BlendshapeSelectionScreen`). Remplacée par une flèche de retour standard
(`Icons.AutoMirrored.Filled.ArrowBack`, 28dp au lieu de la taille par défaut ~24dp) sur les six
écrans, et ajout de `androidx.activity.compose.BackHandler(onBack = onClose)` sur chacun : le
bouton retour matériel et le geste de balayage système (predictive back, Android 13+) déclenchent
maintenant la même fermeture que l'icône, sans détection de geste custom (`BackHandler` est
justement le point d'accroche du geste système). `androidx-activity-compose` était déjà une
dépendance du projet, aucun ajout nécessaire. Aucune logique pure à extraire ici -- pur remaniement
UI, pas de nouveau test unitaire.

### 22. Licence du projet -- PolyForm Shield 1.0.0, avec CLA en prévision de contributeurs

Constat de départ : aucun fichier `LICENSE` n'existait jusqu'ici, alors que le README parlait
d'"open source" -- légalement, le code restait "tous droits réservés" par défaut. Décidé suite à
une revue des options (permissif type MIT/Apache 2.0, copyleft type GPL/AGPL, source-available,
dual licensing) : le projet adopte la **PolyForm Shield License 1.0.0** (voir `LICENSE`).

Raisonnement : l'objectif prioritaire identifié est de se protéger contre le risque concret qu'un
tiers reprenne le dépôt pour publier un produit concurrent (par exemple sur le Play Store, là où ce
projet ne peut pas aller pour l'instant) -- risque d'autant plus réel que le comparatif
concurrentiel a confirmé un vrai vide de marché (MeowFace disparu, aucun successeur). PolyForm
Shield répond exactement à ce risque (interdiction de fournir un produit qui concurrence le
logiciel) sans restreindre l'usage normal des utilisateurs visés. C'est ce dernier point qui a
écarté PolyForm Noncommercial, envisagée d'abord : cette variante interdit tout usage commercial
par les licenciés, ce qui aurait pu techniquement inclure l'usage par un streamer dont le contenu
est monétisé -- exactement le public ciblé par l'app, donc contre-productif.

Réversibilité actée en discussion : un changement de licence n'est jamais rétroactif (ce qui a déjà
été distribué sous ces termes le reste pour toujours), et assouplir plus tard (vers Apache 2.0/MIT
par exemple) est sans risque, alors que durcir après coup est structurellement fragile -- des
précédents connus du secteur (Elasticsearch/OpenSearch, Terraform/OpenTofu, Redis/Valkey) montrent
que la communauté peut simplement forker la dernière version permissive et continuer sous les
anciens termes. D'où le choix de partir protecteur maintenant plutôt que l'inverse.

Pour préparer un passage éventuel vers une licence plus permissive si le projet accepte des
contributions externes un jour, un accord de licence contributeur (`CLA.md`, référencé depuis
`CONTRIBUTING.md`) a été rédigé : chaque contribution garde son auteur comme détenteur des droits,
mais accorde au mainteneur une licence assez large pour relicencier l'ensemble du projet plus tard
sans avoir à retrouver individuellement chaque contributeur. Sans ce document, relicencier
nécessiterait l'accord explicite de chaque personne ayant contribué au fil du temps.

Non traité pour l'instant, à reprendre si un produit payant voit le jour : la mention
`Licensor Line of Business:` prévue par PolyForm Shield, qui permettrait de protéger une ligne de
métier précise (ex. "AndroidMoCap Pro") même en cas de pause ou d'arrêt -- n'a de sens que le jour
où un tel produit existe réellement.

Statut : décision actée et mise en œuvre -- `LICENSE`, `CLA.md`, `CONTRIBUTING.md` créés, `README.md`
mis à jour (section Licence, correction de la mention "open source").

### 23. Localisation complète de l'UI -- ✅ extraction faite, vérification visuelle device en attente

L'app a été construite au fil de l'eau entièrement en français : `strings.xml` ne contient que
`app_name`, tout le texte utilisateur est écrit en dur dans les appels `Text(...)` Compose,
directement dans les 14 fichiers du package `ui/` (plus quelques messages d'erreur dans
`CameraController.kt` et `MainViewModel.kt`). Estimation après inspection : ~90-110 chaînes
distinctes à sortir vers des ressources, 0% actuellement externalisé.

Trois niveaux de difficulté identifiés :
- La majorité (~60) sont des chaînes statiques simples -- extraction mécanique et peu risquée vers
  `strings.xml` + `stringResource(R.string.x)`.
- Une quinzaine sont des chaînes composées avec des valeurs dynamiques (ex. le sous-titre
  "Palier ... · visage détecté/non détecté" de `SettingsScreen`/`DiagnosticsScreen`, ou "Délai
  d'inactivité : Xs" de `DisplaySettingsScreen`) -- besoin de ressources à placeholders plutôt qu'un
  remplacement 1:1, l'ordre des mots pouvant changer d'une langue à l'autre.
- Les messages d'erreur de `MainViewModel.kt`/`CameraController.kt` posent un vrai souci
  d'architecture : ni l'un ni l'autre n'est un `@Composable`, donc pas d'accès direct à
  `stringResource()` -- il faudra soit leur passer un `Context`/`Application`, soit faire remonter
  des identifiants de ressource jusqu'à la couche UI qui les résout à l'affichage.

Les 7 libellés de `BlendshapeCategory` ("Sourcils", "Yeux"...) sont triviaux à extraire. À l'inverse,
les 52 noms de blendshapes ARKit (`jawOpen`, `mouthSmileLeft`...) ne doivent PAS être traduits --
c'est le vocabulaire du protocole, pas du texte d'affichage.

Une fois l'extraction faite, il restera : un dossier `res/values-en/` avec les traductions,
`android:localeConfig` dans le manifeste pour le sélecteur de langue par app (Android 13+), et une
repasse visuelle de chaque écran en langue secondaire (dépend d'un accès device, comme le reste des
vérifications visuelles en attente).

Nature du travail : un seul commit "refactor" au sens de la règle de commit du projet (aucune
nouvelle fonctionnalité, juste un découpage) -- mais qui touche tous les écrans, donc plus risqué à
faire à l'aveugle sans pouvoir vérifier visuellement ensuite. La passe d'extraction elle-même peut
démarrer sans device (validable structurellement) ; seule la vérification visuelle finale attend un
accès device.

**Mise à jour (6 août 2026) : extraction faite**, commit `f0da6be`. 78 chaînes distinctes
externalisées (moins que l'estimation ~90-110 : beaucoup de doublons regroupés sous une seule
ressource -- "Retour" apparaissait 6 fois, "Réglages"/"Diagnostics"/"Affichage & confort" servaient à
la fois de titre d'écran et de titre de ligne de menu, etc.) vers `res/values/strings.xml` (FR,
défaut) + traduction complète dans `res/values-en/strings.xml`. Portée réelle : 11 des 14 fichiers
`ui/` (`FaceMeshOverlay.kt`, `PowerSaveOverlay.kt`, `LandmarkProjection.kt` n'avaient aucune chaîne
utilisateur, seulement des commentaires) plus `CameraController.kt` et `FaceLandmarkerHelper.kt`.

Deux points prévus comme difficiles se sont révélés plus simples à l'usage :
- **Messages d'erreur hors `@Composable`** : `MainViewModel` est un `AndroidViewModel`, qui a déjà
  `getApplication<Application>()` ; `CameraController` et `FaceLandmarkerHelper` ont chacun déjà un
  champ `Context` dans leur constructeur. Aucun changement d'architecture n'a donc été nécessaire
  (contrairement à l'hypothèse "Context/Application à faire remonter" envisagée plus haut) --
  `context.getString(R.string.x, ...)` suffit partout.
- **Labels de `BlendshapeCategory`** : la classe reste une fonction pure inchangée dans
  `tracking/BlendshapeCatalog.kt` (pas d'accès à `stringResource()` en dehors d'un `@Composable`, et
  pas de raison d'en ajouter un pour un enum testé en JVM) -- un mapping `displayLabel()`
  `@Composable` résout le texte localisé dans `ui/BlendshapeSelectionScreen.kt`, seul point d'usage.

Ajouté dans la foulée : `android:localeConfig` (`AndroidManifest.xml`) + `res/xml/locales_config.xml`
pour le sélecteur de langue par app (Android 13+, fr/en).

Vérifié : `testDebugUnitTest` (49 tests, 0 échec, identique à l'état pré-refactor -- confirme
qu'aucun comportement n'a changé) + `assembleDebug` → succès. Vérification croisée FR/EN : 78 clés de
chaque côté, aucune manquante ni orpheline, aucune référence `R.string.*` cassée dans le code.

**Question soulevée après coup par l'utilisateur, traitée le même jour : que se passe-t-il sur
Android 11/12 ?** Le sélecteur manuel de langue par app (`localeConfig`) est spécifique à Android
13+ -- sur 11/12 (couverts par `minSdk = 30`), aucune UI système équivalente n'existe. Sur toutes les
versions cependant, la résolution de ressources Android standard (indépendante de `localeConfig`,
fonctionne depuis toujours) fait déjà suivre l'app à la langue système du téléphone : francophone →
FR, anglophone → EN, autre langue → repli sur le dossier par défaut (`res/values/`, sans qualificatif
de langue). Décision actée en discussion : **ce repli par défaut passe du français à l'anglais**
(commit `9454bb8`) -- l'anglais étant plus universel, un utilisateur non francophone ni anglophone a
statistiquement plus de chances de comprendre l'anglais. Concrètement : `res/values/` (défaut/repli)
contient désormais le texte anglais, `res/values-fr/` (nouveau) le texte français explicite ;
`res/values-en/` supprimé, devenu redondant avec le défaut. Le français reste entièrement disponible
(langue système française, ou sélecteur par app sur 13+) -- rien n'est retiré, seule la priorité de
repli change. Offrir un vrai sélecteur de langue *dans l'app* pour Android 11/12 (équivalent du
sélecteur système 13+) demanderait `androidx.appcompat` + `AppCompatDelegate.setApplicationLocales()`
plus un contrôle dédié dans les réglages -- pas fait ici, `MainActivity` reste un `ComponentActivity`
100% Compose sans AppCompat ; à reconsidérer si Android 11/12 s'avèrent représenter une part notable
du parc visé.

**Backlog ouvert par ce travail : validation des traductions.** Le texte anglais (`res/values/`,
maintenant le repli par défaut pour la majorité des locales non reconnues) et le texte français
(`res/values-fr/`) ont été rédigés dans cette session sans relecture native -- à faire valider par un
locuteur natif de chaque langue avant une diffusion plus large, en particulier l'anglais vu son rôle
de repli universel. Aucun code bloquant, juste une relecture de contenu.

**Reste ouvert** : la repasse visuelle sur device (les deux langues, sur chaque écran) -- seule étape
qui dépendait d'un accès device, comme anticipé.

## Priorités suggérées (mise à jour)

Le point 9 est un correctif ciblé, sûr à faire sans pouvoir tester sur device (aucun impact visuel, juste un travail évité). Le point 10 est traité (README réécrit). Le point 3/13 (ARCore phase 2) est maintenant mieux cerné mais reste un investissement lourd et risqué à finir "à l'aveugle" -- la suite (bascule caméra CameraX→ARCore) attend un accès device. Le point 11 (signature + versionnage) est traité (point 12). Le point 8 (minify) reste pour plus tard, une fois les tests device de nouveau possibles. Le point 14 (vérification de mise à jour) est en backlog, pas urgent. Le point 15 (navigation retour) est traité. Le point 16 (dénomination du mode iFacialMocap) est traité. Le point 17 (CI build/tests sur PR) est traité. Le point 27 (projection du mesh overlay) est traité, corrigé sans dépendre d'un nouvel accès device (le bug avait déjà été reproduit par la photo fournie).

**Ordre de priorité actuel (mis à jour) :** ~~1) revue/merge des PR #5 et #6~~ -- fait, voir suivi
PR #5/#6 ci-dessous. ~~2) point 23 (localisation complète de l'UI)~~ -- extraction faite (voir
section dédiée ci-dessus), seule la vérification visuelle device reste ouverte. Prochain choix
ouvert : pas de priorité unique déjà tranchée dans ce document au-delà de ces deux points -- voir
l'index en tête de document pour le reste du backlog (points 3/13, 8, 14, 19, 20, 28).

**Suivi PR #5/#6 (6 août 2026)** : PR #6 (Kotlin 2.3.20→2.4.10, risque jugé faible -- même clé de
version pilote le plugin Compose, voir §CI ci-dessus) mergée sur GitHub (commit `030aae5`) suite à
validation explicite de l'utilisateur. PR #5 (MediaPipe tasks-vision 0.10.35→1.0.0, première version
stable, risque non vérifié) volontairement laissée ouverte, en attente de test device avant tout
merge -- consigne explicite de ne pas y toucher sans validation.

~~⚠️ Point d'attention pour le prochain push...~~ **Résolu** : contrairement à ce que cette section
affirmait, ce sandbox *a* un accès réseau à GitHub (confirmé par un `git fetch` qui a bien remonté le
merge serveur de la PR #6). Cause de l'écart, confirmée par l'utilisateur : cette note avait été
écrite depuis un environnement **Cowork** (la conversation "réflexion" non-codante mentionnée en
introduction de ce document), un sandbox isolé sans accès réseau sortant vers `api.github.com` --
alors qu'une session Claude Code classique (comme celle-ci) y a accès. Retenir pour l'avenir :
l'accès réseau (fetch/push GitHub) dépend du type d'environnement d'exécution, pas du dépôt lui-même
-- toujours le tester dans la session en cours plutôt que de se fier à une note écrite depuis un
environnement différent. `git rebase origin/main` a résolu la divergence sans conflit (le commit
local du bump Kotlin, identique au commit `ec3989d` déjà sur `origin/main`, a été automatiquement
sauté par git), puis `git push origin main` a réussi (`030aae5..222adea`, fast-forward, validation
explicite de l'utilisateur obtenue avant le push).

**Suivi PR #5/#6 (mise à jour, même jour)** : rattrapage local complet des deux PR, chacune en
commit dédié conformément à la convention "local d'abord" adoptée le même jour, puis poussées.
- PR #6 (Kotlin 2.4.10) : bump reproduit localement, identique au commit Dependabot déjà mergé côté
  serveur (`ec3989d`/`030aae5`) -- absorbé par le rebase, pas de commit local distinct au final.
  `testDebugUnitTest` + `assembleDebug` → succès, seul warning est une dépréciation
  `LocalLifecycleOwner` préexistante et sans rapport.
- PR #5 (MediaPipe tasks-vision 1.0.0) : testée avant merge comme prévu -- l'artefact `1.0.0` se
  résout correctement depuis Maven, `testDebugUnitTest` (49 tests/8 classes, 0 échec, dont
  `FaceLandmarkerHelperTest`) et `assembleDebug` → succès, aucune rupture de compilation dans
  `FaceLandmarkerHelper.kt` (seul point de contact avec l'API MediaPipe). Validation utilisateur
  obtenue sur cette base ; bump commité (commit `1305cbc` après rebase). **Validée sur device par
  l'utilisateur le même jour** : comportement runtime réel (init MediaPipe, delegate GPU, callback
  live-stream) conforme -- plus aucune réserve, point totalement clos.

Les deux PR sont maintenant traitées, validées de bout en bout (compilation, tests JVM, comportement
device) **et poussées sur `origin/main`**. Reste : fermer manuellement la PR #5 sur GitHub (branche
Dependabot `dependabot/gradle/com.google.mediapipe-tasks-vision-1.0.0` toujours présente côté serveur)
-- pas d'accès `gh`/API GitHub authentifiée depuis ce sandbox pour le faire par outil, à faire par
l'utilisateur ou Dependabot la fermera de lui-même en detectant que `main` satisfait déjà la version
cible.

### 24. Indicateur de fiabilité par blendshape -- ✅ corrigé (correspond au "point 17" de l'index ci-dessus)

`BlendshapeCatalog.unreliable` : ensemble statique des blendshapes connus pour être mal restitués
par MediaPipe (`jawForward`, `jawLeft`, `jawRight`, `mouthDimpleLeft`, `mouthDimpleRight`,
`cheekPuff`, `tongueOut`). `BlendshapeSelectionScreen` affiche une petite icône d'avertissement
(14dp, couleur ambre) à côté du nom concerné -- purement informatif, n'empêche pas la sélection.
Couvert en TDD : cohérence de la liste avec le catalogue complet (pas de faute de frappe silencieuse
qui la viderait de son effet), non-vide.

### 25. Persistance optionnelle de la sélection de blendshapes -- ✅ persistance faite, volet valeur brute/ajustée en attente (correspond au "point 18" de l'index ci-dessus)

`AppSettingsStore` : `persistBlendshapeSelectionEnabled` (défaut `false`, comportement historique de
remise à zéro au lancement inchangé tant que non activé) + `persistedBlendshapeSelectionNames`.
`MainViewModel` charge la sélection sauvegardée une seule fois au lancement (`first()`, pas de
reboucle avec les écritures ultérieures) si le réglage est actif ; sauvegarde à la volée à chaque
bascule quand actif. Switch dédié dans `DisplaySettingsScreen`.

Volet non traité : afficher la valeur brute à côté de la valeur ajustée -- n'a pas de sens tant
qu'aucune pondération par blendshape n'existe (rien à "ajuster" aujourd'hui). Reste en attente,
dépend d'abord de la fonctionnalité de gain/poids par blendshape mentionnée en hors-périmètre dans
`AndroidMoCap_spec_fonctionnelle.md` §4.

### 26. Tri de l'écran de réglages en 4 sous-écrans -- ✅ corrigé (correspond au "point 21" de l'index ci-dessus)

`SettingsScreen` est devenu un menu à 4 entrées (Diagnostics, Connexion, Affichage & confort,
Fonctionnalités expérimentales), même patron de navigation qu'utilisait déjà l'écran de sélection
des blendshapes (ligne cliquable + chevron). Nouveaux écrans : `DiagnosticsScreen` (lecture seule),
`ConnectionSettingsScreen` (affichage conditionnel du seul type choisi, plus les deux systématiques
comme avant), `DisplaySettingsScreen`, `ExperimentalFeaturesScreen` (message d'attente, catégorie
réservée aux points 15/16 de l'index ci-dessus, aucun des deux implémenté). Pure extraction/
réorganisation, aucune logique nouvelle -- voir `AndroidMoCap_tests_unitaires.md`.

### 27. Correctif de la projection du mesh overlay (ratio image/écran) -- ✅ corrigé

Retour de test device (ASUS ROG Phone II, photo à l'appui) : le mesh de tracking (overlay optionnel,
478 points) apparaissait écrasé et décalé par rapport au visage -- plus petit que l'écran en
largeur. Sur tablette (XPPen Magic Drawing Pad) le placement était correct (ratio écran/caméra
proches, écart invisible). Comportement anticipé dès l'écriture initiale (commit `01c1e34`) : les
doc comments de `LandmarkProjection.kt` et `FaceMeshOverlay.kt` signalaient déjà l'approximation
volontaire "à affiner après un premier essai sur device si besoin".

Cause : `LandmarkProjection.toScreenPoint()` étirait les coordonnées normalisées MediaPipe
directement sur toute la surface du canvas, sans tenir compte (a) du ratio largeur/hauteur réel de
l'image analysée par `ImageAnalysis` (aucune résolution cible explicite configurée, donc ratio natif
de la caméra) ni (b) du recadrage centré ("FILL_CENTER") appliqué par défaut par `PreviewView` pour
remplir l'écran en conservant ce ratio.

Correctif : `toScreenPoint()` reproduit maintenant ce recadrage centré -- `scale =
max(canvas/image)` sur chaque axe, mise à l'échelle uniforme, puis un décalage centré compensant
l'axe qui déborde. Repli sur l'ancien étirement simple si les dimensions de l'image sont encore
inconnues (avant la première frame). Dimensions de l'image obtenues via le paramètre `input: MPImage`
de `FaceLandmarkerHelper.onLiveStreamResult()`, déjà reçu mais jusqu'ici inutilisé
(`@Suppress("UNUSED_PARAMETER")`, supprimé) -- propagées jusqu'à l'overlay via
`FaceTrackingResult.imageWidthPx/imageHeightPx` puis `MainViewModel.TrackingFrame`. Couvert en TDD :
cas ratio identique (comportement inchangé), cas de rognage sur chaque axe, combinaison avec le
miroir, repli dimensions inconnues -- voir `LandmarkProjectionTest.kt`.

### 16. Dénomination du mode de connexion "iFacialMocap" -- ✅ corrigé

Retour de test : le mode de connexion s'affichait comme "iFacialMocap" dans le sélecteur (chip)
alors que l'app ne se connecte pas à cette application, elle implémente seulement un protocole
compatible -- confusion possible côté propriété intellectuelle et côté utilisateur. Renommage des
chaînes utilisateur uniquement (chip, titre de section, statut, sous-titre du menu réglages) pour
mettre en avant le protocole/les récepteurs réels (VBridger, VSeeFace...) plutôt que le nom de
l'app tierce, tout en gardant "iFacialMocap" en mention secondaire pour rester trouvable par
quelqu'un qui chercherait spécifiquement ce terme (titre de section : "iFacialMocap — protocole
compatible"). Chip : "UDP / VBridger". Statut : "Connexion UDP : …". Aucun identifiant interne
touché : `ConnectionType.IFACIALMOCAP`, la classe `IFacialMocapSender` et ses constantes de
handshake protocolaire restent inchangés -- ce sont des identifiants techniques, pas de la
présentation utilisateur.

### 17. CI de build/tests sur chaque pull request -- ✅ corrigé

Le seul workflow existant (`release.yml`) ne se déclenche que sur un tag `vX.Y.Z` : aucune
vérification automatique ne tournait sur les PR (notamment celles de Dependabot), donc aucun
signal objectif de compilation/tests avant une décision de merge -- constaté concrètement sur les
PR #5 (MediaPipe tasks-vision) et #6 (Kotlin).

Ajout de `.github/workflows/ci.yml` : se déclenche sur chaque pull request et chaque push sur
`main`, installe le JDK 17, télécharge le modèle MediaPipe (comme `release.yml`), puis exécute
`./gradlew testDebugUnitTest assembleDebug`. Utilise `gradle/actions/setup-gradle@v6` avec
`cache-provider: basic` -- gratuit sur repo privé (l'option "enhanced" est encore en free preview
et son statut sur repo privé n'est pas garanti, `basic` évite toute ambiguïté). Aucun secret
utilisé (build debug non signé), donc sans risque même sur une PR externe/Dependabot.

Limite à connaître : sur le plan GitHub Free, les "required status checks" (bloquer un merge tant
que la CI n'est pas verte) ne sont disponibles que sur les dépôts publics, pas privés -- ce
workflow reste donc **informatif** (un ✅/❌ visible sur chaque PR) mais pas **contraignant** : rien
n'empêche techniquement un merge si le check échoue. Passer le dépôt en public lèverait cette
limite gratuitement, mais c'est un choix distinct de distribution/visibilité, pas juste de CI.

### 28. Fiabilisation du clignement des yeux avec lunettes -- idée ouverte, aucun code écrit

Retour de test device (ROG Phone II) : le clignement reste bien visible sur le mesh (mouvement
géométrique des paupières net) alors que l'utilisateur porte des lunettes -- un cas où les modèles
de tracking facial souffrent souvent (reflets sur les verres, monture qui occulte partiellement
l'œil, contour de l'œil moins net pour un modèle basé sur l'apparence). Question posée : peut-on
exploiter ce signal géométrique, visiblement plus robuste ici que ce que le blendshape `eyeBlinkLeft/
Right` de MediaPipe restitue peut-être en pratique avec lunettes, pour fiabiliser la détection ?

Piste de conception (non actée, à creuser) : dans le même esprit que les cascades géométriques déjà
prévues pour `tongueOut`/`cheekPuff` (points 15/16 de l'index ci-dessus), calculer un ratio
d'ouverture de l'œil directement à partir des landmarks du contour de la paupière (mesh 478 points,
déjà extrait quand l'overlay est actif -- sinon à extraire spécifiquement pour cet usage) et
l'utiliser soit en repli quand le blendshape `eyeBlinkLeft/Right` est jugé peu fiable, soit en
recalibrage/renforcement du score existant. Points d'attention avant de formaliser : (a) vérifier
d'abord, avec et sans lunettes, si le score `eyeBlinkLeft/Right` de MediaPipe est réellement dégradé
en pratique (l'observation actuelle porte sur le mesh, pas encore sur les valeurs de blendshape
elles-mêmes affichées à l'écran) -- possible que MediaPipe compense déjà correctement en interne ;
(b) un contour de paupière n'est pas trivial à isoler de façon fiable dans les 478 points sans
étude préalable des indices de landmarks concernés ; (c) coût de calcul supplémentaire par frame à
évaluer, dans le même esprit de sobriété batterie que le reste du pipeline (voir spec technique
§budget batterie). Pas de priorité assignée pour l'instant -- observation à garder en tête pour une
future session de conception détaillée, éventuellement avec les points 15/16.

## Rapatriement des points 15, 16, 19, 20 (6 août 2026)

Sections détaillées écrites à l'origine sur `feature/arcore-fusion` (conversation "réflexion", même
jour) et jusqu'ici jamais fusionnées dans `main` -- voir le point soulevé dans l'index en tête de
document. Contenu repris tel quel (c'est la trace du raisonnement d'origine, pas à réécrire) ; seules
des notes entre crochets `[main, 6 août]` ont été ajoutées où l'état de `main` a bougé depuis
l'écriture initiale sur la branche.

**Note sur la numérotation** : ces points reprennent la numérotation de la branche
`feature/arcore-fusion`, qui entre en collision avec des points sans rapport déjà utilisés sous les
mêmes numéros ailleurs dans ce document (voir l'avertissement dans l'index) -- se fier au sujet des
titres ci-dessous, pas au seul numéro. Les points 17, 18 et 21 de la branche ne sont **pas** repris
ici : ils correspondent respectivement aux points 24, 25 et 26 déjà rédigés plus haut dans ce
document, sous ces numéros-là, une fois implémentés sur `main`.

### 15. Détection de la langue tirée (`tongueOut`) -- piste retenue : cascade + calibration par embedding, en fonctionnalité expérimentale

Point de départ : ce qu'indique déjà le commentaire dans `BlendshapeCatalog` (« `tongueOut` excepté
en pratique -- limitation connue ») n'est pas un défaut d'implémentation côté app mais une limite
structurelle du modèle MediaPipe -- le mesh facial (478 points) ne modélise que la surface visible du
visage, jamais l'intérieur de la bouche, donc aucun mapping landmarks→blendshapes, aussi bien
entraîné soit-il, ne peut "voir" la langue à partir de ce seul signal. Confirmé en creusant un mapping
alternatif (`haibalabs/face-mesh-to-blendshapes`, pour mémoire) qui force lui aussi `tongueOut` à
zéro, pour la même raison. Un signal fiable doit donc venir d'ailleurs que des landmarks seuls --
typiquement de l'image caméra elle-même, déjà disponible via le bitmap RGBA construit par
`CameraController.processFrame()` pour nourrir MediaPipe (pas de capture supplémentaire à prévoir).
*[main, 6 août : confirmé par observation device le même jour -- `tongueOut` n'est pas du tout
restitué (pas juste peu fiable), voir index en tête de document.]*

Architecture retenue -- cascade à trois étages, du moins cher au plus cher, chaque étage ne se
déclenchant que si le précédent donne un indice positif :

1. **Porte géométrique** (quasi gratuite) : le score `jawOpen` déjà produit par MediaPipe à chaque
   frame (aucun coût supplémentaire) sert de pré-filtre -- en dessous d'un seuil d'ouverture de
   bouche, on ne va pas plus loin.
2. **Analyse couleur** (déclenchée seulement si l'étage 1 s'active) : recadrage de la région
   intérieure de la bouche dans le bitmap déjà produit par `CameraController`, à partir des landmarks
   de lèvres -- implique de forcer `FaceLandmarkerHelper.setLandmarksNeeded(true)` en continu tant
   que la fonctionnalité est activée (ce flag ne sert aujourd'hui qu'à l'overlay de debug, désactivé
   par défaut ; à faire dépendre d'un OU logique entre "overlay activé" et "détection langue
   activée"). Recherche d'une teinte rose/rouge saturée dépassant la ligne des lèvres inférieures --
   toujours aucun réseau de neurones à ce stade.
3. **Classification par comparaison à une calibration personnelle** (le seul étage réellement
   coûteux, et seulement sur les frames candidates) : plutôt qu'un dataset et un ré-entraînement par
   utilisateur (écarté pendant la discussion -- généraliserait mal d'une personne à l'autre et
   demande une collecte lourde), un extracteur de features générique pré-entraîné (backbone léger
   type MobileNet, TFLite, embarqué une fois pour toutes dans l'app) transforme le recadrage buccal
   en vecteur, comparé par plus proche voisin à deux références enregistrées lors d'une calibration
   dédiée ("langue dehors" / "langue rentrée", quelques secondes chacune) -- même logique que la
   calibration de pose neutre déjà présente dans l'app (`uiState.isCalibrated`), à reprendre comme
   modèle d'UX.

Rangement prévu : catégorie "Fonctionnalités expérimentales" dans `SettingsScreen` *[main, 6 août :
déjà créée, vide -- voir point 26]*, désactivée par défaut, avec un interrupteur qui révèle un accès
à un écran de calibration dédié (même principe de navigation que `BlendshapeSelectionScreen`) -- tant
que la calibration n'a pas été faite au moins une fois, le `tongueOut` calculé par cette cascade n'est
simplement pas injecté (comportement identique à aujourd'hui : il reste à 0). Stockage des deux
vecteurs de référence : pas dans `AppSettingsStore`/DataStore (pensé pour des préférences scalaires,
pas des tableaux de floats) -- un petit fichier dans le stockage interne de l'app (`filesDir`) suffit,
protégé par le bac à sable Android comme le reste des données de l'app.

Avertissement "téléphone qui souffre" : combiner `DeviceCapabilityDetector.isThermalThrottling()`
(existe, mais toujours pas appelé en continu pendant la capture -- point 3/13 ; cette fonctionnalité
en fait un prérequis direct plutôt qu'un simple levier non exploité) avec la latence d'inférence déjà
tracée dans `FaceTrackingResult.inferenceTimeMs` -- une moyenne glissante qui dépasse le budget frame
du palier courant est un signal de charge complémentaire au thermique (l'app peut saturer le CPU sans
déclencher de throttling thermique mesurable). Si l'un des deux signaux se déclenche pendant que la
détection de langue est active, afficher un avertissement non bloquant, sur le même principe visuel
que `LowBatteryAlert` (icône en contour, pulsante, jamais plein écran) -- pas de désactivation
automatique de la fonctionnalité (cohérent avec le reste de l'app : c'est toujours l'utilisateur qui
choisit), juste un accès direct au réglage pour la couper si besoin.

Coût attendu : les étages 1 et 2 sont négligeables (pas de réseau de neurones, quelques opérations sur
une petite zone de pixels). L'étage 3 est le seul qui ajoute une inférence, mais seulement sur les
frames où les deux premiers étages ont déjà donné un indice positif -- contribution marginale au
budget déjà mesuré par `inferenceTimeMs`, à vérifier concrètement une fois implémenté plutôt qu'estimé
ici.

Statut : idée de conception actée suite à discussion, aucun code écrit. Prochaine étape naturelle :
brancher réellement la surveillance thermique en continu (point 3/13) avant de commencer l'étage 3,
puisque l'avertissement en dépend directement.

### 16. Détection expérimentale de `cheekPuff` (joues gonflées) -- même famille que le point 15, cascade allégée

Même statut que `tongueOut` chez MediaPipe (signal peu fiable, cf. issue GitHub #4436 et le mapping
haibalabs qui le force aussi à zéro) mais une cause différente : contrairement à la langue, le
gonflement des joues déforme la silhouette du visage et *est* donc visible dans le mesh de landmarks
-- probablement un manque de données d'entraînement côté modèle officiel plutôt qu'un point aveugle
structurel. Conséquence sur la conception : un simple signal géométrique (écartement des landmarks du
contour des joues par rapport à un point stable, normalisé par la distance inter-oculaire) a de
bonnes chances de suffire seul, sans les étages couleur ni calibration par embedding visuel
nécessaires au point 15 -- cascade à 2 niveaux au lieu de 3, réutilisable en pratique :
`headRotationMatrix`/`headEulerDegrees` (déjà calculés à chaque frame dans `FaceTrackingResult`,
aucun coût ajouté) pour gater ou corriger la mesure. Rattaché à la même catégorie "Fonctionnalités
expérimentales" que le point 15 (même justification : fiabilité pas encore éprouvée, à ne pas imposer
par défaut). *[main, 6 août : confirmé par observation device le même jour -- le mesh bouge très peu
au gonflement des joues, le signal géométrique disponible risque d'être faible/bruité, point
d'attention à garder pour la conception détaillée -- voir index en tête de document.]*

Individuel (par joue) vs. en paire : mesurer chaque joue séparément ne pose pas de difficulté
technique particulière (clusters de landmarks déjà distincts par côté, même logique que
`computeEyeGazeDegrees` pour le regard), mais le blendshape `cheekPuff` du catalogue ARKit/VMC utilisé
ici est unique et partagé -- pas de `cheekPuffLeft`/`Right` dans `BlendshapeCatalog` (contrairement à
`cheekSquintLeft`/`Right`, séparés dès l'origine). Mesurer les deux côtés reste utile en interne (ex.
retenir le minimum des deux pour limiter un faux positif lié à la rotation de tête) mais la sortie
doit converger vers ce scalaire unique existant, sauf à envisager une extension non standard -- non
retenu pour l'instant.

**Point d'attention explicite : faux positifs.** C'est le risque principal de cette fonctionnalité, à
traiter comme un objectif de conception à part entière et pas comme un détail d'implémentation :
- Rotation de tête : un visage de 3/4 fait paraître une joue plus "gonflée" que l'autre par pur effet
  de perspective -- gater la mesure à une plage de rotation quasi frontale (yaw/pitch faibles) plutôt
  que tenter une correction complexe, cohérent avec la philosophie déjà retenue au point 15 (cascade
  qui s'abstient plutôt que de deviner dans le doute).
- Confusion avec d'autres expressions qui élargissent le visage (grand sourire, `mouthStretch`) -- à
  vérifier explicitement contre ces cas pendant la calibration/les tests, pas seulement contre le cas
  neutre.
- Morphologie individuelle (joues naturellement pleines) -- absorbée par la calibration par
  utilisateur (vecteur de mesures géométriques normalisées, pas besoin d'un extracteur d'image comme
  pour la langue), mais une calibration mal faite (mauvaise position de tête au moment de
  l'enregistrement) biaise durablement le résultat -- prévoir une recalibration facilement
  accessible, pas enfouie dans un sous-menu.

Statut : idée de conception actée suite à discussion, aucun code écrit.

### 19. Détection d'anomalie de calibrage -- alerte discrète, jamais d'action automatique

Suite à la discussion sur une éventuelle relance automatique de calibrage : écarté sous cette forme,
risque réel de se déclencher en pleine expression tenue volontairement (effet comique, surprise jouée
longtemps) et de confondre ça avec une dérive physique du téléphone. Retenu à la place : détection
seule, jamais d'action déclenchée toute seule -- cohérent avec le reste de l'app (l'alerte batterie
n'éteint rien, l'avertissement thermique du point 15 ne coupe pas la fonctionnalité, toujours
l'utilisateur qui agit).

Le critère de détection ne doit pas être "les valeurs sont extrêmes depuis longtemps" (indiscernable
d'une expression tenue volontairement) mais "quand le visage semble au repos (faible variation
frame-à-frame des blendshapes), les valeurs ne reviennent pas près de zéro comme elles le devraient
juste après un calibrage" -- signature bien plus spécifique d'une dérive réelle, qui ne se déclenche
jamais pendant une performance active puisque celle-ci n'est par définition pas un état de repos.
Signal complémentaire, plus simple et déjà disponible sans rien ajouter : une perte de détection du
visage (`faceDetected` qui repasse à faux puis revrai, déjà suivi à chaque frame dans
`FaceTrackingResult`) est un indice plus spécifique qu'un changement physique a eu lieu (téléphone
bougé/repositionné).

Restitution : purement visuelle, sur le bouton de calibrage existant du `MainHud`
(`Icons.Filled.CenterFocusStrong`, aujourd'hui toujours blanc) teinté en rouge tant que l'anomalie est
détectée -- réutilise la couleur "problème" déjà employée ailleurs (`LowBatteryAlert`, messages
d'erreur de `SettingsScreen`) plutôt que d'introduire un nouveau langage visuel. Pas de bandeau, pas
de popup ; se résout de lui-même dès que l'utilisateur appuie pour recalibrer. Point d'attention
explicite : le seuil/la durée d'observation doivent être réglés pour éviter un clignotement
rouge/blanc si la mesure oscille près de la limite -- sans quoi le signal cesse d'être discret.

Statut : idée de conception actée suite à discussion, aucun code écrit.

### 20. Écrans de réglages non adaptatifs -- et un verrouillage portrait déjà ignoré par le système sur grand écran

Point de départ d'une discussion sur le tri de l'écran de réglages *[main, 6 août : le tri lui-même
est traité, voir point 26]* : l'idée de faire tourner l'écran de réglages avec l'orientation du
téléphone/de la tablette, sur le même principe que la rotation individuelle des icônes du `MainHud`.
Écarté sous cette forme -- une rotation visuelle bricolée sur du contenu interactif (champs de texte,
sliders) est fragile (zones tactiles désalignées, comportement du clavier système imprévisible),
contrairement à de simples icônes décoratives. La bonne réponse est de laisser les écrans de réglages
suivre l'orientation système normalement (contrairement à l'écran de capture, qui a de bonnes raisons
de rester verrouillé portrait) plutôt que de la simuler visuellement.

Nuance apportée en discussion : sur téléphone, garder les réglages verrouillés en portrait reste
cohérent (le ratio d'écran rend un réglages en paysage peu pratique -- peu de hauteur, beaucoup de
largeur inutilisée pour une liste de sliders/switches). La pertinence d'un vrai support de
l'orientation système concerne donc surtout les grands écrans (tablettes), où le ratio se prête mieux
au paysage et où l'utilisateur choisit vraiment cette orientation plutôt que de la subir.

**Ce qui change la donne, découvert en creusant plutôt qu'anticipé** : ce n'est plus vraiment une
question de choix. Depuis Android 16 (API 36), l'adaptation complète à toutes les orientations/tailles
est devenue le comportement par défaut sur tout écran de largeur minimale ≥ 600dp (tablettes, grands
pliables) -- avec une désactivation manifeste encore possible côté appli. Android 17 (API 37) supprime
entièrement cette désactivation : sur un tel écran, les restrictions d'orientation/redimensionnement
déclarées par l'appli sont purement et simplement ignorées par le système, sans aucun levier pour les
réaffirmer. Or `app/build.gradle.kts` fixe déjà `targetSdk = 37` (poussé par les dépendances AndroidX
récentes qui exigent `compileSdk = 37`) -- ce n'est donc pas une perspective lointaine, c'est déjà la
configuration actuelle du projet : sur toute tablette tournant déjà Android 16 ou 17, le verrouillage
portrait de l'appli est d'ores et déjà ignoré par l'OS, aujourd'hui. Répartition du parc à titre de
repère (créée en cours de discussion, août 2026) : Android 16 est passé de 7,5 % (déc. 2025, chiffres
officiels Google) à environ 24 % (relevé communautaire AppBrain, août 2026) -- en progression rapide,
mais encore loin de couvrir tout le parc tablette, donc l'exposition réelle reste partielle pour
l'instant et continuera de croître.

Portée du problème : ça dépasse l'écran de réglages. La spec technique note explicitement que la
matrice de rotation caméra n'est recalculée que si l'angle change, en s'appuyant sur le fait que
"l'app est verrouillée portrait" pour toute la session -- une hypothèse qui ne tient plus sur une
tablette où le système ignore ce verrouillage. Un risque potentiellement plus grave que la mise en
page des réglages : aperçu mal orienté/mal mirroré, voire image transmise à MediaPipe dans le mauvais
sens. *[main, 6 août : ce risque et son lien avec le point 1/verrouillage portrait sont déjà reflétés
dans `AndroidMoCap_spec_technique.md` §7 -- seul le raisonnement complet et les chiffres d'adoption
manquaient jusqu'ici à ce journal, maintenant rapatriés ci-dessus.]*

Statut : constat factuel remonté en discussion, aucune décision prise sur la suite (adapter réellement
l'écran de réglages et revoir la logique de rotation caméra, vs. accepter que le comportement sur
tablette reste dégradé pour l'instant, l'usage principal visé restant le téléphone).

### 31. CI cassée depuis le premier run -- `gradlew` sans bit exécutable

Détecté par la nouvelle routine de vérification nocturne (Claude Code cloud, tous les jours ~21h,
lecture seule, voir §Automatisation ci-dessous) lors de son tout premier passage manuel le 6 août
2026 -- ce n'est pas quelque chose qu'une session de travail habituelle aurait remarqué, puisque le
build local fonctionne sans problème (Git for Windows/NTFS ne fait pas respecter le bit d'exécution
Unix comme le fait un checkout Linux).

Constat : `git ls-files -s gradlew` montrait le mode `100644` (non exécutable) depuis le tout premier
commit du dépôt (`7337c6c`) -- jamais corrigé depuis. Conséquence : sur un checkout Linux (donc tout
runner GitHub Actions), `./gradlew ...` échoue immédiatement en `Permission denied` (exit 126), avant
même d'atteindre Gradle. Le seul run CI ayant jamais eu lieu sur `main` (workflow `ci.yml`, déclenché
par le commit `a6e5327`) a échoué pour cette raison précise -- et ça n'avait été remarqué nulle part,
ni dans ce document ni ailleurs : la CI (point 17 de l'index, "traité") était en réalité rouge depuis
sa création, silencieusement.

Corrigé : `git update-index --chmod=+x gradlew` (mode `100755`), commit `df640a4`. `gradlew.bat`
laissé tel quel (`100644`), le bit d'exécution Unix n'a pas de sens pour un script Windows.

**Point non résolu, à vérifier au prochain accès `gh`/API GitHub authentifiée** : la même
vérification a également remonté qu'aucun run CI n'existe pour les 6 commits poussés après
`a6e5327` (jusqu'à `41d04dc` au moment du rapport), alors que `ci.yml` est censé se déclencher sur
chaque push vers `main`. Cause non déterminée depuis ce sandbox (pas d'accès `gh` local ni accès
réseau `api.github.com` authentifié pour lister les runs) -- hypothèses à vérifier : Actions
désactivées après l'échec initial, quota de minutes, ou tout simplement pas encore redéclenché. Le
commit `df640a4` (ce correctif) devrait le confirmer ou l'infirmer au prochain push : si la CI ne se
redéclenche toujours pas dessus, le problème est réel et mérite sa propre investigation.

## Automatisation

**Vérification nocturne GitHub (Claude Code cloud, routine planifiée)** -- créée le 6 août 2026,
tous les jours à 19:00 UTC (~21h Paris en été, ~20h en hiver -- le cron reste fixe en UTC). Lecture
seule : delta de commits `origin/main`, état CI, PR ouvertes (recoupées avec la convention "local
d'abord" de ce document), issues ouvertes, et un audit léger façon "regard extérieur/repo public"
(recherche grossière de secrets committés par erreur). Ne committe ni ne pousse jamais rien --
seulement un rapport. Le point 31 ci-dessus est sa première trouvaille concrète. Gérée depuis
https://claude.ai/code/routines (id `trig_01AnKSNF9qEMC3hom1eegjow`), pas depuis ce dépôt.
