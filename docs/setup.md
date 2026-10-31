# 環境構築

tsp-app の、ビルドとテストの手順です。作りは [tsp-app](https://github.com/sluchin/tsp-app) の `SETUP.md` と同じです (Docker のイメージ、Gradle、CI)。

## 1. 必要なもの

- Docker (Docker Compose を含む)
- Git

Android Studio、JDK、Android SDK はホストに入れません (Docker のイメージの中にあります)。

## 2. ホストのユーザー ID を環境変数にエクスポートする

生成物 (`build/` など) が root の所有にならないように、コンテナをホストのユーザーで実行します。

```bash
export UID GID=$(id -g)
```

## 3. コマンド

```bash
docker compose build                  # 開発用のイメージを作る (Dockerfile を変えたときも)
docker compose run --rm build         # デバッグ用の APK (app/build/outputs/apk/debug/tsp-app-<版>-debug.apk)
docker compose run --rm test          # 単体テスト
docker compose run --rm coverage      # core の行と分岐のカバレッジ 100%
docker compose run --rm format        # 整形の確認 (ktlint)
docker compose run --rm format-fix    # 整形して書き換える
docker compose run --rm analyze       # 静的解析 (detekt と Android Lint)
docker compose run --rm docs          # ドキュメント (Dokka)。core/build/dokka/html/index.html
docker compose run --rm clean         # 生成物を消す
docker compose run --rm dev           # 開発シェル
```

`docker compose run` には必ず `--rm` を付けます。

### ログを見る

アプリのログは、Android の logcat に出ます (`app/src/main/kotlin/jp/tspapp/app/AppLog.kt`)。タグは機能ごとです (`Solver`: 解いた結果、`Board`: 盤のタップ)。

```bash
# エミュレータ (Docker) のログを、タグで絞って見る (D はデバッグ以上)
docker compose exec emulator adb logcat -s Solver:D Board:D AndroidRuntime:E
# 実機 (USB でつなぐ) なら
adb logcat -s Solver:D Board:D AndroidRuntime:E
```

- デバッグのログ (`AppLog.d`) は、デバッグ版 (`assembleDebug`) だけで出ます。リリース版では、情報 (`AppLog.i`)、警告 (`AppLog.w`)、エラー (`AppLog.e`) だけが出ます。
- アプリが落ちたときの例外は、`AndroidRuntime` のタグで出ます。
- Google Play で公開したら、落ちたときの例外と ANR (応答なし) は、Play Console の「Android Vitals」に集まります (アプリのコードは要りません。利用者が、使用状況と診断情報の送信を許可している端末から、集まります)。リリース版で R8 の難読化を有効にしたら、Play Console に、対応表 (`mapping.txt`) を上げると、例外の場所が読めます。

## 4. クラウドのセッション (claude.ai/code)

通常の `docker compose` はプロキシの設定がないため、失敗します。`scripts/cloud-docker.sh build` と `scripts/cloud-docker.sh run <サービス>` を使います (詳しくは `.claude/CLAUDE.md`)。

## 5. CI (GitHub Actions)

push のたびに、`.github/workflows/build.yml` が `build` / `test` / `coverage` / `format` / `analyze` / `docs` を実行します。`build` が成功すると、デバッグ用の APK をアーティファクト `tsp_app_apk` に保存します (3 日で期限が切れます。Actions の実行の画面から、ダウンロードできます)。
