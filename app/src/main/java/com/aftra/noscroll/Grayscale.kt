package com.aftra.noscroll

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

/** Mode Noir & Blanc via le filtre de couleurs système (daltonizer en monochromie). */
object Grayscale {
    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun adbCommand(context: Context) =
        "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
}
