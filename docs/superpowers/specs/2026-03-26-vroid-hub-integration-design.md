# VRoid Hub 連携 設計仕様

## 概要

VRoid Hub API を使って、ユーザーが VRoid Hub 上のアバターを直接 Minecraft mod で利用できるようにする。初期実装はシングルプレイ向け。将来的にマルチプレイでの他プレイヤーのモデル表示も視野に入れる。

## 認証情報の管理

### 外部ファイル注入方式

- `config/vrmmod-vroidhub.json` に `clientId` / `clientSecret` を記述
- **このファイルは mod に同梱しない**（ユーザーが自分で VRoid Hub の開発者登録・アプリケーション作成を行い、認証情報を配置する）
- ファイルが存在しない or `clientId` が空の場合、VRoid Hub 連携機能を非表示にする（設定画面にもボタンを出さない）
- 将来、pixiv に問い合わせて許可が得られれば、mod 同梱を検討

```json
{
  "clientId": "your_client_id_here",
  "clientSecret": "your_client_secret_here"
}
```

### 検証済み事項

- VRoid Hub の `/oauth/token` は `client_secret` 必須（PKCE のみの public client はサポートされていない）
- `redirect_uri` に `urn:ietf:wg:oauth:2.0:oob` を使用可能（ブラウザに code が表示される方式）
- PKCE (`code_challenge_method=S256`) は必須
- API バージョン: `X-Api-Version: 11`

## OAuth フロー

### 認証

1. 設定画面に「VRoid Hub にログイン」ボタン（認証情報ファイルがある場合のみ表示）
2. ボタン押下 → `code_verifier` (ランダム 43-128 文字) と `code_challenge` (SHA256 + Base64URL) を生成
3. システムブラウザで authorize URL を開く:
   ```
   GET https://hub.vroid.com/oauth/authorize
     ?response_type=code
     &client_id={clientId}
     &redirect_uri=urn:ietf:wg:oauth:2.0:oob
     &scope=default
     &state={random}
     &code_challenge={code_challenge}
     &code_challenge_method=S256
   ```
4. ユーザーが VRoid Hub で認証 → ブラウザに authorization code が表示される
5. ユーザーが code をコピーし、ゲーム内テキストフィールドにペースト
6. mod がトークン交換を実行:
   ```
   POST https://hub.vroid.com/oauth/token
     client_id={clientId}
     client_secret={clientSecret}
     redirect_uri=urn:ietf:wg:oauth:2.0:oob
     grant_type=authorization_code
     code={authorization_code}
     code_verifier={code_verifier}
   ```
7. `access_token` / `refresh_token` を取得・保存

### トークン永続化

- `config/vrmmod-vroidhub-token.json` に保存:
  ```json
  {
    "accessToken": "...",
    "refreshToken": "...",
    "expiresAt": 1774483158
  }
  ```
- 起動時にトークンがあれば有効期限を確認し、期限切れなら refresh_token でリフレッシュ
- リフレッシュ失敗時は再認証を促す（「VRoid Hub に再ログイン」表示）
- このファイルは gitignore 対象

### トークンリフレッシュ

```
POST https://hub.vroid.com/oauth/token
  client_id={clientId}
  client_secret={clientSecret}
  grant_type=refresh_token
  refresh_token={refresh_token}
```

## モデル選択・ダウンロード

### モデル一覧取得

認証完了後、設定画面にモデル選択 UI を表示:

1. `GET /api/hearts?count=100` でお気に入り（ハート済み）モデル一覧を取得
   - `is_downloadable=true` のモデルのみ表示対象
2. サムネイル画像 + キャラクター名 + 作者名をリスト表示
3. ユーザーがモデルを選択

### VRM ダウンロード

1. `POST /api/download_licenses` で `character_model_id` を指定しダウンロードライセンスを発行
2. `GET /api/download_licenses/{id}/download` で 302 リダイレクト → S3 presigned URL を取得
3. presigned URL から VRM ファイルをダウンロード
4. ローカルキャッシュに保存
5. キャッシュ済みモデルは再ダウンロード不要（バージョン ID で更新検出）

### ダウンロードキャッシュ

- キャッシュディレクトリ: `cache/vrmmod/vroidhub/`
- ファイル名: `{character_model_id}_{version_id}.vrm`
- キャッシュメタデータ: `cache/vrmmod/vroidhub/cache.json`
  ```json
  {
    "models": {
      "{character_model_id}": {
        "versionId": "...",
        "filePath": "...",
        "downloadedAt": "..."
      }
    }
  }
  ```
- モデル選択時にキャッシュのバージョンと `latest_character_model_version.id` を比較し、一致すればキャッシュを使用

## 設定画面 UI

### Cloth Config 設定画面（既存の VrmConfigScreen に追加）

#### 認証情報なしの場合

VRoid Hub 関連の UI を一切表示しない。`localModelPath` のみでモデル指定。

#### 認証情報あり・未ログインの場合

- 「VRoid Hub にログイン」ボタン
- ボタン押下 → ブラウザ起動 + code 入力テキストフィールド表示
- code 入力 → 「認証」ボタン

#### ログイン済みの場合

- ログインユーザー名表示
- 「VRoid Hub モデルを選択」ボタン → カスタム Screen を開く
- 現在選択中のモデル名表示（選択済みの場合）
- 「ログアウト」ボタン（トークン破棄）

### モデル選択 Screen（カスタム Minecraft Screen）

Cloth Config ではなく Minecraft の `Screen` API で実装する専用画面。

#### レイアウト

- **左側: モデルリスト** — スクロール可能なリスト。各項目にモデル名 + 作者名をテキスト表示（初期実装ではサムネイルなし）
- **右側: 選択中モデルの詳細** — モデル名、作者名、ライセンス条件のテキスト表示
- **下部: アクションボタン** — 「このモデルを使用する（ライセンスに同意）」「キャンセル」

#### ライセンス表示項目

CharacterModelLicenseSerializer から以下を表示:
- アバター利用権限 (characterization_allowed_user)
- 暴力表現 (violent_expression)
- 性的表現 (sexual_expression)
- 法人商用利用 (corporate_commercial_use)
- 個人商用利用 (personal_commercial_use)
- 改変 (modification)
- 再配布 (redistribution)
- クレジット表記 (credit)

#### フロー

1. 画面表示時に `GET /api/hearts` でお気に入りモデル一覧を取得（非同期、ローディング表示）
2. ユーザーがリストからモデルを選択 → 右側にライセンス詳細表示
3. 「このモデルを使用する（ライセンスに同意）」押下 → ダウンロード開始
4. ダウンロード完了 → 設定画面に戻り、選択モデル名を表示 → モデルロード

### モデルソースの優先順位

1. `localModelPath` が設定されていればそれを使用
2. VRoid Hub で選択されたモデルがあればそれを使用
3. どちらもなければモデルなし（バニラプレイヤーモデル表示）

## マルチプレイ対応（将来）

- `POST /api/download_licenses/multiplay` で発行者以外も使えるライセンスを発行
- サーバー mod が各プレイヤーの `character_model_id` を Architectury ネットワーキング API で同期
- 各クライアントが multiplay ライセンスでダウンロード
- プライベートモデルの表示は `is_private_visibility` / `is_public_visibility` フラグで制御

## ファイル構成

```
config/
├── vrmmod.json                    # 既存の基本設定
├── vrmmod-animations.json         # 既存のアニメーション設定
├── vrmmod-vroidhub.json           # VRoid Hub 認証情報 (gitignore 推奨)
└── vrmmod-vroidhub-token.json     # トークン保存 (自動生成、gitignore 必須)

cache/vrmmod/vroidhub/             # VRM ダウンロードキャッシュ
├── cache.json                     # キャッシュメタデータ
└── {model_id}_{version_id}.vrm    # キャッシュ済み VRM ファイル
```

## 実装モジュール構成

| モジュール | 責務 |
|-----------|------|
| `VRoidHubConfig` | 認証情報ファイルの読み込み。ファイル不在時は機能無効 |
| `VRoidHubAuth` | OAuth フロー (PKCE 生成、トークン交換、リフレッシュ、永続化) |
| `VRoidHubApi` | API クライアント (account, hearts, download_licenses) |
| `VRoidHubModelCache` | ダウンロードキャッシュ管理 |
| `VRoidHubModelSelectScreen` | モデル選択カスタム Screen (Minecraft Screen API) |

## API エンドポイント一覧（使用するもの）

| メソッド | パス | 用途 |
|---------|------|------|
| GET | `/oauth/authorize` | 認証開始 |
| POST | `/oauth/token` | トークン取得/リフレッシュ |
| POST | `/oauth/revoke` | ログアウト |
| GET | `/api/account` | ログインユーザー情報 |
| GET | `/api/hearts` | お気に入りモデル一覧 |
| GET | `/api/character_models/{id}` | モデル詳細 |
| POST | `/api/download_licenses` | ダウンロードライセンス発行 |
| GET | `/api/download_licenses/{id}/download` | VRM ダウンロード (302 → S3) |
