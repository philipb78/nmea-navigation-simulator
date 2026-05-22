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
        get() = connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.CONNECTING
}
