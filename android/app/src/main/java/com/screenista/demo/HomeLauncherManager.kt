package com.screenista.demo

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

object HomeLauncherManager {
    private const val TAG = "HomeLauncherManager"

    fun setAsPersistentHome(context: Context): Boolean {
        val packageName = context.packageName
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        if (!dpm.isDeviceOwnerApp(packageName)) {
            Log.d(TAG, "Not device owner — cannot set persistent home launcher")
            return false
        }

        return try {
            val adminComponent = ComponentName(context, AdminReceiver::class.java)
            val activityComponent = ComponentName(context, MainActivity::class.java)
            val filter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }

            dpm.clearPackagePersistentPreferredActivities(adminComponent, packageName)
            dpm.addPersistentPreferredActivity(adminComponent, filter, activityComponent)
            Log.d(TAG, "Persistent home launcher set for $packageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "setAsPersistentHome failed: ${e.message}", e)
            false
        }
    }

    fun clearPersistentHome(context: Context) {
        val packageName = context.packageName
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, AdminReceiver::class.java)

        try {
            dpm.clearPackagePersistentPreferredActivities(adminComponent, packageName)
            Log.d(TAG, "Persistent home launcher cleared for $packageName")
        } catch (e: Exception) {
            Log.w(TAG, "clearPersistentHome threw: ${e.message}")
        }
    }
}
