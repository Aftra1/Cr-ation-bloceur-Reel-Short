package com.aftra.noscroll

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
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
    private val recentBlocks = ArrayDeque<Long>()
    private var loopPausedUntil = 0L
    private var lastDump = 0L
    private val windowClasses = mutableMapOf<String, String>()

    override fun onServiceConnected() {
        Log.i(Detectors.TAG, "Service connecté")
        DebugStatus.connectedAt = System.currentTimeMillis()
        if (Prefs.debugMode(this)) DebugLog.append(this, "=== ${DebugLog.time(DebugStatus.connectedAt)} service connecté\n")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val spec = Detectors.specFor(event.packageName) ?: return

        DebugStatus.eventCount++
        DebugStatus.lastEventAt = System.currentTimeMillis()
        DebugStatus.lastEventPackage = spec.packageName
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.className != null) {
            windowClasses[spec.packageName] = event.className.toString()
        }
        if (Prefs.debugMode(this)) dump(event, spec)

        // Les événements arrivent en rafale : on limite les parcours d'arbre.
        val now = SystemClock.uptimeMillis()
        if (now - lastScan < SCAN_INTERVAL_MS) return
        lastScan = now

        val detection = scan(spec) ?: return
        DebugStatus.lastDetection = "[${spec.packageName}] ${detection.matched} (score ${detection.score}, ${detection.signals})"
        DebugStatus.lastDetectionAt = System.currentTimeMillis()

        // On ne logue que les changements d'état pour garder Logcat lisible.
        val state = "${detection.matched}:${detection.signals}"
        if (state != lastState) {
            lastState = state
            Log.i(Detectors.TAG, "[${spec.packageName}] $detection")
        }

        if (detection.matched && isBlockingEnabled(spec) && now - lastBlock >= COOLDOWN_MS &&
            now >= loopPausedUntil && isScreenOn() && !tripsLoopGuard(now)
        ) {
            lastBlock = now
            block(spec)
        }
    }

    private fun scan(spec: Detectors.Spec): Detection? = try {
        val root = rootInActiveWindow
        when {
            root == null -> null.also { DebugStatus.lastScanNote = "rootInActiveWindow = null" }
            root.packageName?.toString() != spec.packageName ->
                null.also { DebugStatus.lastScanNote = "fenêtre active = ${root.packageName}" }
            else -> ScreenDetector.detect(root, spec)
        }
    } catch (e: RuntimeException) {
        // Nœud périmé pendant le parcours : on ignore ce scan.
        Log.w(Detectors.TAG, "Scan échoué", e)
        DebugStatus.lastScanNote = "exception ${e.javaClass.simpleName}"
        null
    }

    /** Mode debug : écrit l'écran courant dans le journal (cooldown 500 ms). */
    private fun dump(event: AccessibilityEvent, spec: Detectors.Spec) {
        val now = SystemClock.uptimeMillis()
        if (now - lastDump < DUMP_INTERVAL_MS) return
        lastDump = now

        val root = rootInActiveWindow
        val text = buildString {
            append("=== ").append(DebugLog.time(System.currentTimeMillis()))
                .append(" ").append(AccessibilityEvent.eventTypeToString(event.eventType)).append('\n')
            append("package=").append(spec.packageName)
                .append(" fenêtre=").append(windowClasses[spec.packageName] ?: "?")
                .append(" eventClass=").append(event.className).append('\n')
            when {
                root == null -> append("rootInActiveWindow = null\n")
                root.packageName?.toString() != spec.packageName ->
                    append("fenêtre active d'un autre package : ").append(root.packageName).append('\n')
                else -> try {
                    append("détection : ").append(ScreenDetector.detect(root, spec)).append('\n')
                    append(DebugLog.dumpNodes(root))
                } catch (e: RuntimeException) {
                    append("exception : ").append(e).append('\n')
                }
            }
        }
        DebugLog.append(this, text)
    }

    private fun isBlockingEnabled(spec: Detectors.Spec): Boolean {
        if (Prefs.isPaused(this)) return false
        return when (spec.kind) {
            Detectors.Kind.REELS -> Prefs.blockReels(this)
            Detectors.Kind.SHORTS -> Prefs.blockShorts(this)
        }
    }

    private fun isScreenOn() = getSystemService(PowerManager::class.java).isInteractive

    /** Anti-boucle : trop de blocages en peu de temps (le clic Accueil ramène sur Reels ?) => pause 10 s. */
    private fun tripsLoopGuard(now: Long): Boolean {
        while (recentBlocks.isNotEmpty() && now - recentBlocks.first() > LOOP_WINDOW_MS) recentBlocks.removeFirst()
        if (recentBlocks.size < LOOP_MAX_BLOCKS) return false
        loopPausedUntil = now + LOOP_PAUSE_MS
        recentBlocks.clear()
        Log.w(Detectors.TAG, "Anti-boucle : blocage suspendu 10 s")
        return true
    }

    private fun block(spec: Detectors.Spec) {
        val label = if (spec.kind == Detectors.Kind.REELS) "Reels bloqués" else "Shorts bloqués"
        Log.i(Detectors.TAG, "Blocage : $label, BACK")
        recentBlocks.addLast(SystemClock.uptimeMillis())
        Prefs.recordBlock(this)
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

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.i(Detectors.TAG, "Service déconnecté")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private companion object {
        const val SCAN_INTERVAL_MS = 150L
        const val DUMP_INTERVAL_MS = 500L
        const val COOLDOWN_MS = 1000L
        const val RECHECK_DELAY_MS = 500L
        const val LOOP_WINDOW_MS = 5000L
        const val LOOP_MAX_BLOCKS = 4
        const val LOOP_PAUSE_MS = 10_000L
    }
}
