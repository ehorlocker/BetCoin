package com.betcoin.data.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.betcoin.data.model.InvalidImageException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [AvatarStore] that keeps avatars as JPEG files in `filesDir/avatars/<uuid>.jpg`. */
@Singleton
class LocalAvatarStore @Inject constructor(
  @ApplicationContext private val context: Context,
) : AvatarStore {

  private val avatarsDir: File
    get() = File(context.filesDir, AVATARS_DIR)

  override suspend fun save(source: Uri): String = withContext(Dispatchers.IO) {
    val dir = avatarsDir
    val temp = File(dir, "${UUID.randomUUID()}.tmp")
    try {
      val bitmap = decodeScaledAndRotated(source)
      try {
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
          throw IOException("Couldn't create $dir")
        }
        temp.outputStream().use { out ->
          if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
            throw IOException("JPEG encoding failed")
          }
        }
      } finally {
        bitmap.recycle()
      }
      val ref = "${UUID.randomUUID()}.jpg"
      if (!temp.renameTo(File(dir, ref))) throw IOException("Couldn't move avatar into place")
      ref
    } catch (e: InvalidImageException) {
      temp.delete()
      throw e
    } catch (e: IOException) {
      temp.delete()
      throw InvalidImageException().apply { initCause(e) }
    } catch (e: RuntimeException) {
      temp.delete()
      throw InvalidImageException().apply { initCause(e) }
    } catch (e: OutOfMemoryError) {
      temp.delete()
      throw InvalidImageException().apply { initCause(e) }
    }
  }

  override suspend fun delete(avatarRef: String) {
    withContext(Dispatchers.IO) { fileFor(avatarRef)?.delete() }
  }

  override suspend fun load(avatarRef: String): Any? = withContext(Dispatchers.IO) {
    fileFor(avatarRef)?.takeIf { it.isFile }
  }

  /** The file for [avatarRef], or null when the ref could escape the avatars directory. */
  private fun fileFor(avatarRef: String): File? {
    if (avatarRef.isEmpty() || '/' in avatarRef || '\\' in avatarRef || ".." in avatarRef) {
      return null
    }
    return File(avatarsDir, avatarRef)
  }

  private fun decodeScaledAndRotated(source: Uri): Bitmap {
    val resolver = context.contentResolver

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(source).use { input ->
      if (input == null) throw InvalidImageException()
      BitmapFactory.decodeStream(input, null, bounds)
    }
    val width = bounds.outWidth
    val height = bounds.outHeight
    if (width <= 0 || height <= 0) throw InvalidImageException()

    // Largest power-of-two sample size that keeps the longest side at or above the target.
    val longestSide = maxOf(width, height)
    var sampleSize = 1
    while (longestSide / (sampleSize * 2) >= MAX_SIDE) sampleSize *= 2
    val decoded = resolver.openInputStream(source).use { input ->
      if (input == null) throw InvalidImageException()
      BitmapFactory.decodeStream(
        input,
        null,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
      )
    } ?: throw InvalidImageException()

    try {
      val orientation = resolver.openInputStream(source).use { input ->
        if (input == null) {
          ExifInterface.ORIENTATION_NORMAL
        } else {
          ExifInterface(input).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
          )
        }
      }

      val matrix = Matrix()
      val longest = maxOf(decoded.width, decoded.height)
      if (longest > MAX_SIDE) {
        val scale = MAX_SIDE.toFloat() / longest
        matrix.postScale(scale, scale)
      }
      applyOrientation(matrix, orientation)

      // Draw onto white so transparent areas don't turn black in the JPEG.
      val bounds = RectF(0f, 0f, decoded.width.toFloat(), decoded.height.toFloat())
      matrix.mapRect(bounds)
      val outWidth = maxOf(1, bounds.width().roundToInt())
      val outHeight = maxOf(1, bounds.height().roundToInt())
      val result = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
      try {
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        canvas.translate(-bounds.left, -bounds.top)
        canvas.drawBitmap(decoded, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
      } catch (t: Throwable) {
        result.recycle()
        throw t
      }
      return result
    } finally {
      decoded.recycle()
    }
  }

  private fun applyOrientation(matrix: Matrix, orientation: Int) {
    when (orientation) {
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
      ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
      ExifInterface.ORIENTATION_TRANSPOSE -> {
        matrix.postRotate(90f)
        matrix.postScale(-1f, 1f)
      }
      ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
      ExifInterface.ORIENTATION_TRANSVERSE -> {
        matrix.postRotate(-90f)
        matrix.postScale(-1f, 1f)
      }
      ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(-90f)
    }
  }

  private companion object {
    const val AVATARS_DIR = "avatars"
    const val MAX_SIDE = 512
    const val JPEG_QUALITY = 85
  }
}
