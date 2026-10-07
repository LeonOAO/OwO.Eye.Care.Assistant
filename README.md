## 1.0.2 更新重點

新增連續兩次 25／30 公分距離狀態規則、十次中位數引導校正、方向與影像尺寸基準匹配。
保留待機暫停；遊戲期間不新增預覽或提高分析頻率。
更新後請重新校正橫直向。詳細限制、建置與實機測試清單請閱讀 VERSION_1.0.2.md。
本版本未在交付環境完成 APK 編譯與實機驗證，詳見 ChangeAudit/Validation.txt。

# 👁️✨ OwO Eye Care Assistant (OwO 護眼助手)

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

**OwO Eye Care Assistant** 是一款專為保護視力與培養良好手機使用習慣而設計的 Android 應用程式。透過智慧距離偵測、定時休息語音提醒以及強制鎖定機制，全方位守護您與家人的靈魂之窗。

## 🌟 核心功能說明 (Features)

本應用程式由多個智慧模組組成，提供全方位的護眼方案：

1. 📏 **智慧距離偵測 (Distance Monitoring)**
   * 透過背景服務 (`DistanceForegroundService`) 持續偵測使用者臉部與螢幕的距離。
   * 當距離過近時，會觸發懸浮窗 (`DistanceOverlayController`) 溫馨提醒使用者拉開距離，避免近視加深。

2. ⏰ **定時休息與語音提醒 (Timer & Voice Reminders)**
   * 內建計時器與語音提示 (`voice_reminder.wav`)。
   * 當達到設定的使用時間上限時，系統會自動透過懸浮窗 (`ReminderOverlayController`) 與語音雙管齊下，提醒使用者放下手機讓眼睛休息。

3. 🛑 **強制休息阻擋機制 (App Blocking & Forced Breaks)**
   * 當使用者忽略提醒或需要強制休息時，系統會啟動全螢幕遮蔽 (`BlockOverlayController`)。
   * 透過覆蓋螢幕的方式，強制暫停手機的使用，直到休息時間結束。

4. 🔐 **安全 PIN 碼保護 (PIN Code Protection)**
   * 支援設定「解除鎖定 PIN」與「停止鎖定 PIN」。
   * 防止兒童或未經授權的使用者隨意更改護眼規則或關閉背景服務。
   * **貼心設計**：支援「留空不修改」機制，修改其他設定時不必每次重新輸入密碼。

5. 🛡️ **防護無障礙服務 (Guard Accessibility)**
   * 透過 `GuardAccessibilityService` 防止應用程式被惡意強行關閉或解除安裝，確保護眼機制持續運作。

## 🛠️ 安裝說明 (Installation)

### 📥 快速下載安裝 (Download Latest APK)
您可以直接取得最新編譯好的安裝檔 (APK)，無須自行編譯程式碼：
1. 點擊本 GitHub 專案頁面上方的 **[Actions]** 頁籤。
2. 點選最新一次成功執行（顯示綠色打勾）的 Workflow 紀錄（例如 `Android CI` 或 `Build`）。
3. 滑動至該頁面最下方的 **Artifacts** 區塊。
4. 點擊下載 APK 檔案（通常會包裝成 `.zip` 格式，需先解壓縮）。
5. 將 APK 傳送至您的 Android 手機，點擊開啟並允許「安裝未知來源應用程式」即可完成安裝。

### 💻 開發者自行編譯 (Build from Source)
1. **環境準備**：請確保您的電腦已安裝最新版的 [Android Studio](https://developer.android.com/studio) 與 Android SDK。
2. **取得原始碼**：將本專案解壓縮至您的電腦或透過 Git Clone 下載。
3. **開啟專案**：打開 Android Studio，點選 `File` > `Open`，選擇專案資料夾。
4. **同步與編譯**：等待 Gradle 同步完成後，點擊 `▶ Run` 按鈕安裝至手機。

## 📖 使用說明 (Usage Guide)

1. **首次啟動與權限授權**：開啟 App 後，系統會引導您授予必要的權限（如懸浮窗、無障礙服務等）。**請務必全數同意**，否則核心防護功能將無法正常運作。
2. **設定護眼規則 (Rules Setup)**：設定期望的「安全觀看距離」、「連續使用時間」以及「休息時間」。
3. **設定 PIN 碼 (Security Setup)**：設定鎖定與解除的密碼。設定成功後輸入框會顯示 `******`，未來若僅需修改時間而不修改密碼，請將密碼欄位留空直接儲存。
4. **啟動防護**：開啟護眼服務開關。App 將會進入背景執行，並在螢幕上方顯示持續運作的前景服務通知。

## 🚨 常見安裝與權限問題排解 (Troubleshooting)

由於本應用程式使用了「懸浮窗」與「無障礙服務」等進階系統權限來達成強制護眼效果，您在安裝與設定時可能會遇到系統的安全阻擋。請參考以下解決方式：

### 1. 安裝時遭 Google Play Protect 阻擋
從 GitHub 下載 APK 安裝時，系統可能會跳出「封鎖不安全的應用程式」或「未識別的開發者」紅色警告。
* **原因**：因為此 APK 未經由 Google Play 商店官方發佈與審核，Android 預設會阻擋第三方安裝。
* **解決方法**：點擊警告視窗中的 **「詳細資訊 (More details)」**，然後選擇 **「仍要安裝 (Install anyway)」** 即可順利安裝。

### 2. Android 13 以上「無障礙服務」開關反灰（受限制的設定）
如果您使用的是 Android 13 (含) 以上的手機，在嘗試開啟無障礙服務時，可能會跳出「受限制的設定」提示，且無法開啟開關。
* **原因**：Google 為了防止惡意軟體，全面限制了「非 Google Play 商店下載的 App」直接啟用無障礙服務。
* **解決方法**：
  1. 進入手機系統的 **「設定 (Settings)」** > **「應用程式 (Apps)」**。
  2. 在應用程式列表中找到 **OwO Eye Care** 並點擊進入。
  3. 點擊畫面右上角的 **「三個點 (更多選項)」**，選擇 **「允許受限制的設定 (Allow restricted settings)」**。
  4. 驗證指紋或密碼後，回到無障礙設定頁面，您就可以順利開啟服務了！

## ⚠️ 權限使用提示 (Permissions Required)

我們承諾以下權限**僅用於本機護眼運算與防護邏輯，絕不會收集或外洩您的個人隱私資料**：

* **相機權限 (`CAMERA`)**：用於運算臉部與螢幕的物理距離。影像僅在記憶體中即時運算，**不會進行拍照、錄影或上傳**。
* **顯示在其他應用程式上層 (`SYSTEM_ALERT_WINDOW`)**：核心權限，用於彈出全螢幕遮罩 (`BlockOverlayController`) 強制覆蓋畫面讓眼睛休息。
* **無障礙服務 (`BIND_ACCESSIBILITY_SERVICE`)**：用於判斷手機目前的畫面狀態，防止兒童進入系統設定強行停止 App 或解除安裝。
* **前景服務 (`FOREGROUND_SERVICE`)**：確保服務在背景長時間穩定執行，不被系統省電機制殺死。

## 📄 授權條款 (License)

此專案採用 **GNU General Public License v3.0 (GPLv3)** 授權條款。
詳情請參閱專案根目錄下的 [LICENSE](LICENSE) 檔案。

*Built with ❤️ for better eye health.*