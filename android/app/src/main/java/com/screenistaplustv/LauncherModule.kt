package com.screenistaplustv

import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.Settings
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

class LauncherModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String {
        return "LauncherModule"
    }

    @ReactMethod
    fun isDefaultLauncher(promise: Promise) {
        try {
            val myPackage = reactContext.packageName

            // RoleManager (Android 10+) — reflects user choice and DPM persistent home.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = reactContext.getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    promise.resolve(true)
                    return
                }
            }

            // resolveActivity honors persistent preferred activities set by device owner.
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfo: ResolveInfo? = reactContext.packageManager.resolveActivity(homeIntent, 0)

            if (resolveInfo?.activityInfo != null) {
                val resolvedPackage = resolveInfo.activityInfo.packageName

                if (resolvedPackage == "android" ||
                    resolvedPackage.startsWith("com.android.internal") ||
                    resolvedPackage.contains("resolver") ||
                    resolvedPackage.contains("chooser")) {
                    promise.resolve(false)
                    return
                }

                promise.resolve(resolvedPackage == myPackage)
                return
            }

            promise.resolve(false)
        } catch (e: Exception) {
            promise.reject("Error", e)
        }
    }

    @ReactMethod
    fun openSettings() {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        reactContext.startActivity(intent)
    }
}
