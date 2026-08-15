package com.nauticontrol.nmeanavigationsimulator.model

data class AppUiState(
    val ipAddress: String = "192.168.1.1",
    val port: String = "8091",
    val ipAddressError: String? = null,
    val portError: String? = null,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isSimulating: Boolean = false,
    val statusText: String = "Disconnected | Stopped",
    val headingText: String = "Heading: --",
    val speedText: String = "Speed: --",
    val xteText: String = "XTE: --",
    val waypointText: String = "Waypoint: --",
    val speedConfigText: String = "7.0–9.0 kn",
    val updateRateText: String = "2 Hz",
    val deviationText: String = "0.00 NM",
    val rudderAngleText: String = "0.0°",
    val variationText: String = "4.0°W",
    val windDirectionText: String = "220–260°T",
    val windSpeedText: String = "10.0–14.0 kn",
    val depthText: String = "7.5–8.5 m",
    val waterTemperatureText: String = "13.0–15.0 °C",
    val currentDirectionText: String = "80–100°T",
    val currentSpeedText: String = "0.3–0.7 kn",
    val vesselControlsExpanded: Boolean = true,
    val windControlsExpanded: Boolean = false,
    val waterControlsExpanded: Boolean = false,
    val currentControlsExpanded: Boolean = false,
    val aisControlsExpanded: Boolean = false,
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
