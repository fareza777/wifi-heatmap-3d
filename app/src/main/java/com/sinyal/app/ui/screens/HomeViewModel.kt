package com.sinyal.app.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sinyal.app.wifi.WifiMonitor
import com.sinyal.app.wifi.WifiScanner
import com.sinyal.app.wifi.WifiSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val snapshot: WifiSnapshot = WifiSnapshot.Disconnected,
    val wifiEnabled: Boolean = true,
    val hasLocationPermission: Boolean = false,
)

/**
 * Just the live link state.
 *
 * Nearby networks and saved scans used to be loaded here too; both now belong
 * to the screens that show them, which is also what stopped this one polling
 * the Wi-Fi scan cache for a list nobody was looking at.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val monitor = WifiMonitor(app)
    private val scanner = WifiScanner(app)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        observeLink()
        observePermission()
    }

    private fun observeLink() = viewModelScope.launch {
        monitor.snapshots().collect { snapshot ->
            _state.update {
                it.copy(snapshot = snapshot, wifiEnabled = monitor.isWifiEnabled)
            }
        }
    }

    /** Polls because a grant made in system settings sends no callback here. */
    private fun observePermission() = viewModelScope.launch {
        while (true) {
            _state.update { it.copy(hasLocationPermission = scanner.hasLocationPermission) }
            delay(PERMISSION_POLL_MS)
        }
    }

    fun onPermissionResult() {
        _state.update { it.copy(hasLocationPermission = scanner.hasLocationPermission) }
    }

    private companion object {
        const val PERMISSION_POLL_MS = 2_000L
    }
}
