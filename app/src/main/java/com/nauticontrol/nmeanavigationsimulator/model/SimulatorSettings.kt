package com.nauticontrol.nmeanavigationsimulator.model

data class SimulatorSettings(
    val speedKnotsMin: Double = 7.0,
    val speedKnotsMax: Double = 9.0,
    val updateRateHz: Int = 2,
    val injectedDeviationNm: Double = 0.0,
    val rudderAngleDegrees: Double = 0.0,
    val rsaStatusInvalid: Boolean = false,
    val mwvStatusInvalid: Boolean = false,
    val emitAis: Boolean = true,
    val emitAivdo: Boolean = false,
    val windDirectionTrueMin: Double = 255.0,
    val windDirectionTrueMax: Double = 275.0,
    val windSpeedKnotsMin: Double = 10.0,
    val windSpeedKnotsMax: Double = 13.0,
    val depthMetersMin: Double = 7.5,
    val depthMetersMax: Double = 8.5,
    val waterTemperatureCelsiusMin: Double = 13.0,
    val waterTemperatureCelsiusMax: Double = 15.0,
    // Lough Neagh is not tidal. Residual set is the northward Bann outflow.
    val currentDirectionTrueMin: Double = 350.0,
    val currentDirectionTrueMax: Double = 10.0,
    val currentSpeedKnotsMin: Double = 0.10,
    val currentSpeedKnotsMax: Double = 0.25,
    val magneticVariationDegrees: Double = -4.0,
    val muteNmeaTx: Boolean = false,
    val gpsFixInvalid: Boolean = false,
    val depthFieldsBlank: Boolean = false
)
