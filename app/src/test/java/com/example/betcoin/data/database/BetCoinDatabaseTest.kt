package com.example.betcoin.data.database

import com.example.betcoin.data.database.dao.AppSettingsDao
import com.example.betcoin.data.database.dao.BetDao
import com.example.betcoin.data.database.dao.BetOutcomeDao
import com.example.betcoin.data.database.dao.BetParticipantDao
import com.example.betcoin.data.database.dao.UserDao
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [BetCoinDatabase] structure: DAO accessor presence and class hierarchy.
 *
 * Room annotations have CLASS retention and are not available at runtime, so annotation-based
 * checks are verified at compile time by the Room annotation processor instead. These tests
 * verify the class structure and DAO accessor methods.
 */
class BetCoinDatabaseTest {

  @Test
  fun databaseClass_isAbstract() {
    assertThat(java.lang.reflect.Modifier.isAbstract(BetCoinDatabase::class.java.modifiers))
      .isTrue()
  }

  @Test
  fun databaseClass_extendsRoomDatabase() {
    assertThat(androidx.room.RoomDatabase::class.java.isAssignableFrom(BetCoinDatabase::class.java))
      .isTrue()
  }

  @Test
  fun databaseClass_hasUserDaoAccessor() {
    val method = BetCoinDatabase::class.java.getDeclaredMethod("userDao")
    assertThat(method.returnType).isEqualTo(UserDao::class.java)
  }

  @Test
  fun databaseClass_hasBetDaoAccessor() {
    val method = BetCoinDatabase::class.java.getDeclaredMethod("betDao")
    assertThat(method.returnType).isEqualTo(BetDao::class.java)
  }

  @Test
  fun databaseClass_hasBetOutcomeDaoAccessor() {
    val method = BetCoinDatabase::class.java.getDeclaredMethod("betOutcomeDao")
    assertThat(method.returnType).isEqualTo(BetOutcomeDao::class.java)
  }

  @Test
  fun databaseClass_hasBetParticipantDaoAccessor() {
    val method = BetCoinDatabase::class.java.getDeclaredMethod("betParticipantDao")
    assertThat(method.returnType).isEqualTo(BetParticipantDao::class.java)
  }

  @Test
  fun databaseClass_hasAppSettingsDaoAccessor() {
    val method = BetCoinDatabase::class.java.getDeclaredMethod("appSettingsDao")
    assertThat(method.returnType).isEqualTo(AppSettingsDao::class.java)
  }

  @Test
  fun databaseClass_hasFiveDaoAccessors() {
    val expectedDaoTypes = setOf(
      UserDao::class.java,
      BetDao::class.java,
      BetOutcomeDao::class.java,
      BetParticipantDao::class.java,
      AppSettingsDao::class.java,
    )
    val daoMethods = BetCoinDatabase::class.java.declaredMethods.filter { method ->
      method.returnType in expectedDaoTypes
    }
    assertThat(daoMethods).hasSize(5)
  }
}
