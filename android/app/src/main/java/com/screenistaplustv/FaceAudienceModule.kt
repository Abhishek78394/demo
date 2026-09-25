package com.screenistaplustv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * On-device audience classification (offline):
 * 1) ML Kit finds faces in a still image
 * 2) TFLite age + gender models classify the largest face
 * 3) Maps to demo categories: child | male | female
 */
class FaceAudienceModule(private val reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  companion object {
    private const val AGE_MODEL = "model_lite_age_q.tflite"
    private const val GENDER_MODEL = "model_lite_gender_q.tflite"
    private const val AGE_INPUT = 200
    private const val GENDER_INPUT = 128
    private const val AGE_SCALE = 116f
    private const val CHILD_MAX_AGE = 15
    /** Ignore gender if male/female scores are this close (avoid flicker). */
    private const val GENDER_CONFIDENCE_GAP = 0.08f
  }

  private val executor = Executors.newSingleThreadExecutor()
  private val ready = AtomicBoolean(false)

  private var ageInterpreter: Interpreter? = null
  private var genderInterpreter: Interpreter? = null

  private val faceDetector by lazy {
    val options = FaceDetectorOptions.Builder()
      .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
      .setMinFaceSize(0.08f)
      .build()
    FaceDetection.getClient(options)
  }

  private val ageProcessor = ImageProcessor.Builder()
    .add(ResizeOp(AGE_INPUT, AGE_INPUT, ResizeOp.ResizeMethod.BILINEAR))
    .add(NormalizeOp(0f, 255f))
    .build()

  private val genderProcessor = ImageProcessor.Builder()
    .add(ResizeOp(GENDER_INPUT, GENDER_INPUT, ResizeOp.ResizeMethod.BILINEAR))
    .add(NormalizeOp(0f, 255f))
    .build()

  override fun getName(): String = "FaceAudience"

  @ReactMethod
  fun prepare(promise: Promise) {
    executor.execute {
      try {
        ensureModelsLoaded()
        promise.resolve(true)
      } catch (e: Exception) {
        promise.reject("MODEL_LOAD_FAILED", e.message, e)
      }
    }
  }

  @ReactMethod
  fun classifyImage(path: String, promise: Promise) {
    executor.execute {
      try {
        ensureModelsLoaded()
        val cleanPath = path.removePrefix("file://")
        val bitmap = BitmapFactory.decodeFile(cleanPath)
          ?: throw IllegalArgumentException("Could not decode image: $path")

        val image = InputImage.fromBitmap(bitmap, 0)
        faceDetector.process(image)
          .addOnSuccessListener { faces ->
            executor.execute {
              try {
                if (faces.isEmpty()) {
                  val map = Arguments.createMap()
                  map.putBoolean("faceFound", false)
                  promise.resolve(map)
                  return@execute
                }

                val largest = faces.maxByOrNull {
                  it.boundingBox.width() * it.boundingBox.height()
                }!!
                val faceBmp = cropSafe(bitmap, largest.boundingBox)
                val age = predictAge(faceBmp)
                val genderScores = predictGender(faceBmp)
                val maleScore = genderScores[0]
                val femaleScore = genderScores[1]
                val isMale = maleScore > femaleScore
                val genderGap = kotlin.math.abs(maleScore - femaleScore)
                val years = floor(age.toDouble()).toInt()
                val isChild = years <= CHILD_MAX_AGE
                // For adults, require a clear gender winner; otherwise stay sticky (JS keeps last ad).
                val uncertain = !isChild && genderGap < GENDER_CONFIDENCE_GAP
                val gender = if (isMale) "male" else "female"
                val category = when {
                  isChild -> "child"
                  uncertain -> null
                  isMale -> "male"
                  else -> "female"
                }

                val map = Arguments.createMap()
                map.putBoolean("faceFound", true)
                map.putBoolean("uncertain", uncertain)
                map.putDouble("age", age.toDouble())
                map.putInt("ageYears", years)
                map.putString("gender", gender)
                map.putDouble("maleScore", maleScore.toDouble())
                map.putDouble("femaleScore", femaleScore.toDouble())
                if (category != null) {
                  map.putString("category", category)
                }
                promise.resolve(map)
              } catch (e: Exception) {
                promise.reject("CLASSIFY_FAILED", e.message, e)
              }
            }
          }
          .addOnFailureListener { e ->
            promise.reject("FACE_DETECT_FAILED", e.message, e)
          }
      } catch (e: Exception) {
        promise.reject("CLASSIFY_FAILED", e.message, e)
      }
    }
  }

  private fun ensureModelsLoaded() {
    if (ready.get() && ageInterpreter != null && genderInterpreter != null) return
    synchronized(this) {
      if (ready.get() && ageInterpreter != null && genderInterpreter != null) return
      val options = Interpreter.Options().apply { setNumThreads(2) }
      ageInterpreter = Interpreter(FileUtil.loadMappedFile(reactContext, AGE_MODEL), options)
      genderInterpreter = Interpreter(FileUtil.loadMappedFile(reactContext, GENDER_MODEL), options)
      ready.set(true)
    }
  }

  private fun predictAge(face: Bitmap): Float {
    val tensor = TensorImage.fromBitmap(face)
    val input = ageProcessor.process(tensor).buffer
    val output = Array(1) { FloatArray(1) }
    ageInterpreter!!.run(input, output)
    return output[0][0] * AGE_SCALE
  }

  private fun predictGender(face: Bitmap): FloatArray {
    val tensor = TensorImage.fromBitmap(face)
    val input = genderProcessor.process(tensor).buffer
    val output = Array(1) { FloatArray(2) }
    genderInterpreter!!.run(input, output)
    return output[0]
  }

  private fun cropSafe(image: Bitmap, box: Rect): Bitmap {
    // Pad face box slightly — models expect more context than a tight crop.
    val padX = (box.width() * 0.25f).toInt()
    val padY = (box.height() * 0.25f).toInt()
    val left = max(0, box.left - padX)
    val top = max(0, box.top - padY)
    val right = min(image.width, box.right + padX)
    val bottom = min(image.height, box.bottom + padY)
    val width = max(1, right - left)
    val height = max(1, bottom - top)
    return Bitmap.createBitmap(image, left, top, width, height)
  }

  override fun invalidate() {
    super.invalidate()
    ageInterpreter?.close()
    genderInterpreter?.close()
    ageInterpreter = null
    genderInterpreter = null
    ready.set(false)
    faceDetector.close()
    executor.shutdownNow()
  }
}
