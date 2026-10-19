package com.betcoin.testing

import android.net.Uri
import com.betcoin.data.avatar.CameraCaptureStore

/** [CameraCaptureStore] for JVM tests. */
class FakeCameraCaptureStore : CameraCaptureStore {

  /** Uri returned by the next [createCaptureUri]. */
  var nextUri: Uri = testUri()

  /** When set, [createCaptureUri] throws it. */
  var createFailure: Exception? = null

  /** Uris passed to [discard], in call order. */
  val discarded = mutableListOf<Uri>()

  override suspend fun createCaptureUri(): Uri {
    createFailure?.let { throw it }
    return nextUri
  }

  override suspend fun discard(uri: Uri) {
    discarded += uri
  }
}
