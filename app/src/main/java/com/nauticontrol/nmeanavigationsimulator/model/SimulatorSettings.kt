package com.nauticontrol.nmeanavigationsimulator.model

data class SimulatorSettings(
    val speedKnotsMin: Double = 7.0,
    val speedKnotsMax: Double = 9.0,
    val updateRateHz: Int = 2,
    val injectedDeviationNm: Double = 0.0,
    val windDirectionTrueMin: Double = 220.0,
    val windDirectionTrueMax: Double = 260.0,
    val windSpeedKnotsMin: Double = 10.0,
    val windSpeedKnotsMax: Double = 14.0,
    val depthMetersMin: Double = 7.5,
    val depthMetersMax: Double = 8.5,
    val waterTemperatureCelsiusMin: Double = 13.0,
    val waterTemperatureCelsiusMax: Double = 15.0,
    val currentDirectionTrueMin: Double = 80.0,
    val currentDirectionTrueMax: Double = 100.0,
    val currentSpeedKnotsMin: Double = 0.3,
    val currentSpeedKnotsMax: Double = 0.7
)
