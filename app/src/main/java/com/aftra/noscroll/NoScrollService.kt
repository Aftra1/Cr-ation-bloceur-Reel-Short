package com.aftra.noscroll

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class NoScrollService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastScan = 0L
    private var lastBlock = 0L
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

        val detection = scan(spec) ?: return

        // On ne logue que les changements d'état pour garder Logcat lisible.
        val state = "${detection.matched}:${detection.signals}"
        if (state != lastState) {
            lastState = state
            Log.i(Detectors.TAG, "[${spec.packageName}] $detection")
        }

        if (detection.matched && isBlockingEnabled(spec) && now - lastBlock >= COOLDOWN_MS) {
            lastBlock = now
            block(spec)
        }
    }

    private fun scan(spec: Detectors.Spec): Detection? {
        val root = rootInActiveWindow ?: return null
        if (root.packageName?.toString() != spec.packageName) return null
        return ScreenDetector.detect(root, spec)
    }

    private fun isBlockingEnabled(spec: Detectors.Spec) = when (spec.kind) {
        Detectors.Kind.REELS -> Prefs.blockReels(this)
        Detectors.Kind.SHORTS -> Prefs.blockShorts(this)
    }

    private fun block(spec: Detectors.Spec) {
        val label = if (spec.kind == Detectors.Kind.REELS) "Reels bloqués" else "Shorts bloqués"
        Log.i(Detectors.TAG, "Blocage : $label, BACK")
        Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
        performGlobalAction(GLOBAL_ACTION_BACK)

        // Si le BACK n'a pas suffi, on bascule sur l'onglet Accueil.
        handler.postDelayed({
            if (scan(spec)?.matched != true) return@postDelayed
            val home = rootInActiveWindow?.let { findHomeTab(it, spec) }
            Log.i(Detectors.TAG, "Toujours sur ${spec.kind} : clic Accueil (${if (home != null) "trouvé" else "introuvable"})")
            home?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }, RECHECK_DELAY_MS)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private companion object {
        const val SCAN_INTERVAL_MS = 150L
        const val COOLDOWN_MS = 1000L
        const val RECHECK_DELAY_MS = 500L
    }
}
