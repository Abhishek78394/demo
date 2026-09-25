package com.screenistaplustv

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.Window
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ScreenCaptureModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String {
        return "ScreenCaptureModule"
    }

    @ReactMethod
    fun captureScreen(promise: Promise) {
        val activity = getCurrentActivity()
        if (activity == null) {
            promise.reject("NO_ACTIVITY", "Current activity is null")
            return
        }

        val window: Window = activity.window
        val view = window.decorView.rootView

        if (view.width <= 0 || view.height <= 0) {
            promise.reject("INVALID_DIMENSIONS", "View dimensions are invalid")
            return
        }

        try {
            // Priority 1: Check for TextureView (e.g., some video players) - easiest to capture
            val textureView = findTextureView(view)
            if (textureView != null) {
                val bitmap = textureView.getBitmap()
                if (bitmap != null) {
                    saveBitmap(bitmap, promise)
                    return
                }
            }

            // Priority 2: Check for SurfaceView (e.g., react-native-video typically)
            // Note: PixelCopy on SurfaceView captures ONLY the video, not the UI overlays.
            // However, capturing Video is often more critical than the UI if the alternative is a black screen.
            // Merge logic could be added here, but complex.
            val surfaceView = findSurfaceView(view)
            if (surfaceView != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val bitmap = Bitmap.createBitmap(surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888)
                val location = IntArray(2)
                surfaceView.getLocationInWindow(location)

                PixelCopy.request(surfaceView, bitmap, { copyResult ->
                    if (copyResult == PixelCopy.SUCCESS) {
                        saveBitmap(bitmap, promise)
                    } else {
                        // Fallback to Window capture if Surface capture fails
                        captureWindow(activity, promise)
                    }
                }, Handler(Looper.getMainLooper()))
                return
            }

            // Priority 3: Standard Window Capture (Includes UI, handles SurfaceView on API 26+)
            captureWindow(activity, promise)

        } catch (e: Exception) {
            promise.reject("CAPTURE_FAILED", "Error capturing screen: ${e.message}")
        }
    }

    private fun captureWindow(activity: Activity, promise: Promise) {
        val window = activity.window
        val view = window.decorView.rootView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val location = IntArray(2)
            view.getLocationInWindow(location)
             // Use the window directly
            PixelCopy.request(window, android.graphics.Rect(0, 0, view.width, view.height), bitmap, { copyResult ->
                if (copyResult == PixelCopy.SUCCESS) {
                    saveBitmap(bitmap, promise)
                } else {
                    promise.reject("COPY_FAILED", "PixelCopy failed with result: $copyResult")
                }
            }, Handler(Looper.getMainLooper()))
        } else {
             // API < 26 Fallback (Drawing Cache) - Won't capture Video/SurfaceView
             try {
                 val c = Canvas(bitmap)
                 view.draw(c)
                 saveBitmap(bitmap, promise)
             } catch (e: Exception) {
                 promise.reject("CAPTURE_FAILED_LOW_API", "Capture failed on low API: ${e.message}")
             }
        }
    }

    private fun findSurfaceView(view: View): SurfaceView? {
        if (view is SurfaceView) {
            return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val result = findSurfaceView(child)
                if (result != null) return result
            }
        }
        return null
    }

    private fun findTextureView(view: View): TextureView? {
        if (view is TextureView) {
            return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val result = findTextureView(child)
                if (result != null) return result
            }
        }
        return null
    }
    
    private fun saveBitmap(bitmap: Bitmap, promise: Promise) {
        try {
            val cacheDir = reactApplicationContext.cacheDir
            val fileName = "screenshot_${UUID.randomUUID()}.jpg"
            val file = File(cacheDir, fileName)
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.flush()
            outputStream.close()
            promise.resolve("file://${file.absolutePath}")
        } catch (e: Exception) {
            promise.reject("SAVE_FAILED", "Failed to save screenshot", e)
        } finally {
            bitmap.recycle()
        }
    }
}
