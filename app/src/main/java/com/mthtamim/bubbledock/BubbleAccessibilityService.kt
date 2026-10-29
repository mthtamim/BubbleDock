package com.mthtamim.bubbledock

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Optional Accessibility service. It does NOT read screen content
 * (canRetrieveWindowContent is false) and performs NO gesture.
 * It only performs a global action when the user taps a tile in the
 * BubbleDock panel: Back, Recents, or Screenshot. Without this grant
 * those three tiles stay disabled with a plain explanation.
 */
class BubbleAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty: BubbleDock never reacts to other apps' events.
    }

    override fun onInterrupt() {
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private var instance: BubbleAccessibilityService? = null

        fun performBack(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_BACK)
        }

        fun performRecents(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_RECENTS)
        }

        fun performScreenshot(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        }
    }
}
