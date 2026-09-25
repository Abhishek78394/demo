package com.screenistaplustv

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.UiThreadUtil

/** Prefs + activity orientation for the demo player. */
class EnvConfigModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "EnvConfig"

    override fun getConstants(): MutableMap<String, Any> {
        return hashMapOf(
            "API_ENV" to BuildConfig.API_ENV,
            "SERVER_BASE_URL" to BuildConfig.SERVER_BASE_URL,
            "IS_DEV_API" to (BuildConfig.API_ENV == "dev"),
        )
    }

    private fun prefs() =
        reactContext.getSharedPreferences("screenista_demo_prefs", Context.MODE_PRIVATE)

    @ReactMethod
    fun getPref(key: String, promise: Promise) {
        try {
            promise.resolve(prefs().getString(key, null))
        } catch (e: Exception) {
            promise.reject("PREF_GET_FAILED", e.message, e)
        }
    }

    @ReactMethod
    fun setPref(key: String, value: String, promise: Promise) {
        try {
            prefs().edit().putString(key, value).apply()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("PREF_SET_FAILED", e.message, e)
        }
    }

    /**
     * Rotate the Activity so ads / UI physically match 0 / 90 / 180 / 270.
     * More reliable than CSS transform for ExoPlayer surfaces.
     */
    @ReactMethod
    fun setOrientationDegrees(degrees: Int, promise: Promise) {
        UiThreadUtil.runOnUiThread {
            try {
                val activity: Activity = reactContext.currentActivity
                    ?: throw IllegalStateException("No current activity")
                val flag = when (((degrees % 360) + 360) % 360) {
                    90 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    180 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                    270 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                activity.requestedOrientation = flag
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("ORIENTATION_FAILED", e.message, e)
            }
        }
    }
}
