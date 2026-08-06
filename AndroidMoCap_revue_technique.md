# AndroidMoCap — Revue technique et pistes d'optimisation

*Passage complet sur l'ensemble du code (caméra, tracking, capteurs, réseau, UI, réglages), à date du 5 août 2026.*

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

### 10. `README.md` décrit une UI qui n'existe plus

Le README (section "Builder sur un appareil physique") mentionne un "aperçu diagnostic en haut à gauche" et un "champ IP sur l'écran principal" -- toute cette UI a été remplacée depuis par le bandeau d'icônes minimal + l'écran de réglages dédié. La section "Prochaines étapes" liste aussi des choses déjà faites (persistance DataStore) à côté de choses toujours vraies (ARCore, throttling thermique) et d'une idée jamais mentionnée ailleurs (lissage temporel des blendshapes, One Euro Filter) -- à trier/rafraîchir avant qu'il induise en erreur quelqu'un qui découvrirait le projet.

### 11. Pas de configuration de signature release, versionnage figé

`app/build.gradle.kts` n'a pas de bloc `signingConfigs` : un `assembleRelease` produirait un APK non signé, impossible à installer tel quel. `versionCode`/`versionName` sont toujours à `1`/`0.1.0-poc`, alors que l'app a beaucoup changé depuis. Sans conséquence tant que le build reste local (`installDebug`), mais bloquant dès qu'il s'agit de partager un APK -- voir la discussion sur le déploiement.

## Priorités suggérées (mise à jour)

Le point 9 est un correctif ciblé, sûr à faire sans pouvoir tester sur device (aucun impact visuel, juste un travail évité). Le point 10 est une simple mise à jour de documentation. Le point 3 reste un investissement plus lourd (ARCore phase 2 surtout) à ne déclencher que si le besoin se confirme. Le point 11 (signature + versionnage) ne devient prioritaire qu'au moment de distribuer l'app au-delà d'un usage local -- voir la section déploiement.
