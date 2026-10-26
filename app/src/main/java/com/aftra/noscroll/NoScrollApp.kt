package com.aftra.noscroll

import android.app.Application
import java.io.File

/** Enregistre les plantages dans le journal (pas de Logcat sans PC). */
class NoScrollApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                File(filesDir, DebugLog.FILE).appendText(
                    "=== ${DebugLog.time(System.currentTimeMillis())} PLANTAGE (${thread.name})\n${e.stackTraceToString()}\n"
                )
            }
            previous?.uncaughtException(thread, e)
        }
    }
}
