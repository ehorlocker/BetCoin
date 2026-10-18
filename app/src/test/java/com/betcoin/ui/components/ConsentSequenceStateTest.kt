package com.betcoin.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ConsentSequenceStateTest {

    private val steps = listOf(
        ConsentStep("Jay") { false },
        ConsentStep("Sam") { false },
    )

    @Test
    fun startsAtFirstStep() {
        val state = ConsentSequenceState(steps)

        assertThat(state.currentStep.label).isEqualTo("Jay")
        assertThat(state.progressText).isEqualTo("1 of 2")
        assertThat(state.isComplete).isFalse()
    }

    @Test
    fun confirmCurrent_advancesAndReportsCompletionOnce() {
        val state = ConsentSequenceState(steps)

        assertThat(state.confirmCurrent()).isFalse()
        assertThat(state.currentStep.label).isEqualTo("Sam")
        assertThat(state.progressText).isEqualTo("2 of 2")

        assertThat(state.confirmCurrent()).isTrue()
        assertThat(state.isComplete).isTrue()

        assertThat(state.confirmCurrent()).isFalse()
    }

    @Test
    fun emptySteps_areRejected() {
        assertThrows(IllegalArgumentException::class.java) { ConsentSequenceState(emptyList()) }
    }
}
