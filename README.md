# OwO 護眼小助手

OwO 護眼小助手是一個以 Accessibility Overlay 與前鏡頭臉部寬度估算為核心的 Android 專案，提供全域使用時間限制、每日 PIN 解鎖次數，以及 30 cm 距離提醒。

## 架構

- `MainActivity`：規則、PIN、相機權限、距離校正與服務控制介面。
- `GuardAccessibilityService`：每秒整理管控狀態，決定顯示時間遮罩或距離遮罩。
- `DistanceForegroundService`：以 CameraX 取得前鏡頭影像，交由 ML Kit 偵測最大人臉並估算距離。
- `RulesStore`：保存管控規則、PIN 與每日解鎖使用量。
- `DistanceStateStore`：在前台服務、Activity 與 Accessibility Service 之間共享距離狀態。
- `BlockOverlayController`：時間額度用完後顯示 PIN 解鎖介面。
- `DistanceOverlayController`：距離小於 30 cm 時顯示優先級最高的全螢幕提醒。

## GitHub 編譯 APK

1. 建立新的 GitHub Repository。
2. 將本專案根目錄全部檔案上傳，必須包含隱藏目錄 `.github`。
3. 開啟 Repository 的 **Actions** 頁面。
4. 選擇 **Build Android APK**。
5. 點選 **Run workflow**，或直接推送到 `main` / `master`。
6. 建置成功後，在該次執行頁面的 **Artifacts** 下載 `OwO-EyeCare-Assistant-debug-執行編號`。
7. 解壓縮 Artifact，即可取得 `app-debug.apk`。

工作流程使用 JDK 17、Gradle 8.7、Android Gradle Plugin 8.6.1 與 compileSdk 35。`gradlew` 會在第一次執行時下載固定版本的 Gradle，因此 Repository 不需提交二進位 Wrapper JAR。

## 本機編譯

Linux / macOS：

```bash
chmod +x gradlew
./gradlew clean assembleDebug
```

Windows：

```bat
gradlew.bat clean assembleDebug
```

輸出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 首次使用

1. 安裝 APK 並開啟 OwO 護眼小助手。
2. 前往系統無障礙設定，啟用 OwO 護眼小助手。
3. 設定家長 PIN、停止 PIN、每次使用分鐘數與每日解鎖額度。
4. 啟動距離守護並允許相機權限。
5. 將裝置與臉部維持約 30 cm，待偵測到臉後執行 30 cm 校正。
6. 開始管控。

> 距離為依人臉框寬度計算的近似值，不是測距感測器讀值。光線、鏡頭視角、姿勢與多人同框都會影響估算。

## 遊戲低負載距離守護

距離守護固定採用 480 x 360 分析解析度、FAST 人臉模式、只保留最新影格，並將 ML Kit 推論限制為每秒最多 3 次。時間遮罩仍維持每秒檢查，30 cm 校正、持續過近 1.2 秒鎖定及安全距離 0.7 秒解除的規則不變。
