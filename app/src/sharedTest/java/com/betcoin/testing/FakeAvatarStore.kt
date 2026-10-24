package com.betcoin.testing

import android.net.Uri
import com.betcoin.data.avatar.AvatarStore
import com.betcoin.data.model.InvalidImageException

/**
 * In-memory [AvatarStore] shared by unit and instrumented tests. Ignores the source [Uri]; [save]
 * returns `"fake-<n>.jpg"`, and [failNextSave] makes the next [save] throw [InvalidImageException].
 */
class FakeAvatarStore : AvatarStore {

  /** Refs currently stored. */
  val saved: MutableSet<String> = mutableSetOf()

  /** Refs passed to [delete], in call order. */
  val deleted: MutableList<String> = mutableListOf()

  /** Images returned by [load], keyed by avatarRef. A stored ref without an entry loads as itself. */
  val images = mutableMapOf<String, Any>()

  private val _loadCalls = mutableListOf<String>()

  /** Refs passed to [load], in call order. */
  val loadCalls: List<String> get() = _loadCalls

  /** When true, the next [save] throws [InvalidImageException] (then resets to false). */
  var failNextSave: Boolean = false

  /** Runs inside [save] after the new ref is stored, so tests can interleave other writes. */
  var onSave: (suspend () -> Unit)? = null

  private var counter = 0

  override suspend fun save(source: Uri): String {
    if (failNextSave) {
      failNextSave = false
      throw InvalidImageException()
    }
    val ref = "fake-${++counter}.jpg"
    saved += ref
    onSave?.invoke()
    return ref
  }

  override suspend fun delete(avatarRef: String) {
    deleted += avatarRef
    saved -= avatarRef
    images.remove(avatarRef)
  }

  override suspend fun load(avatarRef: String): Any? {
    _loadCalls += avatarRef
    return images[avatarRef] ?: if (avatarRef in saved) avatarRef else null
  }
}
