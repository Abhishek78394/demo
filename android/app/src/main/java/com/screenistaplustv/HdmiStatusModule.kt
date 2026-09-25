package com.screenistaplustv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.Display
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.io.File

/**
 * Detects whether an HDMI cable / display is connected on Android TV boxes.
 *
 * Uses (in order of reliability for STBs):
 *  1. Sticky android.intent.action.HDMI_PLUGGED broadcast
 *  2. Common sysfs HDMI HPD paths (Amlogic / Rockchip / DRM)
 *  3. AudioManager HDMI audio plug state
 *  4. DisplayManager external display presence
 */
class HdmiStatusModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext), LifecycleEventListener {

    companion object {
        private const val TAG = "HdmiStatus"
        const val EVENT_HDMI_STATUS = "onHdmiStatusChanged"
        private const val ACTION_HDMI_PLUGGED = "android.intent.action.HDMI_PLUGGED"

        private val SYSFS_PATHS = listOf(
            "/sys/class/switch/hdmi/state",
            "/sys/devices/virtual/switch/hdmi/state",
            "/sys/class/amhdmitx/amhdmitx0/hpd_state",
            "/sys/class/drm/card0-HDMI-A-1/status",
            "/sys/class/drm/card0-HDMI-A-2/status",
            "/sys/devices/platform/display-subsystem/drm/card0/card0-HDMI-A-1/status",
        )
    }

    private var lastConnected: Boolean? = null
    private var receiverRegistered = false

    private val hdmiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            val connected = when (intent.action) {
                ACTION_HDMI_PLUGGED ->
                    intent.getBooleanExtra("state", false)

                AudioManager.ACTION_HDMI_AUDIO_PLUG ->
                    intent.getIntExtra(AudioManager.EXTRA_AUDIO_PLUG_STATE, 0) == 1

                else -> return
            }

            Log.d(TAG, "Broadcast ${intent.action} → connected=$connected")
            emitIfChanged(connected, source = intent.action ?: "broadcast")
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            Log.d(TAG, "Display added: $displayId")
            refreshAndEmit("displayAdded")
        }

        override fun onDisplayRemoved(displayId: Int) {
            Log.d(TAG, "Display removed: $displayId")
            refreshAndEmit("displayRemoved")
        }

        override fun onDisplayChanged(displayId: Int) {
            refreshAndEmit("displayChanged")
        }
    }

    init {
        reactContext.addLifecycleEventListener(this)
        registerReceivers()
    }

    override fun getName() = "HdmiStatusModule"

    override fun invalidate() {
        unregisterReceivers()
        reactContext.removeLifecycleEventListener(this)
        super.invalidate()
    }

    override fun onHostResume() {
        registerReceivers()
        refreshAndEmit("hostResume")
    }

    override fun onHostPause() {}

    override fun onHostDestroy() {
        unregisterReceivers()
    }

    @ReactMethod
    fun isHdmiConnected(promise: Promise) {
        try {
            promise.resolve(resolveConnected())
        } catch (e: Exception) {
            Log.e(TAG, "isHdmiConnected failed: ${e.message}", e)
            promise.reject("HDMI_STATUS_ERROR", e.message ?: "Unknown error", e)
        }
    }

    @ReactMethod
    fun getHdmiStatus(promise: Promise) {
        try {
            promise.resolve(buildStatusMap())
        } catch (e: Exception) {
            Log.e(TAG, "getHdmiStatus failed: ${e.message}", e)
            promise.reject("HDMI_STATUS_ERROR", e.message ?: "Unknown error", e)
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

    private fun resolveConnected(): Boolean {
        readStickyHdmiPlugged()?.let { return it }
        readSysfsHdmi()?.let { return it }
        readHdmiAudioPlug()?.let { return it }
        return hasActiveExternalDisplay()
    }

    private fun buildStatusMap(): WritableMap {
        val sticky = readStickyHdmiPlugged()
        val sysfs = readSysfsHdmi()
        val audio = readHdmiAudioPlug()
        val display = hasActiveExternalDisplay()
        val connected = sticky ?: sysfs ?: audio ?: display

        return Arguments.createMap().apply {
            putBoolean("connected", connected)
            if (sticky != null) putBoolean("stickyBroadcast", sticky)
            if (sysfs != null) putBoolean("sysfs", sysfs)
            if (audio != null) putBoolean("hdmiAudio", audio)
            putBoolean("externalDisplay", display)
            putString(
                "source",
                when {
                    sticky != null -> "stickyBroadcast"
                    sysfs != null -> "sysfs"
                    audio != null -> "hdmiAudio"
                    else -> "displayManager"
                },
            )
        }
    }

    /** Sticky HDMI_PLUGGED intent — primary signal on most Android TV boxes. */
    private fun readStickyHdmiPlugged(): Boolean? {
        return try {
            val filter = IntentFilter(ACTION_HDMI_PLUGGED)
            val sticky: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                reactContext.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                reactContext.registerReceiver(null, filter)
            }
            if (sticky == null) null else sticky.getBooleanExtra("state", false)
        } catch (e: Exception) {
            Log.w(TAG, "readStickyHdmiPlugged failed: ${e.message}")
            null
        }
    }

    /** Sysfs hot-plug detect — common on Amlogic / Rockchip STBs. */
    private fun readSysfsHdmi(): Boolean? {
        for (path in SYSFS_PATHS) {
            try {
                val file = File(path)
                if (!file.exists() || !file.canRead()) continue

                val value = file.readText().trim().lowercase()
                val connected = when (value) {
                    "1", "connected" -> true
                    "0", "disconnected" -> false
                    else -> continue
                }
                Log.d(TAG, "sysfs $path → $value ($connected)")
                return connected
            } catch (e: Exception) {
                Log.w(TAG, "sysfs read failed for $path: ${e.message}")
            }
        }
        return null
    }

    private fun readHdmiAudioPlug(): Boolean? {
        return try {
            val filter = IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG)
            val sticky: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                reactContext.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                reactContext.registerReceiver(null, filter)
            }
            if (sticky == null) null
            else sticky.getIntExtra(AudioManager.EXTRA_AUDIO_PLUG_STATE, 0) == 1
        } catch (e: Exception) {
            Log.w(TAG, "readHdmiAudioPlug failed: ${e.message}")
            null
        }
    }

    private fun hasActiveExternalDisplay(): Boolean {
        return try {
            val dm = reactContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            dm.displays.any { display ->
                display.displayId != Display.DEFAULT_DISPLAY &&
                    display.state == Display.STATE_ON
            }
        } catch (e: Exception) {
            Log.w(TAG, "hasActiveExternalDisplay failed: ${e.message}")
            false
        }
    }

    private fun refreshAndEmit(source: String) {
        try {
            emitIfChanged(resolveConnected(), source)
        } catch (e: Exception) {
            Log.w(TAG, "refreshAndEmit failed: ${e.message}")
        }
    }

    private fun emitIfChanged(connected: Boolean, source: String) {
        if (lastConnected == connected) return
        lastConnected = connected

        val payload = Arguments.createMap().apply {
            putBoolean("connected", connected)
            putString("source", source)
        }

        try {
            if (!reactContext.hasActiveReactInstance()) return
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                ?.emit(EVENT_HDMI_STATUS, payload)
            Log.d(TAG, "Emitted $EVENT_HDMI_STATUS connected=$connected source=$source")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to emit HDMI status: ${e.message}", e)
        }
    }

    private fun registerReceivers() {
        if (receiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(ACTION_HDMI_PLUGGED)
                addAction(AudioManager.ACTION_HDMI_AUDIO_PLUG)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                reactContext.registerReceiver(hdmiReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                reactContext.registerReceiver(hdmiReceiver, filter)
            }

            val dm = reactContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            dm.registerDisplayListener(displayListener, null)

            receiverRegistered = true
            Log.d(TAG, "HDMI receivers registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register HDMI receivers: ${e.message}", e)
        }
    }

    private fun unregisterReceivers() {
        if (!receiverRegistered) return
        try {
            reactContext.unregisterReceiver(hdmiReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "unregisterReceiver: ${e.message}")
        }
        try {
            val dm = reactContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            dm.unregisterDisplayListener(displayListener)
        } catch (e: Exception) {
            Log.w(TAG, "unregisterDisplayListener: ${e.message}")
        }
        receiverRegistered = false
    }
}
