package com.nauticontrol.nmeanavigationsimulator.model

data class SimulatorSettings(
    val speedKnots: Double = 8.0,
    val updateRateHz: Int = 2,
    val injectedDeviationNm: Double = 0.0,
    val windDirectionTrue: Double = 240.0,
    val windSpeedKnots: Double = 12.0,
    val depthMeters: Double = 8.0,
    val waterTemperatureCelsius: Double = 14.0,
    val currentDirectionTrue: Double = 90.0,
    val currentSpeedKnots: Double = 0.5
)
