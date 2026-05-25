package com.betcoin.ui.players

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.data.database.entity.User
import com.betcoin.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PlayerDetailUiState {
    data object Loading : PlayerDetailUiState()
    data class Success(val user: User) : PlayerDetailUiState()
    data object NotFound : PlayerDetailUiState()
}

@HiltViewModel
class PlayerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlayerDetailUiState>(PlayerDetailUiState.Loading)
    val uiState: StateFlow<PlayerDetailUiState> = _uiState

    init {
        val userId = savedStateHandle.get<Long>("userId") ?: 0L
        viewModelScope.launch {
            val user = userRepository.getUser(userId)
            _uiState.value = if (user != null) {
                PlayerDetailUiState.Success(user)
            } else {
                PlayerDetailUiState.NotFound
            }
        }
    }
}
