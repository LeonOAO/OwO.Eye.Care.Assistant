# 1.0.3 編譯命名衝突修正

## 原因與修正
使用者 CI 日誌顯示 DistanceForegroundService.kt 的 mainExecutor 屬性 getter
與繼承的 Context.getMainExecutor() 產生相同 JVM 簽章，造成 Accidental override。
將私有屬性與全部引用重新命名為 callbackExecutor；保留 ContextCompat.getMainExecutor(this) 呼叫。
versionCode=4，versionName=1.0.3。

## 功能範圍
不變更 1.0.2 的距離門檻、連續次數、十次校正、方向匹配、待機暫停、PIN 或影像分析頻率。
本次沒有刪除單元測試或跳過 testDebugUnitTest。

## 驗證
已執行來源回歸檢查、XML 解析、括號配對、參考模型、ZIP 完整性與獨立副本回滾。
這些不是 Kotlin 型別檢查或 Android 編譯；本環境仍未完成 Gradle/APK 建置。
使用者日誌已確認上一版在 CI 成功下載 Gradle，失敗點是 compileDebugKotlin 的命名衝突。
本次修正移除該私有 getter 衝突，但完整編譯是否通過仍以 CI 實際結果為準。
libface_detector_v2_jni.so 與 libimage_processing_util_jni.so 的 strip 提示不是日誌中導致失敗的錯誤。

## 使用
以本完整專案覆蓋上一版後重新執行原建置工作流程。
回滾原始碼：python ChangeAudit/Rollback.py，還原至 1.0.2。
