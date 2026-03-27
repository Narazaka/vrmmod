# マルチプレイ VRoid Hub モデル同期 設計書

## 概要

サーバー経由で各プレイヤーの VRoid Hub モデルIDとスケール値を共有し、受信側が各自で VRoid Hub API からモデルをダウンロード・表示する。

## 方針

- VRoid Hub モデルIDのみ対応（ローカルVRMの同期はスコープ外）
- アニメーション状態の同期は不要（MC標準の PlayerRenderState から各クライアントが独立計算）
- mod未導入サーバーではグレースフルデグラデーション（従来通り自分のVRMのみ表示）
- サーバー側は永続化なし（メモリのみ）

## パケット設計

### ModelAnnounce (C2S)

クライアント → サーバー。自分のモデル情報を通知する。

| フィールド | 型 | 説明 |
|-----------|------|------|
| `vroidHubModelId` | `String?` | VRoid Hub モデルID。null でクリア（VRM解除） |
| `scale` | `Float` | 補正済み最終スケール値（`cachedScale`） |

### PlayerModel (S2C)

サーバー → クライアント。他プレイヤーのモデル情報を通知する。

| フィールド | 型 | 説明 |
|-----------|------|------|
| `playerUUID` | `UUID` | 対象プレイヤーの UUID |
| `vroidHubModelId` | `String?` | VRoid Hub モデルID。null でクリア |
| `scale` | `Float` | 補正済み最終スケール値 |

パケットは2種類のみ。ログイン時の一括送信は `PlayerModel` を人数分繰り返す。

## フロー

### プレイヤーログイン時

1. クライアントAがサーバーに接続
2. クライアントAが `ModelAnnounce(myModelId, myScale)` を送信
   - mod未導入サーバーの場合、パケットは無視される（エラーにならない）
3. サーバーがメモリ上のマップに保存: `playerA → (modelId, scale)`
4. サーバーが既存全プレイヤーに `PlayerModel(playerA, modelId, scale)` を送信
5. サーバーが既存プレイヤー分の `PlayerModel` をクライアントAに送信

### モデル変更時

1. クライアントAが設定画面でモデルを変更
2. `ModelAnnounce(newModelId, newScale)` を送信
3. サーバーがマップ更新 → 全員に `PlayerModel(playerA, newModelId, newScale)` をブロードキャスト

### プレイヤーログアウト時

1. サーバーがマップからプレイヤーを削除
2. 全員に `PlayerModel(playerA, null, 1.0)` を送信
3. 受信側が `VrmPlayerManager.unload(playerA)` でVRM解放

### 受信側の処理

1. `PlayerModel` 受信
2. `vroidHubModelId` が null → `VrmPlayerManager.unload(uuid)` で解放
3. `vroidHubModelId` があり、VRoid Hubにログイン済み:
   - VRoid Hub API でモデルをダウンロード（キャッシュ利用）
   - `VrmPlayerManager.loadLocal()` でロード
   - 受信した `scale` を `VrmState.cachedScale` に適用
4. VRoid Hub 未ログイン:
   - 初回のみチャットに通知:「他プレイヤーのVRMを表示するには VRoid Hub にログインしてください」
   - 以降は無視（通知を繰り返さない）
5. ダウンロード失敗（権限なし等）:
   - ログに通知:「〇〇さんのモデルをダウンロードできませんでした」

## サーバー側設計

### データ構造

```kotlin
// サーバー側のプレイヤーモデル管理
object VrmModServer {
    private val playerModels = ConcurrentHashMap<UUID, PlayerModelInfo>()

    data class PlayerModelInfo(
        val vroidHubModelId: String,
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
    val vroidHubModelId: String?,  // null = クリア
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
