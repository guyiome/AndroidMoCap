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
| `FaceLandmarkerHelper.kt` | `setup()`, `tryCreateLandmarker()`, `detectAsync()`, `onLiveStreamResult()` | -- | ❌ Non testable en JUnit pur : enrobe le moteur natif MediaPipe (`FaceLandmarker.createFromOptions`, délégué GPU/CPU réel) -- nécessite un appareil/émulateur. Test instrumenté à envisager si un bug survient ici. |
| `FaceTrackingResult.kt` | Data class (`FaceTrackingResult`, `BlendshapeScore`) | -- | ➖ Pas de logique propre, sert de fixture aux autres tests (voir `IFacialMocapSenderTest`, `FaceLandmarkerHelperTest`). |

## network/

| Fichier | Élément couvert | Test(s) | Statut |
|---|---|---|---|
| `IFacialMocapSender.kt` | `toIFacialMocapName()`, `buildMessage()` (extraites en `internal`, pures) | `IFacialMocapSenderTest.kt` | ✅ Couvert -- format déjà responsable d'un bug silencieux (nommage eyeSquint) avant cette session. |
| `IFacialMocapSender.kt` | `startListening()`, `send()` (partie socket), `stopListening()` | -- | ❌ Dépend d'un vrai `DatagramSocket`/thread réseau -- nécessite un test instrumenté ou un socket factice. Non prioritaire, la partie à risque (le format du message) est déjà couverte séparément. |
| `VmcOscSender.kt` | `send()`, `updateTarget()` | -- | ❌ Dépend d'un vrai `OSCPortOut`/UDP. Pas de logique pure à extraire pour l'instant (boucle triviale) -- si le regroupement en `OSCBundle` (piste d'optimisation du rapport technique) est implémenté, en extraire la construction pour la tester comme `IFacialMocapSender.buildMessage`. |
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
| `DeviceOrientationTracker.kt` | `rotationDeltaMatrix()`, `snapshotRotationMatrix()` | -- | ❌ Dépend de `SensorManager`/`SensorEvent` réels pour être exercées de bout en bout. La partie mathématique (multiplication/transposition) qu'elles délèguent à `RotationMath` est, elle, déjà couverte par `RotationMathTest`. Faible valeur à mocker `SensorManager` juste pour ce glue code. |
| `IconOrientationTracker.kt` | `onOrientationChanged()` | -- | ❌ Wrapper direct d'`OrientationEventListener` (Android), aucune logique propre à isoler. |
| `BatteryMonitor.kt` | `onReceive()` | -- | ❌ Wrapper direct de `BroadcastReceiver`/`ACTION_BATTERY_CHANGED`, nécessite un test instrumenté pour simuler un vrai broadcast. |

## settings/

| Fichier | Élément couvert | Test(s) | Statut |
|---|---|---|---|
| `AppSettingsStore.kt`, `ConnectionSettingsStore.kt` | Getters/setters DataStore | -- | ❌ Dépendent de `Context`/DataStore Preferences -- nécessitent Robolectric (avec un `PreferenceDataStore` en mémoire) ou un test instrumenté. Bon candidat pour une prochaine itération : la logique (lire une clé, valeur par défaut si absente, écrire) est simple mais entièrement non couverte aujourd'hui. |

## ui/ (Compose)

| Fichier | Élément couvert | Test(s) | Statut |
|---|---|---|---|
| `MainViewModel.kt` | Orchestration (caméra, capteurs, réseau, DataStore, minuteur éco...) | -- | ❌ Dépend d'`AndroidViewModel`/`Application` et de tous les composants ci-dessus. Volontairement laissé "fin" : sa seule logique non triviale (composition de la calibration) a été extraite dans `RotationMath.composeCalibratedEuler`, testée séparément -- stratégie à reconduire pour toute nouvelle logique ajoutée au ViewModel. |
| `MainScreen.kt`, `MainHud.kt`, `SettingsScreen.kt`, `BlendshapeSelectionScreen.kt`, `BlendshapePanel.kt`, `PowerSaveOverlay.kt`, `LowBatteryAlert.kt`, `Theme.kt` | Composables | -- | ❌ Hors périmètre des tests unitaires JVM : nécessitent soit des tests d'UI Compose (`androidx.compose.ui.test`, instrumentés ou Robolectric), soit une vérification manuelle. Sujet séparé des "TUs" si souhaité plus tard. |

## Racine

| Fichier | Élément couvert | Test(s) | Statut |
|---|---|---|---|
| `MainActivity.kt`, `MoCapApplication.kt` | Cycle de vie Android | -- | ❌ Points d'entrée Android purs, testables uniquement via test instrumenté. |

## Bilan actuel

7 fichiers de test, tous en JVM pur (aucun appareil/émulateur requis) : `RotationMathTest`, `TrackingTierSelectorTest`, `BlendshapeCatalogTest`, `FaceLandmarkerHelperTest`, `IFacialMocapSenderTest`, `CameraControllerTest`. Ils couvrent l'intégralité de la logique mathématique et de formatage identifiée comme la plus fragile (rotation/calibration, mapping de noms de blendshapes, regard des yeux, sélection de palier, dimensions de rotation caméra), en s'appuyant sur quelques extractions de fonctions pures (visibilité `internal`) qui ne changent aucun comportement.

## Règle pour la suite (TDD)

Pour toute nouvelle fonctionnalité ou correction : 1) écrire d'abord le test qui décrit le comportement attendu (il doit échouer), 2) écrire le code minimal pour le faire passer, 3) mettre à jour ce document (nouvelle ligne dans le tableau du module concerné, ou nouvelle section). Si une fonction contient de la logique non triviale mais est mélangée à du code Android (comme c'était le cas pour la calibration ou le regard des yeux), l'extraire d'abord en fonction pure (`internal` si elle ne fait pas partie de l'API publique) avant de l'écrire -- c'est ce qui a permis de couvrir la quasi-totalité de la logique à risque de cette app sans passer par Robolectric ni test instrumenté.
