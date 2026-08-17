# AndroidMoCap

*🇬🇧 English: [README.md](README.md) · 🇫🇷 Français : [README_FR.md](README_FR.md) · 🇨🇳 简体中文: [README_ZH.md](README_ZH.md)*

> ⚠️ **機械翻訳について**：このドキュメントはAIによる自動翻訳であり、人間やネイティブスピーカーによる
> 校正は行われていません。内容に疑問がある場合は英語版原文を参照してください：[README.md](README.md)。

Android スマートフォンでの顔面モーションキャプチャを、ローカルネットワーク経由でリアルタイムに
Blender、Unity、VBridger、あるいは VTube Studio に直接ストリーミングします。スマートフォンが単体で
動作する顔面トラッカーになります：サードパーティアプリ不要、クラウドサービス不要、フロントカメラと
PC へ送信される blendshape ストリームだけで完結します。

現在メンテナンスされている MeowFace の Android 版代替が存在しない（開発が放棄され、内部で使われていた
トラッキングライブラリも非推奨になった）という状況から生まれたプロジェクトです。この分野のどのアプリ
でも完全には解消できない Android 特有の制約——専用の深度センサー（iPhone の TrueDepth のような）が
無いこと——により、ソフトウェアの品質にかかわらず到達可能な精度には上限があります。

個人プロジェクトとして、一人の開発者により開発・保守されており、現在も活発に開発が進められています。

## 主な機能

- **デバイス能力に基づく最適なパイプラインの自動選択**（GPU/CPU、RAM、コア数、ARCore 対応状況）——
  設定不要で、ハイエンドからエントリーレベルまで自動的に適応し、GPU デリゲートの初期化に失敗した場合は
  自動的に CPU にフォールバックします。
- MediaPipe Face Landmarker による **52 種類の ARKit ブレンドシェイプ**、および視線方向の推定
  （MediaPipe がネイティブには提供しないため、目に関するブレンドシェイプから再構成）。
- **3 種類のネットワーク出力**：VMC/OSC プロトコル（Blender、Unity）、iFacialMocap/UDP プロトコル
  （VBridger）、そして VTube Studio 独自の Plugin API を介した直接統合（VTube Studio は入力として
  VMC/OSC を受け付けません）。
- カウントダウン付きの **オンデマンドなニュートラルポーズ校正**。
- スマートフォンの向きに関わらず読みやすい最小限の HUD、詳細な設定項目（表示するブレンドシェイプの
  選択、低バッテリー閾値、省電力モード、478 点トラッキングメッシュのデバッグオーバーレイ）。
- **省電力モード**：一定時間操作がないと画面を最小輝度にし、カメラプレビューを非表示にしますが、
  トラッキングとデータ送信はバックグラウンドで継続します——スマートフォンをユーザーから離れた場所に
  置く長時間の配信セッション向けです。
- **半自動アップデートチェック**：最新の GitHub Releases タグと比較し、直接リンクを提示します——
  サイレントインストールはせず、通知のみです。
- **ブレンドシェイプごとの重み調整**：設定 > ブレンドシェイプ から個々のブレンドシェイプ（反応が
  強すぎる/弱すぎるものなど）を微調整できます。

## 動作要件

- Android 11（API 30）以降。
- フロントカメラを備えた**実機**——Android エミュレーターはトラッキングに使用できるカメラ映像を
  提供しません。
- スマートフォンと受信側 PC は**同一ローカル Wi-Fi ネットワーク**上にある必要があります。

## インストール

本アプリは Play ストアでは配信されていません。最新の APK を [GitHub Releases](../../releases) から
ダウンロードし、直接インストールしてください。インストール時に Android が「提供元不明のアプリ」の
警告を表示しますが、これはストア以外で配布される APK では通常のことで、インストール設定でその都度
許可すれば問題ありません。

## PC 側の接続方法

**Blender / Unity**：VMC 互換のアドオン/パッケージを使用し、同じポート（デフォルトは `39539`、
アプリ側で変更可能）をリッスンするよう設定してください。

**VTube Studio**：VMC/OSC をネイティブには受け付けません——設定にそのようなオプションはありません。
本アプリは VTube Studio 独自の Plugin API を介した直接統合を提供します（接続設定で「VTube Studio」を
選択し、PC の IP とポート（デフォルト 8001）を入力）：初回接続時に VTube Studio 側で認証ポップアップが
表示され、その後モデルを動かすには VTube Studio のパラメータエディタで作成されたパラメータを一度
手動でマッピングする必要があります。

**VBridger**：アプリの設定で iFacialMocap プロトコルを選択し、スマートフォンに表示される IP を指定
して VBridger 側の手順に従ってください——アプリへは VBridger 側から接続しに来るため、スマートフォン
側で IP を入力する必要はありません。

## プライバシーとネットワーク

本アプリは設定で選択したローカルネットワーク上の宛先とのみ通信します——サードパーティサービスや
テレメトリは一切なく、受信側 PC へのこの任意の送信以外にデータが送られることはありません。

**ログ**：ローカルにのみ保存され（アプリ専用のファイルで、自動的に送信されることはありません）、
デフォルトのログレベルは「エラー」で、設定 > ログ で変更可能です。技術情報（エラー、接続状態）や
設定されたローカル IP アドレスを含む場合がありますが——顔トラッキングのデータが含まれることは
決してありません。IP アドレスは開発ビルド以外では自動的にマスクされます。「ログを共有」ボタンで
このファイルを送信できます（問題報告時など）——これは完全にユーザー自身の判断で行われ、送信先も
ユーザーが選択します。

## ソースからのビルド

1. **MediaPipe モデルをダウンロード**（必須、サイズが大きいためバージョン管理対象外）し、
   `app/src/main/assets/face_landmarker.task` に配置します：
   `https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task`
   リンクが変更されている場合は、公式の
   [Face landmark detection guide for Android](https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker/android)
   ページ（「Model」セクション）から取得し直してください。
   - **任意**：実験的な舌出し検出機能（ステージ3、デフォルトで無効）向けの 2 つ目のモデル、
     `app/src/main/assets/image_embedder.tflite`：
     `https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_small/float32/latest/mobilenet_v3_small.tflite`。
     このファイルがなくてもアプリは正常にビルド・動作します——この実験的機能のみが必要とします。
2. Android Studio でフォルダを開きます（`File > Open`）。最初の Gradle 同期で AGP、Kotlin、および
   `gradle/libs.versions.toml` に記載された ARCore/CameraX/MediaPipe/JavaOSC/nv-websocket-client/
   kotlinx.serialization の各依存関係がダウンロードされます。
3. **実機**でビルド・実行してください（「動作要件」参照）——トラッキングのテストにエミュレーターは
   使用できません。

## プロジェクト構成

```
app/src/main/java/com/guyiome/androidmocap/
  MainActivity.kt              カメラ権限 + Compose エントリーポイント
  capabilities/                デバイス能力検出（ARCore、GPU、RAM、温度）
  tracking/                    ティア選択 + MediaPipe Face Landmarker ラッパー + 回転計算
  camera/                      CameraX 制御（フロントカメラ -> MPImage、ビットマッププール）
  sensors/                     スマートフォンの向き、HUD アイコン、バッテリー
  network/                     OSC/UDP 送信（VMC）、UDP 送信（iFacialMocap）、WebSocket（VTube Studio Plugin API）
  settings/                    設定の永続化（DataStore）
  ui/                          ViewModel + Compose 画面（HUD、設定、メッシュオーバーレイ）
```

## テスト

純粋な JVM 単体テストスイート（Android/Robolectric 非依存）が `app/src/test/` にあります。関数単位の
詳細と、意図的にカバーされていない箇所の理由は `docs/AndroidMoCap_unit_tests.md`（英語）を
参照してください。実行方法：

```
./gradlew testDebugUnitTest
```

## ドキュメント

以下のドキュメントは現在、英語版/フランス語版のみ提供されています：

- `docs/AndroidMoCap_functional_spec.md` -- アプリが現在提供している機能（ユーザー向け）。
- `docs/AndroidMoCap_technical_spec.md` -- アーキテクチャ、キャプチャパイプライン、ネットワーク
  プロトコル、非機能要件。
- `docs/AndroidMoCap_unit_tests.md` -- テストカバレッジの詳細。

## ロードマップ

現在も未着手の主な項目：

- 実験的な頬膨らませ検出（`cheekPuff`）——既に実装済みの舌出し検出と同じ系統の機能で、まだ設計段階
  です。
- 大画面（タブレット）でのシステム画面回転への設定画面の対応。
- 既存のブレンドシェイプごとの重み調整に加えた、調整可能なスムージング。

## お問い合わせ

質問・フィードバック・バグ報告は Discord `guy_iome`（本プロジェクト専用に作成したアカウント）まで。

## ライセンス

[PolyForm Shield 1.0.0](https://polyformproject.org/licenses/shield/1.0.0) ライセンスの下で配布
されています（`LICENSE` 参照。日本語訳は参考情報として [LICENSE_JA.md](LICENSE_JA.md) にあります
が、相違がある場合は英語原文が優先されます）：商用利用を含め自由に使用できますが、本ソフトウェア自体と
競合する製品の構築には使用できません。厳密な意味（OSI の定義）での「オープンソース」ライセンスでは
ありません——個人利用に限りソースコードの閲覧・改変が可能ですが、競合製品として自由に再配布することは
できません。

## コントリビュート

プルリクエストを開く前に `docs/CONTRIBUTING_JA.md`（日本語訳、参考情報）をお読みください——
コントリビュートには貢献者ライセンス契約への同意が必要です（`docs/CLA_JA.md`、日本語訳、参考情報。
法的効力を持つのは英語版の `docs/CLA.md` です）。

## リリースの公開（メンテナー向け）

署名は環境変数（`RELEASE_KEYSTORE_BASE64`、`RELEASE_KEYSTORE_PASSWORD`、`RELEASE_KEY_ALIAS`、
`RELEASE_KEY_PASSWORD`）経由で設定され、ローカルまたは GitHub Actions の secrets から読み込まれます
——リポジトリにコミットされることはありません。タグをプッシュすると公開処理がトリガーされます：

```
git tag v0.2.0
git push origin v0.2.0
```

ワークフロー（`.github/workflows/release.yml`）は APK をビルドし（MediaPipe モデルのダウンロードを
含む）、署名した上で、APK を添付した GitHub Release を作成します。

**Beta チャンネル**：`-beta` を含むタグ（例：`v0.3.0-beta.1`）は全く同じ流れで処理されますが、
その Release は GitHub の *prerelease* として公開されます——更新追跡ツール（Obtainium など）は
インストール側で明示的に有効化しない限り、デフォルトではこれをスキップします。「推奨アップデート」
として表示させずにテストビルドを共有したい場合に便利です。

```
git tag v0.3.0-beta.1
git push origin v0.3.0-beta.1
```
