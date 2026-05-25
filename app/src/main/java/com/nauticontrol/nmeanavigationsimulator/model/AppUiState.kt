package com.nauticontrol.nmeanavigationsimulator.model

data class AppUiState(
    val ipAddress: String = "192.168.1.100",
    val port: String = "10110",
    val ipAddressError: String? = null,
    val portError: String? = null,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isSimulating: Boolean = false,
    val statusText: String = "Disconnected | Stopped",
    val headingText: String = "Heading: --",
    val speedText: String = "Speed: --",
    val xteText: String = "XTE: --",
    val waypointText: String = "Waypoint: --",
    val speedConfigText: String = "8.0 kn",
    val updateRateText: String = "2 Hz",
    val deviationText: String = "0.00 NM",
    val windDirectionText: String = "240°T",
    val windSpeedText: String = "12.0 kn",
    val depthText: String = "8.0 m",
    val waterTemperatureText: String = "14.0 °C",
    val currentDirectionText: String = "90°T",
    val currentSpeedText: String = "0.5 kn",
    val vesselControlsExpanded: Boolean = true,
    val windControlsExpanded: Boolean = false,
    val waterControlsExpanded: Boolean = false,
    val currentControlsExpanded: Boolean = false,
    val settings: SimulatorSettings = SimulatorSettings(),
    val route: List<GeoPoint> = emptyList(),
    val vesselTrack: List<GeoPoint> = emptyList(),
    val vesselPosition: GeoPoint? = null,
    val headingTrue: Double = 0.0,
    val logLines: List<String> = listOf("Simulator ready")
) {
    val isConnectedOrConnecting: Boolean
        get() = connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.CONNECTING

    val canEditConnectionSettings: Boolean
        get() = !isConnectedOrConnecting

    val canToggleSimulation: Boolean
        get() = isSimulating || connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.CONNECTING
}
