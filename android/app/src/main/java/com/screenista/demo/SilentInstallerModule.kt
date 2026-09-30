package com.screenista.demo

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.util.Log
import com.facebook.react.bridge.*
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream

class SilentInstallerModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "SilentInstaller"

    /**
     * Silently installs an APK using the PackageInstaller API.
     * Requires the app to be set as Device Owner via:
     *   adb shell dpm set-device-owner com.screenista.demo/.AdminReceiver
     *
     * @param apkPath  Absolute local file path to the downloaded APK
     * @param promise  Resolves on success, rejects on failure
     */
    @ReactMethod
    fun installApk(apkPath: String, promise: Promise) {
        try {
            val file = File(apkPath)
            if (!file.exists()) {
                promise.reject("FILE_NOT_FOUND", "APK file not found at path: $apkPath")
                return
            }

            val context: Context = reactApplicationContext
            val packageInstaller = context.packageManager.packageInstaller

            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )
            params.setAppPackageName(context.packageName)

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            // Write APK bytes into the session
            val inputStream: InputStream = FileInputStream(file)
            val outputStream: OutputStream = session.openWrite("package", 0, file.length())

            val buffer = ByteArray(65536)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } >= 0) {
                outputStream.write(buffer, 0, bytesRead)
            }
            session.fsync(outputStream)
            inputStream.close()
            outputStream.close()

            // Create a broadcast intent to receive install result
            val intent = Intent(context, InstallResultReceiver::class.java).apply {
                action = "com.screenista.demo.INSTALL_RESULT"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            session.commit(pendingIntent.intentSender)
            session.close()

            Log.d("SilentInstaller", "APK install session committed for: $apkPath")
            promise.resolve("Install session committed successfully")

        } catch (e: Exception) {
            Log.e("SilentInstaller", "Silent install failed: ${e.message}", e)
            promise.reject("INSTALL_FAILED", e.message ?: "Unknown error during install")
        }
    }
}
