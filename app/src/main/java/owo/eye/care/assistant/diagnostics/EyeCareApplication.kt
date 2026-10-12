package owo.eye.care.assistant.diagnostics

import android.app.Application

/** 只初始化診斷，不變更任何護眼規則或服務啟動行為。 */
class EyeCareApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ExecutionLogStore.initialize(this)
    }
}
