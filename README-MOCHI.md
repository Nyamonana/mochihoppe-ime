# もちエクIME（Android）

Sumire Lite v1.7.128 (`be9cccd7e6fef176667a12eb66fa6fe64380c9db`) を基にしたMIT派生版。
Android 7.0以上。日本語変換は端末内、画像検索だけサイトへ接続する単一APKです。
上流のnamespaceは差分を小さくするため維持し、applicationIdは `com.mochihoppe.explorer.ime` に変更しています。

## 操作

- キーボード上の「画像」→検索欄→日本語入力・変換→「検索」。検索ボタンを押すまでリクエストしません。
- 検索結果のタップでURLだけを挿入します。アルバムはタップ後に1枚を選択します。GIFサムネは静止画です。
- ☆は端末内のお気に入り（200件まで）。最近使用は50件。サイトのブックマーク・使用数とは連携しません。
- 保存した画像は「画像」→検索欄直下の「お気に入り」で表示します。☆ボタンは画像下のタイトル行に置き、サムネを隠しません。
- 地球アイコンを押すと日本語テンキーと日本語QWERTY（ローマ字入力）を切り替えます。選択は保存され、キーボードを閉じた後も復元します。検索欄の日本語入力にも使えます。「配列」ボタンはありません。
- スマホ縦画面は通常3列（狭い画面は2列）、広い画面は4列。タイトルは画像の下に2行表示します。
- 検索・GIFタブへの切替は検索しません。検索欄が空なら検索ボタンで最新投稿を取得します。
- 案内文の常設行は設けず、画像一覧をタブの直下に表示します。通信中は一覧上のスピナー、エラーなどは一時的なToastで表示します。
- 物理キーボードやMuMuでも画面キーボードを併用する場合は、Androidの「物理キーボード接続時に画面キーボードも表示」をONにします。この設定を尊重して画面キーボードを表示します。
- 初期接続先は `https://pub.mochi-hoppe-explorer.com`。サイトにIME APIが未デプロイなら検索は利用できません。

## ビルド（Windows）

Android SDK 36、JDK17以上（確認環境はAndroid StudioのJBR21）、Gradle wrapperを使用します。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleLiteStandardDebug :mochi-input-lab:assembleDebug
.\gradlew.bat :app:testLiteStandardDebugUnitTest --tests=com.kazumaproject.markdownhelperkeyboard.mochi.MochiSafetyTest --tests=com.kazumaproject.markdownhelperkeyboard.mochi.MochiUiTest --tests=com.kazumaproject.markdownhelperkeyboard.mochi.MochiNetworkTest --tests=com.kazumaproject.markdownhelperkeyboard.ime_service.FloatingDictionaryInputTargetTest
```

APK: `app/build/outputs/apk/liteStandard/debug/app-lite-standard-debug.apk`。
テスト用メモ帳: `mochi-input-lab/build/outputs/apk/debug/mochi-input-lab-debug.apk`。
Liteのみ有効化しており、Zenz/Gemmaのネイティブモジュール・AIモデルはビルドしません。

ローカルAPI検証用は `-PmochiApiBaseUrl=http://10.0.2.2:3000` を追加します。
デバッグ接続先はlocalhost/127.0.0.1/10.0.2.2/10.0.3.2のみ許可。端末・エミュレーター接続やネットワーク転送は所有者の事前承認後に行ってください。
release版はHTTPSの公開サイトのみ使用し、ローカル接続設定を入れないでください。

署名なしrelease検証: `:app:assembleLiteStandardReleaseUnsigned`。
署名付きreleaseは資産レビュー・公開承認後、`tools/mochi_signing.ps1 -Mode Release` でビルド・監査・署名します。本番署名情報は保護されたローカル環境から渡し、Gradleのconfiguration cacheを無効化します。
通常releaseビルドはレビュー確認または本番署名設定がない場合停止します。このフラグ自体がライセンス確認を代行するものではありません。署名なしを含むrelease変種は公開HTTPS APIへの接続だけを許可します。
鍵・パスワード・署名設定をコミットしないでください。署名鍵を用意していない場合、デバッグ鍵を公開版に流用しないでください。

本フォークの本番鍵はGit対象外の `release-private/` にあります。`-Mode Verify` で署名を再検証できます。新しい鍵を更新のたびに生成しないでください。鍵・パスワードの安全なバックアップについては [SIGNING.md](docs/SIGNING.md) を確認してください。

## プライバシー・通信

- 投稿欄のテキスト・パスワード・クリップボードを画像検索に使用しません。検索語は専用欄だけから明示送信し、永続保存・アプリログ出力をしません。
- INTERNET権限を使うため、上流の「完全オフライン保証」は本フォークには当てはまりません。広告・解析SDK・ログイン・端末追跡IDは追加していません。
- IP等の基盤アクセス記録が残る可能性があります。APIは検索本文をログ出力しません。
- パスワード/PIN/数値/プライベート入力欄では画像機能を無効にします。検索欄の変換は学習しません。
- 最近・お気に入りは `noBackupFilesDir`、サムネはバックアップ対象外の `cacheDir` に保存します。設定アプリから削除できます。通常入力の学習・辞書設定は上流のローカル機能として維持します。
- API検索結果キャッシュ60秒、最大20ページ。サムネ取得同時4件、ディスク50MB・メモリ8MB。原画像の先読み・GIF再生・バックグラウンド検索は行いません。
- 挿入直前に公開状態を再確認し、通信・入力先確認が失敗した場合は挿入しません。Enter/送信/改行/自動クリップボード退避はありません。

## テストと公開ゲート

自動テストはJVM/RobolectricとサイトのVitestで行います。実機の操作は必ず事前承認を求めてください。
全上流テストを実行する場合は `:app:testLiteStandardDebugUnitTest` をフィルターなしで実行します。今回のWindows/JBR21環境では変更前の固定コミットにも5件の失敗があるため、全件成功とはしていません。[検証記録](VERIFICATION.md)を参照してください。
MuMuPlayerへの接続・ADB操作・インストールも承認なしで行わないでください。
操作テストはメモ帳または同梱の別APK（通信権限・送信ボタンなし）だけで行い、ChMateや掲示板で投稿しません。
承認済みエミュレーター用の送信なしスモークテストは `:mochi-input-lab:assembleDebugAndroidTest` で生成できます。Mochi IMEの日本語テンキーを選択後、テスト用APKを導入して `adb shell am instrument -w -r -e class com.mochihoppe.inputlab.KeyboardSmokeTest com.mochihoppe.inputlab.test/androidx.test.runner.AndroidJUnitRunner`。検索は行わず、パネル・地球アイコンの切替・配列の復元を確認します。終了後は元のIMEへ戻してください。
ChMate互換性は実機未確認であり、確認済みと表示しません。

公開先は [Nyamonana/mochihoppe-ime](https://github.com/Nyamonana/mochihoppe-ime)、APKは [Releases](https://github.com/Nyamonana/mochihoppe-ime/releases/latest)。本番APIデプロイは別途承認が必要です。
2026-10-10に出典と同梱ライセンスを記録し、許諾未確認のATOK由来ルール・追加語を除いて基本辞書を再生成しました。レビュー済み資産のハッシュは `release/runtime-sha256.json` に固定しています。追加・更新時は再レビューが必要です。[NOTICE-MOCHI.md](NOTICE-MOCHI.md)、[資産の確認状況](release/ASSET_PROVENANCE.md)、[本番配布手順](docs/PRODUCTION_DISTRIBUTION.md)を参照してください。
