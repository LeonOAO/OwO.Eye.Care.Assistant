# 👁️✨ OwO Eye Care Assistant (OwO 護眼助手)

**OwO Eye Care Assistant** 是一款專為保護視力與培養良好手機使用習慣而設計的 Android 應用程式。透過智慧距離偵測、定時休息語音提醒以及強制鎖定機制，全方位守護您與家人的靈魂之窗。

---

## 🌟 核心功能說明 (Features)

本應用程式由多個智慧模組組成，提供全方位的護眼方案：

1. 📏 **智慧距離偵測 (Distance Monitoring)**
   - 透過背景服務 (`DistanceForegroundService`) 持續偵測使用者臉部與螢幕的距離。
   - 當距離過近時，會觸發懸浮窗 (`DistanceOverlayController`) 溫馨提醒使用者拉開距離，避免近視加深。
2. ⏰ **定時休息與語音提醒 (Timer & Voice Reminders)**
   - 內建計時器與語音提示 (`voice_reminder.wav`)。
   - 當達到設定的使用時間上限時，系統會自動透過懸浮窗 (`ReminderOverlayController`) 與語音雙管齊下，提醒使用者放下手機讓眼睛休息。
3. 🛑 **強制休息阻擋機制 (App Blocking & Forced Breaks)**
   - 當使用者忽略提醒或需要強制休息時，系統會啟動全螢幕遮蔽 (`BlockOverlayController`)。
   - 透過覆蓋螢幕的方式，強制暫停手機的使用，直到休息時間結束。
4. 🔐 **安全 PIN 碼保護 (PIN Code Protection)**
   - 支援設定「解除鎖定 PIN」與「停止鎖定 PIN」。
   - 防止兒童或未經授權的使用者隨意更改護眼規則或關閉背景服務。
   - **貼心設計**：支援「留空不修改」機制，修改其他設定時不必每次重新輸入密碼。
5. 🛡️ **防護無障礙服務 (Guard Accessibility)**
   - 透過 `GuardAccessibilityService` 防止應用程式被惡意強行關閉或解除安裝，確保護眼機制持續運作。

---

## 🛠️ 安裝說明 (Installation)

### 開發者自行編譯 (Build from Source)
1. **環境準備**：請確保您的電腦已安裝最新版的 [Android Studio](https://developer.android.com/studio) 與 Android SDK。
2. **取得原始碼**：將本專案 (`OwO.Eye.Care.Assistant-main.zip`) 解壓縮至您的電腦。
3. **開啟專案**：
   - 打開 Android Studio。
   - 點選 `File` > `Open`，選擇解壓縮後的專案資料夾。
4. **同步 Gradle**：等待 Android Studio 自動下載依賴套件並完成 Gradle 同步 (Sync)。
5. **編譯與安裝**：
   - 透過 USB 傳輸線連接您的 Android 裝置（需開啟「開發人員選項」與「USB 偵錯」）。
   - 點擊 Android Studio 上方的 `▶ Run` 按鈕（或按下 `Shift + F10`），將 App 安裝至您的手機。

---

## 📖 使用說明 (Usage Guide)

1. **首次啟動與權限授權**：
   - 開啟 App 後，系統會引導您授予必要的權限（如懸浮窗、無障礙服務等）。**請務必全數同意**，否則核心防護功能將無法正常運作。
2. **設定護眼規則 (Rules Setup)**：
   - 進入主畫面，設定您期望的「安全觀看距離」、「連續使用時間」以及「休息時間」。
3. **設定 PIN 碼 (Security Setup)**：
   - 於設定欄位中輸入「解除鎖定 PIN」與「停止鎖定 PIN」並點擊儲存。
   - *註：設定成功後輸入框會顯示 `******`，若未來只需修改時間而不修改密碼，請將密碼欄位留空直接按儲存即可。*
4. **啟動防護**：
   - 確認設定無誤後，開啟護眼服務開關。App 將會進入背景執行，並在螢幕上方顯示持續運作的前景服務通知。
5. **日常使用**：
   - 正常使用手機即可。當距離過近或時間到達時，OwO 護眼助手會自動彈出提醒或進行畫面遮蔽。

---

## ⚠️ 權限使用提示 (Permissions Required)

為了實現完整的強制護眼與背景偵測功能，本應用程式會要求以下較為敏感的 Android 系統權限。我們承諾這些權限**僅用於本機護眼邏輯，絕不會收集或外洩您的個人隱私資料**：

*   **相機權限 (`android.permission.CAMERA`)**
    *   *用途說明*：用於 `DistanceForegroundService`。系統需要透過前置鏡頭（或距離感測器）來運算臉部與螢幕的物理距離。影像資料僅在記憶體中即時運算，**不會進行拍照、錄影或上傳至網路**。
*   **顯示在其他應用程式上層 (`android.permission.SYSTEM_ALERT_WINDOW`)**
    *   *用途說明*：這是護眼助手的核心權限。當距離過近或需要休息時，系統需要透過此權限彈出全螢幕的遮罩 (`BlockOverlayController` / `ReminderOverlayController`) 來覆蓋目前的畫面。
*   **無障礙服務 (`android.permission.BIND_ACCESSIBILITY_SERVICE`)**
    *   *用途說明*：用於 `GuardAccessibilityService`。我們需要此權限來判斷目前手機處於什麼畫面，藉此防止兒童進入系統設定強制停止 App 或解除安裝。
*   **前景服務 (`android.permission.FOREGROUND_SERVICE`)**
    *   *用途說明*：確保護眼功能可以在背景長時間穩定執行，不會因為 Android 系統的省電機制而被隨意關閉。啟動時狀態列會顯示一個常駐通知。

---
*Built with ❤️ for better eye health.*