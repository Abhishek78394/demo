package com.screenistaplustv

import android.util.Log
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule

class AdbClipboardModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        private const val TAG = "AdbClipboard"

        const val ACTION_SET_TEXT = "com.screenistaplustv.ADB_SET_TEXT"
        const val EXTRA_TEXT = "text"
        const val EVENT_TEXT_RECEIVED = "onAdbTextReceived"

        @Volatile
        private var instance: AdbClipboardModule? = null

        fun emitTextReceived(text: String) {
            try {
                instance?.sendEvent(text)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to emit ADB text event: ${e.message}", e)
            }
        }
    }

    init {
        instance = this
    }

    override fun getName() = "AdbClipboardModule"

    override fun invalidate() {
        instance = null
        super.invalidate()
    }

    private fun sendEvent(text: String) {
        try {
            if (!reactContext.hasActiveReactInstance()) return

            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                ?.emit(EVENT_TEXT_RECEIVED, text)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send ADB text event: ${e.message}", e)
        }
    }

    @ReactMethod
    fun getPendingText(promise: Promise) {
        try {
            promise.resolve(AdbClipboardStore.getText(reactContext))
        } catch (e: Exception) {
            Log.w(TAG, "getPendingText failed: ${e.message}", e)
            promise.resolve("")
        }
    }

    @ReactMethod
    fun clearPendingText(promise: Promise) {
        try {
            promise.resolve(AdbClipboardStore.clearText(reactContext))
        } catch (e: Exception) {
            Log.w(TAG, "clearPendingText failed: ${e.message}", e)
            promise.resolve(false)
        }
    }

    @ReactMethod
    fun addListener(eventName: String) {
        // Required for NativeEventEmitter on RN 0.65+
    }

    @ReactMethod
    fun removeListeners(count: Int) {
        // Required for NativeEventEmitter on RN 0.65+
    }
}
