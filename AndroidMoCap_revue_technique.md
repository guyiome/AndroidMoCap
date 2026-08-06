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
| 3 / 13 | Fusion ARCore (palier `OPTIMAL`) | Codée sur `feature/arcore-fusion`, pas mergée, pas testée sur device |
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

Points 1, 2, 4, 5, 6, 7, 9, 10, 11, 12, 22, 23 : traités ou correctement à l'état de backlog priorisé
(point 23), rien à corriger côté statut. Note : ce fichier ne contient pas (encore) les sections
détaillées des points 15/16/19/20 -- leur rédaction complète existe uniquement sur la copie du
document présente sur `feature/arcore-fusion`, jamais fusionnée dans celle-ci.

## Vue d'ensemble

L'app est structurée proprement en couches : `camera` (CameraX), `tracking` (MediaPipe + maths de rotation), `sensors` (gyroscope, orientation d'icônes, batterie), `network` (VMC/OSC et iFacialMocap/UDP), `settings` (DataStore) et `ui` (Compose MVVM avec un seul `MainViewModel`/`MainUiState`). L'architecture globale est saine et déjà éprouvée sur les points durs (calibration matricielle, paliers adaptatifs, repli GPU→CPU). Les points ci-dessous sont classés du plus impactant au plus cosmétique.

## 1. Le goulot d'étranglement principal : allocation d'un bitmap à chaque frame

`CameraController.processFrame()` crée un nouveau `Bitmap.createBitmap(...)` à **chaque frame caméra**, copie le buffer dedans, puis — si l'image n'est pas déjà à l'endroit — en crée un **second** via une rotation matricielle (`Bitmap.createBitmap(bitmapBuffer, ..., matrix, true)`, avec filtrage bilinéaire). À 30-60 fps ça représente potentiellement plus d'une centaine de bitmaps/seconde alloués puis jetés, tout ça sur l'unique thread `cameraExecutor` qui bloque aussi l'arrivée de la frame suivante pendant ce travail. C'est très probablement la plus grosse source de pression GC, de chauffe et de latence de tout le pipeline, et le point le plus rentable à corriger : réutiliser un bitmap tampon (recréé seulement si les dimensions changent) et, si la rotation est fixe pour toute la session (elle l'est : l'app est verrouillée portrait), la calculer une fois plutôt qu'à chaque frame, ou utiliser `Bitmap.Config.ARGB_8888` avec `postRotate` en filtrage `false` (nearest) puisqu'un léger crénelage n'affecte pas la détection MediaPipe.

## 2. Le palier de tracking ne throttle rien

`TrackingTierSelector` définit un `targetFps` par palier (60 / 30 / 20) mais cette valeur n'est **utilisée nulle part** : `ImageAnalysis` tourne au rythme natif du capteur quel que soit le palier, et `COMPATIBLE` (délégué CPU, appareil d'entrée de gamme) traite donc autant de frames qu'`OPTIMAL`. Sur les appareils visés par ce palier, c'est justement là que ça chauffe et consomme le plus. Un throttling simple (ignorer la frame si moins de `1000/targetFps` ms se sont écoulées depuis la dernière analyse acceptée, dans `processFrame` ou dans l'`analyzer`) donnerait un vrai gain sans toucher à MediaPipe.

## 3. Fonctionnalités documentées mais jamais branchées

`DeviceCapabilityDetector.isThermalThrottling()` existe, avec un commentaire explicite ("à appeler périodiquement pendant la capture... pour déclencher une rétrogradation dynamique de palier") mais n'est appelé nulle part dans le code : aucune surveillance thermique ni rétrogradation de palier en cours de session. Dans le même esprit, `TierConfig.useArCorePose` est bien calculé pour le palier `OPTIMAL` mais la pose ARCore elle-même n'est branchée nulle part (le commentaire l'indique : "phase 2, pas encore actif") — la résolution de capture n'est pas non plus adaptée au palier (`ImageAnalysis.Builder()` ne fixe aucune résolution cible, CameraX choisit son défaut quel que soit `COMPATIBLE` ou `OPTIMAL`). Rien d'urgent ici, mais ce sont des leviers d'optimisation "gratuits" déjà prévus dans l'architecture et non exploités.

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

### 13. Fusion ARCore (palier `OPTIMAL`, phase 2) -- conflit d'architecture identifié, design en cours

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

### 23. Localisation complète de l'UI -- priorité, juste après la revue de la branche main en cours

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

## Priorités suggérées (mise à jour)

Le point 9 est un correctif ciblé, sûr à faire sans pouvoir tester sur device (aucun impact visuel, juste un travail évité). Le point 10 est traité (README réécrit). Le point 3/13 (ARCore phase 2) est maintenant mieux cerné mais reste un investissement lourd et risqué à finir "à l'aveugle" -- la suite (bascule caméra CameraX→ARCore) attend un accès device. Le point 11 (signature + versionnage) est traité (point 12). Le point 8 (minify) reste pour plus tard, une fois les tests device de nouveau possibles. Le point 14 (vérification de mise à jour) est en backlog, pas urgent. Le point 15 (navigation retour) est traité. Le point 16 (dénomination du mode iFacialMocap) est traité. Le point 17 (CI build/tests sur PR) est traité. Le point 27 (projection du mesh overlay) est traité, corrigé sans dépendre d'un nouvel accès device (le bug avait déjà été reproduit par la photo fournie).

**Ordre de priorité actuel (mis à jour) :** 1) revue/merge des PR #5 et #6 et test de l'état actuel de la branche `main` dès qu'un accès device est possible ; 2) point 23 (localisation complète de l'UI), juste derrière -- peut démarrer (passe d'extraction) avant même la fin du point 1, seule la vérification visuelle finale dépend du device.

**Suivi PR #5/#6 (6 août 2026)** : PR #6 (Kotlin 2.3.20→2.4.10, risque jugé faible -- même clé de
version pilote le plugin Compose, voir §CI ci-dessus) mergée sur GitHub (commit `030aae5`) suite à
validation explicite de l'utilisateur. PR #5 (MediaPipe tasks-vision 0.10.35→1.0.0, première version
stable, risque non vérifié) volontairement laissée ouverte, en attente de test device avant tout
merge -- consigne explicite de ne pas y toucher sans validation.

⚠️ Point d'attention pour le prochain push depuis la machine locale de l'utilisateur : ce merge a été
fait directement sur GitHub, côté serveur, sans passer par ce dépôt local (aucun accès réseau
`api.github.com`/push depuis ce sandbox). `origin/main` a donc avancé d'un commit que ni ce
sandbox ni la machine locale de l'utilisateur ne possèdent encore, alors que la locale est par
ailleurs très en avance (plusieurs commits non poussés, voir historique de ce document). Le prochain
`git push` depuis la machine de l'utilisateur sera très probablement rejeté (non-fast-forward) et
demandera un `git pull --rebase` (ou merge) avant de pouvoir pousser.

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
