package owo.eye.care.assistant.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import owo.eye.care.assistant.data.DistanceStateStore
import owo.eye.care.assistant.data.RulesStore
import owo.eye.care.assistant.overlay.BlockOverlayController

class GuardAccessibilityService : AccessibilityService() {
    private lateinit var rules: RulesStore
    private lateinit var stateStore: DistanceStateStore
    private var blockController: BlockOverlayController? = null

    override fun onCreate() {
        super.onCreate()
        rules = RulesStore(this)
        stateStore = DistanceStateStore(this)
        blockController = BlockOverlayController(this, rules) {
            stateStore.setBlocked(false)
            blockController?.hide()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!rules.isControlEnabled()) {
            if (blockController?.isShowing() == true) blockController?.hide()
            return
        }
        if (stateStore.isBlocked()) {
            if (blockController?.isShowing() == false) blockController?.show()
        } else {
            if (blockController?.isShowing() == true) blockController?.hide()
        }
    }

    override fun onInterrupt() { blockController?.hide() }
    override fun onDestroy() { blockController?.hide(); super.onDestroy() }
}