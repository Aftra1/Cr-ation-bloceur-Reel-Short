package com.aftra.noscroll

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class NoScrollService : AccessibilityService() {

    private var lastScan = 0L
    private var lastState: String? = null

    override fun onServiceConnected() {
        Log.i(Detectors.TAG, "Service connecté")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val spec = Detectors.specFor(event?.packageName) ?: return

        // Les événements arrivent en rafale : on limite les parcours d'arbre.
        val now = SystemClock.uptimeMillis()
        if (now - lastScan < SCAN_INTERVAL_MS) return
        lastScan = now

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != spec.packageName) return

        val detection = ScreenDetector.detect(root, spec)

        // On ne logue que les changements d'état pour garder Logcat lisible.
        val state = "${detection.matched}:${detection.signals}"
        if (state != lastState) {
            lastState = state
            Log.i(Detectors.TAG, "[${spec.packageName}] $detection")
        }
        // Étape 4 : bloquer si detection.matched
    }

    override fun onInterrupt() = Unit

    private companion object {
        const val SCAN_INTERVAL_MS = 150L
    }
}
