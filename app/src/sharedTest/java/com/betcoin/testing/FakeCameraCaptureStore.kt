package com.betcoin.testing

import android.net.Uri
import com.betcoin.data.avatar.CameraCaptureStore

/** [CameraCaptureStore] shared by unit and instrumented tests; never launches the camera. */
class FakeCameraCaptureStore : CameraCaptureStore {

  /** Uri returned by the next [createCaptureUri]. */
  var nextUri: Uri = testUri()

  /** When set, [createCaptureUri] throws it. */
  var createFailure: Exception? = null

  /** Uris passed to [discard], in call order. */
  val discarded = mutableListOf<Uri>()

  /** Number of successful [createCaptureUri] calls. */
  var createCount = 0
    private set

  override suspend fun createCaptureUri(): Uri {
    createFailure?.let { throw it }
    createCount++
    return nextUri
  }

  override suspend fun discard(uri: Uri) {
    discarded += uri
  }
}
