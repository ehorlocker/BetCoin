package com.betcoin.data.avatar

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented tests for [LocalCameraCaptureStore]. */
@RunWith(AndroidJUnit4::class)
class LocalCameraCaptureStoreTest {

  private lateinit var context: Context
  private lateinit var store: LocalCameraCaptureStore
  private lateinit var captureDir: File

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    captureDir = File(context.cacheDir, "camera")
    captureDir.deleteRecursively()
    store = LocalCameraCaptureStore(context)
  }

  @After
  fun tearDown() {
    captureDir.deleteRecursively()
  }

  @Test
  fun createCaptureUri_returnsContentUriUnderFileProviderAuthority() = runBlocking {
    val uri = store.createCaptureUri()

    assertThat(uri.scheme).isEqualTo("content")
    assertThat(uri.authority).isEqualTo("${context.packageName}.fileprovider")
    context.contentResolver.openOutputStream(uri)!!.use { it.write(byteArrayOf(1, 2, 3)) }
  }

  @Test
  fun createCaptureUri_returnsDistinctUris() = runBlocking {
    assertThat(store.createCaptureUri()).isNotEqualTo(store.createCaptureUri())
  }

  @Test
  fun discard_deletesTheFile() = runBlocking {
    val uri = store.createCaptureUri()
    context.contentResolver.openOutputStream(uri)!!.use { it.write(byteArrayOf(1, 2, 3)) }

    store.discard(uri)

    assertThrows(FileNotFoundException::class.java) {
      context.contentResolver.openInputStream(uri)
    }
    Unit
  }

  @Test
  fun discard_unknownUri_isNoOp() = runBlocking {
    store.discard(Uri.parse("content://other/x"))
  }
}
