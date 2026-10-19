package com.betcoin.devtools

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NoOpDevToolsEntryTest {
    @Test
    fun isDevToolsEntry() {
        assertThat(NoOpDevToolsEntry()).isInstanceOf(DevToolsEntry::class.java)
    }
}
