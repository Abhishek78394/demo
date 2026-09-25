package com.screenistaplustv  // ← Replace with your actual package name

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d("BootBroadcastReceiver", "Device booted. Launching app...")
                launchApp(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // Fired when THIS app's APK is updated — relaunch automatically
                Log.d("BootBroadcastReceiver", "App updated. Relaunching...")
                launchApp(context)
            }
        }
    }

    private fun launchApp(context: Context?) {
        if (context == null) return

        HomeLauncherManager.setAsPersistentHome(context)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(launchIntent)
    }
}
