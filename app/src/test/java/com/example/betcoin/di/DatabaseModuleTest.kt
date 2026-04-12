package com.example.betcoin.di

import com.example.betcoin.data.database.BetCoinDatabase
import com.example.betcoin.data.database.dao.AppSettingsDao
import com.example.betcoin.data.database.dao.BetDao
import com.example.betcoin.data.database.dao.BetOutcomeDao
import com.example.betcoin.data.database.dao.BetParticipantDao
import com.example.betcoin.data.database.dao.UserDao
import com.google.common.truth.Truth.assertThat
import dagger.Module
import org.junit.Test

/**
 * Tests for [DatabaseModule] class structure.
 *
 * Hilt annotations like @InstallIn have CLASS retention and cannot be verified via
 * runtime reflection. The @Module annotation has RUNTIME retention and can be checked.
 * The KSP processor verifies @InstallIn at compile time.
 */
class DatabaseModuleTest {

    @Test
    fun databaseModule_isAnnotatedWithModule() {
        val annotation = DatabaseModule::class.java.getAnnotation(Module::class.java)
        assertThat(annotation).isNotNull()
    }

    @Test
    fun databaseModule_isObject() {
        // Kotlin objects have a static INSTANCE field
        val instanceField = DatabaseModule::class.java.getDeclaredField("INSTANCE")
        assertThat(instanceField).isNotNull()
    }

    @Test
    fun databaseModule_hasProvidesDatabase() {
        val method = DatabaseModule::class.java.declaredMethods.find { it.name == "provideDatabase" }
        assertThat(method).isNotNull()
        assertThat(method!!.returnType).isEqualTo(BetCoinDatabase::class.java)
    }

    @Test
    fun databaseModule_providesUserDao() {
        val method = DatabaseModule::class.java.declaredMethods.find { it.name == "provideUserDao" }
        assertThat(method).isNotNull()
        assertThat(method!!.returnType).isEqualTo(UserDao::class.java)
    }

    @Test
    fun databaseModule_providesBetDao() {
        val method = DatabaseModule::class.java.declaredMethods.find { it.name == "provideBetDao" }
        assertThat(method).isNotNull()
        assertThat(method!!.returnType).isEqualTo(BetDao::class.java)
    }

    @Test
    fun databaseModule_providesBetOutcomeDao() {
        val method = DatabaseModule::class.java.declaredMethods.find { it.name == "provideBetOutcomeDao" }
        assertThat(method).isNotNull()
        assertThat(method!!.returnType).isEqualTo(BetOutcomeDao::class.java)
    }

    @Test
    fun databaseModule_providesBetParticipantDao() {
        val method = DatabaseModule::class.java.declaredMethods.find { it.name == "provideBetParticipantDao" }
        assertThat(method).isNotNull()
        assertThat(method!!.returnType).isEqualTo(BetParticipantDao::class.java)
    }

    @Test
    fun databaseModule_providesAppSettingsDao() {
        val method = DatabaseModule::class.java.declaredMethods.find { it.name == "provideAppSettingsDao" }
        assertThat(method).isNotNull()
        assertThat(method!!.returnType).isEqualTo(AppSettingsDao::class.java)
    }

    @Test
    fun databaseModule_hasSixProvidesMethods() {
        val providesMethods = DatabaseModule::class.java.declaredMethods.filter {
            it.name.startsWith("provide")
        }
        // 1 database + 5 DAOs = 6
        assertThat(providesMethods).hasSize(6)
    }
}
