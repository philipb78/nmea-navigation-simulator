package com.nauticontrol.nmeanavigationsimulator.simulation

import kotlin.math.absoluteValue
import kotlin.random.Random

class RangeOscillator(
    min: Double,
    max: Double,
    private val maxRatePerSecond: Double,
    private val retargetIntervalSecondsMin: Double,
    private val retargetIntervalSecondsMax: Double,
    private val circular: Boolean = false,
    private val random: Random = Random.Default
) {
    private var minBound = min
    private var maxBound = max
    private var value = (min + max) / 2.0
    private var target = value
    private var nextRetargetAtMillis: Long = 0L

    fun reset(min: Double, max: Double, timestampMillis: Long) {
        minBound = min
        maxBound = max
        value = (min + max) / 2.0
        target = value
        scheduleRetarget(timestampMillis)
    }

    fun updateBounds(min: Double, max: Double) {
        minBound = min
        maxBound = max
        value = value.coerceIn(min, max)
        target = target.coerceIn(min, max)
        if (circular) {
            value = GeoMath.normalizeDegrees(value)
            target = GeoMath.normalizeDegrees(target)
        }
    }

    fun tick(deltaSeconds: Double, timestampMillis: Long): Double {
        if (minBound >= maxBound) {
            value = minBound
            return value
        }

        if (timestampMillis >= nextRetargetAtMillis) {
            pickNewTarget()
            scheduleRetarget(timestampMillis)
        }

        val maxStep = maxRatePerSecond * deltaSeconds
        if (circular) {
            val delta = GeoMath.shortestSignedAngleDegrees(value, target)
                .coerceIn(-maxStep, maxStep)
            value = GeoMath.normalizeDegrees(value + delta)
        } else {
            val delta = (target - value).coerceIn(-maxStep, maxStep)
            value = (value + delta).coerceIn(minBound, maxBound)
        }
        return value
    }

    fun currentValue(): Double = value

    private fun pickNewTarget() {
        target = if (circular) {
            val span = spanDegrees(minBound, maxBound)
            GeoMath.normalizeDegrees(minBound + random.nextDouble() * span)
        } else {
            minBound + random.nextDouble() * (maxBound - minBound)
        }
    }

    private fun scheduleRetarget(timestampMillis: Long) {
        val intervalSeconds = random.nextDouble(
            retargetIntervalSecondsMin,
            retargetIntervalSecondsMax
        )
        nextRetargetAtMillis = timestampMillis + (intervalSeconds * 1000.0).toLong()
    }

    private fun spanDegrees(min: Double, max: Double): Double {
        val normalizedMin = GeoMath.normalizeDegrees(min)
        val normalizedMax = GeoMath.normalizeDegrees(max)
        return if (normalizedMax >= normalizedMin) {
            normalizedMax - normalizedMin
        } else {
            (360.0 - normalizedMin) + normalizedMax
        }
    }
}
