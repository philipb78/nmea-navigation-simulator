package com.nauticontrol.nmeanavigationsimulator.model

data class SimulatorSettings(
    val speedKnots: Double = 8.0,
    val updateRateHz: Int = 2,
    val injectedDeviationNm: Double = 0.0
)
