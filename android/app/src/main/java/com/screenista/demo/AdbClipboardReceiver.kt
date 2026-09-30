package com.screenista.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AdbClipboardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            if (context == null || intent?.action != AdbClipboardModule.ACTION_SET_TEXT) return

            val text = intent.getStringExtra(AdbClipboardModule.EXTRA_TEXT)
            if (text.isNullOrBlank()) {
                Log.w("AdbClipboard", "Received broadcast without text extra")
                return
            }

            val normalized = text.trim().take(AdbClipboardStore.MAX_TEXT_LENGTH)
            if (normalized.isEmpty()) {
                Log.w("AdbClipboard", "Received broadcast without usable text")
                return
            }

            val appContext = context.applicationContext
            if (!AdbClipboardStore.saveText(appContext, normalized)) {
                Log.w("AdbClipboard", "ADB text was not saved")
                return
            }

            Log.d("AdbClipboard", "Text received via ADB (${normalized.length} chars)")
            AdbClipboardModule.emitTextReceived(normalized)
        } catch (e: Exception) {
            Log.w("AdbClipboard", "Broadcast handling failed: ${e.message}", e)
        }
    }
}
