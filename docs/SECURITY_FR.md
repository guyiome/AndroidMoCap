# Politique de sécurité

*🇬🇧 English: [SECURITY.md](SECURITY.md) · 🇨🇳 简体中文: [SECURITY_ZH.md](SECURITY_ZH.md) · 🇯🇵 日本語: [SECURITY_JA.md](SECURITY_JA.md)*

## Signaler une faille

Merci de signaler toute faille de sécurité en privé via la fonctionnalité GitHub de
[signalement privé de vulnérabilité](https://github.com/guyiome/AndroidMoCap/security/advisories/new)
(onglet Security → "Report a vulnerability"), et non via une issue publique.

Ce projet est maintenu par une seule personne — la réponse se fait au mieux, sans SLA formel.
J'accuserai réception dès que possible et tiendrai informé pendant la résolution.

## Portée

AndroidMoCap ne communique que sur le réseau local (Wi-Fi) auquel le téléphone est connecté. Il ne
parle jamais à un serveur distant, et l'app elle-même n'initie jamais de connexion sortante en
dehors d'une vérification optionnelle des Releases GitHub pour les mises à jour. À garder en tête
pour évaluer la sévérité : exploiter un problème réseau ici suppose un attaquant déjà présent sur
le même réseau local.

Zones les plus pertinentes pour une revue de sécurité :

- `network/IFacialMocapSender.kt` — écoute UDP passive (protocole iFacialMocap/VBridger), sans
  authentification par conception, conformément au protocole tiers qu'il implémente.
- `network/VTubeStudioSender.kt` / `VTubeStudioProtocol.kt` — client WebSocket avec une poignée de
  main d'authentification par token contre l'API Plugin propre à VTube Studio.
- `network/VmcOscSender.kt` — OSC/UDP sortant uniquement, aucun socket d'écoute.
- `logging/AppLog.kt` / `LogFormatting.kt` — fichier de log local ; les adresses IP sont masquées
  hors build de développement, et aucune donnée de suivi du visage n'est jamais journalisée
  au-dessus du niveau `DEBUG`.

## Versions supportées

Seule la dernière version publiée est supportée — il n'y a pas de branche de maintenance long
terme.
