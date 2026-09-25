package com.screenistaplustv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

/**
 * Receives the result of a PackageInstaller session commit.
 * Logs success/failure — the app will auto-relaunch via
 * BootBroadcastReceiver's MY_PACKAGE_REPLACED handler.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                Log.d("InstallResultReceiver", "APK installed successfully")
                // App will be relaunched automatically by BootBroadcastReceiver (MY_PACKAGE_REPLACED)
            }
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Should not happen with Device Owner — but handle gracefully
                val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                confirmIntent?.let { context.startActivity(it) }
                Log.w("InstallResultReceiver", "User action required (Device Owner not set?)")
            }
            else -> {
                Log.e("InstallResultReceiver", "APK install failed — status: $status, message: $message")
            }
        }
    }
}
