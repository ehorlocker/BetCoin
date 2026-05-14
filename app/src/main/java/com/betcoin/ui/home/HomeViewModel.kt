package com.betcoin.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.data.repository.BetRepository
import com.betcoin.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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

    private val _activeBetCount = MutableStateFlow(0)
    val activeBetCount: StateFlow<Int> = _activeBetCount

    private val _userCount = MutableStateFlow(0)
    val userCount: StateFlow<Int> = _userCount

    init {
        viewModelScope.launch {
            betRepository.getActiveBets().collectLatest { bets ->
                _activeBetCount.value = bets.size
            }
        }
        viewModelScope.launch {
            userRepository.getAllUsers().collectLatest { users ->
                _userCount.value = users.size
            }
        }
    }
}
