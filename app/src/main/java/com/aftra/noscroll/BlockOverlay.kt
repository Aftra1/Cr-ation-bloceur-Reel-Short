package com.aftra.noscroll

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Écran plein qui masque les Reels/Shorts et coupe le son (flux média).
 * Fenêtre TYPE_ACCESSIBILITY_OVERLAY : aucune permission « superposition » nécessaire.
 */
class BlockOverlay(private val service: AccessibilityService, private val onLeave: () -> Unit) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val audio = service.getSystemService(AudioManager::class.java)
    private var view: View? = null
    private var mutedByUs = false

    val isShown get() = view != null

    fun show(kind: Detectors.Kind) {
        if (view != null) return
        val v = buildView(kind)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        windowManager.addView(v, params)
        view = v
        // Couper le son ne doit jamais empêcher l'affichage (ex. mode Ne pas déranger).
        runCatching { mute() }
    }

    fun hide() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        runCatching { unmute() }
    }

    private fun mute() {
        if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) return
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
        mutedByUs = true
    }

    private fun unmute() {
        if (!mutedByUs) return
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        mutedByUs = false
    }

    private fun buildView(kind: Detectors.Kind): View {
        val ctx = service
        fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, ctx.resources.displayMetrics)

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F2101018"))
            setPadding(64, 64, 64, 64)
            // Absorbe les touches : impossible de faire défiler les vidéos dessous.
            isClickable = true

            addView(TextView(ctx).apply {
                text = ctx.getString(R.string.overlay_title)
                setTextColor(Color.WHITE)
                textSize = 30f
                gravity = Gravity.CENTER
            })
            addView(TextView(ctx).apply {
                text = ctx.getString(
                    if (kind == Detectors.Kind.REELS) R.string.overlay_subtitle_reels else R.string.overlay_subtitle_shorts
                )
                setTextColor(Color.LTGRAY)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(16f))
                gravity = Gravity.CENTER
                setPadding(0, 32, 0, 64)
            })
            addView(Button(ctx).apply {
                text = ctx.getString(R.string.overlay_leave)
                setOnClickListener { onLeave() }
            })
        }
    }
}
