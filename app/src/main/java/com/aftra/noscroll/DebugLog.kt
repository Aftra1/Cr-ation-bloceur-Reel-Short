package com.aftra.noscroll

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** État en mémoire (service et app tournent dans le même processus), affiché en tête du journal. */
object DebugStatus {
    @Volatile var connectedAt = 0L
    @Volatile var eventCount = 0
    @Volatile var lastEventAt = 0L
    @Volatile var lastEventPackage: String? = null
    @Volatile var lastDetection: String? = null
    @Volatile var lastDetectionAt = 0L
    @Volatile var lastScanNote: String? = null

    fun header(): String {
        fun t(ms: Long) = DebugLog.time(ms)
        return buildString {
            append("Service connecté : ")
            appendLine(if (connectedAt > 0) "oui (${t(connectedAt)})" else "non (depuis le lancement de l'app)")
            append("Événements Instagram/YouTube reçus : ")
            appendLine(if (eventCount > 0) "$eventCount (dernier : $lastEventPackage à ${t(lastEventAt)})" else "AUCUN")
            append("Dernière détection : ")
            appendLine(if (lastDetectionAt > 0) "$lastDetection à ${t(lastDetectionAt)}" else "aucune")
            lastScanNote?.let { appendLine("Dernier scan ignoré : $it") }
        }
    }
}

/** Journal debug dans un fichier interne, écrit hors du thread principal. */
object DebugLog {
    private const val FILE = "debug_log.txt"
    private const val MAX_BYTES = 1_000_000L
    private const val KEEP_CHARS = 500_000
    const val MAX_NODES = 200

    private val io = Executors.newSingleThreadExecutor()

    fun time(ms: Long): String = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(ms))

    private fun file(context: Context) = File(context.filesDir, FILE)

    fun append(context: Context, text: String) {
        val f = file(context.applicationContext)
        io.execute {
            f.appendText(text)
            if (f.length() > MAX_BYTES) f.writeText(f.readText().takeLast(KEEP_CHARS))
        }
    }

    fun read(context: Context, maxChars: Int): String {
        val f = file(context)
        return if (f.exists()) f.readText().takeLast(maxChars) else ""
    }

    fun clear(context: Context) {
        val f = file(context.applicationContext)
        io.execute { f.delete() }
    }

    /** Liste des nœuds visibles qui portent un id, un texte ou une description. */
    fun dumpNodes(root: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        var count = 0

        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (count >= MAX_NODES || depth > Detectors.MAX_DEPTH) return
            val id = node.viewIdResourceName?.substringAfter(":id/")
            val text = node.text?.toString()?.replace('\n', ' ')?.take(80)
            val desc = node.contentDescription?.toString()?.replace('\n', ' ')?.take(80)
            if (node.isVisibleToUser && (id != null || text != null || desc != null)) {
                count++
                sb.append("  ").append(" ".repeat(minOf(depth, 20)))
                    .append("id=").append(id ?: "-")
                    .append(" | text=").append(text ?: "-")
                    .append(" | desc=").append(desc ?: "-")
                if (node.isSelected) sb.append(" [selected]")
                sb.append('\n')
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                visit(child, depth + 1)
            }
        }

        visit(root, 0)
        if (count >= MAX_NODES) sb.append("  … limite de $MAX_NODES nœuds atteinte\n")
        return sb.toString()
    }
}
