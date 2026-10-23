package com.betcoin.devtools

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

/** Guards that the prod flavor contains no Dev Tools code. */
class ProdHasNoDevToolsTest {

    @Test
    fun devToolsClasses_areNotInProd() {
        listOf(
            "DevDevToolsEntry",
            "DevToolsViewModel",
            "DevDataSeeder",
            "DevDatabaseTools",
            "DevToolsScreenKt",
            "DevAppProcessKt",
        ).forEach { name ->
            assertThrows(name, ClassNotFoundException::class.java) {
                Class.forName("com.betcoin.devtools.$name")
            }
        }
    }

    @Test
    fun devToolsModule_bindsNoOpEntry() {
        val method = DevToolsModule::class.java.declaredMethods.single { it.name == "bindDevToolsEntry" }

        assertThat(method.parameterTypes.single()).isEqualTo(NoOpDevToolsEntry::class.java)
    }
}
