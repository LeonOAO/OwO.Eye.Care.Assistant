# Guardian (全域時間遮罩 + 距離30cm硬性遮罩)

## 使用方式
1. 用 Android Studio 建立新專案：Empty Views Activity
   - Package name: com.example.guardian
   - Min SDK: 26
2. 將本 zip 解壓縮後，整個資料夾內容貼到專案根目錄（覆蓋同名檔案）。
3. Gradle Sync 後 Run。

## 首次設定
1. 系統設定 → 無障礙 → Guardian → 開啟（必須）。
2. 開啟 App 設定「家長 PIN」與規則，按「開始管控」。
3. 距離守護：按「啟動距離守護」→ 允許相機 → 依指示「校正30cm」。

## 距離守護
- 硬性模式：距離 < 30cm 持續一段時間會出現遮罩，無 PIN 可繞過；拉遠到 ≥30cm 自動解除。
- 沒偵測到臉 → 不鎖。

