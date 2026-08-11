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
| 8 | Minify/R8 en release | **✅ activé et confirmé sur device le 9 août 2026** (shrinking + renommage, `-dontoptimize` conservé -- crash confirmé sinon, voir section dédiée) |
| 14 | Vérification de mise à jour semi-automatique | Backlog -- **bloqué** tant que le dépôt reste privé (API GitHub Releases exige une authentification), voir section dédiée plus bas |
| 15 | Détection langue (cascade) | **Les 3 étages implémentés et confirmés sur device (11 août 2026)** -- étage 1 (porte jawOpen ET mouthOpennessRatio ensemble depuis le point 15quinquies) fiable sur CameraX + ARCore ; étage 2 (couleur) fonctionnel mécaniquement mais son classifieur non fiable seul ; étage 3 (embedding + calibration personnelle) implémenté, un faux positif reproductible ("bouche pressée") trouvé et corrigé, fiabilité globale toujours en cours de validation -- voir sections 15ter/15quater/15quinquies. `tongueOut` toujours à 0 côté réception (aucune injection réseau, `tongueOutInjectionConfirmed` reste `false` -- affiché localement uniquement, panneau de blendshapes). |
| 16 | Détection joues (cascade allégée) | Conception actée, aucun code écrit. **Confirmé par observation device le 6 août 2026** : le mesh bouge très peu au gonflement des joues -- le signal géométrique disponible pour une cascade risque d'être faible/bruité, point d'attention à garder pour la conception détaillée. |
| 17 | Indicateur de fiabilité par blendshape | **Implémenté sur `main`, voir point 24** (l'index le disait encore "aucun code écrit" par erreur) |
| 18 | Persistance sélection blendshapes + valeur brute/ajustée | **Persistance implémentée sur `main`, voir point 25** -- le volet "valeur brute à côté de la valeur ajustée" reste en attente (dépend d'une pondération par blendshape jamais construite) |
| 19 | Détection d'anomalie de calibrage (bouton rouge) | **✅ implémenté et vérifié sur device le 7 août 2026** (tous paliers testés), voir section dédiée -- seuils encore non calés sur un corpus d'usage réel, piste de retours utilisateurs notée pour plus tard |
| 20 | Orientation grand écran / tablette | Constat documenté, aucune décision de mise en œuvre |
| 21 | Tri en sous-écrans des réglages | **Implémenté sur `main`, voir point 26** (l'index le disait encore "aucun code écrit" par erreur) |
| 28 | Fiabilisation du clignement des yeux | **✅ clos côté app le 9 août 2026** -- hypothèse lunettes de départ non reproduite, mais fuite gauche/droite, effondrement en tenue longue et écrasement à angle de caméra inhabituel trouvés et corrigés (`EyeBlinkCorrection.kt`), et faiblesse de l'œil droit tracée à un éclairage physique inégal (pas un bug logiciel). Reste un réglage fin côté VBridger, hors périmètre du dépôt -- voir sections dédiées plus bas (points 45/48) |
| 29 | Validation des traductions FR/EN | **✅ traité le 9 août 2026**, voir point 23 -- relu et validé par l'utilisateur (129 clés, tableau dédié). Pas une relecture par un locuteur natif au sens strict (l'utilisateur n'est natif d'aucune des deux langues), jugé suffisant pour le contexte -- voir la note honnête dans la section point 23 |
| 30 | Sélecteur de langue dans l'app pour Android 11/12 | **✅ confirmé fonctionnel sur device le 9 août 2026** (`androidx.appcompat` 1.7.1 + `AppCompatDelegate.setApplicationLocales()`), FR/EN vérifiés en direct + persistance après redémarrage complet sur l'appareil Android 11 de test -- voir section dédiée plus bas |
| 31 | CI cassée depuis le premier run (`gradlew` sans bit exécutable), puis silencieusement bloquée depuis | **✅ entièrement résolu le 7 août 2026** (commit `df640a4` pour `gradlew` ; cause du blocage silencieux trouvée le même jour -- budget Actions à 0 $, "Stop usage" actif -- corrigée et vérifiée par un run CI réussi), voir section dédiée plus bas |
| 32 | Panneau de blendshapes du HUD : tongueOut disparaissait, noms masqués par le bandeau système | **✅ corrigés et vérifiés sur device le 7 août 2026**, voir section dédiée plus bas |
| 33 | Proposer l'installation ARCore au lieu du repli silencieux | Backlog, priorité mineure, idée ouverte le 7 août 2026, aucun code écrit |
| 34 | Throttling thermique dynamique (débit réduit en cas de chauffe) | **✅ implémenté et vérifié sur device (via mock) le 7 août 2026**, voir section dédiée plus bas -- capteur thermique réel non exercé (appareil de test ne chauffe pas assez), câblage bout en bout confirmé |
| 35 | Panneau de mocks de debug caché (thermique, ARCore, délégué GPU) | **✅ implémenté et vérifié sur device le 7 août 2026**, voir section dédiée plus bas -- les trois mocks confirmés fonctionnels |
| 36 | Relecture globale (code mort, optimisations, incohérences) | **✅ traité le 7 août 2026** -- doc désynchronisée corrigée, silence de `VmcOscSender.connect()` corrigé, 4 correctifs triviaux traités, voir section dédiée plus bas pour ce qui reste volontairement en backlog |
| 37 | Lag ARCore en session, disparu après redémarrage complet | Signalé le 7 août 2026, cause non identifiée -- throttling thermique et coût de la détection d'anomalie écartés par le raisonnement, prochaine étape : capture logcat si reproduit |
| 38 | VMC crashait systématiquement sur Android 11/API 30 (`NoSuchMethodError` javaosc-core) | **✅ corrigé et validé de bout en bout sur device le 8 août 2026** (plus de crash, connecteur VMC Blender reconnaît les paquets, contenu confirmé correct via Protokol -- noms et valeurs de blendshapes cohérents) -- voir section dédiée plus bas |
| 39 | VTube Studio ne reçoit probablement pas VMC/OSC -- intégration directe via son API Plugin | **✅ Validé de bout en bout sur device le 8 août 2026** (WebSocket/nv-websocket-client + kotlinx.serialization -- OkHttp abandonné, incompatible avec le serveur `websocket-sharp` de VTube Studio) -- connexion, popup d'autorisation, création des paramètres, réception confirmés fonctionnels, voir section dédiée plus bas |
| 40 | Indicateur visuel "connexion en cours" sur l'écran principal | **✅ confirmé fonctionnel sur device le 8 août 2026** pour iFacialMocap et VTube Studio (VMC non observable en pratique -- fenêtre trop brève, comportement attendu, pas un bug) -- bug corrigé au passage (l'icône ne passait jamais au vert pour VTube Studio), voir section dédiée plus bas |
| 41 | Traduction des blendshapes ARKit pour éviter le remapping manuel (VRM/Blender, VTube Studio) | Backlog, faisabilité étudiée le 8 août 2026 -- Blender/VRM déjà bon (convention "Perfect Sync"), VTube Studio jugé viable avec les formules VBridger (`AdvancedARKitSettings`) comme point de départ, OVR non exploré, voir section dédiée plus bas |
| 42 | Résolution caméra adaptée au palier (issue GitHub #7) | **✅ implémenté et issue fermée le 9 août 2026** (`ResolutionSelector`, 640x480, `STANDARD`/`COMPATIBLE`), confirmé appliqué sur device -- ⚠️ test thermique A/B mené sur deux appareils, résultat inconclusif/contradictoire, fermeture actant le code fait, pas un gain démontré, voir section dédiée plus bas |
| 46 | Lissage générique des signaux (One Euro Filter) | **✅ intégré et confirmé sur device le 9 août 2026** -- remplace `EyeOpennessSmoother` dans `EyeBlinkCorrection.kt`, tenue de 9s testée sans dégradation visible (mieux que l'ancien lissage sur la même durée). Reste ouvert : lissage général des 52 blendshapes bruts, pas traité ici, voir section dédiée plus bas |
| 47 | Optimisation lumière côté app | En discussion (9 août 2026) -- mis de côté volontairement après le point 28/45 (le vrai problème rencontré était une ombre directionnelle, réglée physiquement ; compensation d'exposition jugée peu utile pour ce cas précis), aucun code écrit |
| 48 | Robustesse du clignement à l'angle de caméra (suite du 45) | **✅ confirmé sur device le 9 août 2026** -- référence EAR "fermé" désormais auto-adaptative par œil (`AdaptiveEarFloor`), corrige un clin d'œil réel écrasé à un angle de caméra en contre-plongée ; testé sans réintroduire la fuite gauche/droite, voir section dédiée plus bas |
| 49 | Canal de release beta | **✅ mis en place (9 août 2026)** -- tag `-beta` publie une Release GitHub en prerelease (`release.yml`), voir section dédiée plus bas |
| 50 | Journalisation uniformisée + partage utilisateur | **✅ confirmé sur device (9 août 2026)** -- un bug réel trouvé et corrigé en cours de validation (niveau INFO vide sur le palier ARCore), couverture étendue à 4 nouveaux logs `INFO`, voir section dédiée plus bas |
| 51 | Cohérence miroir tête / regard / blendshapes | **✅ corrigé et confirmé sur device (10 août 2026)** -- la tête était mirrorée mais aucun blendshape gauche/droite ne l'était ; sortie native (anatomique) et mode miroir désormais tous deux cohérents, mode miroir activé par défaut (cohérent avec l'aperçu écran déjà mirroré), voir section dédiée plus bas |

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

### 13. Fusion ARCore (palier `OPTIMAL`, phase 2) -- ✅ intégrée sur `main`, confirmée fonctionnelle sur device

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

### 14. Vérification de mise à jour semi-automatique -- backlog, bloqué par la visibilité du dépôt

Discuté suite au point 12 (distribution GitHub Releases) : pas de mise à jour automatique possible
hors store (Android exige une confirmation manuelle d'installation pour tout APK sideloadé, protection
système, pas une limite contournable côté code). Piste retenue : l'app interroge périodiquement
l'API GitHub (`GET /repos/guyiome/AndroidMoCap/releases`), compare la version reçue à la sienne, et
affiche une bannière non intrusive ("Mise à jour disponible") avec un lien direct vers la Release si
une version plus récente existe sur le canal choisi -- l'utilisateur garde la main, un ou deux taps
suffisent ensuite. Déclencheurs envisagés : au démarrage, une fois/jour, plus un bouton "Vérifier
maintenant". Réglage de canal (release/beta, voir point 49) persisté comme le reste des réglages de
l'app. Logique de comparaison de version + parsing de la réponse JSON sont de bons candidats à
extraire en fonction pure et à couvrir en TDD le moment venu, avant de toucher à l'appel réseau/UI
qui les entoure.

**Bloqué (9 août 2026, constat de l'utilisateur, confirmé)** : `guyiome/AndroidMoCap` est un dépôt
**privé** -- l'API GitHub Releases exige une authentification pour un dépôt privé, aucun accès
anonyme possible côté app. Un token embarqué dans l'APK est explicitement écarté (extractible par
décompilation, à l'opposé de la discipline déjà suivie pour la clé de signature -- jamais commitée,
jamais distribuée). Décision de l'utilisateur : garder le dépôt privé pour le moment (relecture du
contenu visible, doc à traduire en anglais en vue d'une diffusion plus large), objectif de passer
public à court terme -- pas de solution de contournement temporaire construite (ex. manifest public
via GitHub Pages/second dépôt), ce serait du travail jetable pour un état transitoire. **Ce point
reste en pause jusqu'au passage du dépôt en public.**

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

**Mise à jour (9 août 2026) : relecture faite -- par le porteur du projet, pas un locuteur natif.**
Tableau des 129 clés FR/EN (groupées par écran) généré et transmis pour relecture manuelle ; retour
de l'utilisateur : chaînes validées. À noter précisément pour ne pas surreprésenter cette étape :
l'utilisateur n'est natif d'aucune des deux langues (mais a un bon niveau d'anglais, et jugé "pas de
la littérature, largement suffisant" pour ce contexte) -- ce n'est donc pas la relecture par un
locuteur natif envisagée à l'origine, plutôt une validation de bon sens par la personne la mieux
placée pour juger si le ton/la clarté conviennent à l'app. Repasse visuelle sur device (§ ci-dessus)
faite dans les faits au passage, à plusieurs reprises, lors des sessions de test device de cette
semaine (points 30, 8...), sans qu'aucune incohérence d'affichage FR/EN n'ait été relevée.

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

**Mise à jour (9 août 2026) : recherche faite, outillage de diagnostic ajouté -- toujours en
discussion, rien d'actif en dehors de l'écran Diagnostics.**

Recherche menée pour répondre au point d'attention (a) ci-dessus : le blendshape `eyeBlink` de
MediaPipe est un classifieur ML (texture + géométrie), documenté comme déjà le blendshape le plus
fragile du modèle en usage général (diversité de géométrie oculaire, éclairage, angle de tête) --
rien de spécifique aux lunettes n'est documenté officiellement, mais le mécanisme colle avec
l'observation device : reflets sur les verres, occlusion par la monture, distorsion de réfraction
perturbent un classifieur sensible à la texture ([GitHub issue #5329](https://github.com/google-ai-edge/mediapipe/issues/5329),
[doc officielle face_landmarker](https://developers.google.com/edge/mediapipe/solutions/vision/face_landmarker)).

**Piste retenue pour la suite : Eye Aspect Ratio (EAR)**, Soukupová & Čech 2016 -- mesure
géométrique pure (position des paupières), pas de ML, donc a priori moins sensible aux reflets de
verres que le classifieur MediaPipe (mais pas insensible aux lunettes pour autant : l'occlusion de
monture ou la distorsion de réfraction peuvent aussi tromper la détection de *contour* elle-même).
`EAR = (‖p2-p6‖+‖p3-p5‖)/(2·‖p1-p4‖)`, 6 points par œil ([papier original](https://www.semanticscholar.org/paper/Real-Time-Eye-Blink-Detection-using-Facial-Soukupov%C3%A1-Cech/4fa1ba3531219ca8c39d8749160faf1a877f2ced),
[détail formule](https://pyimagesearch.com/2017/04/24/eye-blink-detection-opencv-python-dlib/)).
Indices dans le mesh 478 points de MediaPipe croisés sur deux sources ([sanderdesnaijer.com/blog](https://www.sanderdesnaijer.com/blog/mediapipe-face-mesh-landmarks),
convention communément reprise dans les implémentations EAR+MediaPipe) -- ⚠️ les tutoriels
communautaires ne s'accordent pas tous sur la convention gauche/droite (sujet vs image) pour les
mêmes indices, donc pas encore assumé comme correspondant à `eyeBlinkLeft`/`eyeBlinkRight` (ARKit)
sans vérification device, voir plus bas.

**Architecture discutée** : deux modes plutôt qu'un remplacement pur du blendshape MediaPipe (EAR
seul a son propre défaut classique -- un plissement des yeux fait aussi chuter l'EAR, pas seulement
un clignement) --
- **Mode léger, actif sur tous les paliers y compris COMPATIBLE** : EAR comme signal correcteur du
  blendshape (fusion, pas substitution) -- le coût du calcul EAR lui-même est négligeable (une
  douzaine de distances), la vraie question de coût est l'extraction des landmarks, aujourd'hui
  conditionnée à l'overlay du mesh (`FaceLandmarkerHelper.landmarksNeeded`). Pas de raison
  identifiée d'exclure COMPATIBLE : MediaPipe calcule ces points en interne de toute façon dès que
  les blendshapes sont demandés, l'extraction ne fait que copier un sous-ensemble.
- **Mode lourd, expérimental** (`ExperimentalFeaturesScreen`, même famille que langue tirée/joues
  gonflées) : calibration EAR personnalisée (accroché au flux de calibration déjà existant),
  lissage temporel/hystérésis (le papier original utilise une fenêtre de frames, pas un seuil
  frame-à-frame), rejet des frames aberrantes (glare = saut brutal des landmarks).

**Upgrades "globales" de capture (gestion logicielle de la lumière/exposition)** évoquées en
discussion, explicitement **gardées en tête pour éviter un double traitement ou un recalcul lourd
plus tard, pas pour être implémentées dans ce lot** -- notées comme piste séparée : CameraX expose
déjà `CameraControl.setExposureCompensationIndex()` et l'interop Camera2 pour le contrôle AE
([doc officielle](https://medium.com/androiddevelopers/using-camerax-exposure-compensation-api-11fd75785bf)),
`CameraController.kt` n'y touche pas du tout aujourd'hui. À traiter comme son propre point de
backlog plus tard (améliorerait la capture globale, pas seulement les yeux), pas fondu dans le 28.

**Diagnostic temporaire ajouté** (`tracking/EyeAspectRatio.kt`, pur et testé -- `EyeAspectRatioTest.kt`,
5 tests) : calcule EAR pour deux groupes d'indices (`GROUP_A`/`GROUP_B`, nommés par indices plutôt
que gauche/droite tant que la correspondance avec `eyeBlinkLeft/Right` n'est pas confirmée), affiché
dans `DiagnosticsScreen` à côté de la latence d'inférence -- nécessite l'overlay du mesh activé
(Affichage & confort) pour des valeurs non nulles. **✅ confirmé fonctionnel sur device** (téléphone,
palier OPTIMAL) : valeurs réelles obtenues immédiatement, `Groupe A : 0,20 · Groupe B : 0,23`, dans
la plage attendue pour un œil ouvert (~0,2-0,35) -- premier signe encourageant, mais ce n'est qu'un
relevé statique (pas encore de clignement observé, ni de comparaison avec/sans lunettes). Prochaine
étape : test réel par l'utilisateur (clignement avec et sans lunettes, en observant EAR à côté du
blendshape `eyeBlinkLeft`/`eyeBlinkRight` brut sélectionné dans le panneau de blendshapes) avant
toute décision de fusion. Statut : toujours en discussion, aucune logique de correction/fusion
écrite, ce diagnostic est un outil de mesure, pas une fonctionnalité.

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

Statut : conception actée le 6 août. **Phase 1 (étages 1+2) implémentée et confirmée sur device le
11 août 2026** -- voir section détaillée juste après. Le prérequis initial (throttling thermique
continu, point 3/13) était déjà levé entre-temps par le point 34 (7 août), sans qu'on ait fait le
lien avant de commencer.

### 15bis. Phase 1 de la cascade langue tirée (étages 1+2) -- ✅ implémentée, confirmée sur device le 11 août 2026

**Étage 1 (porte `jawOpen`)** : `tracking/TongueOutGate.kt`, seuil par défaut 0,3. Confirmé fiable
sur device, **CameraX et ARCore** : porte fermée au repos (jawOpen ~0,01), ouverte de façon
cohérente bouche ouverte (jawOpen 0,3-0,5+), sur les deux paliers testés.

**Étage 2 (analyse couleur)** : `tracking/LipLandmarks.kt` (recadrage buccal) +
`tracking/MouthColorAnalysis.kt` (conversion HSV, ratio de pixels "couleur langue") +
`tracking/TongueColorBaseline.kt` (référence adaptative, voir plus bas). Mécanique confirmée
fonctionnelle : accès pixel opérationnel sur les deux chemins caméra (`CameraController.peekPooledBitmap`
pour CameraX, `ArCoreHeadPoseTracker.peekLastBitmap` -- nouveau, ajouté ce jour-là -- pour ARCore),
recadrage confirmé visuellement centré sur la bouche (image complète + rectangle de debug envoyée à
l'utilisateur pour validation directe).

**Mais le classifieur couleur de l'étage 2 n'est pas fiable**, ni comme seuil absolu ni comme
référence adaptative :
- Un seuil couleur fixe (`DEFAULT_COLOR_RATIO_THRESHOLD`) dérive trop d'une session à l'autre (même
  geste "bouche ouverte, langue rentrée", quelques minutes d'écart, palier ARCore : ratio 0,81-0,93
  puis 0,95-0,99 -- chevauche la plage "langue tirée" mesurée séparément). Probablement exposition
  auto/angle caméra, pas un vrai changement physique.
- Une référence adaptative (`TongueColorBaseline`, même principe qu'`AdaptiveEarFloor` du point 48)
  améliore les choses par moments (jusqu'à 99% de détection correcte sur un essai ARCore) mais reste
  instable d'un essai à l'autre (29% sur un autre essai, même geste, marge élargie) -- sur au moins un
  essai, les valeurs brutes "langue tirée"/"sans langue" se chevauchaient tout court, aucun seuil ne
  pouvait les séparer.
- **Sur palier CameraX/STANDARD, le signal ne discrimine quasiment rien** (0/110 détections correctes
  sur un essai "langue tirée" complet, les trois phases du protocole se chevauchant presque totalement).

**Bug de rotation ARCore découvert en marge de cette investigation** (indépendant du point 15,
voir ci-dessous) : le bitmap ARCore envoyé à MediaPipe est tourné ~90° anti-horaire par rapport à la
réalité, confirmé par observation directe de l'utilisateur sur une image de debug -- la formule de
rotation (`ArCoreHeadPoseTracker.readCameraSensorOrientation`/`rotateBitmap`) était déjà documentée
comme "jamais vérifiée visuellement" ; c'est fait, et le résultat est négatif. N'empêche pas le
recadrage buccal de fonctionner (landmarks et bitmap restent cohérents entre eux dans le même
référentiel tourné) mais reste un problème plus large, potentiellement significatif pour la
précision de tout le tracking sur ce palier -- noté séparément dans le backlog privé, pas encore de
piste de correction actée.

**Conclusion, pas devinée** : le ratio couleur moyen d'un recadrage fixe porte un signal réel (la
langue tirée élève systématiquement le ratio en moyenne, sur presque tous les essais) mais trop
bruyant pour servir de classifieur autonome, quel que soit le réglage. Ça confirme plutôt
qu'infirme la conception d'origine : l'étage 3 (classification par embedding + calibration
personnelle) n'est pas une amélioration optionnelle, c'est ce qui doit réellement trancher --
l'étage 2 ne sert qu'à filtrer les cas clairement négatifs avant lui, jamais censé être parfait seul.

`tongueOut` reste absent de `corrected.blendshapes` (donc à 0 côté réception) -- aucune injection
tant que l'étage 3 n'existe pas, décision explicite pour ne pas envoyer un signal connu peu fiable.
`TONGUE_DIAGNOSTIC_LOGGING` repassé à `false` en attendant l'étage 3.

Tests : `TongueOutGateTest.kt` (5), `LipLandmarksTest.kt` (8), `MouthColorAnalysisTest.kt` (20),
`InferenceLoadMonitorTest.kt` (6), `TongueColorBaselineTest.kt` (6) -- 45 tests au total pour la
phase 1, tous purs/JVM.

Prochaine étape : étage 3 (nouvelle dépendance `ImageEmbedder`, déjà présente dans `tasks-vision`
sans coût de dépendance additionnel -- voir plan détaillé au moment de l'implémentation ; flux de
calibration personnelle à concevoir).

### 15ter. Étage 3 de la cascade langue tirée (embedding + calibration personnelle) -- mécaniquement fonctionnel, fiabilité partielle confirmée sur device le 11 août 2026

**Implémentation** : `tracking/TongueEmbeddingClassifier.kt` (classification pure par similarité
cosinus, `UNDECIDED` si écart sous la marge ou référence absente), `tracking/TongueEmbeddingHelper.kt`
(wrapper `ImageEmbedder`, GPU→CPU comme `FaceLandmarkerHelper`, `RunningMode.IMAGE` synchrone),
`tracking/TongueCalibrationAveraging.kt` + `tracking/TongueCalibrationRecordingState.kt` (machine à
état pure pilotant l'enregistrement des deux références), `settings/TongueCalibrationStore.kt`
(persistance CSV dans `filesDir`, même patron qu'`AppLog`). Modèle `image_embedder.tflite`
(`mobilenet_v3_small`, 224x224, optionnel -- l'app tourne sans si l'étage 3 n'est jamais activé).
`ui/TongueCalibrationScreen.kt` nouvel écran de calibration, accessible depuis "Fonctionnalités
expérimentales" une fois le toggle activé. **Aucune injection réseau** : `tongueOut` est affiché
localement (panneau de test, `allBlendshapes`) mais jamais ajouté à `corrected.blendshapes` -- choix
explicite de l'utilisateur pour avoir un retour direct en test sans risquer d'envoyer un signal non
fiable à VBridger/VMC/VTube Studio.

**Bug trouvé et corrigé** : une course d'initialisation faisait que `TongueEmbeddingHelper` n'était
jamais construit si le toggle expérimental était déjà activé au démarrage (cas normal après une
première session) -- le collecteur réactif de `MainViewModel.init{}` tentait de le construire avant
que `currentTierConfig` soit renseigné par `initializeTracking()`, et comme un `Flow` ne réémet que
sur un changement de valeur (pas périodiquement), la tentative ratée n'était jamais retentée. Symptôme
observé : la calibration semblait se dérouler normalement (deux phases, `DONE` atteint) mais aucun
fichier `tongue_calibration.csv` n'était créé, et l'app affichait "jamais calibré" en boucle. Corrigé
en ajoutant un second appel à `ensureTongueEmbeddingHelper()` à la fin de `initializeTracking()`,
après que `currentTierConfig` soit posé. Confirmé sur device (log de confirmation toujours actif,
`"TongueEmbeddingHelper initialisé"`, apparaissant bien après la sélection du palier).

**Première calibration, mécaniquement réussie mais peu discriminante** : une fois le bug ci-dessus
corrigé, le fichier de calibration se créait correctement, mais un test de tenue de pose (langue
rentrée / langue tirée) montrait le classifieur bloqué sur `TONGUE_IN` presque tout le temps, y
compris pendant une vraie tenue "langue tirée" -- hypothèse (pas certitude) : la transition
instantanée entre les deux phases d'enregistrement capturait des frames de transition côté "langue
dehors", diluant la référence. Corrigé en ajoutant une pause de préparation (`PREPARE_TONGUE_IN`,
2s par défaut, n'accumule jamais) entre les deux phases, plus un compte à rebours numérique affiché
à l'utilisateur (`TongueCalibrationRecordingState.kt` + `TongueCalibrationScreen.kt`) -- demande
explicite de l'utilisateur pour un meilleur retour pendant la calibration.

**Test de validation après recalibration (11 août 2026, palier OPTIMAL/ARCore)** :
- **Langue tirée tenue** : net progrès -- après ~1s de transition, le classifieur reste stable sur
  `TONGUE_OUT` tout le reste du segment (`simOut` ~0,84-0,92 contre `simIn` ~0,74-0,83). Avant la
  pause de calibration, ce cas basculait presque toujours à tort sur `TONGUE_IN`.
- **Langue rentrée tenue** : correcte au début (`TONGUE_IN`, `simIn` 0,91-0,93), mais bascule à tort
  sur `TONGUE_OUT` en fin de tenue (`simOut` 0,78-0,86 contre `simIn` 0,72-0,79), au moment précis
  d'un pic de `jawOpen` (0,37→0,55) et de `ratio` (0,88→0,95) -- confirmé par l'utilisateur : variation
  involontaire d'ouverture de mâchoire pendant la tenue, pas un vrai changement de langue.
- **Test ciblé "mâchoire grande ouverte, langue rentrée"** (pour vérifier si l'étage 3 confond
  bouche ouverte et langue tirée) : inconclusif -- l'étage 2 a filtré la quasi-totalité du segment
  avant même d'atteindre l'étage 3 (`ratio` 0,58-0,65, sous la référence adaptative ~0,61-0,62). La
  seule frame ayant atteint l'étage 3 retombe sur `UNDECIDED` (`simOut=0,592` vs `simIn=0,558`), pas
  de faux positif. Le `ratio` de ce test délibéré (0,58-0,65) est nettement plus bas que celui du
  faux positif observé pendant la tenue "langue rentrée" (0,88-0,95) -- donc ce n'était probablement
  pas un simple bâillement qui causait ce faux positif, cause exacte non identifiée.

**Conclusion honnête, pas forcée** : progrès réel et mesuré par rapport à la première calibration,
mais la barre de fiabilité fixée par le plan ("aucun chevauchement `simOut`/`simIn` sur au moins 2
sessions indépendantes") n'est pas encore atteinte -- un chevauchement a été observé dans cette même
session (fin de tenue "langue rentrée"). `tongueOutInjectionConfirmed` reste `false`, aucune
injection dans `corrected.blendshapes`/les émetteurs réseau. Piste ouverte pour une session
ultérieure : refaire le test "langue rentrée" avec une mâchoire délibérément stable, pour isoler si
la confusion est purement due au mouvement ou une vraie limite du classifieur.

Tests : `TongueEmbeddingClassifierTest.kt`, `TongueCalibrationAveragingTest.kt`,
`TongueCalibrationRecordingStateTest.kt` (12, réécrits pour la phase `PREPARE_TONGUE_IN`) -- tous
purs/JVM, `TongueEmbeddingHelper.kt`/`TongueCalibrationStore.kt` non testés (glue device/IO, même
principe que `FaceLandmarkerHelper`/`AppLog`).

### 15quater. Deux bugs réels trouvés et corrigés, un faux positif reproductible identifié (11 août 2026, même journée)

Retour utilisateur après le 15ter ("je ne vois jamais tongueOut passer à 1") a mené à deux
corrections concrètes, sans lien avec la fiabilité du classifieur lui-même :

- **Calibration corrompue par la locale de l'appareil (corrigé, confirmé)** :
  `TongueCalibrationStore.save()` formatait les floats avec `"%.8f".format(it)` sans locale
  explicite -- en français ça écrit une virgule décimale (`-0,00334370`), qui collisionne avec le
  séparateur CSV et coupe chaque nombre en deux à la relecture (un vecteur de ~1024 valeurs se
  relisait à 2048). `cosineSimilarity()` traite ça comme une incompatibilité de taille et renvoie
  0.0 en permanence -- `UNDECIDED` systématique, sans exception ni log d'erreur. N'affectait pas les
  tests du 15ter (référence encore correcte en mémoire depuis la calibration, jamais rechargée du
  disque) -- n'apparaît qu'après un redémarrage de l'app. Corrigé (`Locale.ROOT` explicite, même
  précédent que `LogFormatting.kt`), avec un test de non-régression sous `Locale.FRANCE`
  (`TongueCalibrationStoreTest.kt`, 5 tests -- cette classe s'avère testable en JVM pur malgré son
  package `settings/`, ne dépendant que de `java.io.File`, contrairement à `AppSettingsStore`).
  Confirmé sur device : fichier relu à 1024 valeurs après recalibration, séparateur correct.

- **Latence du mesh à l'ouverture de bouche (corrigé, confirmé)** : `saveTongueDebugCrop()`, marqué
  "jetable, à retirer une fois la cause confirmée" depuis la phase 1 (débogage du mapping
  `LipLandmarkIndices`, déjà résolu) mais jamais retiré, restait actif à chaque frame où l'étage 1
  (jawOpen) est ouvert tant que `TONGUE_DIAGNOSTIC_LOGGING` est actif : copie bitmap plein cadre +
  dessin d'un rectangle + double encodage/écriture PNG sur disque, plusieurs fois par seconde en
  continu tant que la bouche reste ouverte. Retiré entièrement (les lignes de log légères restent).
  Confirmé par l'utilisateur : "Plus de latence. Ce point est bon."

**Fiabilité du classifieur, après ces deux corrections** : deux tests contrôlés (langue tirée tenue,
51 frames : 76% `TONGUE_OUT` correct, 24% `UNDECIDED`, 0% faux négatif ; langue rentrée tenue, 58
frames : 10% `TONGUE_IN` correct, 90% `UNDECIDED`, 0% faux positif) montrent qu'il ne s'est jamais
trompé de sens, seulement indécis -- meilleur que redouté. Mais l'utilisateur a ensuite démontré un
**faux positif reproductible et net** : bouche entièrement fermée, lèvre inférieure "mangée"/rentrée
avec les dents (recule légèrement la mâchoire) -- 100% des frames sur 12s classées `TONGUE_OUT` à
tort, avec `mouthGeo` proche de zéro (bouche géométriquement fermée) mais `ratio` couleur pégé à 1.0.
Hypothèse de l'utilisateur, plausible mais **pas encore confirmée par les données de recadrage** :
mordre/rentrer la lèvre inférieure expose sa face interne humide, colorimétriquement proche d'une
langue -- pas forcément un bug de calcul mais une vraie ambiguïté du signal sur ce geste précis. Une
ligne de log légère (coordonnées du recadrage seules, pas de bitmap) a été ajoutée pour vérifier,
mais le test a été interrompu par une batterie faible côté téléphone -- **à reprendre**, voir backlog
privé.

### 15quinquies. Faux positif "bouche pressée" résolu -- gate étage 1 géométrique (11 août 2026, suite de la même session)

Reprise après recharge : le log de recadrage a confirmé l'hypothèse du 15quater -- bouche mordue
produisait un recadrage de 7-14px de large (la ligne de contact des lèvres pressées), lu comme
"100% couleur langue" par les étages 2 et 3.

**Premier essai, surcorrigé** : garde-fou de taille minimale sur `mouthCropRegion()`
(`MIN_CROP_DIMENSION_PX`). Calé à 15px sur les premières données -- réduisait le problème sans le
résoudre (67% des faux `TONGUE_OUT` restants tombaient à 15-17px, juste au-dessus). Remonté à 25px
sur la base d'un seul relevé `logcat -d` non contrôlé (mélangeant probablement plusieurs gestes) --
**surcorrection confirmée par l'utilisateur** : plus aucune détection possible même langue
réellement tirée. Reprise immédiate à 15px (dernier état fonctionnel) après un retour ferme de
l'utilisateur sur la décision unilatérale de "faire une pause" prise à ce moment -- **erreur de
posture reconnue et corrigée** : ce genre de décision (arrêter, continuer, revenir en arrière)
appartient à l'utilisateur, pas à l'assistant.

**Cause racine identifiée et corrigée** : la taille de recadrage en pixels absolus n'était pas le
bon signal -- dépend de la distance/position caméra, pas seulement du geste, d'où la marge trop
fine et fragile. `mouthOpennessRatio` (`LipLandmarks.kt`), déjà calculé pour diagnostic, normalisé
par la largeur de bouche donc indépendant de la distance caméra, sépare nettement les deux cas sur
**deux reproductions indépendantes** le même jour (lèvre mordue délibérément, puis en position
neutre au repos) : bouche pressée/fermée ≈ 0,001-0,12 dans les deux cas, bouche réellement ouverte
≈ 0,44+ dans tous les tests "langue tirée" du jour. `TongueOutGate.mouthGeometricGateOpen()`
(nouveau, seuil 0,2) exige maintenant cette confirmation géométrique **en plus** de `jawOpen` (ML)
pour ouvrir l'étage 1 -- `jawOpen` seul s'est montré trompable par une bouche pressée dans les deux
reproductions. `MIN_CROP_DIMENSION_PX` reste en place (filet de sécurité), mais n'est plus la ligne
de défense principale.

**Confirmé sur device** : position neutre -> `etage1=NON (jawOpenOk=false mouthGeoOk=false)` sur
tout un relevé, aucun faux `TONGUE_OUT` ; langue réellement tirée -> détection intacte
(`mouthGeo` 0,27-0,38, confortablement au-dessus du seuil). Confirmation utilisateur : "C'est bon !"

**Lissage d'affichage tenté puis retiré** : une tentative de lisser le clignotement 1/0 local
(`TongueOutDisplaySmoothing.kt`, maintien 300ms après la dernière détection positive) a coïncidé
avec les rapports de faux positifs en position neutre -- corrélation, pas causalité confirmée (le
vrai coupable était le gate étage 1 ci-dessus, qui rendait chaque faux positif plus visible une fois
maintenu 300ms). Le code de lissage reste en place, pas retiré, mais son évaluation est à refaire
maintenant que le gate est corrigé -- possible que le clignotement résiduel (mentionné par
l'utilisateur, "j'ai toujours du blink") soit désormais correctement traité par ce lissage une fois
la source du bruit supprimée. À vérifier dans une session ultérieure plutôt que de conclure sans
données fraîches.

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

### 42. Résolution caméra adaptée au palier (issue GitHub #7) -- ✅ implémenté, bénéfice thermique non confirmé

Scindé de l'issue #1 (voir "Automatisation" plus bas) : `ImageAnalysis.Builder()` ne fixait aucune
résolution cible, CameraX choisissant sa résolution par défaut quel que soit le palier. Concerne
uniquement `STANDARD`/`COMPATIBLE` (`CameraController`, CameraX) -- `OPTIMAL` pilote sa propre
capture via ARCore (`ArCoreHeadPoseTracker`), hors de ce fichier.

**Implémenté** : `ResolutionSelector`/`ResolutionStrategy` (API courante depuis CameraX 1.3, pas
l'ancien `setTargetResolution()` déprécié) ciblant `640x480`, `FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER`.
Une seule valeur pour les deux paliers plutôt que différenciée : MediaPipe Face Landmarker recadre
et redimensionne l'image en interne quelle que soit sa résolution d'entrée (doc officielle) -- au
délà d'un certain point, plus de pixels n'apporte donc aucune précision de détection
supplémentaire, seulement du coût de décodage/copie en amont. Aucune source fiable trouvée pour la
taille d'entrée interne exacte du modèle Android utilisé -- 640x480 choisi comme valeur standard
généreuse plutôt qu'un chiffre "optimal" inventé.

**Confirmé sur device (9 août 2026)** : résolution `640x480` bien retenue par CameraX en palier
`STANDARD` (`ImageAnalysis.resolutionInfo`, loggée), correspond exactement à la cible demandée
(l'appareil de test la supporte nativement, aucun repli déclenché).

**Test thermique A/B mené sur deux appareils en parallèle (téléphone ASUS + tablette XPPen
MDP1221, celle-ci fonctionnant aussi pour le tracking en palier `STANDARD`)** : `git stash`/`pop`
pour bâtir "avant" (sans le correctif) et "après" (avec), 5 minutes de tracking actif par
condition, températures relevées via `dumpsys thermalservice` (capteurs CPU/GPU/NPU/peau/batterie,
sans root). **Résultat inconclusif, voire contradictoire** : le capteur "peau" (le plus
représentatif du ressenti utilisateur) s'améliore sur les deux appareils (téléphone : delta
+3,42°C -> +2,45°C ; tablette : +7,13°C -> +3,53°C, net), mais les capteurs CPU/GPU/NPU vont dans
l'autre sens, parfois franchement sur la tablette (delta quasiment doublé). Incohérence interne
qui sent la variabilité de mesure (un seul run par condition, pièce non contrôlée, historique
thermique différent entre les deux runs) plutôt qu'un vrai signal exploitable. Hypothèse
plausible : ce correctif n'économise que le décodage/copie *avant* MediaPipe, pas l'inférence
elle-même (le vrai gros du coût CPU/GPU/NPU) -- l'effet réel est peut-être simplement trop petit
pour ressortir proprement de ce protocole.

**Statut honnête** : le correctif fait ce qu'il annonce (résolution effectivement plus petite,
confirmé), reste justifié sur le principe (moins de décodage/copie, zéro perte de précision côté
MediaPipe), mais **aucun bénéfice thermique mesurable démontré** avec ce protocole -- pas présenté
comme un gain confirmé, contrairement au reste du travail "confirmé sur device" de cette session.

**Issue #7 fermée (9 août 2026)** avec ce même bilan honnête en commentaire -- fermeture actant que
le code est fait et vérifié, pas que le gain annoncé par l'issue d'origine est démontré.

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

### 40. Indicateur visuel "connexion en cours" sur l'écran principal -- ✅ confirmé fonctionnel sur device le 8 août 2026

Proposé par l'utilisateur pendant le test du point 39 : l'écran principal (bouton de connexion
unique du HUD) ne distingue aujourd'hui que connecté/non connecté -- pas d'état intermédiaire
visible pendant qu'une connexion VTube Studio progresse (plusieurs étapes asynchrones : socket,
popup d'autorisation, création des paramètres, voir `VTubeStudioConnectionState`). Idée : teinte
orange et/ou clignotement du bouton de connexion pendant les états intermédiaires, cohérent avec le
patron déjà établi pour le bouton de calibrage (teinte rouge en cas d'anomalie, point 19).

**Implémenté** (`MainHud.kt`) : teinte ambre (`0xFFFFB74D`, même couleur que l'avertissement
thermique déjà présent dans ce bandeau -- cohérence visuelle plutôt qu'une nouvelle couleur) tant
que `vtsConnectionState` n'est ni `Disconnected`, ni `ParametersRegistered`, ni `Failed`. Teinte
statique, pas de clignotement -- aucun précédent de clignotement ailleurs dans ce bandeau
(calibrage, thermique), rester cohérent plutôt qu'introduire un nouveau langage visuel pour ce
seul cas ; ajustable plus tard si jugé insuffisamment visible en usage réel. Un appui pendant cet
état annule la tentative (`MainViewModel.toggleActiveConnection` le fait déjà, aucun changement
nécessaire côté ViewModel). Description d'accessibilité dédiée
(`cd_connecting_tap_cancel`) plutôt que de réutiliser celle de "non connecté".

**Bug corrigé au passage, trouvé en implémentant ce point** : `isConnected` dans `MainHud.kt` ne
tenait compte que de `vmcEnabled`/`iFacialMocapConnectedTo` -- une connexion VTube Studio
pleinement établie (`ParametersRegistered`) ne faisait jamais passer l'icône au vert, elle restait
indéfiniment sur "non connecté". Sans ce correctif, ajouter la teinte "en cours" aurait laissé
l'icône dans un état incohérent une fois la connexion réellement établie.

**Étendu aux trois types de connexion sur demande explicite (même jour)** :
- **iFacialMocap** : `iFacialMocapListening == true` mais `iFacialMocapConnectedTo == null` (en
  écoute, VBridger pas encore connecté) -- donnée déjà exposée, aucun nouveau champ nécessaire. Un
  appui dans cet état annule réellement (`stopIFacialMocapListening()`, déjà le comportement de
  `toggleActiveConnection`).
- **VMC** : nouveau champ `MainUiState.vmcConnecting` (mis à `true` au début de
  `connectVmcTarget()`, `false` dans toutes les branches de sortie -- succès, échec, exception).
  Fenêtre généralement très brève (résolution d'une IP, pas un vrai lookup réseau) mais affichée
  quand même pour rester cohérent entre les trois types. Nuance assumée : contrairement à
  iFacialMocap/VTube Studio, un appui pendant cette fenêtre ne "annule" pas réellement -- `vmcEnabled`
  est encore faux à ce moment-là, donc `toggleActiveConnection` relance une connexion plutôt que de
  l'annuler. Sans conséquence pratique vu la brièveté de la fenêtre, documenté tel quel plutôt que
  sur-conçu (chaînes de description dédiées par type, état d'annulation propre) pour un cas limite
  qui n'arrivera quasiment jamais en usage réel.

**Correctif de commentaires périmés au passage** : plusieurs endroits de `MainViewModel.kt`
mentionnaient encore VMC comme destiné à "VTube Studio / Blender / Unity" (kdoc de
`connectVmcTarget`, commentaire de section, doc du champ `vmcEnabled`) -- corrigés en "Blender /
Unity", cohérent avec la conclusion du point 39.

**✅ Confirmé sur device (8 août 2026)** : iFacialMocap et VTube Studio fonctionnels, teinte ambre
bien visible pendant l'attente. VMC non observable en pratique -- confirme l'hypothèse posée
pendant l'implémentation (fenêtre trop brève, résolution d'IP sans vrai lookup réseau) plutôt
qu'un bug ; comportement attendu, pas d'action à prendre.

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

### 43. Sélecteur de langue en-app (point 30) -- ✅ confirmé fonctionnel sur device

Demande explicite (9 août 2026) : offrir un sélecteur de langue **dans l'app**, fonctionnel sur
toutes les versions d'Android -- pas seulement le sélecteur système natif (réglages système >
langues de l'app, `res/xml/locales_config.xml`), réservé à Android 13+ et donc invisible sur les
deux appareils de test de ce projet (Android 11).

**Approche retenue, vérifiée sur la doc officielle Android** (`developer.android.com/guide/topics/
resources/app-languages`) plutôt que supposée : `AppCompatDelegate.setApplicationLocales()`, qui
fonctionne sur toutes les API depuis 21 (stockage propre à AppCompat sous API 33, délégué à
`LocaleManager` au-delà). Point dur explicitement documenté par Google : **sous Compose, cette API
exige que l'Activity étende `AppCompatActivity`**, pas `ComponentActivity` -- sinon l'appel est
silencieusement sans effet. L'alternative "cadre système seul" (`LocaleManager` direct) ne
fonctionne qu'à partir de l'API 33, ce qui aurait échoué le critère explicite "toutes les versions".

**Chaîne de prérequis, chacun vérifié avant modification** :
1. `AppCompatActivity.onCreate()` exige un thème descendant de `Theme.AppCompat.*` -- le thème du
   projet (`Theme.AndroidMoCap`, `themes.xml`) avait pour parent `android:Theme.Material.NoActionBar`
   (thème plateforme, pas AppCompat), qui aurait fait planter l'app au lancement une fois
   `MainActivity` changée. Parent remplacé par `Theme.AppCompat.DayNight.NoActionBar` -- les
   surcharges existantes (`windowBackground`/`statusBarColor`/`navigationBarColor`, fond sombre au
   lancement) restent valables, ce sont des attributs `android:` de plateforme, pas spécifiques à
   AppCompat.
2. `androidx.appcompat:appcompat:1.7.1` ajouté (`gradle/libs.versions.toml` +
   `app/build.gradle.kts`) -- 1.7.1 vérifiée comme dernière version **stable** via
   `dl.google.com/android/maven2/androidx/appcompat/appcompat/maven-metadata.xml` (le tag "latest
   release" du dépôt, `1.8.0-rc01`, est une pré-version, explicitement écartée ; `search.maven.org`
   n'indexe pas les artefacts AndroidX de la même façon, détour nécessaire).
3. `MainActivity` étend maintenant `AppCompatActivity` (import `androidx.appcompat.app.AppCompatActivity`)
   au lieu de `ComponentActivity` -- superclasse stricte, `setContent {}` et le flux de permission
   caméra existant (`registerForActivityResult`) restent inchangés.
4. `AndroidManifest.xml` : ajout du `<service android:name="androidx.appcompat.app.
   AppLocalesMetadataHolderService" android:enabled="false" ...>` avec
   `<meta-data android:name="autoStoreLocales" android:value="true" />` -- persistance automatique
   de la langue choisie d'un lancement à l'autre, sur toutes les versions, sans DataStore ni code de
   lecture/écriture propre à ce projet (contrairement au reste des réglages persistés de l'app).

**Conception** : `settings/AppLanguage.kt` (nouveau, pur, testé JVM -- `AppLanguageTest.kt`, 5 tests)
-- enum `SYSTEM`/`FRENCH`/`ENGLISH` + `appLanguageFromTag()` (reconstruit l'enum depuis
`AppCompatDelegate.getApplicationLocales().toLanguageTags()`, qui renvoie une chaîne **vide** --
pas `null` -- quand aucune langue n'est forcée). Volontairement limité au mapping nom/tag : la
construction du `LocaleListCompat` réel et l'appel `AppCompatDelegate.setApplicationLocales()`
vivent dans `MainViewModel.setAppLanguage()` (dépendance au framework Android, non testable en JVM
pur ici, même contrainte que les autres senders réseau du projet). `MainUiState.appLanguage` lu une
seule fois au lancement (`init`, pas de `Flow` à collecter -- AppCompat gère lui-même la persistance,
rien d'autre à observer), mis à jour immédiatement à chaque changement (effet AppCompat synchrone,
pas besoin de relancer l'app).

**UI** : nouvelle section dans `DisplaySettingsScreen` (catégorie "Affichage & confort" -- pas de
catégorie dédiée pour un seul réglage), trois `FilterChip` ("Suivre le système" / "Français" /
"English"), même patron visuel que le sélecteur de type de connexion. Parité FR/EN des nouvelles
clés vérifiée (129/129, `comm -23`/`comm -13`).

`./gradlew testDebugUnitTest assembleDebug` : `BUILD SUCCESSFUL` (129 clés `strings.xml` FR/EN en
parité, `AppLanguageTest` : 5/5 verts). Deux erreurs de lint XML "--" dans des commentaires
(`themes.xml`, `AndroidManifest.xml`, `strings.xml`/`values-fr`) trouvées et corrigées en cours de
route -- même piège XML déjà rencontré plusieurs fois cette session, toujours le même remède
(remplacer `--` par une virgule ou reformuler).

**✅ Confirmé sur device (9 août 2026, téléphone ASUS, Android 11/API 30 -- le cas d'usage réel visé
par "toutes les versions", puisque le sélecteur système natif n'existe pas sur cette version)** :
aucun crash au lancement (`AppCompatActivity`/thème AppCompat), bascule FR -> EN -> FR confirmée en
direct sur le texte réel de l'écran Réglages (`Settings`/`Diagnostics`/`Connection`... puis
`Réglages`/`Diagnostics`/`Connexion`...), **et persistance vérifiée après un redémarrage complet**
(`am force-stop` puis relance, pas juste une re-visite d'écran) -- preuve que le stockage propre à
AppCompat (API <33) fonctionne réellement sur cet appareil, pas seulement en théorie d'après la doc.
Aucun `FATAL EXCEPTION`/ANR dans les logs sur toute la session de test.

**Comportement à noter, pas un bug** : `AppCompatDelegate.setApplicationLocales()` **recreate
l'Activity** pour appliquer la nouvelle langue (comportement documenté d'AppCompat) -- l'écran de
réglages ouvert au moment du changement se ferme donc et revient à l'écran principal (l'état
`remember { mutableStateOf(false) }` de `showDisplaySettings` dans `MainScreen` n'étant pas
`rememberSaveable`, il ne survit pas à la recréation). Repéré en test device : un bref gel/saut
d'écran perceptible au moment du changement de langue, diagnostiqué via logcat (`DisableSensor` +
`BufferQueue has been abandoned` + relance d'activité au même timestamp que le tap sur un chip de
langue) avant d'être confirmé comme un aller-retour Activity attendu plutôt qu'un plantage -- aucun
`FATAL EXCEPTION` associé. Cosmétique mineur (retour à l'écran principal au lieu de rester dans
Réglages), pas fonctionnellement bloquant ; pourrait être amélioré plus tard en sauvegardant l'écran
de réglages ouvert dans `rememberSaveable` si ça gêne à l'usage.

### 44. Minify/R8 en configuration release (point 8) -- ✅ activé et confirmé sur device

Resté désactivé depuis le début du projet (voir note originale ci-dessus, "attendre une vraie mise
en distribution") faute d'appareil pour vérifier que MediaPipe/ARCore/OSC/kotlinx.serialization
survivent à l'obfuscation sans casser de la réflexion. Ce blocage n'existe plus (deux appareils de
test opérationnels), traité dans le cadre de la préparation d'une première release publique.

**Règles -keep ajoutées** (`proguard-rules.pro`), une par bibliothèque à risque, chacune justifiée
plutôt que copiée en l'air : `com.google.mediapipe.**`/`com.google.protobuf.**` (réflexion + JNI),
`com.google.ar.core.**` (JNI natif, recommandation officielle ARCore), `com.illposed.osc.**`
(prudence après le crash réflexif déjà rencontré une fois sur cette bibliothèque, point 38), et le
jeu de règles officiellement recommandé par kotlinx.serialization pour R8 (sérialiseurs générés
`$serializer`/`Companion.serializer()` résolus par nom à l'exécution).

**Premier `assembleRelease` minifié : échec de compilation R8**, pas un crash silencieux -- deux
classes protobuf optionnelles (`CalculatorProfileProto$CalculatorProfile`,
`GraphTemplateProto$CalculatorGraphTemplate`, profiling/templates de graphe MediaPipe, jamais
utilisés en mode `LIVE_STREAM` simple) absentes de l'artefact `tasks-vision` mais référencées par le
code interne de MediaPipe. Résolu avec les `-dontwarn` exacts générés par R8 lui-même dans
`missing_rules.txt` -- pas une supposition.

**Une fois la compilation réussie, crash confirmé au tout premier lancement sur device** (téléphone
Android 11) : `ExceptionInInitializerError` dans `com.google.mediapipe.framework.Graph.<clinit>`,
cause racine `IllegalStateException: no caller found on the stack for: fk0`. Diagnostiqué sans
supposer -- déobfuscation de la trace via `app/build/outputs/mapping/release/mapping.txt` :
- `fk0` = `com.google.common.flogger.FluentLogger` (Guava Flogger, dépendance transitive de
  MediaPipe, utilisée pour son logging interne).
- Le reste de la pile (`ih0`, `fa1`, `ca1`, `fl`, `ca0`...) déobfusque en code parfaitement banal :
  `FaceLandmarkerHelper`, `MainViewModel.initializeTracking()`, et la machinerie standard des
  coroutines Kotlin (`BaseContinuationImpl`, `DispatchedTask`).

Cause réelle : Flogger détecte sa classe appelante en parcourant la pile d'appels à l'exécution --
l'optimiseur R8 (inlining/fusion de méthodes, une transformation par ailleurs correcte et sans
risque en soi) a réorganisé les frames de coroutine autour du point d'appel, privant Flogger d'une
frame "appelant" reconnaissable. Un `-keep` sur les classes Flogger elles-mêmes n'aurait rien changé
(le problème n'est pas leur renommage, mais la restructuration des frames *autour* d'elles).

**Correctif retenu** : `-dontoptimize` dans `proguard-rules.pro` -- désactive spécifiquement les
transformations de bytecode (inlining, fusion de classes) responsables de ce genre de rupture, tout
en gardant le shrinking (classes mortes supprimées, l'essentiel du gain de taille) et le renommage
(obfuscation). Nouveau build : **✅ confirmé sur device**, plus de crash, MediaPipe s'initialise
normalement (modèle chargé, graphe démarré, délégué GPU/OpenCL actif), écran Réglages fonctionnel,
palier OPTIMAL/ARCore actif (visage détecté pendant le test).

**Taille d'APK** : 127,6 Mo (debug) -> 61,4 Mo (release minifié, `-dontoptimize`) -- comparé à 59,0
Mo avec l'optimisation complète (avant le crash trouvé), confirmant que l'essentiel du gain vient du
shrinking, pas de l'optimisation désactivée ici.

**Signature locale** : `assembleRelease` sans les variables d'environnement de signature release
retombe désormais sur la clé debug (`app/build.gradle.kts`) -- seul moyen d'installer et de tester
un build release minifié en local sans les vraies clés ; le workflow de publication
(`release.yml`) a toujours les vraies variables, ce repli ne s'applique qu'en dev local.

**`isShrinkResources` ajouté le 9 août 2026** (revue globale, lint `NotShrinkingResources`) :
`isMinifyEnabled` ne suffisait pas à lui seul à retirer les ressources inutilisées de l'APK release,
les deux réglages sont indépendants. `./gradlew assembleRelease` toujours **✅ réussi** en local
après ajout (61,01 Mo, contre 61,4 Mo avant -- gain modeste mais réel). **Pas encore réinstallé sur
device** pour confirmer que rien d'utilisé dynamiquement n'a été retiré à tort (même famille de
risque que le crash Flogger ci-dessus, bien que le shrinking de ressources soit un mécanisme
différent du shrinking de code) -- à vérifier avant de le considérer aussi fiable que le reste de ce
bloc.

### 45. Fiabilisation du clignement des yeux (point 28) -- ✅ clos côté app, réglage fin restant côté VBridger

Suite complète de la discussion/investigation du point 28 (ouvert le 6 août 2026 sur une hypothèse
"lunettes", refermé le 9 août après plusieurs allers-retours de mesure réelle -- voir aussi le
point 43 pour le sélecteur de langue traité juste avant dans la même journée). Trois problèmes
réels trouvés et corrigés, un quatrième identifié comme n'étant pas un problème logiciel du tout.

**Hypothèse de départ (lunettes) : ni confirmée ni réfutée proprement.** Premier test réel
avec/sans lunettes (voir plus haut) : le blendshape `eyeBlinkLeft/Right` de MediaPipe répondait
correctement dans les deux cas -- aucun cas net où les lunettes cassaient la détection. L'hypothèse
d'origine a été abandonnée faute de reproduction, mais l'investigation a rebondi sur des problèmes
réels et mesurables trouvés en cours de route, sans rapport direct avec les lunettes.

**1. Fuite du blendshape d'un œil vers l'autre** -- mesuré : pendant un clin d'œil isolé,
`eyeBlinkRight`/`eyeBlinkLeft` (l'œil censé rester ouvert) montait quand même à ~0,26-0,49, une
plage qui chevauche les vraies fermetures (~0,43-0,49). Corrigé par `tracking/EyeBlinkCorrection.kt`
(EAR utilisé comme garde-fou, pas comme remplacement) -- **confirmé sur device**.

**2. Effondrement pendant une fermeture tenue plusieurs secondes** -- mesuré : le blendshape brut
restait stable en tenant l'œil fermé, mais la correction s'effondrait progressivement vers 0 après
quelques secondes (dérive du suivi de landmarks de MediaPipe sur une pose tenue, pas un vrai
réouverture). Corrigé par `EyeOpennessSmoother` (lissage asymétrique attaque rapide/relâchement
lent, 3s de constante de temps) -- **confirmé sur device**, la tenue résiste largement mieux
(effondrement repoussé de ~9s à ~25s+ avant le correctif ne suffise plus).

**3. Œil droit "à peine réactif" : pas un bug logiciel.** Sur toute une session de test (~17 min,
plusieurs positions de caméra essayées), le ratio droit/gauche restait constant à ~0,50-0,60 quelle
que soit la position -- signe d'un facteur constant, pas d'un problème de posture/angle. Hypothèse
de l'utilisateur (lampe LED au-dessus, ombre trop marquée) confirmée en ajustant l'éclairage : le
même relevé après correction montre un ratio ~0,90-1,10, l'asymétrie a disparu. **Aucune ligne de
code n'a résolu ce point-là** -- c'était un problème d'éclairage physique du poste de
l'utilisateur, pas de tracking.

**4. Formule VBridger `EyeOpenLeft/Right`** -- trouvaille de l'utilisateur en cours de route :
`.5 + (eyeBlink_X * -k) + (eyeWide_X * .8)`, qui n'atteint une fermeture complète que si
`eyeBlink_X >= 0,5/k`. Les coefficients ajustés manuellement pendant la session (`1.0` gauche,
`1.8` droite) avaient compensé un signal droit affaibli par le mauvais éclairage -- une fois la
lumière corrigée, ces coefficients sont probablement devenus trop agressifs. **Reste un réglage
fin côté VBridger, propre à la machine et à l'éclairage de l'utilisateur** -- hors du périmètre de
ce dépôt (aucun code de l'app n'agit sur cette formule).

**Outillage laissé en place** : `tracking/EyeAspectRatio.kt` et `tracking/EyeBlinkCorrection.kt`
(ce dernier incluant `AdaptiveEarFloor`, voir point 48) -- purs, testés
(`EyeAspectRatioTest`/`EyeBlinkCorrectionTest`/`AdaptiveEarFloorTest`, 5 + 10 + 6 tests JVM),
correction active en permanence dans le pipeline d'envoi. Le
log de diagnostic (`EarDiag`, `MainViewModel.EAR_DIAGNOSTIC_LOGGING`) désactivé par défaut
maintenant que l'investigation est close, mais laissé dans le code (une ligne à repasser à `true`)
pour un futur souci de clignement. La ligne EAR de `DiagnosticsScreen` reste affichée en
permanence (plus qualifiée de "temporaire" dans les chaînes) -- coût négligeable, utile sans repasser
par `adb logcat`.

**Méthode de travail notable pour ce point** : quasiment tout le diagnostic s'est fait par mesure
réelle sur device (logcat, avant/après précis) plutôt que par supposition -- y compris le rejet
propre de l'hypothèse de départ (lunettes) quand elle ne s'est pas reproduite, et la découverte que
la cause principale restante était physique (éclairage), pas logicielle. Cohérent avec la
convention "don't guess" déjà suivie ailleurs dans ce document.

### 46. Lissage générique des signaux (One Euro Filter) -- outil construit, intégration en discussion

Discussion ouverte après coup sur le point 28/45 : `EyeOpennessSmoother` (le lissage
attaque-rapide/relâchement-lent construit pour corriger la dérive pendant une fermeture tenue,
point 45) est un bricolage *spécifique* à ce seul symptôme. Question posée : un mécanisme plus
général aurait-il évité de le découvrir à la dure spécifiquement sur les yeux ?

**Comparatif fait avant de choisir** (pas de saut direct sur la première idée) : EMA fixe (déjà en
place, compromis figé lissage/retard), **One Euro Filter** (Casiez, Roussel & Vogel, CHI 2012 --
EMA à coupure adaptative selon la vitesse du signal), filtre de Kalman (estimateur d'état complet,
fusionne plusieurs mesures indépendantes d'une même quantité avec une incertitude explicite),
Savitzky-Golay (validé en capture faciale pour du lissage de blendshapes, mais introduit un vrai
retard -- pas purement streaming), et le très récent "Half Pound Filter" (arxiv 2602.21702, février
2026 -- vise plutôt le *blending entre états d'animation* que le débruitage continu, trop récent/pas
assez éprouvé pour ce projet).

**Choix retenu : One Euro Filter**, pour trois raisons qui se sont clarifiées en creusant :
1. **Nature du problème** -- la correction anti-fuite gauche/droite (`EyeBlinkCorrection.kt`)
   combine deux signaux indépendants (blendshape ML + EAR géométrique) : c'est un vrai cas de
   fusion, mais **local et pairwise** (un blendshape faible + son proxy géométrique), pas un besoin
   de modéliser des corrélations entre de nombreux canaux -- un Kalman centralisé serait
   disproportionné pour ce que le projet a réellement besoin de résoudre jusqu'ici.
2. **Futures cascades (points 15/16, langue/joues)** identifiées comme le même patron exact que le
   clignement (blendshape absent/peu fiable + proxy géométrique construit sur les landmarks) --
   chacune redeviendrait sa propre petite fusion locale, réutilisant le même outil de lissage
   générique en dessous, plutôt qu'un state Kalman unique et de plus en plus complexe à mesure que
   les cascades s'ajoutent.
3. **Formules composites futures (point 41, traduction ARKit -> paramètres VTube Studio)** : ce
   sont des combinaisons linéaires de plusieurs blendshapes distincts, pas des estimations
   multiples d'une même quantité -- ni Kalman ni filtre complémentaire n'ont de prise là-dessus.
   Ça a clarifié en revanche *où* lisser : à la source (chaque blendshape brut, une seule fois,
   juste après MediaPipe), pas dans chaque formule dérivée -- pour que tous les consommateurs
   (formules composites, cascades, envoi réseau, affichage) profitent d'un signal déjà propre au
   lieu de dupliquer/désynchroniser le lissage à chaque endroit.

**Hypothèse hors sujet écartée en cours de route** : une future détection de main (déclenchement
"wave" pour VTube Studio) a été envisagée comme cas d'usage -- conclusion : **complètement
décorrélée**, pas un cas de fusion (aucun second signal existant à combiner, modèle MediaPipe
distinct, sortie de nature différente -- événement discret plutôt que valeur continue à lisser).
Resservirait uniquement l'outil de lissage générique par réutilisation de code, pas par fusion de
données.

**Fait le 9 août 2026** : `tracking/OneEuroFilter.kt` (nouveau, pur, testé -- `OneEuroFilterTest.kt`,
8 tests) -- implémentation standard de l'algorithme (dérivée lissée à coupure fixe `dCutoff`,
coupure adaptative `minCutoff + beta·|dérivée lissée|`, coefficient de lissage `r/(r+1)` avec
`r = 2π·coupure·Δt`), basée sur le temps réel écoulé entre appels comme le reste des filtres du
projet.

**Intégré le même jour** : `EyeOpennessSmoother` retiré, `EyeBlinkCorrectionState` porte désormais
un `OneEuroFilter` par œil (`minCutoff = 0,5`, `beta = 5` -- point de départ raisonné à partir des
vitesses mesurées, pas encore affiné plus finement). **✅ confirmé sur device** avec un résultat net
: sur une tenue de ~9s (le même protocole de test que pour valider l'ancien lissage), le score
corrigé suit le brut quasiment sans écart du début à la fin -- alors que l'ancien `EyeOpennessSmoother`
montrait déjà une dégradation visible sur cette même durée. La fuite gauche/droite reste bien
atténuée en parallèle. Cohérent avec le raisonnement qui a motivé le choix : une dérive lente
(vitesse faible) reçoit une coupure basse donc un fort lissage résistant, un vrai clignement
(vitesse élevée) fait remonter la coupure et traverse sans retard.

**Reste ouvert, pas traité maintenant** : lissage général des 52 blendshapes bruts à la source
(au-delà des seuls yeux) -- plus gros chantier de réglage par catégorie de blendshape, décidé comme
prochaine étape mais volontairement pas fait dans la foulée.

### 48. Robustesse du clignement à l'angle de caméra (suite du point 45) -- ✅ confirmé sur device

Repris après coup sur le point 45/28 : en repositionnant le téléphone sous l'écran (la position la
plus pratique disponible chez l'utilisateur -- stable, ne masque pas l'écran, bonne latitude de
mouvement -- mais en légère contre-plongée), un clin d'œil droit réel et volontaire (brut ~0,55)
n'était corrigé qu'à hauteur de ~0,03-0,07 -- la correction anti-fuite écrasait l'essentiel d'un
vrai clignement. Mesuré sur device (`EarDiag`, 9 août 2026), pas supposé.

**Cause identifiée** : `EAR_CLOSED_REFERENCE` (0,10) est une constante mesurée face caméra. À
l'angle réel du téléphone, l'EAR d'un œil réellement fermé ne descend pas aussi bas (le contour de
la paupière reste plus visible sous cet angle) -- l'ouverture calculée contre cette référence fixe
restait donc au-dessus du seuil d'atténuation même œil fermé. Contrairement au problème de tenue
longue (point 45.2, la référence était trop basse *ponctuellement*, dérive de landmarks), ici c'est
la référence elle-même qui était fausse pour cet angle, en permanence.

**Décision explicite de l'utilisateur** : plutôt que de chercher un meilleur positionnement caméra
(comme pour l'éclairage, point 45.3), rendre la détection robuste côté code -- le téléphone ne
pourra pas toujours être dans une position idéale.

**Corrigé par `AdaptiveEarFloor`** (`tracking/EyeBlinkCorrection.kt`) : la référence "fermé" est
suivie dynamiquement par œil au lieu d'être fixe. Calée sur le score brut du blendshape (signal
indépendant de l'EAR) plutôt que sur l'EAR seul : quand ce score dépasse un seuil d'activité
(0,45), l'EAR minimum observé durant ce "épisode" de clignement est retenu, puis mélangé
partiellement (30 %) dans la référence une fois l'épisode terminé -- converge sur plusieurs
clignements plutôt que de sauter au dernier vu (protège d'un épisode isolé pollué, ex. une fuite
passée au-dessus du seuil par erreur). Retenir le minimum sur tout l'épisode (pas juste la dernière
frame) protège aussi du bug de tenue longue (point 45.2) : sur une fermeture tenue où l'EAR dérive
vers l'ouvert en cours d'épisode, c'est la valeur basse du tout début qui compte. La référence
"ouvert" reste fixe -- aucune mesure device n'indique qu'elle soit en cause.

**✅ confirmé sur device** (9 août 2026, log `EarDiag`) : série de clignements droits, puis gauches,
puis des deux yeux, à l'angle problématique. Résultat net et propre : la série droite recalibre la
référence droite (le score droit passe rapidement de fortement écrasé à quasi inchangé), la série
gauche fait de même côté gauche **sans réintroduire de fuite** (le score droit reste bas pendant que
seul le gauche cligne), et les clignements des deux yeux passent ensuite proprement et
symétriquement des deux côtés. Testé aussi en JVM (`AdaptiveEarFloorTest.kt`, 6 tests -- convergence
sur plusieurs épisodes, résistance à un épisode isolé pollué, non-régression sur la dérive de
landmarks en tenue longue) et bout-en-bout (`EyeBlinkCorrectionTest.kt`, nouveau test simulant
l'angle problématique).

### 49. Canal de release beta -- ✅ mis en place

Discuté en préparation du point 14 (checker de mise à jour in-app) : l'utilisateur teste très
souvent l'app via `adb install` de builds de dev, sans rapport avec les vraies Releases GitHub
(seul un tag `git tag`/`push` en déclenche une), mais voulait éviter par anticipation qu'un futur
outil de suivi de release (Obtainium ou similaire) le sollicite à chaque tag, y compris pour des
builds destinés à être testés plus largement plutôt qu'à être "LA" version recommandée.

**Mis en place** : un tag contenant `-beta` (convention `v0.3.0-beta.1`) suit exactement le même
`release.yml` qu'un tag stable, mais publie la Release GitHub avec le flag `prerelease: true`
(`softprops/action-gh-release`, `prerelease: ${{ contains(github.ref_name, '-beta') }}`) --
ignoré par défaut par les outils de suivi de release respectant ce flag, sans changer le
déclencheur ni le contenu du build. Documenté dans `README.md`, section "Publier une version".

Indépendant de la visibilité du dépôt (contrairement au point 14 ci-dessus, qui lui reste bloqué
tant que le dépôt est privé) -- ne dépend d'aucun accès à l'API GitHub, uniquement du comportement
de publication du workflow.

### 50. Journalisation uniformisée + partage utilisateur -- ✅ mis en place

Demande explicite de l'utilisateur, en préparation du passage du dépôt en public : des logs
"propres" pour la prod, un format cohérent d'un appel à l'autre, et une option pour qu'un
utilisateur (pas seulement le développeur) puisse fournir ses logs en cas de souci.

**Constat de départ** (audit, pas supposé) : `android.util.Log` appelé directement dans 8 fichiers
(~30 sites), niveaux utilisés de façon cohérente par observation (`e` = échec réel, `w` = repli qui
fonctionne quand même, `d` = traçage verbeux) mais sans convention écrite ; aucun log retiré en
release (ni `BuildConfig.DEBUG`, ni règle R8) ; aucune persistance, uniquement `logcat`.

**Conception, en plusieurs passes avec l'utilisateur avant de coder** :
- Convention Android officielle confirmée (javadoc d'`android.util.Log`) : VERBOSE < DEBUG < INFO <
  WARN < ERROR, les deux premiers ne devraient jamais tourner en release.
- Idée initiale (règle R8 `-assumenosideeffects`) écartée : son effet serait incertain avec
  `-dontoptimize` déjà en place (point 8, crash Flogger) -- remplacée par un gating explicite en
  code (`BuildConfig.DEBUG`), fiable sans dépendre du comportement de l'optimiseur.
- Risque RGPD discuté avant implémentation : IP locales (host/port des cibles réseau configurées)
  identifiées comme le seul contenu potentiellement sensible réellement présent dans les logs de
  l'app -- jamais de valeur de tracking facial au-dessus de DEBUG (donc jamais dans le fichier
  exportable), aucune transmission automatique (le fichier ne part que si l'utilisateur déclenche
  lui-même le partage).
- Masquage IP demandé explicitement par l'utilisateur (derniers octets -> `x`, actif hors build
  debug) plutôt que de compter uniquement sur la discrétion de l'utilisateur au moment de choisir un
  destinataire de partage.

**Implémenté** :
- `logging/LogLevel.kt`, `logging/LogFormatting.kt` (purs, testés -- `formatLogLine`,
  `maskIpAddresses`, `appendWithRotation`, `shouldPersist`) : format de ligne auto-suffisant
  (`AAAA-MM-JJ HH:mm:ss.SSS NIVEAU/TAG: message`), masquage IPv4 par regex appliqué une seule fois
  au niveau du sink (pas à chaque site d'appel), rotation par taille (~1,5 Mo) qui garde toujours
  les entrées les plus récentes.
- `logging/AppLog.kt` (sink réel, non testable en JVM -- même catégorie que le reste du glue code
  Android de ce projet) : `v`/`d` no-op hors `BuildConfig.DEBUG` (ni logcat, ni fichier), `i`/`w`/`e`
  toujours vers logcat + vers un fichier persistant (`filesDir/logs/app_logs.txt`) si le niveau
  configuré l'autorise, masquage IP appliqué hors debug uniquement.
- `AppSettingsStore.logLevel` : niveau minimal conservé dans le fichier, ERROR par défaut, réglable
  jusqu'à WARN/INFO -- VERBOSE/DEBUG volontairement absents des valeurs proposées.
- Partage : `FileProvider` scoppé à `filesDir/logs/` uniquement (`res/xml/file_paths.xml`), bouton
  "Partager les logs" dans le nouvel écran `LoggingSettingsScreen` (cinquième catégorie de
  `SettingsScreen`, voir point 21) -- **toujours visible, pas caché** derrière le déverrouillage
  debug de `DiagnosticsScreen`, son but étant justement de servir à n'importe quel utilisateur, pas
  seulement au développeur.
- Migration mécanique des ~30 sites d'appel existants vers `AppLog` (même signature, aucun
  changement de logique).

**Tests** : `LogFormattingTest.kt` (13 tests -- formatage, masquage IP simple/multiple/absent,
rotation avec/sans dépassement, non-troncature en ligne partielle, filtrage par niveau).

**✅ confirmé sur device** (9 août 2026) : écran de réglages fonctionnel, niveau réglable, partage
déclenché.

**Bug réel trouvé et corrigé pendant la validation device, pas supposé** : au niveau INFO, aucun
fichier ne se générait. Cause identifiée par instrumentation directe sur l'appareil (lecture du
fichier de préférences via `run-as`, log de diagnostic temporaire) : le seul log `INFO`
inconditionnel de toute l'app se trouvait dans `CameraController` (succès de connexion caméra) --
un chemin qui ne s'exécute jamais sur le device de test, qui tourne sur le palier OPTIMAL (ARCore),
un pipeline caméra entièrement différent. Corrigé en ajoutant un log `INFO` équivalent dans
`ArCoreHeadPoseTracker` (démarrage de session). Deuxième bug apparenté trouvé au passage : le tout
premier log de démarrage pouvait partir avant que `AppLog.setMinimumPersistedLevel()` n'ait reçu sa
première valeur depuis `AppSettingsStore.logLevel` (le collecteur réactif de `MainViewModel.init{}`
n'a aucune garantie de gagner la course contre le premier log de démarrage) -- corrigé une bonne
fois en synchronisant le niveau de façon anticipée/bloquante (`appSettingsStore.logLevel.first()`)
au tout début d'`initializeTracking()`, avant tout log de démarrage, plutôt que par patch au cas par
cas à chaque nouveau site d'appel.

**Couverture étendue** (9 août 2026, demande explicite de l'utilisateur -- la couverture initiale
étant surtout des cas d'échec) : quatre nouveaux logs `INFO`, dont deux confirmés sur device dans la
foulée de la correction ci-dessus (marqueur de démarrage avec version d'app, palier de tracking
retenu et pourquoi) et deux implémentés mais pas encore exercés en conditions réelles (connexion/
déconnexion réussie des trois émetteurs réseau -- nécessite une vraie cible PC ; changement de débit
réellement appliqué par le throttling thermique -- nécessite un appareil qui chauffe réellement,
même limite que le point 37 de ce document).

### 51. Cohérence miroir tête / regard / blendshapes -- ✅ corrigé et confirmé sur device

Repris d'une remarque de l'utilisateur en pleine discussion du point 15 (langue tirée) : "on a un
souci de cohérence sur ce qu'on envoie, les yeux gauche/droite sont inversés par rapport à la
rotation de la tête (la tête est en mode 'miroir' pas les yeux)". Investigation menée avec le même
protocole que le point 28 (marqueurs start/stop, mouvements tenus, logs de diagnostic temporaires)
avant de toucher au code -- deux faux départs (mesures trop bruitées, tracking pas encore démarré
au début d'un test) avant d'obtenir des données propres.

**Cause racine confirmée** : `RotationMath.toEulerDegrees` inversait le yaw de la tête
(`-atan2(...)`) depuis l'origine du projet -- une validation empirique passée avait bien confirmé
que ça "marchait" sur device, mais validait en réalité un comportement **mirroré** sans que ce soit
jamais nommé ni voulu comme tel. Aucun autre blendshape gauche/droite (`eyeBlinkLeft/Right`,
`eyeLookIn/OutLeft/Right`, et par extension tous les autres blendshapes latéralisés du catalogue --
sourcils, bouche, joues, nez) n'était mirroré. Donc : la tête suivait une convention, tous les
blendshapes latéralisés en suivaient une autre -- cohérent chacun avec soi-même isolément, mais pas
entre eux dès qu'on combine un mouvement de tête et un blendshape latéralisé (typiquement une
expression complexe, exactement ce qui avait mis la puce à l'oreille de l'utilisateur).

**Confirmé sur device en 2 temps** :
1. D'abord via les logs (`RotationDiag`/`EarDiag`, diagnostics temporaires) : tête tournée à droite
   + clin d'œil droit combinés montre bien `eyeBlinkRight` qui monte (pas une inversion gauche/
   droite du blendshape lui-même) -- mais sans conclusion possible sur le sens de la tête, faute de
   VBridger connecté pendant ce test.
2. Puis directement sur l'avatar VBridger (VBridger reconnecté) : confirmé explicitement --
   "la rota visage est en mirroir, le sens du regard et le côté de clignement ne le sont pas".

**Décision de conception de l'utilisateur** : plutôt que de mirrorer tout le reste pour rejoindre la
tête (rafistolage), la sortie **native** de l'app ne doit pas être mirorée -- comportement
anatomique natif, avec une option de mode miroir explicite (le "point 51" du backlog personnel,
"option d'inversion des blendshapes", pointait déjà vers ce même chantier).

**Implémenté** :
- `RotationMath.toEulerDegrees` : yaw remis en formule brute (non inversée) -- retiré en TDD, avec
  des matrices de rotation connues construites à la main (pas recopiées de l'implémentation), voir
  `RotationMathTest.kt`. Nouvelle fonction `RotationMath.mirrorEulerDegrees()` : inverse yaw ET roll,
  préserve le pitch -- conséquence géométrique d'une vraie réflexion gauche-droite (pas un choix
  arbitraire), le roll n'était jusque-là jamais mirroré même à l'époque de l'ancien comportement.
- `tracking/Mirroring.kt` (nouveau, pur, testé) : `mirrorBlendshapeName()`/`mirrorBlendshapes()`
  échangent les scores entre paires gauche/droite, dérivées du suffixe du nom (pas une liste tenue à
  la main) -- couvre automatiquement toute paire présente dans `BlendshapeCatalog`, test dédié qui
  parcourt le vrai catalogue pour attraper une régression si une future paire est mal nommée.
  `mirrorFaceTrackingResult()` : mirrore tête + blendshapes + regard par œil (échangés entre eux ET
  chacun mirroré individuellement) en un seul appel, point d'entrée unique pour `MainViewModel`.
- `AppSettingsStore.mirrorModeEnabled` : **activé par défaut** -- décidé après coup avec
  l'utilisateur, l'aperçu caméra/mesh à l'écran étant déjà mirroré en permanence (indépendant de ce
  réglage, voir `FaceMeshOverlay`) et la convention VTubing la plus répandue penchant déjà vers un
  comportement miroir par défaut ; désactivable pour la sortie native/anatomique.
- Appliqué en tout dernier dans `MainViewModel.handleTrackingResult()`, après la correction EAR
  (qui reste basée sur l'identité anatomique réelle des landmarks, indépendante de ce réglage) --
  jamais l'un sans l'autre.
- UI : interrupteur "Mode miroir" dans `DisplaySettingsScreen`, volontairement **pas lié** à
  l'aperçu caméra/mesh (déjà mirroré en permanence, sert au confort de positionnement de
  l'utilisateur -- un besoin différent de la convention des données envoyées, les lier risquerait de
  désorienter quelqu'un qui veut un aperçu naturel avec une sortie anatomique).

**Tests** : `RotationMathTest.kt` (2 nouveaux -- `mirrorEulerDegrees` inverse yaw/roll pas le pitch,
appliqué deux fois redonne l'original -- plus le test existant sur la rotation Y mis à jour pour la
formule brute), `MirroringTest.kt` (8 tests -- échange de noms, blendshapes sans paire inchangés,
`FaceTrackingResult` complet, couverture du vrai catalogue, involution).

**✅ confirmé sur device le 10 août 2026, les deux modes** : natif (anatomique) validé "nickel" par
l'utilisateur avec VBridger connecté ; mode miroir revalidé séparément par l'utilisateur comme
reproduisant l'ancien comportement, cette fois cohérent sur toute la chaîne.

**Vérification nocturne GitHub (Claude Code cloud, routine planifiée)** -- créée le 6 août 2026,
tous les jours à 19:00 UTC (~21h Paris en été, ~20h en hiver -- le cron reste fixe en UTC). Lecture
seule : delta de commits `origin/main`, état CI, PR ouvertes (recoupées avec la convention "local
d'abord" de ce document), issues ouvertes, et un audit léger façon "regard extérieur/repo public"
(recherche grossière de secrets committés par erreur). Ne committe ni ne pousse jamais rien --
seulement un rapport. Le point 31 ci-dessus est sa première trouvaille concrète. Gérée depuis
https://claude.ai/code/routines (id `trig_01AnKSNF9qEMC3hom1eegjow`), pas depuis ce dépôt.

**Run du 8 août 2026 (soir)** : dépôt sain, aucune divergence local/origin, CI verte, aucun secret
détecté sur les 25 derniers commits. Deux trouvailles actionnées manuellement le lendemain (le
sandbox de la routine n'a pas d'accès `gh`/API pour agir directement, seulement pour observer) :
- **PR #5** (Dependabot, MediaPipe 0.10.35 -> 1.0.0) fermée sans merge -- déjà reproduite localement
  depuis le 7 août (`mediapipeTasksVision = "1.0.0"`), convention "local d'abord" de ce document.
- **Issue #3** (finaliser la fusion ARCore) fermée -- confirmée fonctionnelle sur device depuis le
  6 août (point 3/13), l'issue décrivait un état antérieur ("non testé sur device", "à ne pas
  merger avant vérification") entièrement dépassé.
- **Issue #1** (thermal throttling + résolution caméra par palier) : couvrait deux sujets distincts
  -- vérifié directement dans `CameraController.kt` avant de fermer quoi que ce soit (pas fié à la
  suggestion initiale de la routine, "probablement à fermer"), seul le premier (throttling
  thermique, point 34) était fait. **Scindée** (8 août 2026, sur demande) : issue #1 fermée,
  nouvelle **issue #7** créée pour la partie encore ouverte (résolution caméra adaptée au palier --
  `ImageAnalysis.Builder()` ne fixe aucune résolution cible selon `COMPATIBLE`/`STANDARD`/`OPTIMAL`),
  renvoi croisé entre les deux dans les commentaires de fermeture/création.
