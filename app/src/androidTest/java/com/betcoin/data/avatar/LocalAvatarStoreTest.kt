package com.betcoin.data.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

  private fun savedFile(ref: String): File {
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
    val source = sampleJpeg(400, 200)
    ExifInterface(source).apply {
      setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
      saveAttributes()
    }

    val ref = store.save(Uri.fromFile(source))

    assertThat(boundsOf(savedFile(ref))).isEqualTo(200 to 400)
  }

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
  fun load_missing_returnsNull() {
    assertThat(store.load("missing.jpg")).isNull()
  }

  @Test
  fun load_pathTraversalRef_returnsNull() {
    File(context.filesDir, "foo.jpg").writeText("x")
    try {
      assertThat(store.load("../foo.jpg")).isNull()
    } finally {
      File(context.filesDir, "foo.jpg").delete()
    }
  }
}
