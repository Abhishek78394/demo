package com.screenista.demo

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.util.Log
import android.view.inputmethod.InputMethodManager

object DeviceOwnerRestrictions {
    private const val TAG = "DeviceOwnerRestrictions"

    private fun getDpm(context: Context): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private fun getAdminComponent(context: Context): ComponentName =
        ComponentName(context, AdminReceiver::class.java)

    fun isDeviceOwner(context: Context): Boolean {
        val dpm = getDpm(context)
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    fun setAppInstallBlocked(context: Context, blocked: Boolean) {
        if (!isDeviceOwner(context)) {
            throw IllegalStateException("App is not device owner")
        }

        val dpm = getDpm(context)
        val admin = getAdminComponent(context)

        if (blocked) {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
            Log.d(TAG, "App installation blocked")
        } else {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
            Log.d(TAG, "App installation unblocked")
        }
    }

    fun setUsbAccessBlocked(context: Context, blocked: Boolean) {
        if (!isDeviceOwner(context)) {
            throw IllegalStateException("App is not device owner")
        }

        val dpm = getDpm(context)
        val admin = getAdminComponent(context)

        if (blocked) {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_USB_FILE_TRANSFER)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dpm.canUsbDataSignalingBeDisabled()) {
                dpm.setUsbDataSignalingEnabled(false)
            }
            Log.d(TAG, "USB access blocked")
        } else {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_USB_FILE_TRANSFER)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dpm.canUsbDataSignalingBeDisabled()) {
                dpm.setUsbDataSignalingEnabled(true)
            }
            Log.d(TAG, "USB access unblocked")
        }
    }

    fun setDebuggingBlocked(context: Context, blocked: Boolean) {
        if (!isDeviceOwner(context)) {
            throw IllegalStateException("App is not device owner")
        }

        val dpm = getDpm(context)
        val admin = getAdminComponent(context)

        if (blocked) {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
            Log.d(TAG, "Debugging blocked")
        } else {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
            Log.d(TAG, "Debugging unblocked")
        }
    }

    fun setFactoryResetBlocked(context: Context, blocked: Boolean) {
        if (!isDeviceOwner(context)) {
            throw IllegalStateException("App is not device owner")
        }

        val dpm = getDpm(context)
        val admin = getAdminComponent(context)

        if (blocked) {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
            Log.d(TAG, "Factory reset blocked")
        } else {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
            Log.d(TAG, "Factory reset unblocked")
        }
    }

    fun applyDefaultLockdown(context: Context) {
        if (!isDeviceOwner(context)) {
            Log.w(TAG, "Skipping lockdown — app is not device owner")
            return
        }

        setAppInstallBlocked(context, true)
        setUsbAccessBlocked(context, true)
        setDebuggingBlocked(context, true)
        setFactoryResetBlocked(context, true)
        Log.d(TAG, "Default device owner lockdown applied")
    }

    fun isAppInstallBlocked(context: Context): Boolean {
        if (!isDeviceOwner(context)) return false
        val restrictions = getDpm(context).getUserRestrictions(getAdminComponent(context))
        return restrictions.getBoolean(UserManager.DISALLOW_INSTALL_APPS, false)
    }

    fun isUsbAccessBlocked(context: Context): Boolean {
        if (!isDeviceOwner(context)) return false
        val restrictions = getDpm(context).getUserRestrictions(getAdminComponent(context))
        return restrictions.getBoolean(UserManager.DISALLOW_USB_FILE_TRANSFER, false)
    }

    fun isDebuggingBlocked(context: Context): Boolean {
        if (!isDeviceOwner(context)) return false
        val restrictions = getDpm(context).getUserRestrictions(getAdminComponent(context))
        return restrictions.getBoolean(UserManager.DISALLOW_DEBUGGING_FEATURES, false)
    }

    fun isFactoryResetBlocked(context: Context): Boolean {
        if (!isDeviceOwner(context)) return false
        val restrictions = getDpm(context).getUserRestrictions(getAdminComponent(context))
        return restrictions.getBoolean(UserManager.DISALLOW_FACTORY_RESET, false)
    }

    /**
     * Packages that must never be suspended or hidden. Suspending/hiding
     * boot-critical system components (SystemUI, launchers, input methods,
     * settings, ...) persists across reboots and leaves the device stuck on
     * the boot screen.
     */
    private fun getProtectedPackages(context: Context): Set<String> {
        val protectedPackages = mutableSetOf(
            context.packageName,
            "com.android.settings",
            "com.android.tv.settings",
        )

        val pm = context.packageManager

        // Anything that can act as a HOME launcher
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val homeResolvers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.queryIntentActivities(homeIntent, PackageManager.MATCH_ALL)
        } else {
            pm.queryIntentActivities(homeIntent, 0)
        }
        homeResolvers.forEach { protectedPackages.add(it.activityInfo.packageName) }

        // Input methods (on-screen keyboards)
        try {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.inputMethodList
                ?.forEach { protectedPackages.add(it.packageName) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not enumerate input methods: ${e.message}")
        }

        return protectedPackages
    }

    private fun getLauncherQueryFlags(includeHidden: Boolean): Int {
        var flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PackageManager.MATCH_ALL
        } else {
            0
        }
        if (includeHidden) {
            flags = flags or
                PackageManager.MATCH_DISABLED_COMPONENTS or
                PackageManager.MATCH_UNINSTALLED_PACKAGES
        }
        return flags
    }

    /**
     * Packages with a launcher icon (phone drawer or TV leanback launcher).
     * Used for suspend/hide so background services and boot-critical components
     * are never touched.
     */
    private fun getLauncherPackageNames(context: Context, includeHidden: Boolean = false): List<String> {
        val pm = context.packageManager
        val flags = getLauncherQueryFlags(includeHidden)
        val packageNames = mutableSetOf<String>()

        val launcherCategories = listOf(
            Intent.CATEGORY_LAUNCHER,
            Intent.CATEGORY_LEANBACK_LAUNCHER,
        )
        for (category in launcherCategories) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            pm.queryIntentActivities(intent, flags).forEach {
                packageNames.add(it.activityInfo.packageName)
            }
        }

        // Hidden/suspended apps no longer appear in launcher queries — include them for recovery.
        if (includeHidden && isDeviceOwner(context)) {
            val dpm = getDpm(context)
            val admin = getAdminComponent(context)
            val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(
                    PackageManager.PackageInfoFlags.of(
                        PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong(),
                    ),
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES)
            }
            for (pkg in installed) {
                try {
                    val hidden = dpm.isApplicationHidden(admin, pkg.packageName)
                    val suspended = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                        dpm.isPackageSuspended(admin, pkg.packageName)
                    if (hidden || suspended) {
                        packageNames.add(pkg.packageName)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not check restricted state for ${pkg.packageName}: ${e.message}")
                }
            }
        }

        return packageNames.toList()
    }

    fun getInstalledPackages(context: Context): List<Map<String, Any>> {
        val pm = context.packageManager

        return getLauncherPackageNames(context)
            .mapNotNull { packageName ->
                try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    mapOf(
                        "packageName" to packageName,
                        "label" to label,
                        "isSystemApp" to ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0),
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping launcher package $packageName: ${e.message}")
                    null
                }
            }
            .sortedBy { (it["label"] as String).lowercase() }
    }

    fun setPackagesSuspended(
        context: Context,
        packageNames: List<String>,
        suspended: Boolean,
    ): List<String> {
        if (!isDeviceOwner(context)) {
            throw IllegalStateException("App is not device owner")
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            throw UnsupportedOperationException("Package suspension requires API 24+")
        }

        // Only guard against protected packages when restricting; unsuspending
        // them is always safe and helps recover a misconfigured device.
        val protectedPackages = if (suspended) getProtectedPackages(context) else setOf(context.packageName)

        val filtered = packageNames
            .filter { it.isNotBlank() && !protectedPackages.contains(it) }
            .distinct()
            .toTypedArray()

        if (filtered.isEmpty()) {
            return emptyList()
        }

        val dpm = getDpm(context)
        val admin = getAdminComponent(context)
        val failed = dpm.setPackagesSuspended(admin, filtered, suspended)
        Log.d(TAG, "Packages ${if (suspended) "suspended" else "unsuspended"}: ${filtered.size - failed.size}/${filtered.size}")
        return failed.toList()
    }

    fun setPackagesHidden(
        context: Context,
        packageNames: List<String>,
        hidden: Boolean,
    ): List<String> {
        if (!isDeviceOwner(context)) {
            throw IllegalStateException("App is not device owner")
        }

        val dpm = getDpm(context)
        val admin = getAdminComponent(context)
        val failed = mutableListOf<String>()

        // Only guard against protected packages when hiding; unhiding them is
        // always safe and helps recover a misconfigured device.
        val protectedPackages = if (hidden) getProtectedPackages(context) else setOf(context.packageName)

        for (pkg in packageNames.filter { it.isNotBlank() && !protectedPackages.contains(it) }.distinct()) {
            try {
                if (!dpm.setApplicationHidden(admin, pkg, hidden)) {
                    failed.add(pkg)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to ${if (hidden) "hide" else "unhide"} $pkg: ${e.message}")
                failed.add(pkg)
            }
        }

        Log.d(TAG, "Packages ${if (hidden) "hidden" else "unhidden"}: ${packageNames.size - failed.size}/${packageNames.size}")
        return failed
    }

    fun suspendAllPackages(context: Context): List<String> {
        if (!isDeviceOwner(context)) {
            throw IllegalStateException("App is not device owner")
        }

        return setPackagesSuspended(context, getLauncherPackageNames(context), true)
    }

    fun unsuspendAllPackages(context: Context): List<String> {
        if (!isDeviceOwner(context)) {
            throw IllegalStateException("App is not device owner")
        }

        return setPackagesSuspended(context, getLauncherPackageNames(context, includeHidden = true), false)
    }

    fun hideAllPackages(context: Context): List<String> {
        if (!isDeviceOwner(context)) {
            throw IllegalStateException("App is not device owner")
        }

        return setPackagesHidden(context, getLauncherPackageNames(context), true)
    }

    fun unhideAllPackages(context: Context): List<String> {
        if (!isDeviceOwner(context)) {
            throw IllegalStateException("App is not device owner")
        }

        return setPackagesHidden(context, getLauncherPackageNames(context, includeHidden = true), false)
    }

}



