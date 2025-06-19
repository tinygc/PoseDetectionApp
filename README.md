# GoogleTV姿勢検出アプリ - Pose Detection App

GoogleTV向けの姿勢検出・ポーズ比較アプリケーションです。USBカメラを使用してリアルタイムで姿勢を検出し、設定されたリファレンスポーズとの一致度を評価します。

## 主な機能

### 🎯 姿勢検出
- MediaPipeを使用したリアルタイム全身姿勢検出（10fps）
- 骨格の可視化表示（ON/OFF切り替え可能）
- USBカメラ対応

### 📊 ポーズ評価
- ハイブリッド評価システム（シルエット70% + 関節角度30%）
- A-E段階評価 + 100点満点スコア
- 部位別評価（腕、脚、体幹）

### 📹 リファレンスポーズ設定
- **静的ポーズ**: 5秒カウントダウン後にスナップショット保存
- **動的シーケンス**: 10秒間の動作録画
- 既存ポーズとの組み合わせ

### 🎮 Android TV対応
- リモコン操作対応
- 左右分割UI（カメラ映像 + ガイド表示）
- TV画面最適化

## 操作方法

### リモコン操作
- **上下左右**: メニュー移動
- **Enter**: 選択・決定
- **Back**: 戻る・キャンセル
- **赤ボタン**: 動的シーケンス録画開始/停止
- **緑ボタン**: 静的ポーズ録画（カウントダウン開始）
- **青ボタン**: 骨格表示ON/OFF切り替え
- **数字キー"1"**: 鏡面表示ON/OFF切り替え
- **数字キー"9"**: アプリ強制終了

## セットアップ

### 必要な環境
- Android Studio 2023.3.1以降
- Android SDK API Level 21以降
- Java 17
- USBカメラ（GoogleTVに接続）

### プロジェクトセットアップ

1. **MediaPipeモデルのダウンロード**
   ```bash
   # pose_landmarker.taskモデルファイルをダウンロード
   wget https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task
   
   # app/src/main/assetsディレクトリに配置
   cp pose_landmarker_lite.task app/src/main/assets/pose_landmarker.task
   ```

2. **Android Studioでプロジェクトを開く**
   ```bash
   # Windows側のAndroid Studioから以下のパスを開く
   \\wsl.localhost\Ubuntu-22.04\home\yoshimu\wsl_Code\PoseDetectionApp
   ```

3. **Gradle同期**
   - Android Studioでプロジェクトを開いた後、Gradle同期を実行

4. **USBカメラの接続**
   - GoogleTVにUSBカメラを接続
   - カメラ権限の許可

### ビルドと実行

```bash
# デバッグビルド
./gradlew assembleDebug

# デバイスにインストール
./gradlew installDebug

# テスト実行
./gradlew test
```

## アーキテクチャ

### パッケージ構成
```
com.posedetection.app/
├── MainActivity.kt                 # メインアクティビティ
├── camera/
│   └── CameraManager.kt           # カメラ制御
├── pose/
│   ├── PoseDetector.kt           # MediaPipe姿勢検出
│   └── PoseEvaluator.kt          # ポーズ評価システム
└── ui/
    └── SkeletonOverlayView.kt    # 骨格描画オーバーレイ
```

### 技術スタック
- **開発言語**: Kotlin
- **UI**: Android TV (Leanback Library)
- **カメラ**: CameraX
- **姿勢検出**: MediaPipe Tasks Vision
- **画像処理**: Android Graphics API

## 開発状況

### ✅ 完了済み機能
- [x] プロジェクト基本構造
- [x] Android TV対応UI
- [x] カメラ統合
- [x] MediaPipe姿勢検出
- [x] 骨格可視化
- [x] ハイブリッド評価システム
- [x] リモコン操作
- [x] カウントダウン機能

### 🔄 実装予定機能
- [ ] 動的シーケンス録画
- [ ] 既存ポーズデータベース
- [ ] ポーズ保存・読み込み
- [ ] USBカメラ自動検出
- [ ] パフォーマンス最適化

## トラブルシューティング

### よくある問題

1. **カメラが認識されない**
   - USBカメラが正しく接続されているか確認
   - カメラ権限が許可されているか確認

2. **MediaPipeエラー**
   - `pose_landmarker.task`がassetsディレクトリに配置されているか確認
   - モデルファイルが破損していないか確認

3. **パフォーマンスの問題**
   - フレームレートが10fpsに制限されているか確認
   - メモリ使用量を監視

## ライセンス

Copyright (c) 2025 AndroidTV Mavericks
このプロジェクトはMITライセンスの下で公開されています。
