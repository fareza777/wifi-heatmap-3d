package com.sinyal.app.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sinyal.app.ar.ArCapability
import com.sinyal.app.ar.ArCapabilityReport
import com.sinyal.app.core.DeviceProbe
import com.sinyal.app.core.DeviceReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DiagnosticsUiState(
    val device: DeviceReport? = null,
    val ar: ArCapabilityReport? = null,
    val running: Boolean = false,
)

/**
 * Runs the hardware readiness probe. Everything here is measured on the device
 * at runtime rather than inferred from the model name, because the published
 * ARCore support list does not resolve every variant.
 */
class DiagnosticsViewModel(app: Application) : AndroidViewModel(app) {

    private val deviceProbe = DeviceProbe(app)
    private val arCapability = ArCapability(app)

    private val _state = MutableStateFlow(DiagnosticsUiState())
    val state: StateFlow<DiagnosticsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(running = true) }
        // Session creation blocks and touches the camera stack; keep it off the main thread.
        val device = withContext(Dispatchers.IO) { deviceProbe.read() }
        val ar = withContext(Dispatchers.IO) { arCapability.probe() }
        _state.update { it.copy(device = device, ar = ar, running = false) }
    }
}
