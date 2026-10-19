package com.betcoin.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.data.repository.BetRepository
import com.betcoin.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for the Home Screen.
 *
 * Exposes active bet count and user count for the hub UI.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val betRepository: BetRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    /** Number of active bets. Collected only while the UI is subscribed (plus a 5 s grace period). */
    val activeBetCount: StateFlow<Int> = betRepository.getActiveBets()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Number of registered users. Collected only while the UI is subscribed (plus a 5 s grace period). */
    val userCount: StateFlow<Int> = userRepository.getAllUsers()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
