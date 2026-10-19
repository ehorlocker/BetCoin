package com.betcoin.data.avatar

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [CameraCaptureStore] that keeps capture files in `cacheDir/camera/<uuid>.jpg`. */
@Singleton
class LocalCameraCaptureStore @Inject constructor(
  @ApplicationContext private val context: Context,
) : CameraCaptureStore {

  private val captureDir: File
    get() = File(context.cacheDir, CAPTURE_DIR)

  private val authority: String
    get() = "${context.packageName}.fileprovider"

  override suspend fun createCaptureUri(): Uri = withContext(Dispatchers.IO) {
    val dir = captureDir
    if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
      throw IOException("Couldn't create $dir")
    }
    val file = File(dir, "${UUID.randomUUID()}.jpg")
    if (!file.createNewFile()) throw IOException("Couldn't create $file")
    FileProvider.getUriForFile(context, authority, file)
  }

  override suspend fun discard(uri: Uri) {
    withContext(Dispatchers.IO) {
      if (uri.scheme != "content" || uri.authority != authority) return@withContext
      val name = uri.lastPathSegment ?: return@withContext
      if (name.isEmpty() || '/' in name || '\\' in name || ".." in name) return@withContext
      File(captureDir, name).takeIf { it.isFile }?.delete()
    }
  }

  private companion object {
    const val CAPTURE_DIR = "camera"
  }
}
