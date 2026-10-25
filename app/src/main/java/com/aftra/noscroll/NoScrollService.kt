package com.aftra.noscroll

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast

class NoScrollService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastScan = 0L
    private var lastBlock = 0L
    private var lastState: String? = null
    private val recentBlocks = ArrayDeque<Long>()
    private var loopPausedUntil = 0L
    private var lastDump = 0L
    private var lastEventSource: AccessibilityNodeInfo? = null
    private val windowClasses = mutableMapOf<String, String>()

    override fun onServiceConnected() {
        Log.i(Detectors.TAG, "Service connecté")
        DebugStatus.connectedAt = System.currentTimeMillis()
        Prefs.setServiceConnectedAt(this, DebugStatus.connectedAt)
        val caps = serviceInfo?.capabilities ?: 0
        DebugStatus.canRetrieveContent =
            caps and AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT != 0
        if (Prefs.debugMode(this)) DebugLog.append(this, "=== ${DebugLog.time(DebugStatus.connectedAt)} service connecté\n")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            DebugStatus.recordForeground("${event.packageName}/${event.className}")
        }
        val spec = Detectors.specFor(event.packageName) ?: return

        DebugStatus.eventCount++
        DebugStatus.lastEventAt = System.currentTimeMillis()
        DebugStatus.lastEventPackage = spec.packageName
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.className != null) {
            windowClasses[spec.packageName] = event.className.toString()
        }
        lastEventSource = event.source
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

    /**
     * Racine de l'arbre de l'app ciblée. rootInActiveWindow est souvent null sur certains
     * téléphones : on essaie ensuite la liste des fenêtres, puis la source de l'événement.
     * Renvoie la racine et la méthode qui a marché (ou la raison de l'échec).
     */
    private fun findRoot(spec: Detectors.Spec): Pair<AccessibilityNodeInfo?, String> {
        val pkg = spec.packageName
        rootInActiveWindow?.let { if (it.packageName?.toString() == pkg) return it to "rootInActiveWindow" }

        val fromWindows = try {
            windows.sortedByDescending { it.isActive || it.isFocused }
                .firstNotNullOfOrNull { w -> w.root?.takeIf { it.packageName?.toString() == pkg } }
        } catch (e: RuntimeException) {
            null
        }
        if (fromWindows != null) return fromWindows to "windows"

        // Fenêtres des autres écrans (affichages virtuels, fenêtres flottantes…).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val fromDisplays = try {
                val all = windowsOnAllDisplays
                (0 until all.size()).flatMap { all.valueAt(it) }
                    .firstNotNullOfOrNull { w -> w.root?.takeIf { it.packageName?.toString() == pkg } }
            } catch (e: RuntimeException) {
                null
            }
            if (fromDisplays != null) return fromDisplays to "windowsOnAllDisplays"
        }

        var node = lastEventSource?.takeIf { it.packageName?.toString() == pkg }
        while (true) node = node?.parent ?: break
        if (node != null) return node to "event.source"

        return null to "aucune racine (rootInActiveWindow=${rootInActiveWindow?.packageName}, " +
            "source=${lastEventSource != null}, fenêtres=${describeWindows()})"
    }

    private fun describeWindows(): String = try {
        val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val all = windowsOnAllDisplays
            (0 until all.size()).flatMap { all.valueAt(it) }
        } else windows
        list.joinToString(prefix = "[", postfix = "]") { w ->
            val flags = (if (w.isActive) "A" else "") + (if (w.isFocused) "F" else "")
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "d${w.displayId}:" else ""
            "$display${typeName(w.type)}:${w.root?.packageName ?: "root=null"}:${w.title ?: "-"}:id${w.id}${if (flags.isEmpty()) "" else ":$flags"}"
        }
    } catch (e: RuntimeException) {
        "erreur ${e.javaClass.simpleName}"
    }

    private fun typeName(type: Int) = when (type) {
        AccessibilityWindowInfo.TYPE_APPLICATION -> "app"
        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "clavier"
        AccessibilityWindowInfo.TYPE_SYSTEM -> "système"
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "overlay"
        AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> "split"
        else -> "type$type"
    }

    private fun scan(spec: Detectors.Spec): Detection? = try {
        val (root, how) = findRoot(spec)
        if (root == null) {
            DebugStatus.lastScanNote = how
            null
        } else {
            DebugStatus.lastRootSource = how
            DebugStatus.lastScanNote = null
            ScreenDetector.detect(root, spec)
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

        val (root, how) = findRoot(spec)
        val text = buildString {
            append("=== ").append(DebugLog.time(System.currentTimeMillis()))
                .append(" ").append(AccessibilityEvent.eventTypeToString(event.eventType)).append('\n')
            append("package=").append(spec.packageName)
                .append(" fenêtre=").append(windowClasses[spec.packageName] ?: "?")
                .append(" eventClass=").append(event.className)
                .append(" windowId=").append(event.windowId).append('\n')
            append("racine : ").append(how).append('\n')
            if (root != null) {
                try {
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
            val home = findRoot(spec).first?.let { findHomeTab(it, spec) }
            Log.i(Detectors.TAG, "Toujours sur ${spec.kind} : clic Accueil (${if (home != null) "trouvé" else "introuvable"})")
            home?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }, RECHECK_DELAY_MS)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.i(Detectors.TAG, "Service déconnecté")
        DebugStatus.connectedAt = 0L
        Prefs.setServiceUnboundAt(this, System.currentTimeMillis())
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
