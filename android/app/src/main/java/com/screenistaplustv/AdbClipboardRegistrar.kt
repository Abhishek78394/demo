package com.screenistaplustv

import android.app.Application
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.util.Log

object AdbClipboardRegistrar {
    private const val TAG = "AdbClipboard"
    private var receiver: AdbClipboardReceiver? = null

    fun register(application: Application) {
        if (receiver != null) return

        try {
            receiver = AdbClipboardReceiver()
            val filter = IntentFilter(AdbClipboardModule.ACTION_SET_TEXT)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                application.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                application.registerReceiver(receiver, filter)
            }
        } catch (e: Exception) {
            receiver = null
            Log.w(TAG, "Failed to register ADB clipboard receiver: ${e.message}", e)
        }
    }
}
