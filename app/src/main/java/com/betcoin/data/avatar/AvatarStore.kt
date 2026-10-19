package com.betcoin.data.avatar

import android.net.Uri

/**
 * Stores profile-picture images and hands out opaque references to them.
 *
 * Callers only ever see an `avatarRef` (a plain file name such as `<uuid>.jpg`) and the opaque
 * model returned by [load]; where and how the bytes are kept is private to the implementation.
 */
interface AvatarStore {

  /**
   * Reads the image at [source], downscales it to at most 512x512 (aspect ratio kept, EXIF
   * orientation applied), re-encodes it as JPEG and stores it.
   *
   * @return the new `avatarRef`, e.g. `"<uuid>.jpg"`
   * @throws com.betcoin.data.model.InvalidImageException if [source] can't be read or decoded;
   *   nothing is stored in that case
   */
  suspend fun save(source: Uri): String

  /** Removes the image for [avatarRef]. Does nothing if it doesn't exist. */
  suspend fun delete(avatarRef: String)

  /**
   * Returns an opaque image model for [avatarRef] that an image loader can display (a
   * [java.io.File] for the local implementation), or null if the image is missing.
   */
  fun load(avatarRef: String): Any?
}
