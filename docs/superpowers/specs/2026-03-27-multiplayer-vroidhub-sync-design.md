# マルチプレイ VRoid Hub モデル同期 設計書

## 概要

サーバー経由で各プレイヤーの VRoid Hub モデル情報を共有し、VRoid Hub API のマルチプレイ用ダウンロードライセンスを使って受信側がモデルをダウンロード・表示する。

## 方針

- VRoid Hub モデルのみ対応（ローカルVRMの同期はスコープ外）
- アニメーション状態の同期は不要（MC標準の PlayerRenderState から各クライアントが独立計算）
- mod未導入サーバーではグレースフルデグラデーション（従来通り自分のVRMのみ表示）
- サーバー側は永続化なし（メモリのみ）

## VRoid Hub API: マルチプレイ用ダウンロードライセンス

### エンドポイント

`POST /api/download_licenses/multiplay`

- 発行者以外も利用可能なダウンロードライセンスを発行する（マルチプレイ専用）
- リクエスト: `{ character_model_id: string }` + `X-Api-Version: 11` + OAuth Bearer
- レスポンス: `{ data: DownloadLicense }` （`DownloadLicense.id` がライセンスID）

### フロー

1. モデル所有者（プレイヤーA）が自分の OAuth トークンで `POST /api/download_licenses/multiplay` を呼ぶ
2. 取得したライセンスIDをパケットで他プレイヤーに共有
3. 受信側（プレイヤーB）が自分の OAuth トークンで `GET /api/download_licenses/{licenseId}/download` でモデルをダウンロード

### フォールバック（将来対応）

マルチプレイ用ライセンスが動作しない場合（アプリ未申請、API拒否等）：
- 受信側が自分の認証で通常の `POST /api/download_licenses` を使い、publicモデルのみ取得可能にする
- この場合パケットには `vroidHubModelId` を送り、受信側が自力でライセンス発行
- 初回実装では含めない。動作検証後に必要であれば追加

## パケット設計

### ModelAnnounce (C2S)

クライアント → サーバー。自分のモデル情報を通知する。

| フィールド | 型 | 説明 |
|-----------|------|------|
| `vroidHubModelId` | `String?` | VRoid Hub モデルID。null でクリア（VRM解除） |
| `multiplayLicenseId` | `String?` | マルチプレイ用ダウンロードライセンスID |
| `scale` | `Float` | 補正済み最終スケール値（`cachedScale`） |

### PlayerModel (S2C)

サーバー → クライアント。他プレイヤーのモデル情報を通知する。

| フィールド | 型 | 説明 |
|-----------|------|------|
| `playerUUID` | `UUID` | 対象プレイヤーの UUID |
| `vroidHubModelId` | `String?` | VRoid Hub モデルID。null でクリア |
| `multiplayLicenseId` | `String?` | マルチプレイ用ダウンロードライセンスID |
| `scale` | `Float` | 補正済み最終スケール値 |

パケットは2種類のみ。ログイン時の一括送信は `PlayerModel` を人数分繰り返す。

## フロー

### プレイヤーログイン時

1. クライアントAがサーバーに接続
2. クライアントAが VRoid Hub API で `POST /api/download_licenses/multiplay` を呼び、マルチプレイ用ライセンスIDを取得
3. クライアントAが `ModelAnnounce(myModelId, licenseId, myScale)` を送信
   - mod未導入サーバーの場合、パケットは無視される（エラーにならない）
   - VRoid Hub 未設定 or ライセンス取得失敗の場合、送信しない
4. サーバーがメモリ上のマップに保存: `playerA → (modelId, licenseId, scale)`
5. サーバーが既存全プレイヤーに `PlayerModel(playerA, modelId, licenseId, scale)` を送信
6. サーバーが既存プレイヤー分の `PlayerModel` をクライアントAに送信

### モデル変更時

1. クライアントAが設定画面でモデルを変更
2. 新モデルの `POST /api/download_licenses/multiplay` でライセンスID取得
3. `ModelAnnounce(newModelId, newLicenseId, newScale)` を送信
4. サーバーがマップ更新 → 全員に `PlayerModel(playerA, newModelId, newLicenseId, newScale)` をブロードキャスト

### プレイヤーログアウト時

1. サーバーがマップからプレイヤーを削除
2. 全員に `PlayerModel(playerA, null, null, 1.0)` を送信
3. 受信側が `VrmPlayerManager.unload(playerA)` でVRM解放

### 受信側の処理

1. `PlayerModel` 受信
2. `vroidHubModelId` が null → `VrmPlayerManager.unload(uuid)` で解放
3. `multiplayLicenseId` があり、VRoid Hub にログイン済み:
   - `GET /api/download_licenses/{licenseId}/download` でモデルダウンロード
   - キャッシュキーは `vroidHubModelId` ベース（同じモデルIDなら再ダウンロード不要）
   - `VrmPlayerManager.loadLocal()` でロード
   - 受信した `scale` を `VrmState.cachedScale` に適用
4. VRoid Hub 未ログイン:
   - 初回のみチャットに通知:「他プレイヤーのVRMを表示するには VRoid Hub にログインしてください」
   - 以降は無視（通知を繰り返さない）
5. ダウンロード失敗（権限なし、ライセンス無効等）:
   - ログに通知:「〇〇さんのモデルをダウンロードできませんでした」

## サーバー側設計

### データ構造

```kotlin
// サーバー側のプレイヤーモデル管理
object VrmModServer {
    private val playerModels = ConcurrentHashMap<UUID, PlayerModelInfo>()

    data class PlayerModelInfo(
        val vroidHubModelId: String,
        val multiplayLicenseId: String?,
        val scale: Float,
    )
}
```

### サーバーの役割

- パケットの中継のみ（モデルデータには関与しない）
- `ConcurrentHashMap<UUID, PlayerModelInfo>` でメモリ管理
- プレイヤー切断時にマップからエントリ削除 + null通知ブロードキャスト

## ネットワーク技術

### Architectury NetworkManager

Architectury 15.0.3 の `CustomPacketPayload` + `StreamCodec` を使用。

```kotlin
// パケット定義（common モジュール）
data class ModelAnnouncePayload(
    val vroidHubModelId: String?,
    val multiplayLicenseId: String?,
    val scale: Float,
) : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<ModelAnnouncePayload>(
            ResourceLocation.fromNamespaceAndPath("vrmmod", "model_announce")
        )
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, ModelAnnouncePayload> = ...
    }
    override fun type() = TYPE
}

data class PlayerModelPayload(
    val playerUUID: UUID,
    val vroidHubModelId: String?,
    val multiplayLicenseId: String?,
    val scale: Float,
) : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<PlayerModelPayload>(
            ResourceLocation.fromNamespaceAndPath("vrmmod", "player_model")
        )
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, PlayerModelPayload> = ...
    }
    override fun type() = TYPE
}
```

### 登録

```kotlin
// common の初期化で登録
NetworkManager.registerReceiver(NetworkManager.s2c(), PlayerModelPayload.TYPE, PlayerModelPayload.CODEC) { payload, context ->
    context.queue { handlePlayerModel(payload) }
}
NetworkManager.registerReceiver(NetworkManager.c2s(), ModelAnnouncePayload.TYPE, ModelAnnouncePayload.CODEC) { payload, context ->
    context.queue { handleModelAnnounce(context.player as ServerPlayer, payload) }
}
```

## mod未導入サーバーでのグレースフルデグラデーション

- クライアントは接続時に `ModelAnnounce` を送信するが、サーバーにmodがなければパケットは無視される
- クライアント側は `PlayerModel` を受信しなければ何もしない
- 従来通り自分のVRMだけ表示される（既存機能への影響なし）

## アニメーション独立計算の根拠

PoseContext の全26フィールドは MC 標準の `PlayerRenderState` から取得可能:

- 歩行/走行/しゃがみ/水泳/攻撃状態 → `PlayerRenderState` で自動同期
- アイテムタグ → MC の `ItemStack` レプリケーションで自動同期
- アニメーションクリップ選択は PoseContext から決定的に計算
- 各クライアントで同じ入力 → 同じアニメーション出力

SpringBone物理・Expression（まばたき等）はクライアントごとに独立計算されるが、視覚的に問題ない（微差は知覚されにくい）。

## スコープ外

- ローカルVRMファイルの同期（サーバー経由のファイル転送）
- アニメーション状態のネットワーク同期
- サーバー側の永続化（ファイル保存）
- `heldItemScale` / `heldItemOffset` / `useVrmaAnimation` 等の詳細設定の同期
- 通常ライセンスへのフォールバック（将来対応、動作検証後に判断）
