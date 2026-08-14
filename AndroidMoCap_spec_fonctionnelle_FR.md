# AndroidMoCap — Spécification fonctionnelle

*🇬🇧 English version: [AndroidMoCap_spec_fonctionnelle.md](AndroidMoCap_spec_fonctionnelle.md)*

*Document de référence sur l'état courant du périmètre fonctionnel. Décrit ce que l'app fait
aujourd'hui, pour un lecteur non technique -- pas un journal, pas d'historique de décisions, pas de
feuille de route. Pour l'architecture et les choix d'implémentation, voir
`AndroidMoCap_spec_technique_FR.md`.*

## 1. Présentation générale

AndroidMoCap transforme un téléphone Android en tracker de capture de mouvement facial pour
VTubing : la caméra frontale capture le visage, l'app calcule un jeu de blendshapes (coefficients
d'expression faciale) et les transmet en direct, sur le réseau local, à un logiciel receveur sur PC
qui anime un avatar.

**Public visé** : streamers/VTubers utilisant un téléphone Android comme solution de tracking
facial, en alternative aux solutions iOS (iFacialMocap, FaceMotion3D) qui bénéficient d'un capteur
de profondeur dédié (TrueDepth) absent des téléphones Android.

**Ce que l'app n'est pas** : ni un afficheur d'avatar (contrairement à VTube Studio Android), ni un
service cloud (tout le traitement et l'échange de données reste local à l'appareil et au réseau
Wi-Fi local), ni une app store-first (distribution actuelle uniquement via GitHub Releases).

## 2. Cas d'usage principal

Un streamer installe l'app sur un téléphone Android, le positionne face à lui (main, support, pied
de table...), lance l'app, calibre une pose neutre, choisit le protocole et la cible réseau
correspondant à son logiciel VTuber, puis démarre son stream. Le téléphone tourne en continu
pendant toute la session, généralement sans qu'on y touche ni qu'on le regarde directement -- c'est
l'avatar affiché côté PC qui sert de retour visuel.

## 3. Périmètre fonctionnel

### 3.1 Capture et tracking

- Sélection automatique du meilleur pipeline disponible selon l'appareil (palier `COMPATIBLE`,
  `STANDARD` ou `OPTIMAL`), basée sur le support ARCore, la classe de performance officielle
  Android, le nombre de cœurs CPU et la RAM totale -- aucune configuration manuelle requise.
  Repli automatique du délégué GPU vers CPU si l'initialisation GPU échoue. Au palier `OPTIMAL`,
  la pose de tête vient d'ARCore Augmented Faces plutôt que de MediaPipe (les blendshapes restent
  toujours calculés par MediaPipe) -- repli automatique et silencieux sur le palier `STANDARD` si
  ARCore s'avère indisponible à l'usage malgré un appareil qui le supporte a priori.
- Calcul de 52 blendshapes au format ARKit à partir de la caméra frontale (MediaPipe Face
  Landmarker). Deux blendshapes (`tongueOut`, `cheekPuff`) ne sont pas restitués de façon fiable
  par ce modèle -- limitation du modèle lui-même, pas un bug de l'app. `tongueOut` bénéficie d'une
  détection expérimentale alternative (voir ci-dessous) ; `cheekPuff` reste sans mitigation.
- **Détection expérimentale de la langue tirée** (`tongueOut`) -- cascade dédiée (porte d'ouverture
  de bouche → comparaison à une calibration personnelle par embedding), activable dans
  "Fonctionnalités expérimentales" (§3.4). Une fois la calibration personnelle
  effectuée, la valeur est envoyée aux protocoles réseau (§3.3), pas seulement affichée localement
  -- rangée en "Expérimental" du fait d'un risque résiduel de faux positif isolé.
- Estimation de la direction du regard (par œil, pitch/yaw) reconstruite à partir des blendshapes
  directionnels (`eyeLookUp/Down/In/OutLeft/Right`), MediaPipe ne fournissant pas cette donnée
  nativement.
- Estimation de la rotation de la tête à partir de la matrice de transformation faciale fournie par
  MediaPipe.
- Overlay optionnel (désactivé par défaut) du mesh de tracking complet (478 points) superposé à
  l'aperçu caméra, à but de diagnostic visuel.

### 3.2 Calibrage

Calibrage manuel de la pose neutre, déclenché à la demande depuis le bandeau principal, avec un
compte à rebours de 5 secondes avant capture -- laisse le temps à l'utilisateur de reprendre une
expression neutre face à la caméra.

**Détection d'anomalie de calibrage** : le bouton de calibrage se teinte en rouge si la pose de
tête semble avoir dérivé depuis le dernier calibrage (visage au repos mais pose qui ne revient pas
près de zéro, ou perte de détection du visage suivie d'une redétection) -- purement informatif,
aucune action automatique, se résout uniquement par un nouveau calibrage explicite.

### 3.3 Connectivité réseau

Trois protocoles de sortie, mutuellement exclusifs (un seul actif à la fois, choix dans les
réglages) :

- **VMC/OSC** -- destiné à Blender, Unity (pas VTube Studio, qui ne reçoit pas ce protocole en
  entrée). L'app envoie les données vers une IP/port PC saisis manuellement dans les réglages.
- **Protocole compatible iFacialMocap/UDP** -- destiné à VBridger. Affiché "UDP / VBridger" dans
  l'interface (le nom "iFacialMocap" reste en mention secondaire pour rester trouvable par qui
  cherche ce terme précis, mais l'app ne se connecte pas à cette application tierce, elle implémente
  seulement un protocole compatible). L'app écoute passivement ; c'est le logiciel PC qui vient se
  connecter, à partir de l'IP du téléphone affichée dans les réglages -- aucune saisie manuelle côté
  téléphone pour ce chemin.
- **API Plugin VTube Studio** -- intégration directe, en contournant VMC/OSC que VTube Studio ne
  reçoit pas. IP/port (8001 par défaut) saisis manuellement, comme pour VMC. Popup d'autorisation à
  accepter dans VTube Studio à la première connexion (jeton ensuite mémorisé, avec nouvelle demande
  automatique s'il est révoqué entre-temps, plus un bouton "Oublier le jeton" en secours). Une fois
  connecté, les paramètres créés doivent être mappés une fois par l'utilisateur dans l'éditeur de
  paramètres de VTube Studio pour animer un modèle Live2D -- l'app ne peut pas le faire à sa place.

Le téléphone et le PC receveur doivent être sur le même réseau Wi-Fi local dans les trois cas.

### 3.4 Interface utilisateur

- **Bandeau HUD minimal**, affiché en permanence sur l'aperçu caméra plein écran : indicateur de
  détection du visage, bouton connexion/déconnexion, bouton de calibrage (avec anneau de compte à
  rebours), accès aux réglages. Chaque icône pivote sur elle-même pour rester lisible quel que soit
  l'angle auquel le téléphone est tenu ou posé.
- **Écran de réglages**, organisé en menu à 5 catégories (chacune son propre écran, retour vers
  le menu par flèche standard ou bouton/geste retour système) : Diagnostics (lecture seule -- palier
  actif, délégué GPU/CPU, visage détecté, latence d'inférence, état de calibration), Connexion
  (type + cible réseau, seul le sous-bloc du type choisi affiché), Affichage & confort (accès à
  l'écran de sélection des blendshapes affichés + sa persistance, overlay du mesh de tracking, mode
  économie d'énergie, seuil d'alerte batterie), Fonctionnalités expérimentales (interrupteur de
  détection de la langue tirée + accès à son écran de calibration dédié, voir ci-dessus), Journalisation
  (niveau de log, partage du fichier de logs).
- **Écran de sélection des blendshapes affichés** : catalogue complet des 52 blendshapes ARKit,
  groupés par catégorie (sourcils, yeux, joues, nez, mâchoire, bouche, langue), avec recherche.
  Affiche la valeur en direct de chaque blendshape coché sur l'écran principal. Icône d'avertissement
  discrète à côté des blendshapes connus pour être mal restitués par MediaPipe (`jawForward`,
  `jawLeft`, `jawRight`, `mouthDimpleLeft/Right`, `cheekPuff`, `tongueOut`) -- informatif, n'empêche
  pas la sélection. Non conservée d'une session à l'autre par défaut, mais persistance activable
  dans les réglages (Affichage & confort).
- **Navigation** : chaque écran superposé (réglages et ses 5 catégories, sélection des blendshapes)
  se ferme via une flèche retour standard, le bouton retour matériel, ou le geste de balayage
  système (predictive back, Android 13+) -- les trois déclenchent la même action.
- **Langue** : interface disponible en français (défaut) et anglais. Deux façons de la choisir :
  le sélecteur système par app (réglages Android, Android 13+ seulement), ou un sélecteur **dans
  l'app** (Affichage & confort > "Langue de l'app" -- "Suivre le système" / "Français" / "English"),
  qui fonctionne sur toutes les versions d'Android et mémorise le choix automatiquement d'un
  lancement à l'autre. Changer la langue depuis ce sélecteur ferme l'écran de réglages en cours
  (retour à l'écran principal) le temps d'appliquer le changement. Les noms de blendshapes ARKit
  (`jawOpen`, `mouthSmileLeft`...) restent en anglais technique quelle que soit la langue choisie --
  ce sont des identifiants de protocole, pas du texte d'affichage.

### 3.5 Gestion de l'énergie

- **Mode économie d'énergie** : après un délai d'inactivité configurable (aucun toucher de
  l'écran), assombrit l'écran au minimum et masque l'aperçu caméra affiché -- le tracking et
  l'envoi réseau continuent normalement en arrière-plan. Sortie immédiate au moindre toucher.
  Pensé pour les sessions longues où le téléphone est posé loin de l'utilisateur.
- **Alerte batterie faible** : overlay visuel (icône pulsante) quand la batterie descend sous un
  seuil configurable et que le téléphone n'est pas en charge.
- **Throttling thermique dynamique** : le débit d'analyse cible est réduit de moitié si l'appareil
  chauffe en cours de session (icône discrète dans le bandeau HUD), et remonte automatiquement dès
  que la chauffe retombe -- aucune action requise de l'utilisateur.

### 3.6 Distribution

Distribution en dehors du Play Store, via GitHub Releases (APK signé, publié automatiquement à
chaque tag de version). Pas de vérification de mise à jour intégrée à l'app.

## 4. Contraintes fonctionnelles transverses

- **Vie privée / réseau** : aucune communication autre que le flux volontaire vers la cible choisie
  par l'utilisateur, sur le réseau local -- pas de télémétrie, pas de service tiers.
- **Matériel requis** : un appareil physique avec caméra frontale ; l'émulateur Android ne fournit
  pas de flux caméra exploitable pour le tracking.
- **Version Android minimale** : Android 11 (API 30).
- **Licence** : usage libre du logiciel, y compris commercial, à l'exception de la construction
  d'un produit concurrent -- voir `LICENSE`.

## 5. Glossaire rapide

- **Blendshape** : coefficient (0 à 1) représentant l'intensité d'une expression faciale
  élémentaire (sourire, clignement, ouverture de mâchoire...), au format standardisé ARKit (52
  coefficients).
- **VMC (Virtual Motion Capture)** : protocole réseau basé sur OSC, standard de facto pour
  transmettre des données de mocap à des logiciels VTuber (Blender, Unity, VSeeFace... -- pas VTube
  Studio, qui utilise sa propre API Plugin, voir §3.3).
- **Palier de tracking** : niveau de pipeline choisi automatiquement selon les capacités de
  l'appareil (`COMPATIBLE` < `STANDARD` < `OPTIMAL`), déterminant le délégué (CPU/GPU), le débit
  cible et la source de pose de tête utilisée.
- **Perfect Sync** : convention (issue de VSeeFace/VBridger) désignant un rig d'avatar capable
  d'exploiter l'intégralité des 52 blendshapes ARKit, au-delà des expressions basiques.
