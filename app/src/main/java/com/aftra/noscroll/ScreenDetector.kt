package com.aftra.noscroll

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class Detection(val spec: Detectors.Spec, val score: Int, val signals: List<String>) {
    val matched get() = score >= Detectors.SCORE_THRESHOLD
    override fun toString() = "${spec.kind} score=$score matched=$matched signals=$signals"
}

object ScreenDetector {

    /** Parcourt l'arbre et combine plusieurs signaux (ids, libellés, état "selected" de l'onglet). */
    fun detect(root: AccessibilityNodeInfo, spec: Detectors.Spec): Detection {
        val signals = linkedSetOf<String>()
        var score = 0
        var visited = 0

        fun add(signal: String, points: Int) {
            if (signals.add(signal)) score += points
        }

        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > Detectors.MAX_DEPTH || visited++ > Detectors.MAX_NODES) return

            val id = node.viewIdResourceName?.substringAfter(":id/")
            val text = node.text?.toString()?.trim()?.lowercase()
            val desc = node.contentDescription?.toString()?.trim()?.lowercase()
            if (Detectors.DUMP_TREE) {
                Log.v(Detectors.TAG, "${" ".repeat(depth)}id=$id text=$text desc=$desc sel=${node.isSelected}")
            }

            if (id != null && id in spec.contentIds) add("id:$id", Detectors.SCORE_STRONG)

            val isTab = (id != null && id in spec.tabIds) ||
                spec.tabLabels.any { it == text || desc?.startsWith(it) == true }
            if (isTab && node.isSelected) add("tab-selected:${id ?: text ?: desc}", Detectors.SCORE_STRONG)

            if (spec.weakWords.any { it == text || it == desc }) add("word:${text ?: desc}", Detectors.SCORE_WEAK)

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                visit(child, depth + 1)
            }
        }

        visit(root, 0)
        return Detection(spec, score, signals.toList())
    }
}
