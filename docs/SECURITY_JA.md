# セキュリティポリシー

*🇬🇧 English: [SECURITY.md](SECURITY.md) · 🇫🇷 Français : [SECURITY_FR.md](SECURITY_FR.md) · 🇨🇳 简体中文: [SECURITY_ZH.md](SECURITY_ZH.md)*

> ⚠️ **機械翻訳について**：このドキュメントはAIによる自動翻訳であり、人間やネイティブスピーカーに
> よる校正は行われていません。参考情報としてのみご利用ください。法的効力を持つのは英語原文
> [SECURITY.md](SECURITY.md) です。

## 脆弱性の報告について

セキュリティ上の脆弱性は、公開の issue としてではなく、GitHub の
[プライベート脆弱性報告](https://github.com/guyiome/AndroidMoCap/security/advisories/new)
（Security タブ → "Report a vulnerability"）を通じて非公開で報告してください。

本プロジェクトは一人のメンテナーによって管理されています——ベストエフォートでの対応となり、正式な
SLA はありません。可能な限り速やかに受領を確認し、修正の進捗をお伝えします。

## 対象範囲

AndroidMoCap は、スマートフォンが接続しているローカルネットワーク（Wi-Fi）上でのみ通信します。
リモートサーバーと通信することは一切なく、アプリ自体がアウトバウンド接続を開始することも、更新確認
のための GitHub Releases への任意アクセスを除いてありません。深刻度を評価する際はこの点にご留意
ください：ここでのネットワーク関連の問題を悪用するには、攻撃者が同じローカルネットワーク上に既に
存在している必要があります。

セキュリティレビューにおいて特に関連性の高い箇所：

- `network/IFacialMocapSender.kt` —— パッシブな UDP リスナー（iFacialMocap/VBridger のワイヤー
  プロトコル）。実装元のサードパーティプロトコルに合わせ、設計上認証なし。
- `network/VTubeStudioSender.kt` / `VTubeStudioProtocol.kt` —— VTube Studio 自身の Plugin API に
  対する、トークンベースの認証ハンドシェイクを伴う WebSocket クライアント。
- `network/VmcOscSender.kt` —— アウトバウンドのみの OSC/UDP、リスニングソケットなし。
- `logging/AppLog.kt` / `LogFormatting.kt` —— ローカルのログファイル。IP アドレスはデバッグビルド
  以外ではマスクされ、`DEBUG` レベルより上で顔トラッキングデータがログに記録されることはありません。

## サポート対象バージョン

サポートされるのは最新の公開リリースのみです——長期メンテナンスブランチはありません。
