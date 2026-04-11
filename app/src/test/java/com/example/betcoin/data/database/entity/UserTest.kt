package com.example.betcoin.data.database.entity

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [User] entity data class construction and default values.
 */
class UserTest {

  @Test
  fun defaultValues_areCorrect() {
    val user = User(
      username = "testuser",
      pinHash = "hashed_pin",
      createdAt = 1000L,
    )

    assertThat(user.id).isEqualTo(0L)
    assertThat(user.balance).isEqualTo(1000L)
    assertThat(user.totalWins).isEqualTo(0)
    assertThat(user.totalLosses).isEqualTo(0)
    assertThat(user.totalEarnings).isEqualTo(0L)
    assertThat(user.totalLost).isEqualTo(0L)
    assertThat(user.bailoutCount).isEqualTo(0)
    assertThat(user.totalDebt).isEqualTo(0L)
  }

  @Test
  fun requiredFields_areSetCorrectly() {
    val user = User(
      username = "alice",
      pinHash = "abc123hash",
      createdAt = 1712793600000L,
    )

    assertThat(user.username).isEqualTo("alice")
    assertThat(user.pinHash).isEqualTo("abc123hash")
    assertThat(user.createdAt).isEqualTo(1712793600000L)
  }

  @Test
  fun allFields_areSetCorrectly() {
    val user = User(
      id = 42L,
      username = "bob",
      pinHash = "hash",
      balance = 5000L,
      totalWins = 10,
      totalLosses = 5,
      totalEarnings = 3000L,
      totalLost = 1500L,
      bailoutCount = 2,
      totalDebt = 2000L,
      createdAt = 1712793600000L,
    )

    assertThat(user.id).isEqualTo(42L)
    assertThat(user.username).isEqualTo("bob")
    assertThat(user.balance).isEqualTo(5000L)
    assertThat(user.totalWins).isEqualTo(10)
    assertThat(user.totalLosses).isEqualTo(5)
    assertThat(user.totalEarnings).isEqualTo(3000L)
    assertThat(user.totalLost).isEqualTo(1500L)
    assertThat(user.bailoutCount).isEqualTo(2)
    assertThat(user.totalDebt).isEqualTo(2000L)
  }

  @Test
  fun copy_createsIndependentInstance() {
    val original = User(
      username = "alice",
      pinHash = "hash",
      createdAt = 1000L,
    )
    val copy = original.copy(balance = 2000L)

    assertThat(copy.balance).isEqualTo(2000L)
    assertThat(original.balance).isEqualTo(1000L)
    assertThat(copy.username).isEqualTo(original.username)
  }

  @Test
  fun currencyFields_useLongType() {
    val user = User(
      username = "test",
      pinHash = "hash",
      balance = Long.MAX_VALUE,
      totalEarnings = Long.MAX_VALUE,
      totalLost = Long.MAX_VALUE,
      totalDebt = Long.MAX_VALUE,
      createdAt = 1000L,
    )

    assertThat(user.balance).isEqualTo(Long.MAX_VALUE)
    assertThat(user.totalEarnings).isEqualTo(Long.MAX_VALUE)
    assertThat(user.totalLost).isEqualTo(Long.MAX_VALUE)
    assertThat(user.totalDebt).isEqualTo(Long.MAX_VALUE)
  }
}
