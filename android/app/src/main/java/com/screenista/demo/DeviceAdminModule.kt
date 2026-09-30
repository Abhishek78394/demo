package com.screenista.demo

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import java.io.File

class DeviceAdminModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        private const val PERSISTENT_PREFS = "screenista_persistent"
        private const val KEY_MAC_ID = "unique_id"
    }

    override fun getName() = "DeviceAdminModule"

    private fun persistentPrefs() =
        reactContext.getSharedPreferences(PERSISTENT_PREFS, Context.MODE_PRIVATE)

    @ReactMethod
    fun getPersistentMacId(promise: Promise) {
        try {
            promise.resolve(persistentPrefs().getString(KEY_MAC_ID, "") ?: "")
        } catch (e: Exception) {
            Log.e("DeviceAdminModule", "getPersistentMacId failed: ${e.message}", e)
            promise.reject("MAC_ID_READ_ERROR", e.message ?: "Unknown error", e)
        }
    }

    @ReactMethod
    fun setPersistentMacId(macId: String, promise: Promise) {
        try {
            val trimmed = macId.trim()
            if (trimmed.isEmpty()) {
                promise.reject("MAC_ID_INVALID", "MAC ID cannot be empty")
                return
            }

            val committed = persistentPrefs().edit().putString(KEY_MAC_ID, trimmed).commit()
            if (committed) {
                Log.d("DeviceAdminModule", "Persistent MAC ID saved")
                promise.resolve(trimmed)
            } else {
                promise.reject("MAC_ID_WRITE_ERROR", "SharedPreferences commit failed")
            }
        } catch (e: Exception) {
            Log.e("DeviceAdminModule", "setPersistentMacId failed: ${e.message}", e)
            promise.reject("MAC_ID_WRITE_ERROR", e.message ?: "Unknown error", e)
        }
    }

    @ReactMethod
    fun clearPersistentMacId(promise: Promise) {
        try {
            persistentPrefs().edit().remove(KEY_MAC_ID).commit()
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e("DeviceAdminModule", "clearPersistentMacId failed: ${e.message}", e)
            promise.reject("MAC_ID_CLEAR_ERROR", e.message ?: "Unknown error", e)
        }
    }

    private fun readableArrayToStringList(array: ReadableArray): List<String> {
        val list = mutableListOf<String>()
        for (i in 0 until array.size()) {
            array.getString(i)?.let { list.add(it) }
        }
        return list
    }

    @ReactMethod
    fun getInstalledPackages(promise: Promise) {
        try {
            val packages = DeviceOwnerRestrictions.getInstalledPackages(reactContext)
            val array = Arguments.createArray()
            for (pkg in packages) {
                val map = Arguments.createMap()
                map.putString("packageName", pkg["packageName"] as String)
                map.putString("label", pkg["label"] as String)
                map.putBoolean("isSystemApp", pkg["isSystemApp"] as Boolean)
                array.pushMap(map)
            }
            promise.resolve(array)
        } catch (e: Exception) {
            Log.e("DeviceAdminModule", "getInstalledPackages failed: ${e.message}", e)
            promise.reject("PACKAGE_LIST_ERROR", e.message ?: "Unknown error", e)
        }
    }

    @ReactMethod
    fun setPackagesSuspended(packageNames: ReadableArray, suspended: Boolean, promise: Promise) {
        try {
            val failed = DeviceOwnerRestrictions.setPackagesSuspended(
                reactContext,
                readableArrayToStringList(packageNames),
                suspended,
            )
            promise.resolve(Arguments.fromList(failed))
        } catch (e: Exception) {
            Log.e("DeviceAdminModule", "setPackagesSuspended failed: ${e.message}", e)
            promise.reject("PACKAGE_SUSPEND_ERROR", e.message ?: "Unknown error", e)
        }
    }

    @ReactMethod
    fun setPackagesHidden(packageNames: ReadableArray, hidden: Boolean, promise: Promise) {
        try {
            val failed = DeviceOwnerRestrictions.setPackagesHidden(
                reactContext,
                readableArrayToStringList(packageNames),
                hidden,
            )
            promise.resolve(Arguments.fromList(failed))
        } catch (e: Exception) {
            Log.e("DeviceAdminModule", "setPackagesHidden failed: ${e.message}", e)
            promise.reject("PACKAGE_HIDE_ERROR", e.message ?: "Unknown error", e)
        }
    }

    @ReactMethod
    fun setPersistentHomeLauncher(promise: Promise) {
        try {
            promise.resolve(HomeLauncherManager.setAsPersistentHome(reactContext))
        } catch (e: Exception) {
            Log.e("DeviceAdminModule", "setPersistentHomeLauncher failed: ${e.message}", e)
            promise.reject("PERSISTENT_HOME_ERROR", e.message ?: "Unknown error", e)
        }
    }

    @ReactMethod
    fun isDeviceOwner(promise: Promise) {
        try {
            val packageName = reactContext.packageName
            val dpm = reactContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            promise.resolve(dpm.isDeviceOwnerApp(packageName))
        } catch (e: Exception) {
            Log.e("DeviceAdminModule", "isDeviceOwner failed: ${e.message}", e)
            promise.reject("DEVICE_OWNER_ERROR", e.message ?: "Unknown error", e)
        }
    }

    @ReactMethod
    fun clearApplicationUserData(promise: Promise) {
        try {
            val packageName = reactContext.packageName
            val dpm = reactContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

            if (!dpm.isDeviceOwnerApp(packageName)) {
                promise.reject("NOT_DEVICE_OWNER", "Device owner privileges required to clear app data")
                return
            }

            // DPM clearApplicationUserData cannot wipe the device-owner app itself
            // (device admin apps are protected). Try pm clear, then manual wipe.
            if (clearOwnAppDataViaShell(packageName)) {
                promise.resolve(true)
                return
            }

            finishClearWithManualFallback(promise, "pm clear failed")
        } catch (e: Exception) {
            Log.e("DeviceAdminModule", "clearApplicationUserData failed: ${e.message}", e)
            promise.reject("CLEAR_DATA_ERROR", e.message ?: "Unknown error", e)
        }
    }

    private fun finishClearWithManualFallback(promise: Promise, reason: String) {
        try {
            if (clearOwnAppDataManually(reactContext)) {
                promise.resolve(true)
            } else {
                promise.reject("CLEAR_DATA_ERROR", "Failed to clear application user data: $reason")
            }
        } catch (e: Exception) {
            Log.e("DeviceAdminModule", "Manual clear fallback failed: ${e.message}", e)
            promise.reject("CLEAR_DATA_ERROR", e.message ?: "Unknown error", e)
        }
    }

    private fun clearOwnAppDataViaShell(packageName: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("pm", "clear", packageName))
            val exitCode = process.waitFor()
            Log.d("DeviceAdminModule", "Runtime pm clear exit code: $exitCode")
            exitCode == 0
        } catch (e: Exception) {
            Log.w("DeviceAdminModule", "Runtime pm clear failed: ${e.message}")
            false
        }
    }

    private fun clearOwnAppDataManually(context: Context): Boolean {
        return try {
            val appContext = context.applicationContext
            val dataDir = appContext.dataDir

            listOf("shared_prefs", "databases", "files", "app_webview", "no_backup").forEach { subDir ->
                deleteContents(File(dataDir, subDir))
            }
            deleteContents(appContext.cacheDir)
            appContext.getExternalFilesDir(null)?.let { deleteContents(it) }
            appContext.externalCacheDir?.let { deleteContents(it) }

            Handler(Looper.getMainLooper()).postDelayed({
                val launchIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
                launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                if (launchIntent != null) {
                    appContext.startActivity(launchIntent)
                }
                android.os.Process.killProcess(android.os.Process.myPid())
            }, 300)

            true
        } catch (e: Exception) {
            Log.e("DeviceAdminModule", "Manual app data clear failed: ${e.message}", e)
            false
        }
    }

    private fun deleteContents(dir: File?) {
        if (dir == null || !dir.exists()) return

        if (dir.isDirectory) {
            dir.listFiles()?.forEach { child ->
                deleteRecursively(child)
            }
            return
        }

        deleteRecursively(dir)
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                deleteRecursively(child)
            }
        }
        file.delete()
    }

    @ReactMethod
    fun setAppInstallBlocked(blocked: Boolean, promise: Promise) {
        try {
            DeviceOwnerRestrictions.setAppInstallBlocked(reactContext, blocked)
            promise.resolve(blocked)
        } catch (e: Exception) {
            Log.e("DeviceAdminModule", "setAppInstallBlocked failed: ${e.message}", e)
            promise.reject("RESTRICTION_ERROR", e.message ?: "Unknown error", e)
        }
    }

    @ReactMethod
    fun setUsbAccessBlocked(blocked: Boolean, promise: Promise) {
        try {
            DeviceOwnerRestrictions.setUsbAccessBlocked(reactContext, blocked)
            promise.resolve(blocked)
        } catch (e: Exception) {
            Log.e("DeviceAdminModule", "setUsbAccessBlocked failed: ${e.message}", e)
            promise.reject("RESTRICTION_ERROR", e.message ?: "Unknown error", e)
        }
    }

    @ReactMethod
    fun setDebuggingBlocked(blocked: Boolean, promise: Promise) {
        try {
            DeviceOwnerRestrictions.setDebuggingBlocked(reactContext, blocked)
            promise.resolve(blocked)
        } catch (e: Exception) {
            Log.e("DeviceAdminModule", "setDebuggingBlocked failed: ${e.message}", e)
            promise.reject("RESTRICTION_ERROR", e.message ?: "Unknown error", e)
        }
    }

    @ReactMethod
    fun setFactoryResetBlocked(blocked: Boolean, promise: Promise) {
        try {
            DeviceOwnerRestrictions.setFactoryResetBlocked(reactContext, blocked)
            promise.resolve(blocked)
        } catch (e: Exception) {
            Log.e("DeviceAdminModule", "setFactoryResetBlocked failed: ${e.message}", e)
            promise.reject("RESTRICTION_ERROR", e.message ?: "Unknown error", e)
        }
    }

    @ReactMethod
    fun applyDeviceOwnerLockdown(promise: Promise) {
        try {
            DeviceOwnerRestrictions.applyDefaultLockdown(reactContext)
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e("DeviceAdminModule", "applyDeviceOwnerLockdown failed: ${e.message}", e)
            promise.reject("RESTRICTION_ERROR", e.message ?: "Unknown error", e)
        }
    }

    @ReactMethod
    fun isAppInstallBlocked(promise: Promise) {
        try {
            promise.resolve(DeviceOwnerRestrictions.isAppInstallBlocked(reactContext))
        } catch (e: Exception) {
            Log.e("DeviceAdminModule", "isAppInstallBlocked failed: ${e.message}", e)
            promise.reject("RESTRICTION_ERROR", e.message ?: "Unknown error", e)
        }
    }

    @ReactMethod
    fun isUsbAccessBlocked(promise: Promise) {
        try {
            promise.resolve(DeviceOwnerRestrictions.isUsbAccessBlocked(reactContext))
        } catch (e: Exception) {
            Log.e("DeviceAdminModule", "isUsbAccessBlocked failed: ${e.message}", e)
            promise.reject("RESTRICTION_ERROR", e.message ?: "Unknown error", e)
        }
    }

    @ReactMethod
    fun isDebuggingBlocked(promise: Promise) {
        try {
            promise.resolve(DeviceOwnerRestrictions.isDebuggingBlocked(reactContext))
        } catch (e: Exception) {
            Log.e("DeviceAdminModule", "isDebuggingBlocked failed: ${e.message}", e)
            promise.reject("RESTRICTION_ERROR", e.message ?: "Unknown error", e)
        }
    }

    @ReactMethod
    fun isFactoryResetBlocked(promise: Promise) {
        try {
            promise.resolve(DeviceOwnerRestrictions.isFactoryResetBlocked(reactContext))
        } catch (e: Exception) {
            Log.e("DeviceAdminModule", "isFactoryResetBlocked failed: ${e.message}", e)
            promise.reject("RESTRICTION_ERROR", e.message ?: "Unknown error", e)
        }
    }

    @ReactMethod
    fun suspendAllPackages(promise: Promise) {
        try {
            val failed = DeviceOwnerRestrictions.suspendAllPackages(reactContext)
            promise.resolve(Arguments.fromList(failed))
        } catch (e: Exception) {
            Log.e("DeviceAdminModule", "suspendAllPackages failed: ${e.message}", e)
            promise.reject("RESTRICTION_ERROR", e.message ?: "Unknown error", e)
        }
    }

    @ReactMethod
    fun unsuspendAllPackages(promise: Promise) {
        try {
            val failed = DeviceOwnerRestrictions.unsuspendAllPackages(reactContext)
            promise.resolve(Arguments.fromList(failed))
        } catch (e: Exception) {
            Log.e("DeviceAdminModule", "unsuspendAllPackages failed: ${e.message}", e)
            promise.reject("RESTRICTION_ERROR", e.message ?: "Unknown error", e)
        }
    }

    @ReactMethod
    fun hideAllPackages(promise: Promise) {
        try {
            val failed = DeviceOwnerRestrictions.hideAllPackages(reactContext)
            promise.resolve(Arguments.fromList(failed))
        } catch (e: Exception) {
            Log.e("DeviceAdminModule", "hideAllPackages failed: ${e.message}", e)
            promise.reject("RESTRICTION_ERROR", e.message ?: "Unknown error", e)
        }
    }

    @ReactMethod
    fun unhideAllPackages(promise: Promise) {
        try {
            val failed = DeviceOwnerRestrictions.unhideAllPackages(reactContext)
            promise.resolve(Arguments.fromList(failed))
        } catch (e: Exception) {
            Log.e("DeviceAdminModule", "unhideAllPackages failed: ${e.message}", e)
            promise.reject("RESTRICTION_ERROR", e.message ?: "Unknown error", e)
        }
    }

    @ReactMethod
    fun removeAdminAndUninstall(promise: Promise) {
        try {
            val packageName = reactContext.packageName
            val dpm = reactContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(reactContext, AdminReceiver::class.java)

            // Step 1: Clear device owner if we are one.
            // On standard Android/emulator this works cleanly.
            // On some Android TV/Box ROMs it throws due to missing telephony provider —
            // we catch and ignore that since device owner may still get cleared.
            if (dpm.isDeviceOwnerApp(packageName)) {
                HomeLauncherManager.clearPersistentHome(reactContext)

                try {
                    dpm.clearDeviceOwnerApp(packageName)
                    Log.d("DeviceAdminModule", "Device owner cleared")
                } catch (e: Exception) {
                    Log.w("DeviceAdminModule", "clearDeviceOwnerApp threw: ${e.message}")
                }
            }

            // Step 2: Remove active admin if still set
            if (dpm.isAdminActive(adminComponent)) {
                try {
                    dpm.removeActiveAdmin(adminComponent)
                    Log.d("DeviceAdminModule", "Active admin removed")
                } catch (e: Exception) {
                    Log.w("DeviceAdminModule", "removeActiveAdmin threw: ${e.message}")
                }
            }

            // Step 3: Wait briefly for the system to propagate admin removal,
            // then launch the standard uninstall dialog
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra(Intent.EXTRA_RETURN_RESULT, false)
                    }
                    Log.d("DeviceAdminModule", "Launching uninstall dialog for: $packageName")
                    reactContext.startActivity(intent)
                    promise.resolve(true)
                } catch (e: Exception) {
                    Log.e("DeviceAdminModule", "Failed to launch uninstall dialog: ${e.message}", e)
                    promise.reject("UNINSTALL_ERROR", e.message ?: "Unknown error", e)
                }
            }, 800)

        } catch (e: Exception) {
            Log.e("DeviceAdminModule", "Uninstall failed: ${e.message}", e)
            promise.reject("UNINSTALL_ERROR", e.message ?: "Unknown error", e)
        }
    }
}