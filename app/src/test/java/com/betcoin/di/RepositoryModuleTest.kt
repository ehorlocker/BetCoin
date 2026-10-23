package com.betcoin.di

import com.betcoin.data.avatar.CameraCaptureStore
import com.betcoin.data.avatar.LocalCameraCaptureStore
import com.betcoin.data.repository.BetRepository
import com.betcoin.data.repository.SettingsRepository
import com.betcoin.data.repository.UserRepository
import com.betcoin.data.repository.local.LocalBetRepository
import com.betcoin.data.repository.local.LocalSettingsRepository
import com.betcoin.data.repository.local.LocalUserRepository
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dagger.Binds
import dagger.Module
import javax.inject.Singleton
import org.junit.Test

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
    fun repositoryModule_bindsCameraCaptureStore() {
        val method = RepositoryModule::class.java.declaredMethods.find {
            it.name == "bindCameraCaptureStore"
        }
        assertThat(method).isNotNull()
        assertThat(method!!.returnType).isEqualTo(CameraCaptureStore::class.java)
        assertThat(method.parameterTypes).asList().contains(LocalCameraCaptureStore::class.java)
    }

    @Test
    fun repositoryModule_hasFiveBindsMethods() {
        val bindsMethods = RepositoryModule::class.java.declaredMethods.filter {
            it.isAnnotationPresent(Binds::class.java)
        }
        assertThat(bindsMethods).hasSize(5)
    }

    @Test
    fun repositoryModule_everyBindsMethodIsSingleton() {
        val bindsMethods = RepositoryModule::class.java.declaredMethods.filter {
            it.isAnnotationPresent(Binds::class.java)
        }
        assertThat(bindsMethods).isNotEmpty()
        bindsMethods.forEach { method ->
            assertWithMessage(method.name)
                .that(method.isAnnotationPresent(Singleton::class.java))
                .isTrue()
        }
    }
}
