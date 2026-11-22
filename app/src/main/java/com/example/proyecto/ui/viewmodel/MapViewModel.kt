package com.example.proyecto.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyecto.data.model.UserLocation
import com.example.proyecto.data.repository.AuthRepository
import com.example.proyecto.data.repository.LocationRepository
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MapUiState(
    val isConnected: Boolean = true,
    val hasLocationPermission: Boolean = false,
    val currentUserLocation: LatLng? = null,
    val currentUserRoute: List<LatLng> = emptyList(),
    val otherOnlineUsers: List<UserLocation> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class MapViewModel(
    private val locationRepository: LocationRepository = LocationRepository(),
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val currentUserId: String
        get() {
            val state = authRepository.authState.value
            return state.usuario?.id ?: state.empresa?.id ?: "u1"
        }

    private val currentUserName: String
        get() {
            val state = authRepository.authState.value
            return state.usuario?.nombre ?: state.empresa?.nombre ?: "Yo"
        }

    private val currentUserEmail: String
        get() {
            val state = authRepository.authState.value
            return state.usuario?.correo ?: state.empresa?.correo ?: "prueba@correo.com"
        }

    init {
        observeOnlineUsers()
    }

    private fun observeOnlineUsers() {
        viewModelScope.launch {
            locationRepository.getOnlineUsers().collect { users ->
                val myUid = currentUserId
                val others = users.filter { it.uid != myUid }
                val myUser = users.firstOrNull { it.uid == myUid }

                _uiState.update { state ->
                    state.copy(
                        otherOnlineUsers = others,
                        currentUserRoute = myUser?.toLatLngList() ?: state.currentUserRoute
                    )
                }
            }
        }
    }

    fun setPermissionStatus(hasPermission: Boolean) {
        _uiState.update { it.copy(hasLocationPermission = hasPermission) }
    }

    fun toggleConnection(connected: Boolean) {
        val uid = currentUserId
        viewModelScope.launch {
            _uiState.update { it.copy(isConnected = connected) }
            val result = locationRepository.setConnectedState(uid, connected)
            if (result.isFailure) {
                _uiState.update { state ->
                    state.copy(
                        isConnected = !connected,
                        errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Error al actualizar estado"
                    )
                }
            }
        }
    }

    fun updateLocation(lat: Double, lng: Double) {
        val uid = currentUserId
        if (!_uiState.value.isConnected) return

        val newLatLng = LatLng(lat, lng)
        _uiState.update { state ->
            state.copy(
                currentUserLocation = newLatLng,
                currentUserRoute = state.currentUserRoute + newLatLng
            )
        }

        viewModelScope.launch {
            locationRepository.updateLocation(
                uid = uid,
                name = currentUserName,
                email = currentUserEmail,
                lat = lat,
                lng = lng
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
