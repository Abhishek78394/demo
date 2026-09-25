package com.screenistaplustv

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log

object AdbClipboardStore {
    private const val TAG = "AdbClipboard"
    private const val PREFS = "screenista_adb_clipboard"
    private const val KEY_TEXT = "pending_text"
    const val MAX_TEXT_LENGTH = 1024

    fun saveText(context: Context, text: String): Boolean {
        return try {
            val trimmed = text.trim().take(MAX_TEXT_LENGTH)
            if (trimmed.isEmpty()) return false

            val committed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TEXT, trimmed)
                .commit()

            if (!committed) {
                Log.w(TAG, "SharedPreferences commit failed while saving ADB text")
            }

            setSystemClipboard(context, trimmed)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save ADB text: ${e.message}", e)
            false
        }
    }

    fun getText(context: Context): String {
        return try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_TEXT, "") ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read ADB text: ${e.message}", e)
            ""
        }
    }

    fun clearText(context: Context): Boolean {
        return try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_TEXT)
                .commit()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear ADB text: ${e.message}", e)
            false
        }
    }

    private fun setSystemClipboard(context: Context, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return
            clipboard.setPrimaryClip(ClipData.newPlainText("adb", text))
        } catch (e: Exception) {
            // Non-critical: in-app paste uses SharedPreferences, not system clipboard.
            Log.w(TAG, "Failed to set system clipboard: ${e.message}", e)
        }
    }
}
