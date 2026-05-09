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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.regex.Pattern

class MainViewModel : ViewModel() {
    private val tcpClient = TcpNmeaClient()
    private val simulationEngine = SimulationEngine()
    private val nmeaGenerator = NmeaGenerator()
    private val hostPattern = Pattern.compile("^[a-zA-Z0-9.\\-:\\[\\]]+$")

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
        _uiState.update {
            it.copy(
                ipAddress = value,
                ipAddressError = validateHost(value)
            )
        }
    }

    fun updatePort(value: String) {
        _uiState.update {
            it.copy(
                port = value,
                portError = validatePort(value)
            )
        }
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
        if (state.isConnectedOrConnecting) {
            tcpClient.disconnect()
            return
        }

        val ipError = validateHost(state.ipAddress)
        val portError = validatePort(state.port)
        if (ipError != null || portError != null) {
            _uiState.update {
                it.copy(
                    ipAddressError = ipError,
                    portError = portError
                )
            }
            appendLog(ipError ?: portError.orEmpty())
            return
        }

        tcpClient.connect(state.ipAddress.trim(), state.port.toInt())
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
            var lastTickAt = System.currentTimeMillis()
            while (true) {
                ensureActive()
                val settings = _uiState.value.settings
                val now = System.currentTimeMillis()
                val snapshot = simulationEngine.tick(settings, now, lastTickAt)
                lastTickAt = now
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
                    if (snapshot.crossTrackErrorNm >= 0) "starboard" else "port"
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
        tcpClient.close()
        super.onCleared()
    }

    private fun validateHost(value: String): String? {
        val host = value.trim()
        return when {
            host.isEmpty() -> "Host is required"
            host.contains(' ') -> "Host cannot contain spaces"
            !hostPattern.matcher(host).matches() -> "Host contains invalid characters"
            else -> null
        }
    }

    private fun validatePort(value: String): String? {
        val port = value.toIntOrNull()
        return when {
            value.isBlank() -> "Port is required"
            port == null -> "Port must be numeric"
            port !in 1..65535 -> "Port must be between 1 and 65535"
            else -> null
        }
    }
}
