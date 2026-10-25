package com.aftra.noscroll

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Prefs {
    private const val FILE = "noscroll"
    private const val KEY_REELS = "block_reels"
    private const val KEY_SHORTS = "block_shorts"
    private const val KEY_PAUSED_UNTIL = "paused_until"
    private const val KEY_TOTAL = "blocks_total"
    private const val KEY_DAY = "blocks_day"
    private const val KEY_DAY_COUNT = "blocks_day_count"

    const val PAUSE_MINUTES = 15

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun blockReels(context: Context) = prefs(context).getBoolean(KEY_REELS, true)
    fun blockShorts(context: Context) = prefs(context).getBoolean(KEY_SHORTS, true)

    fun setBlockReels(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_REELS, value).apply()

    fun setBlockShorts(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_SHORTS, value).apply()

    // Pause temporaire
    fun pausedUntil(context: Context) = prefs(context).getLong(KEY_PAUSED_UNTIL, 0L)
    fun isPaused(context: Context) = System.currentTimeMillis() < pausedUntil(context)

    fun pause(context: Context, minutes: Int = PAUSE_MINUTES) =
        prefs(context).edit()
            .putLong(KEY_PAUSED_UNTIL, System.currentTimeMillis() + minutes * 60_000L).apply()

    fun resume(context: Context) =
        prefs(context).edit().putLong(KEY_PAUSED_UNTIL, 0L).apply()

    // Statistiques
    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun recordBlock(context: Context) {
        val p = prefs(context)
        val sameDay = p.getString(KEY_DAY, null) == today()
        p.edit()
            .putInt(KEY_TOTAL, p.getInt(KEY_TOTAL, 0) + 1)
            .putString(KEY_DAY, today())
            .putInt(KEY_DAY_COUNT, if (sameDay) p.getInt(KEY_DAY_COUNT, 0) + 1 else 1)
            .apply()
    }

    fun blocksToday(context: Context): Int {
        val p = prefs(context)
        return if (p.getString(KEY_DAY, null) == today()) p.getInt(KEY_DAY_COUNT, 0) else 0
    }

    fun blocksTotal(context: Context) = prefs(context).getInt(KEY_TOTAL, 0)
}
