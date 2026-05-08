package com.nauticontrol.nmeanavigationsimulator.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nauticontrol.nmeanavigationsimulator.model.AppUiState
import com.nauticontrol.nmeanavigationsimulator.model.ConnectionState
import com.nauticontrol.nmeanavigationsimulator.model.NavigationSnapshot
import com.nauticontrol.nmeanavigationsimulator.model.SimulatorSettings
import com.nauticontrol.nmeanavigationsimulator.network.TcpNmeaClient
import com.nauticontrol.nmeanavigationsimulator.nmea.NmeaGenerator
import com.nauticontrol.nmeanavigationsimulator.simulation.GeoMath
import com.nauticontrol.nmeanavigationsimulator.simulation.SimulationEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

class MainViewModel : ViewModel() {
    private val tcpClient = TcpNmeaClient()
    private val simulationEngine = SimulationEngine()
    private val nmeaGenerator = NmeaGenerator()

    private val _uiState = MutableStateFlow(
        AppUiState(route = simulationEngine.currentRoute())
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var simulationJob: Job? = null

    init {
        tcpClient.onLog = ::appendLog
        viewModelScope.launch {
            tcpClient.connectionState.collect { state ->
                _uiState.update { current ->
                    current.copy(
                        connectionState = state,
                        statusText = buildStatusText(state, current.isSimulating)
                    )
                }
            }
        }
    }

    fun updateIpAddress(value: String) {
        _uiState.update { it.copy(ipAddress = value) }
    }

    fun updatePort(value: String) {
        _uiState.update { it.copy(port = value) }
    }

    fun updateSpeed(value: Float) {
        _uiState.update {
            val settings = it.settings.copy(speedKnots = value.toDouble())
            it.copy(
                settings = settings,
                speedConfigText = String.format(Locale.US, "%.1f kn", settings.speedKnots)
            )
        }
    }

    fun updateRate(value: Float) {
        _uiState.update {
            val settings = it.settings.copy(updateRateHz = value.toInt())
            it.copy(
                settings = settings,
                updateRateText = "${settings.updateRateHz} Hz"
            )
        }
    }

    fun updateDeviation(value: Float) {
        _uiState.update {
            val settings = it.settings.copy(injectedDeviationNm = value.toDouble())
            it.copy(
                settings = settings,
                deviationText = String.format(Locale.US, "%.2f NM", settings.injectedDeviationNm)
            )
        }
    }

    fun toggleConnection() {
        val state = _uiState.value
        if (state.connectionState == ConnectionState.CONNECTED || state.connectionState == ConnectionState.CONNECTING) {
            tcpClient.disconnect()
            return
        }

        val port = state.port.toIntOrNull()
        if (port == null) {
            appendLog("Invalid port: ${state.port}")
            return
        }

        tcpClient.connect(state.ipAddress.trim(), port)
    }

    fun toggleSimulation() {
        if (_uiState.value.isSimulating) {
            stopSimulation()
        } else {
            startSimulation()
        }
    }

    private fun startSimulation() {
        simulationJob?.cancel()
        simulationEngine.reset()
        _uiState.update {
            it.copy(
                isSimulating = true,
                route = simulationEngine.currentRoute(),
                vesselTrack = emptyList(),
                statusText = buildStatusText(it.connectionState, true)
            )
        }
        appendLog("Simulation started")
        simulationJob = viewModelScope.launch {
            while (true) {
                val settings = _uiState.value.settings
                val snapshot = simulationEngine.tick(settings)
                publishSnapshot(snapshot, settings)
                delay((1000L / settings.updateRateHz).coerceAtLeast(100L))
            }
        }
    }

    private fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        _uiState.update {
            it.copy(
                isSimulating = false,
                statusText = buildStatusText(it.connectionState, false)
            )
        }
        appendLog("Simulation stopped")
    }

    private fun publishSnapshot(snapshot: NavigationSnapshot, settings: SimulatorSettings) {
        val sentences = nmeaGenerator.generate(snapshot)
        tcpClient.sendSentences(sentences)
        _uiState.update {
            it.copy(
                headingTrue = snapshot.headingTrue,
                vesselPosition = snapshot.position,
                route = snapshot.route,
                vesselTrack = snapshot.vesselTrack,
                headingText = String.format(Locale.US, "Heading: %.1f°T", snapshot.headingTrue),
                speedText = String.format(Locale.US, "Speed: %.1f kn", snapshot.speedKnots),
                xteText = String.format(
                    Locale.US,
                    "XTE: %.3f NM %s",
                    GeoMath.absRounded(snapshot.crossTrackErrorNm, 3),
                    if (snapshot.crossTrackErrorNm >= 0) "port" else "starboard"
                ),
                waypointText = String.format(
                    Locale.US,
                    "Waypoint: %s | BRG %.1f°T | DTW %.2f NM | %d Hz",
                    snapshot.currentWaypoint.name,
                    snapshot.bearingToWaypoint,
                    snapshot.distanceToWaypointNm,
                    settings.updateRateHz
                )
            )
        }
    }

    private fun buildStatusText(connectionState: ConnectionState, isSimulating: Boolean): String {
        val connectionText = when (connectionState) {
            ConnectionState.CONNECTED -> "Connected"
            ConnectionState.CONNECTING -> "Connecting"
            ConnectionState.DISCONNECTED -> "Disconnected"
        }
        val simulationText = if (isSimulating) "Sending" else "Stopped"
        return "$connectionText | $simulationText"
    }

    private fun appendLog(message: String) {
        _uiState.update { state ->
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(java.util.Date())
            val lines = (listOf("[$timestamp] $message") + state.logLines).take(14)
            state.copy(logLines = lines)
        }
    }

    override fun onCleared() {
        simulationJob?.cancel()
        tcpClient.disconnect()
        super.onCleared()
    }
}
