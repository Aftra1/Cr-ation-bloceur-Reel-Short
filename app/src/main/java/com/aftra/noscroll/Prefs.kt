package com.aftra.noscroll

import android.content.Context

object Prefs {
    private const val FILE = "noscroll"
    private const val KEY_REELS = "block_reels"
    private const val KEY_SHORTS = "block_shorts"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun blockReels(context: Context) = prefs(context).getBoolean(KEY_REELS, true)
    fun blockShorts(context: Context) = prefs(context).getBoolean(KEY_SHORTS, true)

    fun setBlockReels(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_REELS, value).apply()

    fun setBlockShorts(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_SHORTS, value).apply()
}
