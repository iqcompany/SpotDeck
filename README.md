# SpotDeck

SpotDeck は、Android DAP（Digital Audio Player）をSpotify専用プレイヤーとして活用するための軽量ランチャー兼BLEサーバーアプリです。

Oilsky M308 / G88 などのAndroid DAPにインストールし、Spotifyに特化した操作画面とBLE経由でのリモート操作機能を提供します。

---

## 主な機能

### ランチャー機能

* Spotify専用ホーム画面
* ワンタップSpotify起動
* 表示時のSpotify自動起動
* スワイプアップによるアプリ一覧
* テキストベースのアプリリスト
* 軽量・高速動作
* 低リソース消費設計

### BLE GATT Server機能

* BLE Advertising
* BLE GATT Server
* Command Characteristic（コマンド受信）
* Playback Status Characteristic（再生状態通知）
* Metadata Characteristic（楽曲情報通知）
* Device Status Characteristic（端末状態通知）
* Protocol Information Characteristic（プロトコル情報提供）
* Foreground ServiceによるBLE接続維持

### Spotify制御

* MediaSessionの検出
* Spotify MediaControllerの取得
* Play / Pause / Next / Previous
* 音量制御
* 再生状態取得
* メタデータ取得

---

## システム構成

```text
┌──────────────────────────────┐
│ Android Smartphone           │
│                              │
│ SpotDeck Remote              │
│                              │
│ - BLE GATT Client            │
│ - MediaSession               │
│ - MediaStyle Notification    │
│ - Foreground Service         │
│ - Remote Control UI          │
└──────────────┬───────────────┘
               │
               │ Bluetooth Low Energy
               │ GATT Write / Notify
               │
┌──────────────▼───────────────┐
│ Android DAP                  │
│                              │
│ SpotDeck ◀ このアプリ          │
│                              │
│ - BLE GATT Server            │
│ - MediaController            │
│ - Spotify Control            │
│ - Playback Status Provider   │
└──────────────┬───────────────┘
               │
               │ Android MediaSession
               │
┌──────────────▼───────────────┐
│ Spotify                      │
│                              │
│ - Offline Playback           │
│ - Lossless Audio             │
│ - Downloaded Tracks          │
└──────────────────────────────┘
```

---

## リポジトリ構成

SpotDeckシリーズは、役割ごとにリポジトリを分離しています。

| リポジトリ | 役割 |
|-----------|------|
| [SpotDeck](https://github.com/iqcompany/SpotDeck) | DAP側ランチャー＋BLEサーバー（このリポジトリ） |
| [SpotDeck Remote](https://github.com/iqcompany/SpotDeck-Remote) | スマートフォン側BLEリモコン |

---

## 背景

Android DAPをSpotify専用プレイヤーとして使用する場合、音質面では以下のメリットがあります。

* DAP内蔵DACを使用できる
* 4.4mmバランス出力を使用できる
* Spotifyのロスレス音源をDAP側にダウンロードして再生できる
* スマートフォンのバッテリーやUSB端子を消費しない
* オフライン再生が可能

一方で、DAPをポケットや腰に装着していると操作が不便です。SpotDeckはBLE GATT Serverを内蔵することで、スマートフォン側の[SpotDeck Remote](https://github.com/iqcompany/SpotDeck-Remote)からの遠隔操作を可能にします。

---

## BLE通信仕様

### 役割

| 端末 | BLEロール |
|------|----------|
| Android DAP（SpotDeck） | Peripheral / GATT Server |
| Android Smartphone（SpotDeck Remote） | Central / GATT Client |

### GATTサービス

以下のUUIDは開発用の仮値です。

```text
Service UUID
8f5a0000-6c4b-4c7e-9f26-7cb53a4e0000

Command Characteristic
8f5a0001-6c4b-4c7e-9f26-7cb53a4e0000

Playback Status Characteristic
8f5a0002-6c4b-4c7e-9f26-7cb53a4e0000

Metadata Characteristic
8f5a0003-6c4b-4c7e-9f26-7cb53a4e0000

Device Status Characteristic
8f5a0004-6c4b-4c7e-9f26-7cb53a4e0000

Protocol Information Characteristic
8f5a0005-6c4b-4c7e-9f26-7cb53a4e0000
```

### Command Characteristic

スマートフォンからのコマンドを受信します。

```text
Properties: Write, Write Without Response
```

|      値 | コマンド |
| -----: | ------- |
| `0x01` | Play |
| `0x02` | Pause |
| `0x03` | Play / Pause |
| `0x04` | Next |
| `0x05` | Previous |
| `0x06` | Volume Up |
| `0x07` | Volume Down |
| `0x08` | Mute |
| `0x09` | Shuffle Toggle |
| `0x0A` | Repeat Toggle |
| `0x0B` | Request Current Status |
| `0x0C` | Request Metadata |
| `0x0D` | Request Device Status |

### Playback Status Characteristic

再生状態をスマートフォンへ通知します。

```text
Properties: Read, Notify
```

```json
{
  "version": 1,
  "state": "playing",
  "positionMs": 65342,
  "durationMs": 214000,
  "shuffle": false,
  "repeat": "off"
}
```

### Metadata Characteristic

楽曲情報をスマートフォンへ通知します。

```text
Properties: Read, Notify
```

```json
{
  "version": 1,
  "title": "Track Title",
  "artist": "Artist Name",
  "album": "Album Name",
  "trackId": "spotify:track:xxxxxxxx",
  "artworkId": "xxxxxxxx",
  "durationMs": 214000
}
```

### Device Status Characteristic

DAP側の端末状態を通知します。

```text
Properties: Read, Notify
```

```json
{
  "version": 1,
  "batteryLevel": 82,
  "volume": 57,
  "charging": false,
  "wifiEnabled": false,
  "spotifyRunning": true,
  "spotDeckVersion": "1.0.0"
}
```

### Protocol Information Characteristic

プロトコル互換性の確認に使用します。

```text
Properties: Read
```

```json
{
  "protocolVersion": 1,
  "minimumClientVersion": 1,
  "deviceName": "SpotDeck G88",
  "capabilities": [
    "playback-control",
    "metadata",
    "device-status",
    "volume-control"
  ]
}
```

---

## プロトコル設計方針

* コマンドはバイナリ優先（1バイト）
* 状態通知はJSON形式（将来的にCBOR/MessagePack等へ移行可能）
* すべての複合データにプロトコルバージョンを含める

---

## 対象デバイス

* Oilsky M308 / G88
* AndroidベースのDAP
* Spotify専用プレイヤー
* 音楽再生特化のAndroidデバイス

---

## 推奨技術スタック

* Kotlin
* Android SDK
* Material Design 3
* Android BLE API
* Foreground Service
* MediaSession / MediaController
* Coroutines / StateFlow
* Gradle Kotlin DSL

---

## 開発フェーズ

### Phase 1: BLE GATT Server基盤

BLE GATT Serverの起動とアドバタイズを実装し、SpotDeck Remoteからの接続を受け付ける。

実装対象：

* BLE GATT Server起動
* BLE Advertising開始
* Service / Characteristic定義
* Command Characteristic（Write受付）
* SpotDeck Remoteからの接続受付
* 受信コマンドのログ出力

完了条件：

```text
SpotDeck RemoteがSpotDeckを検出
        ↓
GATT接続成功
        ↓
NEXTコマンドを受信
        ↓
ログに出力される
```

### Phase 2: Spotify MediaSession制御

受信したBLEコマンドをSpotifyのMediaControllerへ転送する。

実装対象：

* SpotifyのMediaSession検出
* MediaControllerの取得
* Play / Pause / Next / Previous
* Volume Up / Volume Down
* コマンド受信からSpotify操作への接続

### Phase 3: 再生情報通知

Spotifyの再生状態・楽曲情報をBLE経由でスマートフォンへ通知する。

実装対象：

* Playback Status Characteristicへの書き込み・Notify
* Metadata Characteristicへの書き込み・Notify
* Device Status Characteristicへの書き込み・Notify
* Protocol Information Characteristic
* MediaSessionコールバックによる変更検知
* 状態変更時の自動Notify

### Phase 4: 接続安定化

BLE接続の維持と再接続の安定化を行う。

実装対象：

* Foreground Service化
* 接続端末管理
* 再接続対応
* バッテリー最適化対策
* GATTエラーハンドリング
* MTU交渉

### Phase 5: UX改善

DAP側UIの改善とセキュリティ基盤を整備する。

実装対象：

* 接続状態のUI表示
* 接続承認UI
* 接続許可端末のホワイトリスト
* ランチャーとBLE機能の統合UI
* ログ表示画面

---

## パーミッション

### BLE関連（Android 12以降）

```xml
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

### BLE関連（Android 11以前）

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
```

### Foreground Service

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
```

### 通知

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### MediaSession検出

```xml
<uses-permission android:name="android.permission.MEDIA_CONTENT_CONTROL" />
```

---

## 既知の課題

* Android DAP端末によってBLE Peripheral対応状況が異なる
* DAP側AndroidがカスタマイズされておりBLE Advertisingに制限がある可能性がある
* Spotify側MediaSessionの動作がバージョンによって変わる可能性がある
* GATT 133エラーなどAndroid BLE固有の問題がある
* MTUサイズが端末によって異なる
* Notifyの送信頻度を上げすぎると不安定になる可能性がある
* DAP側Androidが独自カスタマイズされている場合がある

---

## 非目標

* Spotify Web APIの直接利用
* インターネット経由の制御
* 音声データのBLE転送
* Bluetoothオーディオ機能
* 汎用リモコン機能（初期版）

---

## ビルド

```bash
./gradlew assembleDebug
```

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

```bash
adb logcat | grep SpotDeck
```

---

## Author

**IQ COMPANY Ltd.**

* 株式会社アイキューカンパニ
* Ryo Ikuyama
* https://iqcompany.jp/

---

## License

ライセンスは未定です。

* MIT License
* Apache License 2.0
* Proprietary License

---

## Status

```text
Status: Planning / Initial Development
```
