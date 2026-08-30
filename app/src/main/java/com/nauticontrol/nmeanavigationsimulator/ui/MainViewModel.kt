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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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
                        statusText = buildStatusText(state, current.isSimulating, current.settings.muteNmeaTx)
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

    fun updateSpeedRange(min: Float, max: Float) {
        _uiState.update {
            val settings = it.settings.copy(
                speedKnotsMin = min.toDouble(),
                speedKnotsMax = max.toDouble()
            )
            it.copy(settings = settings, speedConfigText = formatSpeedRange(settings))
        }
    }

    fun updateRate(value: Float) {
        _uiState.update {
            val settings = it.settings.copy(updateRateHz = value.toInt().coerceAtLeast(1))
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

    fun updateRudderAngle(value: Float) {
        _uiState.update {
            val settings = it.settings.copy(rudderAngleDegrees = value.toDouble().coerceIn(-40.0, 40.0))
            it.copy(
                settings = settings,
                rudderAngleText = formatRudderAngle(settings.rudderAngleDegrees)
            )
        }
    }

    fun updateRsaStatusInvalid(enabled: Boolean) {
        _uiState.update {
            it.copy(settings = it.settings.copy(rsaStatusInvalid = enabled))
        }
    }

    fun updateMwvStatusInvalid(enabled: Boolean) {
        _uiState.update {
            it.copy(settings = it.settings.copy(mwvStatusInvalid = enabled))
        }
    }

    fun updateVariation(value: Float) {
        _uiState.update {
            val settings = it.settings.copy(
                magneticVariationDegrees = value.toDouble().coerceIn(-30.0, 30.0)
            )
            it.copy(settings = settings, variationText = formatVariation(settings.magneticVariationDegrees))
        }
    }

    fun updateMuteNmeaTx(enabled: Boolean) {
        val wasMuted = _uiState.value.settings.muteNmeaTx
        _uiState.update {
            it.copy(
                settings = it.settings.copy(muteNmeaTx = enabled),
                statusText = buildStatusText(it.connectionState, it.isSimulating, enabled)
            )
        }
        if (wasMuted != enabled) {
            appendLog(if (enabled) "NMEA TX muted" else "NMEA TX resumed")
        }
    }

    fun updateGpsFixInvalid(enabled: Boolean) {
        _uiState.update {
            it.copy(settings = it.settings.copy(gpsFixInvalid = enabled))
        }
    }

    fun updateDepthFieldsBlank(enabled: Boolean) {
        _uiState.update {
            it.copy(settings = it.settings.copy(depthFieldsBlank = enabled))
        }
    }

    fun updateEmitAis(enabled: Boolean) {
        _uiState.update {
            it.copy(settings = it.settings.copy(emitAis = enabled))
        }
    }

    fun updateEmitAivdo(enabled: Boolean) {
        _uiState.update {
            it.copy(settings = it.settings.copy(emitAivdo = enabled))
        }
    }

    fun updateWindDirectionRange(min: Float, max: Float) {
        _uiState.update {
            val settings = it.settings.copy(
                windDirectionTrueMin = min.toDouble(),
                windDirectionTrueMax = max.toDouble()
            )
            it.copy(settings = settings, windDirectionText = formatWindDirectionRange(settings))
        }
    }

    fun updateWindSpeedRange(min: Float, max: Float) {
        _uiState.update {
            val settings = it.settings.copy(
                windSpeedKnotsMin = min.toDouble(),
                windSpeedKnotsMax = max.toDouble()
            )
            it.copy(settings = settings, windSpeedText = formatWindSpeedRange(settings))
        }
    }

    fun updateDepthRange(min: Float, max: Float) {
        _uiState.update {
            val settings = it.settings.copy(
                depthMetersMin = min.toDouble(),
                depthMetersMax = max.toDouble()
            )
            it.copy(settings = settings, depthText = formatDepthRange(settings))
        }
    }

    fun updateWaterTemperatureRange(min: Float, max: Float) {
        _uiState.update {
            val settings = it.settings.copy(
                waterTemperatureCelsiusMin = min.toDouble(),
                waterTemperatureCelsiusMax = max.toDouble()
            )
            it.copy(settings = settings, waterTemperatureText = formatWaterTemperatureRange(settings))
        }
    }

    fun updateCurrentDirectionRange(min: Float, max: Float) {
        _uiState.update {
            val settings = it.settings.copy(
                currentDirectionTrueMin = min.toDouble(),
                currentDirectionTrueMax = max.toDouble()
            )
            it.copy(settings = settings, currentDirectionText = formatCurrentDirectionRange(settings))
        }
    }

    fun updateCurrentSpeedRange(min: Float, max: Float) {
        _uiState.update {
            val settings = it.settings.copy(
                currentSpeedKnotsMin = min.toDouble(),
                currentSpeedKnotsMax = max.toDouble()
            )
            it.copy(settings = settings, currentSpeedText = formatCurrentSpeedRange(settings))
        }
    }

    fun toggleVesselControls() {
        _uiState.update { it.copy(vesselControlsExpanded = !it.vesselControlsExpanded) }
    }

    fun toggleWindControls() {
        _uiState.update { it.copy(windControlsExpanded = !it.windControlsExpanded) }
    }

    fun toggleWaterControls() {
        _uiState.update { it.copy(waterControlsExpanded = !it.waterControlsExpanded) }
    }

    fun toggleCurrentControls() {
        _uiState.update { it.copy(currentControlsExpanded = !it.currentControlsExpanded) }
    }

    fun toggleAisControls() {
        _uiState.update { it.copy(aisControlsExpanded = !it.aisControlsExpanded) }
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
        val settings = _uiState.value.settings
        val startAt = System.currentTimeMillis()
        simulationEngine.reset(settings, startAt)
        _uiState.update {
            it.copy(
                isSimulating = true,
                route = simulationEngine.currentRoute(),
                vesselTrack = emptyList(),
                statusText = buildStatusText(it.connectionState, true, it.settings.muteNmeaTx)
            )
        }
        appendLog("Simulation started")
        simulationJob = viewModelScope.launch {
            var lastTickAt = startAt
            while (true) {
                ensureActive()
                val currentSettings = _uiState.value.settings
                val now = System.currentTimeMillis()
                val snapshot = simulationEngine.tick(currentSettings, now, lastTickAt)
                lastTickAt = now
                publishSnapshot(snapshot, currentSettings)
                delay((1000L / currentSettings.updateRateHz.coerceAtLeast(1)).coerceAtLeast(100L))
            }
        }
    }

    private fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        _uiState.update {
            it.copy(
                isSimulating = false,
                statusText = buildStatusText(it.connectionState, false, it.settings.muteNmeaTx),
                speedConfigText = formatSpeedRange(it.settings),
                windDirectionText = formatWindDirectionRange(it.settings),
                windSpeedText = formatWindSpeedRange(it.settings),
                depthText = formatDepthRange(it.settings),
                waterTemperatureText = formatWaterTemperatureRange(it.settings),
                currentDirectionText = formatCurrentDirectionRange(it.settings),
                currentSpeedText = formatCurrentSpeedRange(it.settings)
            )
        }
        appendLog("Simulation stopped")
    }

    private fun publishSnapshot(snapshot: NavigationSnapshot, settings: SimulatorSettings) {
        val sentences = nmeaGenerator.generate(snapshot, settings)
        if (!settings.muteNmeaTx) {
            tcpClient.sendSentences(sentences)
        }
        val headingMagnetic = GeoMath.normalizeDegrees(
            snapshot.headingTrue - settings.magneticVariationDegrees
        )
        _uiState.update {
            it.copy(
                headingTrue = snapshot.headingTrue,
                vesselPosition = snapshot.position,
                route = snapshot.route,
                vesselTrack = snapshot.vesselTrack,
                headingText = String.format(
                    Locale.US,
                    "HDG %.1f°T / %.1f°M | COG %.1f°T (set %+.1f°)",
                    snapshot.headingTrue,
                    headingMagnetic,
                    snapshot.courseOverGroundTrue,
                    GeoMath.shortestSignedAngleDegrees(snapshot.headingTrue, snapshot.courseOverGroundTrue)
                ),
                speedText = String.format(
                    Locale.US,
                    "Speed: %.1f kn STW | %.1f kn SOG",
                    snapshot.speedThroughWaterKnots,
                    snapshot.speedOverGroundKnots
                ),
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
                ),
                speedConfigText = formatSpeedLive(snapshot, settings),
                rudderAngleText = formatRudderAngle(settings.rudderAngleDegrees),
                windDirectionText = formatWindDirectionLive(snapshot, settings),
                windSpeedText = formatWindSpeedLive(snapshot, settings),
                depthText = formatDepthLive(snapshot, settings),
                waterTemperatureText = formatWaterTemperatureLive(snapshot, settings),
                currentDirectionText = formatCurrentDirectionLive(snapshot, settings),
                currentSpeedText = formatCurrentSpeedLive(snapshot, settings),
                variationText = formatVariation(settings.magneticVariationDegrees)
            )
        }
    }

    private fun buildStatusText(
        connectionState: ConnectionState,
        isSimulating: Boolean,
        muteNmeaTx: Boolean
    ): String {
        val connectionText = when (connectionState) {
            ConnectionState.CONNECTED -> "Connected"
            ConnectionState.CONNECTING -> "Connecting"
            ConnectionState.DISCONNECTED -> "Disconnected"
        }
        val simulationText = when {
            !isSimulating -> "Stopped"
            muteNmeaTx -> "Muted"
            else -> "Sending"
        }
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

    private fun formatRudderAngle(degrees: Double): String =
        String.format(Locale.US, "%.1f°", degrees)

    private fun formatVariation(variationEastPositive: Double): String {
        val hemisphere = if (variationEastPositive >= 0.0) "E" else "W"
        return String.format(Locale.US, "%.1f°%s", kotlin.math.abs(variationEastPositive), hemisphere)
    }

    private fun formatSpeedRange(settings: SimulatorSettings): String =
        String.format(Locale.US, "%.1f–%.1f kn", settings.speedKnotsMin, settings.speedKnotsMax)

    private fun formatSpeedLive(snapshot: NavigationSnapshot, settings: SimulatorSettings): String =
        String.format(
            Locale.US,
            "%.1f kn (%.1f–%.1f)",
            snapshot.speedThroughWaterKnots,
            settings.speedKnotsMin,
            settings.speedKnotsMax
        )

    private fun formatWindDirectionRange(settings: SimulatorSettings): String =
        String.format(Locale.US, "%.0f–%.0f°T", settings.windDirectionTrueMin, settings.windDirectionTrueMax)

    private fun formatWindDirectionLive(snapshot: NavigationSnapshot, settings: SimulatorSettings): String =
        String.format(
            Locale.US,
            "%.0f°T (%.0f–%.0f)",
            snapshot.windDirectionTrue,
            settings.windDirectionTrueMin,
            settings.windDirectionTrueMax
        )

    private fun formatWindSpeedRange(settings: SimulatorSettings): String =
        String.format(Locale.US, "%.1f–%.1f kn", settings.windSpeedKnotsMin, settings.windSpeedKnotsMax)

    private fun formatWindSpeedLive(snapshot: NavigationSnapshot, settings: SimulatorSettings): String =
        String.format(
            Locale.US,
            "%.1f kn (%.1f–%.1f)",
            snapshot.windSpeedKnots,
            settings.windSpeedKnotsMin,
            settings.windSpeedKnotsMax
        )

    private fun formatDepthRange(settings: SimulatorSettings): String =
        String.format(Locale.US, "%.1f–%.1f m", settings.depthMetersMin, settings.depthMetersMax)

    private fun formatDepthLive(snapshot: NavigationSnapshot, settings: SimulatorSettings): String =
        String.format(
            Locale.US,
            "%.1f m (%.1f–%.1f)",
            snapshot.depthMeters,
            settings.depthMetersMin,
            settings.depthMetersMax
        )

    private fun formatWaterTemperatureRange(settings: SimulatorSettings): String =
        String.format(Locale.US, "%.1f–%.1f °C", settings.waterTemperatureCelsiusMin, settings.waterTemperatureCelsiusMax)

    private fun formatWaterTemperatureLive(snapshot: NavigationSnapshot, settings: SimulatorSettings): String =
        String.format(
            Locale.US,
            "%.1f °C (%.1f–%.1f)",
            snapshot.waterTemperatureCelsius,
            settings.waterTemperatureCelsiusMin,
            settings.waterTemperatureCelsiusMax
        )

    private fun formatCurrentDirectionRange(settings: SimulatorSettings): String =
        String.format(Locale.US, "%.0f–%.0f°T", settings.currentDirectionTrueMin, settings.currentDirectionTrueMax)

    private fun formatCurrentDirectionLive(snapshot: NavigationSnapshot, settings: SimulatorSettings): String =
        String.format(
            Locale.US,
            "%.0f°T (%.0f–%.0f)",
            snapshot.currentDirectionTrue,
            settings.currentDirectionTrueMin,
            settings.currentDirectionTrueMax
        )

    private fun formatCurrentSpeedRange(settings: SimulatorSettings): String =
        String.format(Locale.US, "%.1f–%.1f kn", settings.currentSpeedKnotsMin, settings.currentSpeedKnotsMax)

    private fun formatCurrentSpeedLive(snapshot: NavigationSnapshot, settings: SimulatorSettings): String =
        String.format(
            Locale.US,
            "%.1f kn (%.1f–%.1f)",
            snapshot.currentSpeedKnots,
            settings.currentSpeedKnotsMin,
            settings.currentSpeedKnotsMax
        )
}
