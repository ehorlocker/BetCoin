package com.betcoin.devtools

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DevDevToolsEntryTest {
    @Test
    fun routeIsDevTools() {
        assertThat(DEV_TOOLS_ROUTE).isEqualTo("dev_tools")
    }

    @Test
    fun isDevToolsEntry() {
        assertThat(DevDevToolsEntry()).isInstanceOf(DevToolsEntry::class.java)
    }
}
