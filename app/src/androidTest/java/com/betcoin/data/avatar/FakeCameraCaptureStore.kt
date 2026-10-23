package com.betcoin.data.avatar

import android.net.Uri

/** Minimal [CameraCaptureStore] for instrumented tests that never launch the camera. */
class FakeCameraCaptureStore : CameraCaptureStore {

  override suspend fun createCaptureUri(): Uri = Uri.parse("content://fake/capture.jpg")

  override suspend fun discard(uri: Uri) = Unit
}
