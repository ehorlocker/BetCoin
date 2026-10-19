package com.betcoin.testing

import android.net.Uri
import com.betcoin.data.avatar.AvatarStore

/** In-memory [AvatarStore] for JVM tests. */
class FakeAvatarStore : AvatarStore {

  /** Images returned by [load], keyed by avatarRef. */
  val images = mutableMapOf<String, Any>()

  private val _loadCalls = mutableListOf<String>()

  /** Refs passed to [load], in call order. */
  val loadCalls: List<String> get() = _loadCalls

  private var counter = 0

  override suspend fun save(source: Uri): String = "saved-${++counter}.jpg"

  override suspend fun delete(avatarRef: String) {
    images.remove(avatarRef)
  }

  override suspend fun load(avatarRef: String): Any? {
    _loadCalls += avatarRef
    return images[avatarRef]
  }
}
