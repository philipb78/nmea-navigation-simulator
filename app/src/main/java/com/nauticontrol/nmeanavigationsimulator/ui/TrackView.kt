package com.nauticontrol.nmeanavigationsimulator.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.nauticontrol.nmeanavigationsimulator.R
import com.nauticontrol.nmeanavigationsimulator.model.GeoPoint
import com.nauticontrol.nmeanavigationsimulator.simulation.GeoMath
import kotlin.math.max

class TrackView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.route_color)
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.track_color)
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    private val vesselPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.vessel_color)
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 0, 0, 0)
        strokeWidth = 1f
    }

    private var route: List<GeoPoint> = emptyList()
    private var vesselTrack: List<GeoPoint> = emptyList()
    private var vesselPosition: GeoPoint? = null
    private var headingTrue: Double = 0.0

    fun update(route: List<GeoPoint>, vesselTrack: List<GeoPoint>, vesselPosition: GeoPoint?, headingTrue: Double) {
        this.route = route
        this.vesselTrack = vesselTrack
        this.vesselPosition = vesselPosition
        this.headingTrue = headingTrue
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(ContextCompat.getColor(context, R.color.md_theme_light_surface))
        drawGrid(canvas)

        val allPoints = (route + vesselTrack + listOfNotNull(vesselPosition))
        if (allPoints.isEmpty()) {
            return
        }

        val bounds = computeBounds(allPoints)
        drawPath(canvas, route, bounds, routePaint)
        drawPath(canvas, vesselTrack, bounds, trackPaint)
        drawVessel(canvas, bounds)
    }

    private fun drawGrid(canvas: Canvas) {
        val stepX = width / 6f
        val stepY = height / 6f
        for (i in 1 until 6) {
            canvas.drawLine(stepX * i, 0f, stepX * i, height.toFloat(), gridPaint)
            canvas.drawLine(0f, stepY * i, width.toFloat(), stepY * i, gridPaint)
        }
    }

    private fun drawPath(canvas: Canvas, points: List<GeoPoint>, bounds: Bounds, paint: Paint) {
        if (points.size < 2) {
            return
        }
        val path = Path()
        points.forEachIndexed { index, point ->
            val mapped = map(point, bounds)
            if (index == 0) {
                path.moveTo(mapped.first, mapped.second)
            } else {
                path.lineTo(mapped.first, mapped.second)
            }
        }
        canvas.drawPath(path, paint)
    }

    private fun drawVessel(canvas: Canvas, bounds: Bounds) {
        val point = vesselPosition ?: return
        val mapped = map(point, bounds)
        canvas.save()
        canvas.translate(mapped.first, mapped.second)
        canvas.rotate(headingTrue.toFloat())
        val path = Path().apply {
            moveTo(0f, -18f)
            lineTo(10f, 12f)
            lineTo(0f, 6f)
            lineTo(-10f, 12f)
            close()
        }
        canvas.drawPath(path, vesselPaint)
        canvas.restore()
    }

    private fun computeBounds(points: List<GeoPoint>): Bounds {
        val reference = points.first()
        val eastValues = points.map { GeoMath.distanceEastNm(reference, it) }
        val northValues = points.map { GeoMath.distanceNorthNm(reference, it) }
        val minEast = eastValues.minOrNull() ?: 0.0
        val maxEast = eastValues.maxOrNull() ?: 1.0
        val minNorth = northValues.minOrNull() ?: 0.0
        val maxNorth = northValues.maxOrNull() ?: 1.0
        return Bounds(reference, minEast, maxEast, minNorth, maxNorth)
    }

    private fun map(point: GeoPoint, bounds: Bounds): Pair<Float, Float> {
        val padding = 24f
        val east = GeoMath.distanceEastNm(bounds.reference, point)
        val north = GeoMath.distanceNorthNm(bounds.reference, point)
        val eastSpan = max(bounds.maxEast - bounds.minEast, 0.05)
        val northSpan = max(bounds.maxNorth - bounds.minNorth, 0.05)
        val x = padding + ((east - bounds.minEast) / eastSpan).toFloat() * (width - padding * 2)
        val y = height - padding - ((north - bounds.minNorth) / northSpan).toFloat() * (height - padding * 2)
        return x to y
    }

    private data class Bounds(
        val reference: GeoPoint,
        val minEast: Double,
        val maxEast: Double,
        val minNorth: Double,
        val maxNorth: Double
    )
}
