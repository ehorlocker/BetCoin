package com.betcoin.data.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.betcoin.data.model.InvalidImageException
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented tests for [LocalAvatarStore]: downscaling, EXIF rotation, cleanup and lookup. */
@RunWith(AndroidJUnit4::class)
class LocalAvatarStoreTest {

  private lateinit var context: Context
  private lateinit var store: LocalAvatarStore
  private lateinit var avatarsDir: File

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    avatarsDir = File(context.filesDir, "avatars")
    avatarsDir.deleteRecursively()
    store = LocalAvatarStore(context)
  }

  @After
  fun tearDown() {
    avatarsDir.deleteRecursively()
  }

  private fun sampleJpeg(width: Int, height: Int): File {
    val file = File.createTempFile("sample", ".jpg", context.cacheDir)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    bitmap.recycle()
    return file
  }

  private fun boundsOf(file: File): Pair<Int, Int> {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    return options.outWidth to options.outHeight
  }

  private suspend fun savedFile(ref: String): File {
    val loaded = store.load(ref)
    assertThat(loaded).isInstanceOf(File::class.java)
    return loaded as File
  }

  @Test
  fun save_largeImage_downscalesTo512KeepingAspect() = runBlocking {
    val ref = store.save(Uri.fromFile(sampleJpeg(4000, 3000)))

    assertThat(ref).endsWith(".jpg")
    val file = savedFile(ref)
    assertThat(file.parentFile).isEqualTo(avatarsDir)
    assertThat(file.name).isEqualTo(ref)
    assertThat(boundsOf(file)).isEqualTo(512 to 384)
  }

  @Test
  fun save_smallImage_isNotUpscaled() = runBlocking {
    val ref = store.save(Uri.fromFile(sampleJpeg(200, 100)))

    assertThat(boundsOf(savedFile(ref))).isEqualTo(200 to 100)
  }

  @Test
  fun save_exifRotated_appliesOrientation() = runBlocking {
    val source = File.createTempFile("quadrant", ".jpg", context.cacheDir)
    val bitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.BLUE)
    Canvas(bitmap).drawRect(0f, 0f, 200f, 100f, Paint().apply { color = Color.RED })
    source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    ExifInterface(source).apply {
      setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
      saveAttributes()
    }

    val ref = store.save(Uri.fromFile(source))

    val saved = savedFile(ref)
    assertThat(boundsOf(saved)).isEqualTo(200 to 400)
    // ROTATE_90 is clockwise: the red top-left quadrant moves to the top-right.
    val out = BitmapFactory.decodeFile(saved.absolutePath)
    assertThat(isNear(out.getPixel(150, 50), Color.RED)).isTrue()
    assertThat(isNear(out.getPixel(50, 50), Color.BLUE)).isTrue()
    assertThat(isNear(out.getPixel(150, 350), Color.BLUE)).isTrue()
  }

  @Test
  fun save_tallNarrowImage_downscalesLongestSideTo512() = runBlocking {
    val ref = store.save(Uri.fromFile(sampleJpeg(600, 6000)))

    val (w, h) = boundsOf(savedFile(ref))
    assertThat(h).isEqualTo(512)
    assertThat(Math.abs(w - 51)).isAtMost(1)
  }

  @Test
  fun save_transparentPng_isFlattenedOntoWhite() = runBlocking {
    val source = File.createTempFile("clear", ".png", context.cacheDir)
    val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.TRANSPARENT)
    source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    val ref = store.save(Uri.fromFile(source))

    val out = BitmapFactory.decodeFile(savedFile(ref).absolutePath)
    assertThat(isNear(out.getPixel(50, 50), Color.WHITE)).isTrue()
  }

  private fun isNear(actual: Int, expected: Int, tolerance: Int = 40): Boolean =
    Math.abs(Color.red(actual) - Color.red(expected)) <= tolerance &&
      Math.abs(Color.green(actual) - Color.green(expected)) <= tolerance &&
      Math.abs(Color.blue(actual) - Color.blue(expected)) <= tolerance

  @Test
  fun save_corruptInput_throwsAndLeavesNothing() {
    val bad = File.createTempFile("bad", ".jpg", context.cacheDir)
    bad.writeText("this is definitely not an image")

    assertThrows(InvalidImageException::class.java) {
      runBlocking { store.save(Uri.fromFile(bad)) }
    }

    assertThat(avatarsDir.listFiles()?.toList().orEmpty()).isEmpty()
  }

  @Test
  fun save_missingSource_throwsInvalidImage() {
    val missing = File(context.cacheDir, "does-not-exist.jpg")

    assertThrows(InvalidImageException::class.java) {
      runBlocking { store.save(Uri.fromFile(missing)) }
    }
  }

  @Test
  fun save_writesJpeg() = runBlocking {
    val ref = store.save(Uri.fromFile(sampleJpeg(100, 100)))
    val file = savedFile(ref)

    val header = file.inputStream().use { input -> ByteArray(2).also { input.read(it) } }
    assertThat(header[0]).isEqualTo(0xFF.toByte())
    assertThat(header[1]).isEqualTo(0xD8.toByte())
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    assertThat(options.outMimeType).isEqualTo("image/jpeg")
  }

  @Test
  fun save_twice_returnsDistinctRefs() = runBlocking {
    val uri = Uri.fromFile(sampleJpeg(100, 100))

    assertThat(store.save(uri)).isNotEqualTo(store.save(uri))
  }

  @Test
  fun delete_removesFile() = runBlocking {
    val ref = store.save(Uri.fromFile(sampleJpeg(100, 100)))

    store.delete(ref)

    assertThat(store.load(ref)).isNull()
  }

  @Test
  fun delete_missing_isNoOp() = runBlocking {
    store.delete("missing.jpg")
  }

  @Test
  fun load_missing_returnsNull() = runBlocking {
    assertThat(store.load("missing.jpg")).isNull()
  }

  @Test
  fun load_pathTraversalRef_returnsNull() = runBlocking {
    File(context.filesDir, "foo.jpg").writeText("x")
    try {
      assertThat(store.load("../foo.jpg")).isNull()
    } finally {
      File(context.filesDir, "foo.jpg").delete()
    }
  }
}
