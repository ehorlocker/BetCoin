package com.betcoin.di

import com.betcoin.data.repository.BetRepository
import com.betcoin.data.repository.SettingsRepository
import com.betcoin.data.repository.UserRepository
import com.betcoin.data.repository.local.LocalBetRepository
import com.betcoin.data.repository.local.LocalSettingsRepository
import com.betcoin.data.repository.local.LocalUserRepository
import com.google.common.truth.Truth.assertThat
import dagger.Module
import org.junit.Test
import javax.inject.Singleton

/**
 * Tests for [RepositoryModule] class structure.
 *
 * Verifies that RepositoryModule is a Hilt module that binds the 3 repository
 * interfaces to their local implementations.
 */
class RepositoryModuleTest {

    @Test
    fun repositoryModule_isAnnotatedWithModule() {
        val annotation = RepositoryModule::class.java.getAnnotation(Module::class.java)
        assertThat(annotation).isNotNull()
    }

    @Test
    fun repositoryModule_isAbstractClass() {
        assertThat(
            java.lang.reflect.Modifier.isAbstract(RepositoryModule::class.java.modifiers)
        ).isTrue()
    }

    @Test
    fun repositoryModule_bindsUserRepository() {
        val method = RepositoryModule::class.java.declaredMethods.find {
            it.name == "bindUserRepository"
        }
        assertThat(method).isNotNull()
        assertThat(method!!.returnType).isEqualTo(UserRepository::class.java)
        assertThat(method.parameterTypes).asList().contains(LocalUserRepository::class.java)
    }

    @Test
    fun repositoryModule_bindsBetRepository() {
        val method = RepositoryModule::class.java.declaredMethods.find {
            it.name == "bindBetRepository"
        }
        assertThat(method).isNotNull()
        assertThat(method!!.returnType).isEqualTo(BetRepository::class.java)
        assertThat(method.parameterTypes).asList().contains(LocalBetRepository::class.java)
    }

    @Test
    fun repositoryModule_bindsSettingsRepository() {
        val method = RepositoryModule::class.java.declaredMethods.find {
            it.name == "bindSettingsRepository"
        }
        assertThat(method).isNotNull()
        assertThat(method!!.returnType).isEqualTo(SettingsRepository::class.java)
        assertThat(method.parameterTypes).asList().contains(LocalSettingsRepository::class.java)
    }

    @Test
    fun repositoryModule_hasThreeBindsMethods() {
        val bindsMethods = RepositoryModule::class.java.declaredMethods.filter {
            it.name.startsWith("bind")
        }
        assertThat(bindsMethods).hasSize(3)
    }

    @Test
    fun localUserRepository_isSingleton() {
        val annotation = LocalUserRepository::class.java.getAnnotation(Singleton::class.java)
        assertThat(annotation).isNotNull()
    }

    @Test
    fun localBetRepository_isSingleton() {
        val annotation = LocalBetRepository::class.java.getAnnotation(Singleton::class.java)
        assertThat(annotation).isNotNull()
    }
}
