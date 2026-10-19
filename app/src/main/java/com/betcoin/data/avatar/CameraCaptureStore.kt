package com.betcoin.data.avatar

import android.net.Uri

/** Temp files the camera app writes into for the TakePicture contract. */
interface CameraCaptureStore {

  /** Creates an empty temp file and returns a content:// Uri the camera app may write to. */
  suspend fun createCaptureUri(): Uri

  /** Deletes the temp file behind [uri]. Missing file or foreign Uri is a no-op. */
  suspend fun discard(uri: Uri)
}
