# AndroidMoCap — Suivi des tests unitaires

Document vivant, mis à jour à chaque modification (workflow TDD : le test avant le code). Il liste, module par module, ce qui est couvert par des tests unitaires JVM purs (`app/src/test/`, exécutés via `./gradlew testDebugUnitTest`, aucun appareil/émulateur requis), et pourquoi le reste ne l'est pas encore.

Deux grandes catégories reviennent tout du long. Le code **logique pur** (maths, sélection de palier, formatage de message, catalogue statique...) ne dépend d'aucune classe Android et se teste directement en JVM -- c'est le cœur de cette première passe, et la priorité de toute nouvelle fonction de ce type. Le code **lié au framework Android** (capteurs, caméra, MediaPipe natif, DataStore, Compose) a besoin d'un appareil/émulateur (test instrumenté) ou de Robolectric pour simuler l'environnement Android -- non couvert pour l'instant, la raison précise est indiquée à chaque fois plutôt qu'un simple "non testé".

## tracking/

| Fichier | Élément couvert | Test(s) | Statut |
|---|---|---|---|
| `RotationMath.kt` | `multiply`, `transpose`, `rotation3x3FromColumnMajor4x4`, `toEulerDegrees`, `composeCalibratedEuler` | `RotationMathTest.kt` | ✅ Couvert -- c'est la classe la plus délicate de l'app (origine de l'inversion d'axes déjà rencontrée), priorité n°1. |
| `TrackingTier.kt` | `TrackingTierSelector.select()` | `TrackingTierSelectorTest.kt` | ✅ Couvert (fonction pure `DeviceCapabilities -> TierConfig`). |
| `BlendshapeCatalog.kt` | `all`, `byCategory` | `BlendshapeCatalogTest.kt` | ✅ Couvert (cohérence structurelle : 52 entrées, pas de doublon, recouvrement catégorie/liste complète). |
| `FaceLandmarkerHelper.kt` | `computeEyeGazeDegrees()` (extraite en `internal`, pure) | `FaceLandmarkerHelperTest.kt` | ✅ Couvert. |
| `FaceLandmarkerHelper.kt` | `setup()`, `tryCreateLandmarker()`, `detectAsync()`, `onLiveStreamResult()` (dont l'extraction de `faceLandmarks()`) | -- | ❌ Non testable en JUnit pur : enrobe le moteur natif MediaPipe (`FaceLandmarker.createFromOptions`, délégué GPU/CPU réel) -- nécessite un appareil/émulateur. `FaceLandmarkerResult` ne peut pas non plus être construit à la main en test (fabrique `create()` package-private côté MediaPipe). Test instrumenté à envisager si un bug survient ici. |
| `FaceTrackingResult.kt` | Data class (`FaceTrackingResult`, `BlendshapeScore`) | -- | ➖ Pas de logique propre, sert de fixture aux autres tests (voir `IFacialMocapSenderTest`, `FaceLandmarkerHelperTest`). |

## network/

| Fichier | Élément couvert | Test(s) | Statut |
|---|---|---|---|
| `IFacialMocapSender.kt` | `toIFacialMocapName()`, `buildMessage()` (extraites en `internal`, pures) | `IFacialMocapSenderTest.kt` | ✅ Couvert -- format déjà responsable d'un bug silencieux (nommage eyeSquint) avant cette session. |
| `IFacialMocapSender.kt` | `startListening()`, `send()` (partie socket), `stopListening()` | -- | ❌ Dépend d'un vrai `DatagramSocket`/thread réseau -- nécessite un test instrumenté ou un socket factice. Non prioritaire, la partie à risque (le format du message) est déjà couverte séparément. |
| `VmcOscSender.kt` | `buildBundle()` (extraite `internal`, pure) | `VmcOscSenderTest.kt` | ✅ Couvert -- regroupe les messages `/Val` + `/Apply` en un seul `OSCBundle` (voir rapport technique, point 4) au lieu d'un paquet UDP par blendshape. |
| `VmcOscSender.kt` | `send()` (partie socket), `updateTarget()`, `connect()` | -- | ❌ Dépend d'un vrai `OSCPortOut`/UDP -- nécessite un test instrumenté ou un socket factice. La partie à risque (le contenu du bundle) est déjà couverte séparément. |
| `NetworkUtils.kt` | `getLocalIpAddress()` | -- | ❌ Dépend des interfaces réseau réelles de l'appareil (`NetworkInterface.getNetworkInterfaces()`) -- environnement-dépendant, faible valeur à mocker pour une simple lecture d'IP locale. |

## camera/

| Fichier | Élément couvert | Test(s) | Statut |
|---|---|---|---|
| `CameraController.kt` | `rotatedDimensions()` (extraite `internal`, pure) | `CameraControllerTest.kt` | ✅ Couvert. |
| `CameraController.kt` | `bindImageAnalysis()`, `bindPreview()`, `processFrame()`, `acquirePooledBitmap()`, `releaseFrame()` | -- | ❌ Dépend de CameraX (`ProcessCameraProvider`, `ImageProxy`) et `android.graphics.Bitmap/Canvas/Matrix` -- nécessite un test instrumenté (CameraX fournit un `CameraXConfig` de test, mais ça reste un test sur device/émulateur) ou Robolectric avec ses shadows graphiques. Le pool de bitmaps a déjà causé un bug réel (comparaison par identité `MPImage`, corrigé) : bon candidat à un test instrumenté si l'app grossit encore sur ce point. |

## capabilities/

| Fichier | Élément couvert | Test(s) | Statut |
|---|---|---|---|
| `DeviceCapabilities.kt` | `DeviceCapabilities.looksHighEnd` | Exercée indirectement par `TrackingTierSelectorTest.kt` | ⚠️ Couverte en pratique (les tests de sélection de palier construisent des `DeviceCapabilities` et vérifient le palier qui en résulte), mais pas de test dédié isolé sur `looksHighEnd` seul. À ajouter si cette heuristique devient plus complexe. |
| `DeviceCapabilities.kt` | `DeviceCapabilityDetector.detect()`, `isThermalThrottling()` | -- | ❌ Dépend de `ArCoreApk`, `ActivityManager`, `PowerManager` -- nécessite Robolectric ou un test instrumenté. `isThermalThrottling()` n'est de toute façon appelée nulle part encore dans le code (voir le rapport technique, section 3) : pas de test tant qu'elle n'est pas branchée. |

## sensors/

| Fichier | Élément couvert | Test(s) | Statut |
|---|---|---|---|
| `DeviceOrientationTracker.kt` | `rotationDeltaMatrix()`, `snapshotRotationMatrix()`, `start()`/`stop()` | -- | ❌ Dépend de `SensorManager`/`SensorEvent` réels pour être exercées de bout en bout. La partie mathématique (multiplication/transposition) qu'elles délèguent à `RotationMath` est, elle, déjà couverte par `RotationMathTest`. Faible valeur à mocker `SensorManager` juste pour ce glue code. Depuis le point 5 (rapport technique), `start()`/`stop()` sont appelés par `MainViewModel` sur `ON_START`/`ON_STOP` du cycle de vie plutôt qu'une seule fois à l'initialisation -- même remarque, glue code non testable sans Robolectric. |
| `IconOrientationTracker.kt` | `onOrientationChanged()` | -- | ❌ Wrapper direct d'`OrientationEventListener` (Android), aucune logique propre à isoler. |
| `BatteryMonitor.kt` | `onReceive()` | -- | ❌ Wrapper direct de `BroadcastReceiver`/`ACTION_BATTERY_CHANGED`, nécessite un test instrumenté pour simuler un vrai broadcast. |

## settings/

| Fichier | Élément couvert | Test(s) | Statut |
|---|---|---|---|
| `AppSettingsStore.kt`, `ConnectionSettingsStore.kt` | Getters/setters DataStore (dont `faceMeshOverlayEnabled`) | -- | ❌ Dépendent de `Context`/DataStore Preferences -- nécessitent Robolectric (avec un `PreferenceDataStore` en mémoire) ou un test instrumenté. Bon candidat pour une prochaine itération : la logique (lire une clé, valeur par défaut si absente, écrire) est simple mais entièrement non couverte aujourd'hui. |

## ui/ (Compose)

| Fichier | Élément couvert | Test(s) | Statut |
|---|---|---|---|
| `MainViewModel.kt` | Orchestration (caméra, capteurs, réseau, DataStore, minuteur éco...) | -- | ❌ Dépend d'`AndroidViewModel`/`Application` et de tous les composants ci-dessus. Volontairement laissé "fin" : sa seule logique non triviale (composition de la calibration) a été extraite dans `RotationMath.composeCalibratedEuler`, testée séparément -- stratégie à reconduire pour toute nouvelle logique ajoutée au ViewModel. |
| `MainViewModel.kt` | `initializeTracking()` idempotent (`trackingInitialized`, point 6 du rapport technique) | -- | ❌ Un test pertinent demanderait d'instancier un `MainViewModel` réel (Application, ArCoreApk, ActivityManager...) et d'appeler `initializeTracking()` deux fois -- nécessite Robolectric. Garde à une ligne, risque de régression faible ; à couvrir si Robolectric est introduit pour d'autres besoins (ex. `AppSettingsStore`). |
| `MainScreen.kt` | Rattachement d'`IconOrientationTracker`/`BatteryMonitor` au cycle de vie (`ON_START`/`ON_STOP`, point 5 du rapport technique) | -- | ❌ Composable Compose observant un vrai `Lifecycle` -- nécessite un test d'UI Compose ou Robolectric pour simuler des transitions de cycle de vie. Vérifié manuellement (voir note ci-dessous). |
| `MainScreen.kt`, `MainHud.kt`, `SettingsScreen.kt`, `BlendshapeSelectionScreen.kt`, `BlendshapePanel.kt`, `PowerSaveOverlay.kt`, `LowBatteryAlert.kt`, `FaceMeshOverlay.kt`, `Theme.kt` | Composables | -- | ❌ Hors périmètre des tests unitaires JVM : nécessitent soit des tests d'UI Compose (`androidx.compose.ui.test`, instrumentés ou Robolectric), soit une vérification manuelle. Sujet séparé des "TUs" si souhaité plus tard. |
| `LandmarkProjection.kt` | `toScreenPoint()` (projection normalisé -> écran, avec/sans miroir) | `LandmarkProjectionTest.kt` | ✅ Couvert -- seule partie mathématique de l'overlay du mesh (voir `FaceMeshOverlay.kt` ci-dessus, qui l'utilise mais reste lui-même hors périmètre JUnit). |

## Racine

| Fichier | Élément couvert | Test(s) | Statut |
|---|---|---|---|
| `MainActivity.kt`, `MoCapApplication.kt` | Cycle de vie Android | -- | ❌ Points d'entrée Android purs, testables uniquement via test instrumenté. |

## Bilan actuel

8 fichiers de test, tous en JVM pur (aucun appareil/émulateur requis) : `RotationMathTest`, `TrackingTierSelectorTest`, `BlendshapeCatalogTest`, `FaceLandmarkerHelperTest`, `IFacialMocapSenderTest`, `CameraControllerTest`, `VmcOscSenderTest`, `LandmarkProjectionTest`. Ils couvrent l'intégralité de la logique mathématique et de formatage identifiée comme la plus fragile (rotation/calibration, mapping de noms de blendshapes, regard des yeux, sélection de palier, dimensions de rotation caméra, regroupement des messages OSC, projection écran du mesh), en s'appuyant sur quelques extractions de fonctions pures (visibilité `internal`) qui ne changent aucun comportement.

## Overlay du mesh de tracking (option activable)

Nouvelle fonctionnalité : superposition optionnelle des 478 points du mesh facial MediaPipe sur l'aperçu caméra, activable/désactivable depuis les réglages (persisté). Suit exactement le même principe que le reste de l'app : la seule partie mathématique -- la projection d'un point normalisé vers l'espace écran, avec effet miroir pour correspondre à l'aperçu -- a été écrite en TDD (`LandmarkProjectionTest.kt` avant `LandmarkProjection.kt`) et isolée du Composable qui l'utilise (`FaceMeshOverlay.kt`, non testable en JUnit pur). L'aperçu 3D d'un avatar générique reste une piste séparée, non implémentée, à traiter plus tard.

Point d'attention pour la vérification manuelle sur device : la projection suppose que le calque occupe tout l'écran au même rapport largeur/hauteur que l'image analysée par MediaPipe, sans compenser un éventuel recadrage de `PreviewView` -- si les points ne se superposent pas exactement au visage à l'écran (léger décalage ou mise à l'échelle), c'est le premier endroit à regarder.

## Points 5 et 6 (rapport technique) -- pas de test dédié, raison assumée

Contrairement aux points 1, 2 et 4, les points 5 (capteurs/batterie pas alignés sur le cycle de vie) et 6 (`initializeTracking()` pas protégé contre un double appel) ne contenaient aucune logique pure à extraire -- uniquement du glue code Android (observation de `Lifecycle`, garde sur un booléen d'instance). Implémentés directement, sans étape rouge/verte JUnit au préalable, avec la raison documentée ci-dessus plutôt qu'un silence. Vérification manuelle recommandée après relance de l'app : mettre l'app en arrière-plan quelques secondes puis revenir dessus, et confirmer que le tracking reprend normalement (capteurs bien redémarrés) sans redémarrage complet.

## Règle pour la suite (TDD)

Pour toute nouvelle fonctionnalité ou correction : 1) écrire d'abord le test qui décrit le comportement attendu (il doit échouer), 2) écrire le code minimal pour le faire passer, 3) mettre à jour ce document (nouvelle ligne dans le tableau du module concerné, ou nouvelle section). Si une fonction contient de la logique non triviale mais est mélangée à du code Android (comme c'était le cas pour la calibration ou le regard des yeux), l'extraire d'abord en fonction pure (`internal` si elle ne fait pas partie de l'API publique) avant de l'écrire -- c'est ce qui a permis de couvrir la quasi-totalité de la logique à risque de cette app sans passer par Robolectric ni test instrumenté.
