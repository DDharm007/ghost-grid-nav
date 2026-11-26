package com.example.ghostgrid.search

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(
    val lat: Double,
    val lon: Double
)

/**
 * Pure offline geodesic & computational geometry utilities:
 * - Haversine surface distance (meters)
 * - Initial bearing (degrees 0..360)
 * - Destination projection along bearing + distance
 * - Point-to-polyline minimum distance (for >30m off-route detection)
 * - 200m forward road-block avoidance polygon builder
 * - Coordinate string parser ("23.0225, 72.5714")
 */
object GeoMath {

    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private val COORD_REGEX = Regex("""^\s*(-?\d{1,2}(?:\.\d+)?)\s*[,;\s]\s*(-?\d{1,3}(?:\.\d+)?)\s*$""")

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2.0) * sin(dLon / 2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(max(0.0, 1.0 - a)))
        return EARTH_RADIUS_METERS * c
    }

    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun destinationPoint(lat: Double, lon: Double, bearingDeg: Double, distanceMeters: Double): GeoPoint {
        val angularDist = distanceMeters / EARTH_RADIUS_METERS
        val brng = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(lat)
        val lon1 = Math.toRadians(lon)

        val lat2 = asin(
            sin(lat1) * cos(angularDist) + cos(lat1) * sin(angularDist) * cos(brng)
        )
        val lon2 = lon1 + atan2(
            sin(brng) * sin(angularDist) * cos(lat1),
            cos(angularDist) - sin(lat1) * sin(lat2)
        )
        return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }

    /**
     * Computes shortest distance in meters from `point` to a polyline,
     * plus the index of the closest segment start vertex.
     */
    fun distanceToPolylineMeters(point: GeoPoint, polyline: List<GeoPoint>): Pair<Double, Int> {
        if (polyline.isEmpty()) return Double.MAX_VALUE to 0
        if (polyline.size == 1) {
            return haversineMeters(point.lat, point.lon, polyline[0].lat, polyline[0].lon) to 0
        }
        var bestDist = Double.MAX_VALUE
        var bestIndex = 0
        val cosLat = cos(Math.toRadians(point.lat))

        for (i in 0 until polyline.lastIndex) {
            val a = polyline[i]
            val b = polyline[i + 1]
            val dist = pointToSegmentMeters(point, a, b, cosLat)
            if (dist < bestDist) {
                bestDist = dist
                bestIndex = i
            }
        }
        return bestDist to bestIndex
    }

    private fun pointToSegmentMeters(
        p: GeoPoint,
        a: GeoPoint,
        b: GeoPoint,
        cosLat: Double
    ): Double {
        val px = p.lon * cosLat
        val py = p.lat
        val ax = a.lon * cosLat
        val ay = a.lat
        val bx = b.lon * cosLat
        val by = b.lat

        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        val t = if (lenSq == 0.0) {
            0.0
        } else {
            max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / lenSq))
        }
        val projLat = a.lat + t * (b.lat - a.lat)
        val projLon = a.lon + t * (b.lon - a.lon)
        return haversineMeters(p.lat, p.lon, projLat, projLon)
    }

    /**
     * Builds a closed 5-vertex avoidance polygon covering the next `lengthMeters`
     * (default 200m) along the active route polyline starting just ahead of the user.
     */
    fun buildForwardBlockPolygon(
        origin: GeoPoint,
        polyline: List<GeoPoint>,
        lengthMeters: Double = 200.0,
        halfWidthMeters: Double = 28.0
    ): List<GeoPoint> {
        val (_, nearestIdx) = distanceToPolylineMeters(origin, polyline)
        val startIdx = min(nearestIdx, max(0, polyline.lastIndex - 1))
        val pStart = if (polyline.size >= 2) polyline[startIdx] else origin

        // Walk forward along polyline until reaching ~lengthMeters
        var accumulated = 0.0
        var pEnd = pStart
        for (i in startIdx until polyline.lastIndex) {
            val seg = haversineMeters(
                polyline[i].lat, polyline[i].lon,
                polyline[i + 1].lat, polyline[i + 1].lon
            )
            accumulated += seg
            pEnd = polyline[i + 1]
            if (accumulated >= lengthMeters) break
        }

        val bearing = if (pStart == pEnd) 0.0 else bearingDegrees(pStart.lat, pStart.lon, pEnd.lat, pEnd.lon)
        // Offset start 25m ahead so the vehicle's current position isn't trapped inside the polygon
        val boxStart = destinationPoint(origin.lat, origin.lon, bearing, 25.0)
        val boxEnd = destinationPoint(origin.lat, origin.lon, bearing, lengthMeters + 25.0)

        val leftStart = destinationPoint(boxStart.lat, boxStart.lon, (bearing - 90.0 + 360.0) % 360.0, halfWidthMeters)
        val rightStart = destinationPoint(boxStart.lat, boxStart.lon, (bearing + 90.0) % 360.0, halfWidthMeters)
        val rightEnd = destinationPoint(boxEnd.lat, boxEnd.lon, (bearing + 90.0) % 360.0, halfWidthMeters)
        val leftEnd = destinationPoint(boxEnd.lat, boxEnd.lon, (bearing - 90.0 + 360.0) % 360.0, halfWidthMeters)

        return listOf(leftStart, rightStart, rightEnd, leftEnd, leftStart)
    }

    fun boundingBoxAround(centerLat: Double, centerLon: Double, radiusKm: Double): DoubleArray {
        val latDelta = radiusKm / 111.32
        val cosLat = max(0.01, cos(Math.toRadians(centerLat)))
        val lonDelta = radiusKm / (111.32 * cosLat)
        return doubleArrayOf(
            max(-90.0, centerLat - latDelta),
            min(90.0, centerLat + latDelta),
            max(-180.0, centerLon - lonDelta),
            min(180.0, centerLon + lonDelta)
        )
    }

    fun parseCoordinateQuery(query: String): GeoPoint? {
        val match = COORD_REGEX.matchEntire(query) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lon = match.groupValues[2].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return GeoPoint(lat, lon)
    }

    fun formatDistance(meters: Double): String {
        return when {
            meters < 1000.0 -> "${meters.toInt()} m"
            meters < 10_000.0 -> String.format("%.1f km", meters / 1000.0)
            else -> String.format("%.0f km", meters / 1000.0)
        }
    }

    fun formatDuration(millis: Long): String {
        val totalMinutes = max(1L, millis / 60_000L)
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return if (hours > 0) "${hours}h ${mins}m" else "${mins} min"
    }

    fun cardinalDirection(bearingDeg: Double): String {
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val idx = (((bearingDeg % 360.0) + 360.0 + 22.5) / 45.0).toInt() % 8
        return dirs[idx]
    }
}
