package com.aftra.noscroll

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class NoScrollService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Étape 3 : détection. Étape 4 : blocage.
    }

    override fun onInterrupt() = Unit
}
