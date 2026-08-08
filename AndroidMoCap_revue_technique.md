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
| 19 | Détection d'anomalie de calibrage (bouton rouge) | **✅ implémenté et vérifié sur device le 7 août 2026** (tous paliers testés), voir section dédiée -- seuils encore non calés sur un corpus d'usage réel, piste de retours utilisateurs notée pour plus tard |
| 20 | Orientation grand écran / tablette | Constat documenté, aucune décision de mise en œuvre |
| 21 | Tri en sous-écrans des réglages | **Implémenté sur `main`, voir point 26** (l'index le disait encore "aucun code écrit" par erreur) |
| 28 | Fiabilisation du clignement des yeux avec lunettes | Idée de conception ouverte le 6 août 2026, voir section dédiée plus bas -- aucun code écrit |
| 29 | Validation des traductions FR/EN par locuteurs natifs | Backlog, voir point 23 -- **deux passes de self-review faites** (6 août, commit `1b0030a`, 86 clés ; 7 août, clés des points 32-35, incohérence de style corrigée entre les puces ARCore/GPU et thermique), mais aucune relecture par un locuteur natif à ce jour, ni en français ni en anglais |
| 30 | Sélecteur de langue dans l'app pour Android 11/12 | Backlog, voir point 23 -- pas d'équivalent au sélecteur système Android 13+ sur ces versions, demanderait `androidx.appcompat` + `AppCompatDelegate` |
| 31 | CI cassée depuis le premier run (`gradlew` sans bit exécutable), puis silencieusement bloquée depuis | **✅ entièrement résolu le 7 août 2026** (commit `df640a4` pour `gradlew` ; cause du blocage silencieux trouvée le même jour -- budget Actions à 0 $, "Stop usage" actif -- corrigée et vérifiée par un run CI réussi), voir section dédiée plus bas |
| 32 | Panneau de blendshapes du HUD : tongueOut disparaissait, noms masqués par le bandeau système | **✅ corrigés et vérifiés sur device le 7 août 2026**, voir section dédiée plus bas |
| 33 | Proposer l'installation ARCore au lieu du repli silencieux | Backlog, priorité mineure, idée ouverte le 7 août 2026, aucun code écrit |
| 34 | Throttling thermique dynamique (débit réduit en cas de chauffe) | **✅ implémenté et vérifié sur device (via mock) le 7 août 2026**, voir section dédiée plus bas -- capteur thermique réel non exercé (appareil de test ne chauffe pas assez), câblage bout en bout confirmé |
| 35 | Panneau de mocks de debug caché (thermique, ARCore, délégué GPU) | **✅ implémenté et vérifié sur device le 7 août 2026**, voir section dédiée plus bas -- les trois mocks confirmés fonctionnels |
| 37 | Lag ARCore en session, disparu après redémarrage complet | Signalé le 7 août 2026, cause non identifiée -- throttling thermique et coût de la détection d'anomalie écartés par le raisonnement, prochaine étape : capture logcat si reproduit |
| 38 | VMC crashait systématiquement sur Android 11/API 30 (`NoSuchMethodError` javaosc-core) | **✅ corrigé et validé de bout en bout sur device le 8 août 2026** (plus de crash, connecteur VMC Blender reconnaît les paquets, contenu confirmé correct via Protokol -- noms et valeurs de blendshapes cohérents) -- voir section dédiée plus bas |
| 39 | VTube Studio ne reçoit probablement pas VMC/OSC -- intégration directe via son API Plugin | **✅ Validé de bout en bout sur device le 8 août 2026** (WebSocket/nv-websocket-client + kotlinx.serialization -- OkHttp abandonné, incompatible avec le serveur `websocket-sharp` de VTube Studio) -- connexion, popup d'autorisation, création des paramètres, réception confirmés fonctionnels, voir section dédiée plus bas |
| 40 | Indicateur visuel "connexion en cours" sur l'écran principal | Backlog, idée ouverte le 8 août 2026, aucun code écrit -- voir section dédiée plus bas |
| 41 | Traduction des blendshapes ARKit pour éviter le remapping manuel (VRM/Blender, VTube Studio) | Backlog, faisabilité étudiée le 8 août 2026 -- Blender/VRM déjà bon (convention "Perfect Sync"), VTube Studio jugé viable avec les formules VBridger (`AdvancedARKitSettings`) comme point de départ, OVR non exploré, voir section dédiée plus bas |

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
3. ~~Pas de bloc `<queries>` pour `com.google.ar.core` dans le manifeste~~ -- **✅ ajouté le 7 août
   2026** (`AndroidManifest.xml`), correctif défensif sans changement de comportement observable
   (le repli reste de toute façon silencieux).
4. ~~Coût thermique/batterie du duo GL (`RENDERMODE_CONTINUOUSLY`) + inférence MediaPipe
   simultanés, sur un palier qui ne bénéficie toujours pas du throttling thermique dynamique~~ --
   **✅ throttling thermique dynamique branché le 7 août 2026**, voir point 34 plus bas.

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
  latence perçue s'est nettement améliorée du même coup.
- **Warning `aimatter_landmarks_3Dmesh.cc: Not able to find preprocess rotation with expected
  timestamp`, hypothèse initiale infirmée puis confirmé spécifique à ARCore** (commits `ac0ed9f`,
  `d1428a2`) : contrairement à ce qui était supposé, persistait identique après le correctif de
  rotation -- pas causé par l'absence de rotation. **Tranché le même jour** grâce au sélecteur de
  palier (point ci-dessus) : capture logcat continue sur un aller-retour `STANDARD` → `OPTIMAL` sur
  le même appareil -- **0 occurrence en `STANDARD`** sur toute la session, **présent en continu dès
  la première frame en `OPTIMAL`**. Confirme que c'est spécifique à `ArCoreHeadPoseTracker`, pas un
  comportement préexistant de la lib. Nouvelle piste, plus précise mais toujours pas confirmée :
  `frameTimeMs` (`SystemClock.uptimeMillis()`) y est généré *après* la conversion asynchrone (voir
  déport sur thread dédié ci-dessus), donc décalé par rapport à l'instant réel de capture --
  contrairement à `CameraController`, synchrone sur son propre thread caméra dédié. Tracking reste
  fonctionnel malgré ce warning. Pas de troisième correctif tenté à l'aveugle -- la piste
  nécessiterait de logger/comparer le timestamp réel passé à `detectAsync` avant d'agir.
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
- Point 2 (vérification `ArCoreApk` synchrone) : inchangé, toujours ouvert. Point 3 (`<queries>`) :
  **✅ corrigé le 7 août 2026**.
- Point 4 (coût thermique du duo GL+MediaPipe) : partiellement atténué par le déport de thread
  ci-dessus (le thread GL est moins chargé) ; **throttling thermique dynamique branché le 7 août
  2026**, voir point 34 plus bas.
- Nouveau point mineur : `yuv420ToBitmap()` alloue toujours un `Bitmap` par frame (pas de pool de
  réutilisation) -- non prioritaire vu le gain déjà obtenu par le déport de thread, à reconsidérer
  seulement si un nouveau signal de coût apparaît. **Mise à jour (7 août 2026)** : la copie des
  plans YUV elle-même (pas l'allocation du `Bitmap`) a été optimisée -- voir plus bas, "deux
  optimisations mineures".

**Suivi device (6 août 2026, même jour, suite) : palier forçable manuellement, pour diagnostiquer
sans second appareil.** L'utilisateur n'a pas d'appareil qui qualifie naturellement pour `STANDARD`
(le sien qualifie pour `OPTIMAL`), donc pas de moyen direct de comparer le warning `aimatter`
(toujours ouvert, voir ci-dessus) entre les deux paliers. Ajouté (commit `e1c75df`) :
`TrackingTierSelector.select()` accepte un `override: TrackingTier? = null` qui court-circuite la
sélection automatique (fonction reste pure, testée), persisté via
`AppSettingsStore.tierOverride`, réglable depuis un nouveau sélecteur dans `DiagnosticsScreen`
(FilterChips Automatique/OPTIMAL/STANDARD/COMPATIBLE). S'applique seulement au prochain lancement
de l'app (`MainViewModel.initializeTracking()` lit l'override une seule fois via `first()`) --
pas de reconstruction à chaud du pipeline caméra/MediaPipe, choix assumé pour rester simple et sûr
plutôt que de gérer un teardown/rebuild en cours de session. Forcer `OPTIMAL` sur un appareil sans
ARCore réel ne pose pas de problème : le repli silencieux déjà en place
(`ArCoreHeadPoseTracker.onUnavailable`) s'applique identiquement, override ou sélection
automatique. Outil de diagnostic explicitement, pas une fonctionnalité utilisateur normale -- `DiagnosticsScreen`
n'est donc plus tout à fait "en lecture seule" (doc du composant mise à jour).

**Garde-fou ajouté (même jour, commit `f6ed0ea`)**, demande explicite de l'utilisateur après un
premier test réussi en palier forcé `COMPATIBLE` : `TrackingTierSelector.compatibility(tier,
capabilities)` (nouvelle fonction pure, testée) distingue une incompatibilité dure d'un simple
risque de performance. Seule incompatibilité dure identifiée : `OPTIMAL` sans support ARCore réel
(forcer ce palier ne ferait de toute façon que déclencher le repli silencieux déjà en place --
autant l'empêcher clairement à la sélection). Tout palier plus exigeant que ce que la sélection
automatique aurait choisi pour l'appareil est un simple risque de performance (fonctionne, peut
chauffer/ramer) -- jamais un blocage ; dégrader volontairement (palier moins exigeant que
l'automatique) n'est jamais un risque. Côté `DiagnosticsScreen` : puce désactivée si incompatible,
icône d'avertissement ambre à côté du nom si risque de performance seulement -- même code couleur
que l'avertissement des blendshapes peu fiables (`BlendshapeSelectionScreen`), cohérence visuelle
avec le reste de l'app plutôt qu'un nouveau langage visuel.

**Relecture globale post-intégration (7 août 2026), deux fuites de ressource corrigées** (commit
`c466d5b`), remontées par une relecture demandée explicitement, sans device (identification
d'abord, correctifs appliqués sur demande séparée le lendemain) :
1. `MainViewModel` : le repli `onUnavailable` (ARCore indisponible) remplaçait
   `arCoreHeadPoseTracker` par `null` sans jamais appeler `.close()` sur l'instance abandonnée --
   son `imageProcessingExecutor` (thread dédié créé dès la construction, avant même `start()`)
   tournait à vide pour le reste de la session. Corrigé : `.close()` avant de nuller.
2. `ArCoreHeadPoseTracker.tryCreateSession()` : seul le chemin "pas de config caméra frontale"
   fermait explicitement la `Session` en cas d'échec -- si `getSupportedCameraConfigs`/
   `setCameraConfig`/`configure()` levait une exception après `Session(context)`, le `catch`
   générique retournait `null` sans jamais fermer la session, fuite de ressource native ARCore.
   Corrigé : session hissée hors du `try` pour être fermée dans ce cas.

Aucun des deux n'a de conséquence visible en usage normal (fuites mineures, pas de crash) --
trouvés par relecture de code, pas par un symptôme observé sur device.

**Deux optimisations mineures de la même relecture, appliquées le même jour** (commit `de01fba`,
sur confirmation explicite de l'utilisateur) :
3. `yuv420ToBitmap()` copiait chaque plan YUV octet par octet via `ByteBuffer.get(index)` (accès
   virtuel par appel). Remplacé par une copie en bloc de chaque plan dans un `ByteArray` (nouvelle
   extension `ByteBuffer.toByteArray()`) suivie d'un accès indexé direct -- même formule de
   conversion BT.601, juste moins d'overhead par pixel. Le pool de `Bitmap` (déjà noté comme piste
   possible ci-dessus) reste volontairement laissé de côté : refonte plus large (réintroduirait un
   suivi de libération façon `inFlightImages`, supprimé plus haut) avec un risque de correction plus
   élevé, sans gain mesuré sur device pour le justifier.
4. `rotateBitmap()` passait `null` comme `Paint` à `Canvas.drawBitmap` -- incohérent avec
   `CameraController.rotationPaint`, qui fixe explicitement `isFilterBitmap = false` (aucun intérêt
   à lisser une rotation par multiples de 90°). Ajout du même champ ici par cohérence ; comportement
   fonctionnel inchangé (`null` équivaut déjà à un filtrage désactivé côté `Canvas`), correctif
   cosmétique.

`./gradlew testDebugUnitTest assembleDebug` : `BUILD SUCCESSFUL`. Non vérifié sur device (aucun
device dans ce sandbox) -- gain de perf non mesuré, cohérent avec le reste des optimisations de
cette session qui restent "prêtes pour test device" plutôt que "validées".

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

### 19. Détection d'anomalie de calibrage -- ✅ implémentée le 7 août 2026, vérification device en attente

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

**Implémentation (7 août 2026), après une passe de réduction de backlog** (points 13/29) où le
gisement de vrais quick-wins s'est tari -- basculement sur cette feature, la seule des deux
candidates restantes (avec le point 28) dont la conception était réellement actée.

`tracking/CalibrationAnomaly.kt` (nouveau) : `CalibrationAnomalyState` (`flagged`,
`consecutiveRestDriftFrames`, `consecutiveMissingFaceFrames`) + `next(isAtRest, poseMagnitudeDegrees,
faceDetected)`, pure et testée en JVM (10 tests, `CalibrationAnomalyTest.kt`) -- même famille que
`ThermalThrottleState`. `flagged` est **sticky** : ne redevient jamais faux tout seul, remis à zéro
uniquement par `MainViewModel.performCalibration()`. La redétection de visage (signal complémentaire)
court-circuite le critère principal (n'a pas besoin d'être "au repos") mais exige une perte d'au
moins `MIN_FACE_LOSS_FRAMES_FOR_REACQUISITION = 3` frames consécutives avant de compter, pour ignorer
une frame MediaPipe isolée ratée. `tracking/BlendshapeStability.kt` (nouveau) :
`meanAbsoluteBlendshapeDelta()` (delta absolu moyen entre deux jeux de blendshapes appariés par nom,
7 tests, `BlendshapeStabilityTest.kt`) fournit le signal "au repos".

Câblage dans `MainViewModel.handleTrackingResult()` : les 3 signaux (variance blendshapes, magnitude
de la pose calibrée déjà calculée, `faceDetected`) sont calculés à chaque frame, mais `_uiState`
n'est mis à jour que si `flagged` change réellement (même discipline hot/cold que le reste du
fichier, pas de recomposition à 20-60 Hz). `MainHud.kt` : le bouton de calibrage
(`Icons.Filled.CenterFocusStrong`, teinte codée en dur en blanc jusqu'ici) devient rouge
(`0xFFFF8080`, même couleur que les messages d'erreur de `SettingsScreen.kt:114`, précédent déjà cité
dans la conception d'origine) tant que l'anomalie est signalée -- suspendu pendant le compte à
rebours de calibrage (rouge + anneau simultanés serait confus).

Constantes -- toutes des estimations raisonnées, non calées sur device (aucune télémétrie
disponible), même traitement que `SUSTAINED_THROTTLE_TICKS` au point 34 :
- `REST_VARIANCE_THRESHOLD = 0.01f` -- delta moyen sur ~50 blendshapes, lisse largement le bruit
  MediaPipe par blendshape isolée.
- `DRIFT_POSE_THRESHOLD_DEGREES = 8f` -- au-dessus du bruit résiduel habituel d'une calibration.
- `SUSTAINED_DRIFT_FRAMES = 90` -- calé sur ~30 fps (palier STANDARD), soit ~3s d'immobilité
  persistante ; se traduit en ~1.5s sur OPTIMAL (60 fps) et ~4.5s sur COMPATIBLE (20 fps), écart
  assumé, jamais mesuré.
- `MIN_FACE_LOSS_FRAMES_FOR_REACQUISITION = 3`.

`./gradlew testDebugUnitTest assembleDebug` : `BUILD SUCCESSFUL`, 17 nouveaux tests verts.

**✅ Confirmé sur device (7 août 2026, même jour)** : fonctionnel sur les différents paliers de
performance testés par l'utilisateur (COMPATIBLE/STANDARD/OPTIMAL) -- le bouton réagit bien à une
dérive physique et redevient blanc après recalibrage, pas de clignotement rouge/blanc observé
(cohérent avec l'exclusion attendue du design sticky).

**Piste ouverte, notée pour plus tard** : les seuils (`REST_VARIANCE_THRESHOLD`,
`DRIFT_POSE_THRESHOLD_DEGREES`, `SUSTAINED_DRIFT_FRAMES`, `MIN_FACE_LOSS_FRAMES_FOR_REACQUISITION`)
restent des estimations raisonnées, jamais calées sur un vrai corpus d'usage -- l'utilisateur
suggère qu'il sera probablement pertinent de collecter des retours utilisateurs réels (faux
positifs/négatifs perçus en usage prolongé, sur différents appareils/paliers) pour affiner ces
seuils plutôt que de deviner davantage depuis un seul appareil de test. Aucune décision de mise en
œuvre (pas de mécanisme de collecte envisagé pour l'instant, juste l'idée à garder en tête).

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

*[7 août 2026 : une première lecture d'une capture utilisateur avait été rattachée ici par erreur --
la capture montrait en fait `BlendshapeSelectionScreen` juste pour prouver que `tongueOut` était bien
coché, pas un souci de rotation système. Le vrai souci remonté ce jour-là (panneau de blendshapes du
HUD principal masqué par le bandeau système en tenant le téléphone à l'horizontale) est un bug
autonome de `BlendshapePanel`, sans rapport avec le verrouillage portrait ignoré par le système décrit
ici -- voir la section "Panneau de blendshapes masqué par le bandeau système" plus bas.]*

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

**✅ Cause déterminée (7 août 2026) : budget "Actions" à 0 $ avec "Stop usage" actif.**
`gh` installé et authentifié dans ce sandbox (`winget install GitHub.cli`, `gh auth login --web` --
flux navigateur, aucun jeton n'a transité par la conversation) pour investiguer : confirmé qu'aucun
run CI n'existe pour les 27 commits poussés sur `main` après `a6e5327` (jusqu'à `1b0030a`), et pas
seulement absent de la liste -- `gh api .../actions/runs?head_sha=...` renvoie `total_count: 0` pour
chacun, alors que l'API `events` du dépôt confirme que ces push sont bien arrivés normalement
(`PushEvent` authentiques, acteur humain `guyiome`, sur `refs/heads/main`). Dépôt actif, non archivé,
workflow `CI` à l'état `active`, Actions activées (`allowed_actions: all`) -- rien de tout ça
n'expliquait le blocage.

Cause réelle trouvée en inspectant `github.com/settings/billing/budgets` (accès direct au compte,
via l'extension navigateur) : un budget de compte sur le produit **Actions**, plafonné à **0 $**,
avec **"Stop usage: Yes"**. Le tout premier run (`a6e5327`, 2 minutes consommées) a fait passer ce
budget à 100 % de son plafond -- un budget à 0 $ se déclenche dès le moindre usage brut, même
intégralement couvert par les 2 000 minutes gratuites incluses (`/settings/billing` confirme 2/2000
minutes utilisées ce mois-ci, 0 $ facturable) -- et "Stop usage" bloque alors silencieusement tout
nouveau run *avant même sa création*, ce qui explique exactement le symptôme observé. Pas un bug
GitHub ni un problème côté dépôt/workflow : un réglage de compte préexistant, pas mis en cause
jusqu'ici faute d'accès `gh` authentifié.

Correctif : relever ce budget au-dessus de 0 $ (`Stop usage` conservé -- garde la garantie "jamais
facturé", le seuil ne se déclenchera qu'en cas de dépassement réel des minutes gratuites) plutôt que
le supprimer (ce qui rendrait l'usage réellement illimité, sans garde-fou).

**✅ Résolu (7 août 2026, même jour).** Moyen de paiement ajouté côté utilisateur (exigé par GitHub
avant toute modification de budget), budget "Actions" relevé à 1,00 $ (`Stop usage` conservé).
Vérifié en poussant les 9 commits locaux en attente (`1b0030a..8db6a9a`, sur demande explicite de
l'utilisateur) : run `31169132739` déclenché normalement dans la minute, `build-and-test` ✅ réussi
en 5m55s (`testDebugUnitTest assembleDebug` verts). La CI est de nouveau pleinement fonctionnelle.

Deux annotations mineures relevées sur ce run, sans rapport avec ce point, notées pour plus tard :
`actions/checkout@v4`/`actions/setup-java@v4` tournent sur un Node.js 20 déprécié (forcé en Node 24
par GitHub), et `setup-java@v4` est lui-même déprécié au profit de `v5` -- simples mises à jour de
versions d'actions dans `ci.yml`, aucune urgence.

### 32. Panneau de blendshapes du HUD : deux bugs remontés par test device -- ✅ corrigés et vérifiés sur device

Test en temps réel de l'utilisateur (7 août 2026), deux captures d'écran à l'appui. Première lecture
erronée de ma part (une des captures avait été prise pour un souci de rotation système, à tort -- voir
la note de correction au point 20 ci-dessus) ; les deux vrais soucis, une fois clarifiés :

**a) `tongueOut` coché mais absent du panneau, alors qu'il devrait y rester affiché figé à 0.**
`MainScreen.kt` construisait la liste affichée en filtrant `trackingFrame.allBlendshapes` (ce que
MediaPipe produit réellement à cette frame) par `selectedBlendshapeNames` (ce que l'utilisateur a
coché) -- un blendshape coché mais jamais produit par MediaPipe (`tongueOut`, voir
`BlendshapeCatalog.unreliable` et point 15) n'apparaissait donc jamais du tout dans le panneau, pas
même à 0. Corrigé (commit local, voir ci-dessous) : la liste part maintenant de
`selectedBlendshapeNames` (source de vérité de ce qui doit s'afficher) et cherche la valeur
correspondante dans `allBlendshapes`, avec un repli à `0f` si absente -- un blendshape jamais restitué
par le modèle reste donc visible, simplement statique à 0,00, ce qui est le comportement voulu par
l'utilisateur.

**b) Noms de blendshapes masqués par le bandeau système en tenant le téléphone à l'horizontale.**
`BlendshapePanel` (ancré en haut de l'écran, `Alignment.TopStart`) applique une rotation cosmétique de
±90° selon l'orientation physique du téléphone ([panelRotationDegrees], même principe que la rotation
individuelle des icônes du `MainHud`) -- l'app reste verrouillée portrait (point 1), seul l'affichage
du texte pivote pour rester lisible. Le bloc de texte étant nettement plus large que haut (peu de
lignes, mais des noms de blendshapes longs), une rotation à ±90° autour du centre par défaut de
`Modifier.rotate()` fait déborder le rectangle tourné *au-dessus* du bord physique de l'écran (le
rectangle, deux fois plus "haut" une fois sur le côté, reste centré sur le même point) -- il finit
sous le bandeau système, qui masque le début de chaque nom (`cheekPuff` affiché `eekPuff`, etc.).
Confirmé indépendant du point 20 (verrouillage portrait ignoré par le système) : ici l'app reste
authentiquement en portrait tout du long, c'est un pur défaut de géométrie de la rotation cosmétique
elle-même, combiné au fait que l'app ne gère de nulle part les insets système (recherché,
`WindowInsets`/`systemBars`/`safeDrawing` : aucune occurrence dans tout le projet avant ce correctif --
`compileSdk`/`targetSdk = 37` rend l'app edge-to-edge de fait). Corrigé : le pivot de rotation de
`BlendshapePanel` passe du centre (`TransformOrigin.Center`, implicite avec `Modifier.rotate()`) au
bas du bloc (`Modifier.graphicsLayer(rotationZ = ..., transformOrigin = TransformOrigin(0.5f, 1f))`),
ce qui fait déborder la rotation vers l'intérieur de l'écran plutôt que vers son bord ; en complément,
`MainScreen` ajoute `windowInsetsPadding(WindowInsets.safeDrawing)` autour du panneau, en marge de
sécurité pour le résidu de débordement (calcul géométrique, pas mesuré sur device).

`./gradlew testDebugUnitTest assembleDebug` : `BUILD SUCCESSFUL`.

**✅ Correctif (b) confirmé fonctionnel sur device (7 août 2026)** : le raisonnement géométrique
(pivot de rotation déplacé vers le bas du bloc + marge `safeDrawing`) tient en usage réel -- les noms
de blendshapes restent entièrement lisibles en tenant le téléphone à l'horizontale, plus de
chevauchement avec le bandeau système. Les deux correctifs de ce point sont désormais vérifiés.

### 33. Étudier une installation ARCore proposée à l'utilisateur au lieu du repli silencieux -- backlog, priorité mineure

Idée ouverte en discussion (7 août 2026), à partir du point "pas de bloc `<queries>`" (point 13,
risque 3). `ArCoreApk.Availability` a un état dédié `SUPPORTED_NOT_INSTALLED` -- "appareil
compatible, mais l'APK ARCore (Google Play Services for AR) absente" -- et `isSupported` (lu par
`DeviceCapabilityDetector.detect()`) renvoie `true` pour cet état, au même titre qu'installé. Vérifié
dans le code : `ArCoreHeadPoseTracker.tryCreateSession()` catch déjà `UnavailableArcoreNotInstalledException`
séparément de `UnavailableDeviceNotCompatibleException` (message de log distinct), mais les deux
mènent au même repli silencieux vers `CameraX` via `onUnavailable` -- aucune différence de
comportement visible pour l'utilisateur aujourd'hui entre "appareil incompatible" et "appareil
compatible mais composant pas encore installé".

Cas où ce dernier scénario est plausible : appareil sans Google Play Services (marché hors GMS, ROM
custom), premier lancement hors ligne / appareil fraîchement réinitialisé, appareil géré en
entreprise (MDM) restreignant les installations auto, composant désactivé/vidé manuellement par
l'utilisateur. Sur un smartphone grand public avec GMS et Play Store à jour, Google provisionne
plutôt fiablement ce composant en arrière-plan pour les appareils qu'il a certifiés ARCore -- donc le
cas "durablement bloqué" est probablement minoritaire, mais aucune télémétrie n'existe côté app pour
le confirmer ou le quantifier sur la base d'utilisateurs réelle.

Piste à étudier : proposer explicitement l'installation (`ArCoreApk.requestInstall()`, flux canonique
Google -- boucle avant la création de `Session`, gestion du retour `INSTALL_REQUESTED` en réessayant
à `onResume()`) plutôt que de rétrograder silencieusement un utilisateur qui aurait pu accéder au
palier `OPTIMAL` moyennant un petit téléchargement. Implique une vraie décision UX (écran de
consentement, redirection Play Store, gestion du refus -- `UnavailableUserDeclinedInstallationException`
déjà catché mais jamais déclenché puisque `requestInstall()` n'est jamais appelé) -- pas un simple
correctif technique comme le bloc `<queries>` lui-même.

Statut : idée notée en backlog, priorité mineure, aucune décision de mise en œuvre. *[Mise à jour du
même jour : le bloc `<queries>` évoqué ci-dessus comme point de départ de la discussion a lui-même
été ajouté séparément, voir point 13 -- reste sans lien avec la décision produit ci-dessus (proposer
ou non un flux d'installation), qui reste entière.]*

### 34. Throttling thermique dynamique branché (point 1 du top 3 priorisé le 7 août 2026)

`DeviceCapabilityDetector.isThermalThrottling()` existait depuis le début de l'intégration ARCore
(point 3/13) sans jamais être appelée -- premier des trois points priorisés en fin de session (avec
le budget Actions à 0 $, point 31, et l'installation ARCore proposée, point 33).

Décisions de conception actées avec l'utilisateur avant l'implémentation :
- **Rétrogradation dynamique = débit cible réduit, pas un changement de palier complet.** Une vraie
  rétrogradation de palier (`OPTIMAL` -> `STANDARD`) changerait aussi le délégué GPU/CPU et la
  source caméra (ARCore -> CameraX) *à chaud* -- irait à l'encontre d'une décision déjà prise
  explicitement de ne jamais reconstruire le pipeline caméra/MediaPipe en cours de session (même
  principe que le sélecteur de palier manuel, point 13). Le débit cible, lui, était déjà prévu pour
  un ajustement à chaud (`CameraController`/`ArCoreHeadPoseTracker.setTargetFps`, jamais utilisé
  dynamiquement jusqu'ici) -- c'est le seul levier actionné.
- **Retour utilisateur explicitement demandé** : une icône dans le bandeau principal (`MainHud`,
  même famille visuelle que l'icône "visage détecté") apparaît tant que le débit est réduit, plus
  une ligne équivalente dans l'écran Diagnostics -- même code couleur ambre que l'avertissement
  "risque de performance" du sélecteur de palier manuel (cohérence visuelle, même famille de signal).
- **Rétrogradation de palier suggérée, jamais appliquée automatiquement**, en cas de chauffe
  *persistante* : un indicateur sticky (`downgradeSuggested`, ne redevient jamais faux une fois
  déclenché) apparaît en diagnostic après une chauffe continue prolongée, renvoyant vers le
  sélecteur de palier manuel déjà existant -- la décision de rétrograder pour de bon (au prochain
  lancement) reste entièrement celle de l'utilisateur, jamais écrite automatiquement dans
  `AppSettingsStore.tierOverride` (qui reste un choix explicite, pas une suggestion système).
- **Remontée automatique** au débit nominal dès que la chauffe retombe (pas de dégradation
  permanente pour le reste de la session) -- sondage périodique (5 secondes), pas d'hystérésis :
  l'espacement du sondage suffit à éviter une oscillation visible.

Implémentation : `tracking/ThermalThrottle.kt` (nouveau, `ThermalThrottleState` + `next()`, pure et
testée en JVM sans dépendance de timing réel -- 9 tests, `ThermalThrottleTest.kt`) pilotée par
`MainViewModel.startThermalPolling()` (sondage périodique démarré/arrêté avec le cycle de vie,
`ON_START`/`ON_STOP`, même patron que `DeviceOrientationTracker`/`ArCoreHeadPoseTracker`). Débit
réduit de moitié en throttling (plancher `MIN_THROTTLED_FPS = 10`), suggestion de rétrogradation
après `SUSTAINED_THROTTLE_TICKS = 12` sondages consécutifs (~1 minute de chauffe continue).

`./gradlew testDebugUnitTest assembleDebug` : `BUILD SUCCESSFUL`, 9 nouveaux tests verts.

**✅ Confirmé sur device (7 août 2026, même jour)**, via le mock thermique du point 35 (l'appareil de
test, optimisé gaming, ne chauffe pas assez pour déclencher le vrai capteur) : icône ambre du HUD
visible en forçant le throttling actif, débit effectivement réduit, remontée automatique confirmée
en repassant sur Auto/Forcer inactif, et suggestion de rétrogradation (`downgradeSuggested`) bien
apparue après ~1 minute de chauffe forcée continue. Le throttling thermique *réel* (capteur
`PowerManager`, pas le mock) reste non exercé -- l'appareil de test ne chauffe pas suffisamment en
usage normal, mais le câblage bout en bout est désormais vérifié indépendamment de la source du signal.

### 35. Panneau de mocks de debug caché (DiagnosticsScreen)

En préparant le test device du point 34 (throttling thermique), l'utilisateur a signalé que son
appareil de test est optimisé gaming et pourrait ne jamais chauffer suffisamment pour déclencher
naturellement `PowerManager.currentThermalStatus >= THERMAL_STATUS_MODERATE`. Proposition : un mode
debug "secret" (tap multiple sur une ligne d'affichage, même principe que le menu développeur
Android) pour mocker cette détection -- étendu, après discussion, à deux autres comportements dans
la même situation ("ne peut pas se déclencher naturellement sur cet appareil précis").

Décisions actées :
- Mock thermique : bascule **Auto / Forcer actif / Forcer inactif** (pas de courbe simulée -- la
  logique de transition est déjà testée en JVM au point 34, une bascule suffit à vérifier le câblage
  réel).
- Deux mocks supplémentaires retenus : **ARCore indisponible** (vérifie le repli CameraX,
  `ArCoreHeadPoseTracker.onUnavailable`, jamais déclenché sur un appareil où ARCore fonctionne
  réellement) et **délégué GPU indisponible** (vérifie le repli CPU de `FaceLandmarkerHelper`,
  jamais exercé sur un GPU aussi capable). Batterie faible explicitement écartée (déjà validée par
  l'utilisateur en conditions réelles).
- Déverrouillage : 7 taps sur une nouvelle ligne "Version de l'app" (`BuildConfig.VERSION_NAME`,
  jamais affichée nulle part avant ce point -- `buildFeatures.buildConfig` activé pour l'occasion).
- **Distinction en direct vs au lancement**, soulevée par l'utilisateur et confirmée en analysant le
  code : le mock thermique est lu à chaque sondage (`startThermalPolling`, effet en quelques
  secondes, aucun redémarrage), donc volontairement **non persisté** (`MainUiState.debugThermalOverride`,
  absent d'`AppSettingsStore`). Les mocks ARCore/GPU, eux, ne peuvent agir qu'au moment de
  `initializeTracking()` (jamais rejoué en cours de session, même contrainte que le sélecteur de
  palier manuel) -- ils doivent donc être **persistés** (`AppSettingsStore.debugForceArCoreUnavailable`/
  `debugForceGpuUnavailable`, même patron que `tierOverride`) pour survivre au redémarrage
  nécessaire à leur propre test.
- Le déverrouillage lui-même (avoir tapé 7 fois) n'est **pas** persisté -- état Compose local
  (`remember`), remis à zéro à chaque fermeture de l'écran. Le geste ne coûte que quelques secondes ;
  le persister casserait la propriété "vraiment caché" sans réel gain, alors que la vraie friction
  (redémarrer l'app pour les mocks ARCore/GPU) reste incompressible de toute façon.
- **Garde-fou ajouté à l'implémentation**, en réponse directe à la question de l'utilisateur sur la
  persistance : un mock persistant resté actif par erreur dégraderait silencieusement l'app pour de
  bon, sans moyen évident d'y remédier si le geste de déverrouillage est oublié. Un bandeau d'alerte
  **toujours visible** (rouge, pas caché derrière le déverrouillage, pas la même couleur ambre que
  les avertissements de performance -- catégorie différente, plus sévère) apparaît en haut de
  `DiagnosticsScreen` dès qu'un mock est actif, avec un bouton de réinitialisation immédiat.

Implémentation : `ui/DebugPanelUnlock.kt` (nouveau, `DebugPanelUnlockState` + `registerTap()`, pure
et testée en JVM -- 6 tests, `DebugPanelUnlockTest.kt`, même famille que `MeshOverlayVisibility.kt`/
`ThermalThrottleState`). Câblage dans `MainViewModel.initializeTracking()` : les deux mocks persistés
sont lus une seule fois (`.first()`, comme `tierOverride`) et court-circuitent respectivement la
construction d'`ArCoreHeadPoseTracker` (repli direct vers `createCameraController()` déjà existant,
sans toucher `ArCoreHeadPoseTracker`) et le paramètre `forceGpuUnavailable` de `FaceLandmarkerHelper`
(nouveau paramètre constructeur, une seule condition modifiée dans `setup()`). Le mock thermique est
lu en direct dans `startThermalPolling()` (`_uiState.value.debugThermalOverride ?:
DeviceCapabilityDetector.isThermalThrottling(context)`). `DiagnosticsScreen` enveloppée dans un
`verticalScroll` (absente jusqu'ici, latent -- le nouveau contenu risquait de déborder).

`./gradlew testDebugUnitTest assembleDebug` : `BUILD SUCCESSFUL`, 6 nouveaux tests verts.

**✅ Tous confirmés sur device (7 août 2026, même jour)** : le geste des 7 taps fonctionne, le
panneau s'affiche sans débordement visuel, et les trois mocks produisent bien l'effet attendu --
mock thermique vérifié en direct (voir point 34, y compris la suggestion de rétrogradation après
chauffe forcée soutenue), mock ARCore indisponible confirmé après redémarrage (repli CameraX visible
en diagnostic, "Source caméra : CameraX"), mock délégué GPU indisponible confirmé après redémarrage
(diagnostic "Délégué : CPU", tracking fonctionnel sans régression). Les points 34 et 35 passent tous
deux de "prêt pour test device" à "vérifié sur device".

### 36. Relecture globale (code mort, optimisations, incohérences) -- 7 août 2026

Passe demandée explicitement, identification d'abord (trois explorations en parallèle sur les zones
non touchées récemment + relecture personnelle des fichiers les plus modifiés cette session),
correctifs ensuite sur demande séparée. Deux volets traités :

**Documentation désynchronisée** (le vrai gros du constat) -- `AndroidMoCap_spec_fonctionnelle.md`
et `CLAUDE.md` étaient restés en retard de plusieurs jours sur `AndroidMoCap_revue_technique.md` :
la fusion ARCore (point 13) encore décrite "pas testée sur device" (faux depuis le 6 août),
l'anomalie de calibrage (point 19) encore listée "non implémentée" (faux depuis le 7 août), le
throttling thermique (point 34) absent de `CLAUDE.md`. Corrigé (commit `7428afa`), plus deux kdoc/
commentaires ponctuellement faux (`BlendshapeSelectionScreen.kt` sur la persistance, `build.gradle.kts`
sur ce que `AppSettingsStore` persiste réellement). **Note ajoutée en mémoire persistante** : mettre à
jour tous les documents (pas seulement la revue technique) au moment où une feature est *confirmée
sur device*, pas seulement au moment où elle est implémentée -- ce sont deux moments distincts.

**`VmcOscSender` : échec de connexion totalement silencieux** -- `connect()` avalait toute exception
sans log ni callback, et `MainViewModel.connectVmcTarget()` affichait "connecté" inconditionnellement
juste après construction. Corrigé (commit `42a82f6`) : nouvelle propriété `isConnected`, vérifiée par
l'appelant avant de mettre à jour l'UI ; log ajouté côté `VmcOscSender`.

**Quatre correctifs triviaux traités séparément** (commit `7dfaf46`, sur demande explicite après
avoir distingué les correctifs vraiment triviaux -- 1 fichier, quelques lignes, zéro risque -- de
ceux qui méritent plus de soin) :
- ✅ `VmcOscSender.updateTarget()` : code mort supprimé, `host`/`port` redevenus `val`.
- ✅ `CameraController.stop()` : remet `imageAnalysis`/`previewUseCase` à `null`.
- ✅ `FaceLandmarkerHelper.computeEyeGazeDegrees()` : `Map` construite une fois plutôt que 8 scans
  linéaires par frame.
- ✅ `MainScreen.kt` : tri de `selectedBlendshapeNames` mémorisé (`remember`), plus refait à chaque
  frame.

**Reste en backlog, non traité** (priorité mineure à moyenne, jugés soit dispersés sur plusieurs
fichiers pour un gain cosmétique, soit pas réellement "légers" à corriger proprement -- voir le
raisonnement détaillé donné à l'utilisateur avant ce commit) :
- `ConnectionSettingsScreen.kt` : le champ IP peut rester figé sur la valeur par défaut si l'écran
  se compose avant la première émission DataStore (fenêtre de course étroite).
- `MainHud.kt`/`MainViewModel.kt` : la durée du compte à rebours de calibrage (`5`) est dupliquée
  sans source commune entre les deux fichiers.
- Couleurs `Color(0xFF...)` dupliquées (5-6 fois chacune) au lieu de réutiliser `MaterialTheme.colorScheme`
  -- touche 5-6 fichiers pour un gain purement cosmétique/maintenance.
- `meanAbsoluteBlendshapeDelta` (une `HashMap` par frame) : un vrai correctif casserait le caractère
  "fonction pure sans état" choisi délibérément cette session pour rester testable -- laissé tel quel.
- `LandmarkProjection.toScreenPoint` (`Pair` boxé × 478 points/frame) : changerait la signature d'une
  fonction publique déjà testée (`LandmarkProjectionTest.kt`) et son unique appelant.
- `RotationMath.multiply`/`transpose` (allocation par appel sur le chemin de calibration) :
  toucherait le fichier de maths le plus testé du projet, demanderait de nouvelles variantes
  "in-place" sans casser les tests existants.

`./gradlew testDebugUnitTest assembleDebug` : `BUILD SUCCESSFUL` pour les trois commits.

### 37. Lag ARCore observé en session, disparu après redémarrage complet -- cause non identifiée

Retour utilisateur (7 août 2026), pendant une session de test couvrant plusieurs paliers via le
panneau de diagnostic/debug (chaque changement de palier demandant un redémarrage pour s'appliquer,
voir points 13/35) : ralentissement constaté à la fois sur l'aperçu du téléphone et côté réception
VBridger, disparu après un redémarrage complet de l'app.

Deux hypothèses écartées par les retours de l'utilisateur :
- **Throttling thermique réel (point 34)** : écarté -- aucune icône de chauffe visible, téléphone
  froid au toucher.
- **Coût par frame de la détection d'anomalie de calibrage (point 19)** : écarté par le raisonnement
  -- un surcoût fixe par frame resterait identique après redémarrage, il ne disparaîtrait pas.

Hypothèse restante, non confirmée : accumulation d'état côté session ARCore/thread GL
(`ArCoreHeadPoseTracker`) sur plusieurs cycles pause/reprise (`ON_START`/`ON_STOP`) dans le même
process, si certains des "redémarrages" effectués pendant la session de test n'étaient pas de vrais
kills du process (retour depuis les tâches récentes sans balayage, relances via Android Studio en
mode debug) -- disparaît alors avec un vrai kill complet du process. Relecture du code de cycle de
vie (`ArCoreHeadPoseTracker.start()/stop()`, l'observer `ON_START`/`ON_STOP` de
`MainViewModel.initializeTracking()`) : rien d'anormal trouvé à la lecture statique (pas de
double-enregistrement d'observer, `resume()`/`pause()` répété sur la même session est un usage
normal et documenté côté ARCore) -- si dégradation il y a, elle est plus fine qu'un bug de code
visible sans device.

Statut : pas de correctif tenté à l'aveugle. Prochaine étape si ça se reproduit : capture logcat
continue pendant le lag (même méthode que les crashs diagnostiqués au point 13), pour voir si ARCore
ou le driver GL logge un warning au moment où ça ralentit, plutôt que deviner davantage.

### 41. Traduction des blendshapes ARKit pour éviter le remapping manuel (VRM/Blender, VTube Studio) -- backlog, faisabilité étudiée le 8 août 2026

Question posée après validation du point 39 : peut-on traduire les 52 blendshapes ARKit pour
correspondre exactement à ce qu'attendent différents récepteurs, plutôt que de compter sur un
remapping manuel côté utilisateur ?

**VBridger** : déjà bon (protocole iFacialMocap déjà adapté, voir `IFacialMocapSender`).

**Blender/VRM en VMC** : très probablement déjà bon *sans rien changer côté app*. Les 52 noms ARKit
bruts qu'on envoie déjà correspondent exactement à la convention communautaire **"Perfect Sync"**
(terme standardisé depuis 2020 : "un modèle VRM qui possède les 52 blend shape clips nommés comme
ARKit") -- si le modèle cible a été préparé en Perfect Sync (pratique courante, outils un-clic
existants type `blender-vrm-perfect-sync` pour l'ajouter à un modèle VRoid), ça fonctionne déjà.
Si le modèle n'a pas Perfect Sync, c'est une préparation d'avatar (hors app), pas un problème de
protocole -- rien à construire ici.

**VTube Studio (paramètres par défaut, pour éviter le mapping manuel dans son éditeur)** : piste
jugée viable mais non triviale. Vérifié sur la doc officielle : `InjectParameterDataRequest`
accepte explicitement d'écrire dans les paramètres **par défaut** de VTube Studio (`FaceAngleX/Y/Z`,
`FacePositionX/Y/Z`, `MouthSmile`, `EyeOpenLeft/Right`...), pas seulement dans des paramètres
personnalisés créés par un plugin -- la majorité des modèles Live2D existants sont déjà riggés pour
ces noms-là (convention du tracking webcam natif de VTube Studio). Un plugin tiers existant
(`VTube-IFacial-Link`, même pont ARKit -> VTube Studio que ce qu'on fait) envoie les deux jeux de
paramètres simultanément -- les paramètres par défaut (réaction immédiate, zéro mapping, sur un
modèle standard) ET les 52 paramètres ARKit personnalisés (finesse complète, sur un modèle Perfect
Sync) -- probablement la bonne approche à reprendre.

**Le point dur, résolu par une trouvaille de l'utilisateur** : contrairement à VMC/VRM, la
traduction vers les paramètres par défaut de VTube Studio n'est pas un simple renommage -- ses
~15 paramètres grossiers ne correspondent pas un-à-un aux 52 blendshapes ARKit fins (`MouthSmile`
n'est aucun blendshape ARKit précis, c'est une combinaison). VBridger expose déjà ces formules,
testées et éprouvées, dans son propre panneau **`AdvancedARKitSettings`** (accessible directement
dans son UI). Exemple relevé et décodé (labels UI en français, VBridger tournant en localisation FR
-- `boucheFroncements` = `mouthFrown`, `bouchePlisser` = `mouthPucker`, `boucheSourire` =
`mouthSmile`, `boucheFossette` = `mouthDimple`) :

```
MouthSmile = (2 - (mouthFrownLeft + mouthFrownRight + mouthPucker)
                 + (mouthSmileLeft + mouthSmileRight + (mouthDimpleLeft + mouthDimpleRight) / 2)) / 4
```

Vérifiée par calcul : à zéro sur tous les blendshapes (visage neutre), la formule donne bien 0,50,
la valeur affichée en direct dans VBridger pour ce cas -- décodage confirmé correct. `MouthSmile`
porte d'ailleurs le même nom que le paramètre par défaut de VTube Studio, probablement pas un
hasard (convention partagée entre outils Live2D). Reprendre les formules VBridger (à extraire une
par une depuis son panneau `AdvancedARKitSettings` pour chaque paramètre par défaut visé) plutôt
que de les redécouvrir/calibrer à l'œil serait le point de départ pour cette fonctionnalité --
évite l'écueil identifié initialement (aucune formule officiellement documentée par VTube Studio
lui-même).

**OVR (visèmes Oculus/Meta)** : question distincte, pas approfondie -- format beaucoup plus
restreint (~15 visèmes phonétiques, pas les 52 blendshapes ARKit), utilisé surtout pour des
pipelines VRChat/Unity+OVRLipSync. Aucune piste de correspondance trouvée pour l'instant (à la
différence de Perfect Sync et des formules VBridger ci-dessus) -- à clarifier avec l'utilisateur
(quel récepteur final visé) avant toute recherche plus poussée.

Statut : backlog, aucun code écrit. Taille estimée comparable à un nouveau petit sous-système (une
fonction pure de traduction par paramètre par défaut visé, testable en JVM comme le reste du
protocole VTube Studio) plutôt qu'une modification triviale.

### 40. Indicateur visuel "connexion en cours" sur l'écran principal -- backlog, idée ouverte le 8 août 2026

Proposé par l'utilisateur pendant le test du point 39 : l'écran principal (bouton de connexion
unique du HUD) ne distingue aujourd'hui que connecté/non connecté -- pas d'état intermédiaire
visible pendant qu'une connexion VTube Studio progresse (plusieurs étapes asynchrones : socket,
popup d'autorisation, création des paramètres, voir `VTubeStudioConnectionState`). Idée : teinte
orange et/ou clignotement du bouton de connexion pendant les états intermédiaires, cohérent avec le
patron déjà établi pour le bouton de calibrage (teinte rouge en cas d'anomalie, point 19).

Statut : aucun code écrit, idée notée telle quelle. `MainUiState.vtsConnectionState` (déjà exposé)
suffit à dériver l'état "en cours" côté `MainHud.kt` sans changement de modèle -- juste une question
d'affichage à concevoir (quelle teinte exacte, clignotement ou statique, uniquement pour VTube
Studio ou aussi la ligne de statut VMC/iFacialMocap).

### 39. VTube Studio ne reçoit probablement pas VMC/OSC -- intégration directe via son API Plugin -- ✅ validée de bout en bout sur device

Suite à la confirmation device du correctif du point 38 (plus de crash), l'utilisateur signale
n'avoir aucun retour visible côté VTube Studio, et ne pas trouver où paramétrer la réception VMC
dans l'app.

**Investigation (8 août 2026)**, croisée plutôt que devinée :
- La doc officielle des réglages VTube Studio (`github.com/DenchiSoft/VTubeStudio/wiki/VTube-Studio-Settings`)
  ne mentionne aucune option de réception VMC/OSC -- la seule mention VMC concerne l'envoi *sortant*
  vers VSeeFace (sens inverse de ce dont cette app a besoin).
- Confirmé par l'utilisateur en direct dans l'app : aucune option liée à VMC/OSC/réception de
  tracking externe trouvée dans les réglages VTube Studio.
- Recherche plus large : l'intégration tierce de VTube Studio passe par sa propre **API Plugin**
  (WebSocket JSON, port 8001 par défaut), pas par VMC/OSC générique -- VNyan/VSeeFace sont les
  logiciels habituellement cités comme récepteurs VMC natifs, pas VTube Studio.

**Conclusion provisoire** : l'hypothèse de conception du projet ("VMC/OSC -- destiné à VTube
Studio, Blender, Unity", `AndroidMoCap_spec_fonctionnelle.md`, `README.md`) est probablement fausse
pour la partie VTube Studio spécifiquement. Le correctif du point 38 reste valide pour ce qu'il
corrige (le crash) ; à confirmer que l'envoi VMC fonctionne bien de bout en bout avec un vrai
récepteur VMC (Blender, addon officiel) avant de statuer définitivement sur Blender/Unity.

**Piste alternative proposée par l'utilisateur** : VBridger (déjà supporté par cette app via
`IFacialMocapSender`/UDP) est détecté par VTube Studio comme un plugin (connexion locale sur le
port 8001) et lui transmet ses données -- suggère que l'API Plugin officielle de VTube Studio est
la bonne voie pour une intégration *directe*, plutôt que VMC.

**Faisabilité technique vérifiée sur la doc officielle** (`github.com/DenchiSoft/VTubeStudio`), pas
supposée :
- WebSocket JSON, `ws://localhost:8001` par défaut (port configurable côté VTube Studio).
- Auth : `AuthenticationTokenRequest` (nom du plugin/dev, icône optionnelle) -> popup d'autorisation
  affiché à l'utilisateur dans VTube Studio -> `authenticationToken` (jusqu'à 64 caractères ASCII)
  réutilisable pour les sessions suivantes, jusqu'à révocation manuelle par l'utilisateur. À
  persister côté app (DataStore, même patron que l'IP VMC).
- Données : `ParameterCreationRequest` (une fois, par paramètre : nom 4-32 alphanumériques, min/max,
  valeur par défaut) puis `InjectParameterDataRequest` en boucle (tableau `parameterValues` avec
  `id`/`value`). Contrainte : renvoyer au moins 1×/seconde sous peine de reset -- aucun problème,
  l'app envoie à 20-60 Hz.
- **Question de conception non résolue** : contrairement à VMC (noms de blendshapes ARKit reconnus
  automatiquement par un avatar VRM), les paramètres de l'API VTube Studio sont propres à chaque
  modèle Live2D -- des paramètres personnalisés créés via l'API devraient être mappés manuellement
  une fois par l'utilisateur dans son modèle. Probablement le même mécanisme qu'utilise VBridger,
  à confirmer avant de concevoir le mapping ARKit -> paramètres VTube Studio.

**Implémenté (8 août 2026)**, après passage en mode plan (voir le plan sauvegardé) :

- `network/VTubeStudioProtocol.kt` (pur, testé) : `@Serializable data class` pour chaque message de
  l'API (auth par jeton, auth directe, création de paramètre, injection de valeurs) + fonctions
  pures d'encodage/décodage (kotlinx.serialization) -- même esprit que
  `VmcOscSender.buildBundle`/`IFacialMocapSender.buildMessage`. `ParameterCreationRequest` vérifié
  idempotent pour un même plugin (recréer un paramètre déjà créé par "AndroidMoCap" réussit et
  écrase min/max/défaut ; seul un paramètre créé par un *autre* plugin renvoie une erreur) --
  permet d'appeler la création à chaque connexion sans logique de détection "déjà existant".
- `network/VTubeStudioConnectionState.kt` (pur, testé) : machine à état du cycle de connexion
  (`Disconnected` -> `Connecting` -> `AwaitingUserApproval` ou `Authenticating` selon qu'un jeton
  est déjà stocké -> `Authenticated` -> `ParametersRegistered`, `Failed` accessible depuis
  n'importe quelle étape) -- même famille que `CalibrationAnomalyState`/`ThermalThrottleState`.
- `network/VTubeStudioSender.kt` (seule pièce non testable en JVM, socket OkHttp réel) : pilote la
  machine à état en réaction aux messages entrants. Les noms de blendshapes à créer comme
  paramètres sont découverts à la première frame reçue après authentification (pas de liste ARKit
  codée en dur) -- décision pour rester indépendant de ce que MediaPipe fournit réellement à
  l'exécution.
- `ConnectionSettingsStore` : `ConnectionType.VTUBE_STUDIO`, IP et jeton d'authentification
  persistés (même patron que `VMC_HOST`) -- le jeton évite de redemander le popup d'autorisation à
  chaque connexion.
- `MainViewModel`/`ConnectionSettingsScreen`/`SettingsScreen` : troisième type de connexion, IP +
  port (8001 par défaut) éditables, ligne de statut détaillée reflétant les étapes de la connexion
  (contrairement à VMC qui est connecté/non connecté en un seul appel UDP), rappel explicite du
  mapping manuel des paramètres dans le modèle Live2D une fois connecté.
- Dépendances ajoutées : OkHttp (client WebSocket, choix nettement plus établi sur Android que ne
  l'était javaosc-core -- voir point 38) et kotlinx.serialization (JSON pur testable en JVM,
  préféré à `org.json` qui n'est qu'un stub sous `testDebugUnitTest`, aucun Robolectric configuré
  dans ce projet).
- 24 nouveaux tests JVM (protocole + machine à état), `./gradlew testDebugUnitTest assembleDebug` :
  `BUILD SUCCESSFUL`.

**✅ Risque bloquant levé (8 août 2026)** : la doc VTube Studio ne précisant pas si le serveur
WebSocket écoute au-delà de `127.0.0.1`, l'utilisateur a vérifié directement -- un testeur
WebSocket générique se connecte avec succès à `192.168.1.49:8001` depuis un autre appareil du LAN.
Le serveur accepte donc bien des connexions entrantes depuis le réseau local, pas seulement en
loopback -- l'hypothèse de conception de ce point tient. Reste à vérifier en conditions réelles
avec l'app elle-même (pas juste un testeur WebSocket générique) : le popup d'autorisation, la
création effective des paramètres, et le mapping dans un vrai modèle Live2D.

**Bug bloquant trouvé et corrigé au premier essai avec l'app (8 août 2026)** : `NetworkSecurityException`
("not permitted by network security policy") à la connexion. Cause : Android bloque par défaut le
trafic non chiffré depuis l'API 28, et **OkHttp respecte cette politique** contrairement au
`DatagramSocket` brut utilisé par `VmcOscSender`/`IFacialMocapSender` (jamais soumis à cette
vérification -- c'est pour ça que ce problème n'était jamais apparu avant, malgré du trafic tout
aussi non chiffré). Corrigé par un `network_security_config.xml` (`<base-config
cleartextTrafficPermitted="true">`, référencé depuis `AndroidManifest.xml`) plutôt qu'un simple
`android:usesCleartextTraffic="true"` -- équivalent fonctionnellement ici mais plus explicite sur
le raisonnement, et point d'extension si une restriction plus fine s'avérait utile un jour.
`base-config` plutôt que `domain-config` : l'IP cible est saisie librement par l'utilisateur (VMC
et VTube Studio), donc inconnue à la compilation -- un `domain-config` à noms fixes ne peut pas
s'appliquer ici. Justifié par le modèle de l'app (déjà documenté dans le README, section "Vie
privée et réseau") : uniquement du trafic réseau local, jamais de serveur distant, jamais de
donnée sensible.

**Bug de sérialisation trouvé par logcat (8 août 2026)** : `AuthenticationTokenRequest` partait
bien, mais VTube Studio ne répondait jamais -- silence total, ni popup ni erreur. Le JSON réellement
envoyé, révélé par le `Log.d` ajouté à cette occasion, était `{"requestID":"...",
"messageType":"...","data":{}}` : `apiName`, `apiVersion`, `pluginName`, `pluginDeveloper` avaient
tous disparu. Cause : sans `encodeDefaults = true`, kotlinx.serialization omet du JSON tout champ
dont la valeur égale sa valeur par défaut Kotlin -- tous ces champs valaient justement leur défaut
en pratique. Un round-trip encode->décode ne détecte PAS ce bug (le décodage réapplique les mêmes
défauts pour les champs absents, masquant le problème dans les tests JVM) -- corrigé, plus un
nouveau test asserte sur le texte JSON brut plutôt que sur un aller-retour pour empêcher cette
classe de régression de revenir silencieusement. Voir le kdoc de `vtsJson`.

**Bug de bibliothèque WebSocket trouvé par diagnostic croisé (8 août 2026)**, une fois le bug
JSON ci-dessus corrigé -- toujours aucune réponse, malgré une requête cette fois bien formée.
Méthode (jamais deviné, chaque hypothèse vérifiée séparément) :
1. Testeur WebSocket générique sur le téléphone (déjà utilisé pour la connectivité LAN) : "connecté"
   mais jamais testé l'envoi d'un vrai message -- gap identifié.
2. Console JS d'un navigateur sur le PC, `ws://127.0.0.1:8001` : réponse reçue normalement.
3. Même test vers `ws://192.168.1.49:8001` (IP LAN plutôt que loopback, toujours depuis le PC) :
   réponse reçue normalement -- élimine l'hypothèse "VTube Studio ne répond qu'en loopback".
4. Même test depuis le testeur WebSocket du téléphone (donc un vrai autre appareil, avec un JSON
   envoyé manuellement cette fois) : réponse reçue normalement -- élimine réseau/LAN/téléphone.
   **Conclusion : le problème est spécifique à notre client OkHttp.**
5. En-têtes de la poignée de main loggés (`response.headers` dans `onOpen`) :
   `Sec-WebSocket-Extensions: permessage-deflate; client_no_context_takeover;
   server_no_context_takeover`, `Server: websocket-sharp/1.0`. OkHttp propose systématiquement
   l'extension `permessage-deflate`, sans réglage public pour la désactiver -- même un intercepteur
   réseau retirant l'en-tête `Sec-WebSocket-Extensions` de la requête ne change rien (testé et
   confirmé inefficace sur device : OkHttp l'ajoute à un niveau plus bas que la chaîne
   d'intercepteurs). `websocket-sharp` (le serveur VTube Studio) a une implémentation buguée de
   cette extension avec `no_context_takeover` -- incompatibilité documentée et connue
   (`github.com/sta/websocket-sharp/issues/666`).

**Corrigé en basculant de bibliothèque WebSocket** : OkHttp remplacé par **nv-websocket-client**
(`com.neovisionaries:nv-websocket-client`), qui ne propose `permessage-deflate` que si on l'active
explicitement -- jamais par défaut. Confirmé sur device : connexion, authentification (popup
d'autorisation affiché, jeton reçu), et création de 52 paramètres réussies immédiatement après la
bascule.

**Deux bugs de robustesse supplémentaires trouvés en conditions réelles, corrigés dans la foulée** :
1. Le jeton stocké peut être refusé par VTube Studio (observé sur device : "token is invalid or
   has been revoked") -- l'app restait bloquée en `Failed` sans moyen de s'en remettre. Ajout d'un
   retry automatique (nouvelle demande de jeton sur le même socket si le jeton stocké est refusé)
   + un bouton manuel "Oublier le jeton stocké" dans l'écran Connexion pour un nouveau départ
   explicite en cas de besoin.
2. Une collision de nom de paramètre avec un autre plugin déjà connecté (VBridger crée les mêmes
   noms ARKit -- confirmé sur device, errorID 352 "Another plugin has already created a custom
   parameter...") faisait échouer **toute** la connexion dès la première erreur, alors que 48 des
   52 paramètres avaient réussi. Une erreur de création individuelle est maintenant comptée comme
   "traitée" au même titre qu'un succès -- seul ce paramètre-là est perdu, le reste continue
   normalement. Reste un angle non exploré : le comportement réel de `InjectParameterDataRequest`
   (mode `set`) pour un paramètre dont la création a échoué faute d'appartenir à ce plugin -- pas
   encore observé de dysfonctionnement associé, à surveiller si VBridger tourne en simultané.

**Bug d'affichage mineur trouvé et corrigé après confirmation** : le texte de statut "Connecté à
%1$s" affichait littéralement `%1$s` au lieu de l'IP:port -- l'argument de `stringResource()`
n'était simplement pas passé à l'appel. Corrigé.

**✅ Confirmé fonctionnel de bout en bout sur device (8 août 2026)** : connexion, popup
d'autorisation, création des paramètres, réception confirmée par l'utilisateur ("Connexion établie,
et fonctionnelle !"). Reste en dehors du périmètre de cette session : le mapping des paramètres
dans un modèle Live2D réel (dépend du modèle de l'utilisateur, pas de l'app), et le comportement en
cas d'usage simultané avec VBridger au-delà des collisions de création déjà tolérées ci-dessus.

### 38. VMC crashait systématiquement sur Android 11/API 30 -- ✅ corrigé, bug critique

Retour utilisateur (8 août 2026) : changement d'IP VMC + "Envoyer" fait planter l'app, même sans
récepteur ouvert et avec une IP saisie au hasard. Diagnostiqué au départ dans le cadre du point 14
(champ IP figé, cosmétique) -- capture logcat révélant un bug bien plus grave, requalifié en
priorité absolue à la demande de l'utilisateur.

**Stack trace (logcat, 8 août 2026, 12:05:32)** :
```
FATAL EXCEPTION: DefaultDispatcher-worker-2
java.lang.NoSuchMethodError: No virtual method getDefinedPackage(...)Ljava/lang/Package;
  in class Ljava/lang/ClassLoader;
	at com.illposed.osc.LibraryInfo.<clinit>(LibraryInfo.java:46)
	at com.illposed.osc.LibraryInfo.hasStandardProtocolFamily(LibraryInfo.java:283)
	at com.illposed.osc.transport.udp.UDPTransport.<init>(UDPTransport.java:61)
	at com.illposed.osc.transport.OSCPortOut.<init>
	at com.guyiome.androidmocap.network.VmcOscSender.connect(VmcOscSender.kt:81)
```

**Cause, vérifiée sur les sources exactes de javaosc-core 0.9** (`javaosc-core-0.9-sources.jar`,
extrait depuis le cache Gradle -- pas la branche `master` de GitHub, qui peut différer de la version
réellement utilisée) : le bloc d'initialisation statique de `LibraryInfo` appelle
`ClassLoader.getDefinedPackage(String)` (API Java 9+) pour construire une liste de "packages sans
intérêt" à des fins d'affichage (`createLibrarySummary()`, jamais utilisé par cette app) -- absente
du runtime ART sur Android 11 / API 30 (confirmé, `getprop ro.build.version.sdk` = 30 sur
l'appareil de test). Une fois cette classe en échec d'initialisation, la sémantique JVM standard la
marque en erreur pour tout le reste du process -- **pas un cas limite lié à l'IP saisie, une
régression totale** : toute tentative de connexion VMC plantait, quelle que soit l'adresse. C'est un
`Error`, pas une `Exception` : le `catch (e: Exception)` du point 2 (commit `42a82f6`, la veille) ne
pouvait de toute façon pas l'attraper -- corriger le point 2 avait amélioré le diagnostic (log ajouté)
mais ne pouvait pas empêcher ce crash précis.

**javaosc-core 0.9 est déjà la dernière version disponible** (vérifié) -- pas de simple mise à jour
de dépendance possible.

**Correctif** : `VmcOscSender` n'utilise plus `OSCPortOut`/`OSCPort` (dont la construction instancie
`UDPTransport`, qui référence `LibraryInfo`). Vérifié par recherche exhaustive dans les sources 0.9 :
seuls `LibraryInfo.java` et `UDPTransport.java` référencent `LibraryInfo` dans toute la bibliothèque
-- la partie encodage du protocole (`OSCSerializerAndParserBuilder`/`OSCSerializer`/
`BufferBytesReceiver`) en est totalement indépendante. `VmcOscSender` reproduit maintenant
exactement ce que fait `OSCDatagramChannel.send()` en interne (construire un `OSCSerializer` via le
builder, écrire le paquet dans un buffer, en extraire les octets), mais avec un `DatagramSocket`
classique à la place d'`UDPTransport`/`DatagramChannel` -- même sémantique "non connecté", adresse
distante précisée à chaque envoi (déjà le comportement d'origine, `OSCPort.connect()` n'était jamais
appelé).

**Nouveau test de bout en bout** (`VmcOscSenderTest`, la sérialisation extraite en fonction pure
testable `serializeToBytes`) : construit un bundle, le sérialise via le nouveau chemin, le reparse
avec `OSCParser` (indépendant du bug, vérifié) et compare le contenu -- preuve en JVM que le
contournement produit réellement des octets valides, pas seulement qu'il compile.

`./gradlew testDebugUnitTest assembleDebug` : `BUILD SUCCESSFUL`. **Résidu non vérifiable depuis ce
sandbox** : le comportement réel d'ART sur device (un test JVM ne simule pas les particularités
d'ART), mais le nouveau code ne référence plus du tout `LibraryInfo`/`UDPTransport`/`OSCPort` --
vérifié par recherche exhaustive dans le code source, pas par supposition.

**Confirmation device (8 août 2026)** : ✅ plus de crash, changement d'IP + "Envoyer" fonctionnent
normalement. ⚠️ Mais aucune donnée visible côté VTube Studio une fois connecté. Investigué (voir
point 39) : ce n'est pas un residu du bug corrigé ici, mais une hypothèse de conception erronée du
projet -- VTube Studio ne reçoit vraisemblablement pas le protocole VMC/OSC en entrée du tout (sa
doc officielle des réglages ne mentionne aucune réception VMC, seulement l'envoi *vers* VSeeFace, et
l'utilisateur ne trouve aucune option correspondante dans l'app).

**Confirmation Blender (8 août 2026)** : le connecteur VMC de Blender affiche "connecté" -- premier
récepteur tiers indépendant (pas notre propre test JVM round-trip) qui reconnaît les paquets envoyés
comme des bundles VMC/OSC valides. Confirme le correctif ET le format des paquets. Pas encore de
confirmation visuelle des valeurs de blendshapes elles-mêmes à ce stade (aucun mesh/shape keys
configuré côté Blender).

**✅ Confirmation Protokol (8 août 2026)** : moniteur OSC générique pointé sur le port 39539 --
flux continu de messages `/VMC/Ext/Blend/Val` avec noms de blendshapes ARKit corrects (`mouthSmileLeft`,
`noseSneerRight`...) et valeurs flottantes variables et cohérentes (pas figées à zéro), suivis d'un
`/VMC/Ext/Blend/Apply` à la fin de chaque frame. **Le point 38 est maintenant validé de bout en
bout au niveau du contenu**, pas seulement de la connexion -- crash corrigé, format de paquet
correct, et désormais contenu des données confirmé correct par un outil tiers indépendant.

## Automatisation

**Vérification nocturne GitHub (Claude Code cloud, routine planifiée)** -- créée le 6 août 2026,
tous les jours à 19:00 UTC (~21h Paris en été, ~20h en hiver -- le cron reste fixe en UTC). Lecture
seule : delta de commits `origin/main`, état CI, PR ouvertes (recoupées avec la convention "local
d'abord" de ce document), issues ouvertes, et un audit léger façon "regard extérieur/repo public"
(recherche grossière de secrets committés par erreur). Ne committe ni ne pousse jamais rien --
seulement un rapport. Le point 31 ci-dessus est sa première trouvaille concrète. Gérée depuis
https://claude.ai/code/routines (id `trig_01AnKSNF9qEMC3hom1eegjow`), pas depuis ce dépôt.
