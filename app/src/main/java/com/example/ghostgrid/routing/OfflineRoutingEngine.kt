package com.example.ghostgrid.routing

import android.content.Context
import android.os.SystemClock
import com.example.ghostgrid.search.GeoMath
import com.example.ghostgrid.search.GeoPoint
import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import com.graphhopper.json.Statement
import com.graphhopper.util.CustomModel
import com.graphhopper.util.Instruction
import com.graphhopper.util.JsonFeature
import com.graphhopper.util.JsonFeatureCollection
import com.graphhopper.util.TranslationMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import java.io.File
import java.util.Locale

/**
 * Embedded GraphHopper Core 11.1 Offline Routing Engine.
 *
 * - Loads pre-compiled `routing-graph/` folders from `context.noBackupFilesDir/regions/<id>/routing-graph/`.
 * - Supports customizable runtime `CustomModel` rules (`surface`, `smoothness`, `road_class`, `max_speed`)
 *   and dynamic `JsonFeature` avoidance polygons for 1-tap "Road Blocked Ahead" rerouting (<150ms).
 * - Automatically detects off-route deviation (>30m) and computes fast local reroutes.
 */
class OfflineRoutingEngine(
    private val context: Context
) {
    private val engineMutex = Mutex()
    private val loadedGraphs = LinkedHashMap<String, LoadedGraph>()
    private val geometryFactory = GeometryFactory()

    private data class LoadedGraph(
        val regionId: String,
        val hopper: GraphHopper,
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double
    ) {
        fun contains(point: GeoPoint): Boolean =
            point.lat in minLat..maxLat && point.lon in minLon..maxLon
    }

    companion object {
        const val OFF_ROUTE_THRESHOLD_METERS = 30.0
    }

    suspend fun reloadGraphs() = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            loadedGraphs.values.forEach { runCatching { it.hopper.close() } }
            loadedGraphs.clear()

            val regionsRoot = File(context.noBackupFilesDir, "regions")
            if (!regionsRoot.exists() || !regionsRoot.isDirectory) return@withLock

            regionsRoot.listFiles()?.forEach { regionDir ->
                val graphDir = File(regionDir, "routing-graph")
                if (!graphDir.isDirectory || !File(graphDir, "properties").exists()) return@forEach

                runCatching {
                    val hopper = GraphHopper().apply {
                        setGraphHopperLocation(graphDir.absolutePath)
                        setAllowWrites(false)
                    }
                    hopper.load()
                    val bounds = hopper.baseGraph.bounds
                    loadedGraphs[regionDir.name] = LoadedGraph(
                        regionId = regionDir.name,
                        hopper = hopper,
                        minLat = bounds.minLat,
                        maxLat = bounds.maxLat,
                        minLon = bounds.minLon,
                        maxLon = bounds.maxLon
                    )
                }
            }
        }
    }

    suspend fun getLoadedGraphCount(): Int = engineMutex.withLock { loadedGraphs.size }

    suspend fun calculateRoute(
        origin: GeoPoint,
        destination: GeoPoint,
        destinationName: String,
        profile: RoutingProfile,
        avoidanceZones: List<AvoidanceZone> = emptyList()
    ): Result<ActiveRoute> = withContext(Dispatchers.IO) {
        val startMs = SystemClock.elapsedRealtime()

        val graph = engineMutex.withLock {
            loadedGraphs.values.firstOrNull { it.contains(origin) && it.contains(destination) }
                ?: loadedGraphs.values.firstOrNull { it.contains(destination) || it.contains(origin) }
        }

        if (graph == null) {
            return@withContext Result.failure(
                NoRegionGraphException(
                    "No offline routing graph covers this area yet. Import a regional pack in Region Manager to enable turn-by-turn routing."
                )
            )
        }

        runCatching {
            val request = GHRequest(origin.lat, origin.lon, destination.lat, destination.lon).apply {
                setProfile(resolveAvailableProfile(graph.hopper, profile.id))
                locale = Locale.US
                customModel = buildDynamicCustomModel(profile, avoidanceZones)
                hints.putObject("ch.disable", true)
            }

            val response = graph.hopper.route(request)
            if (response.hasErrors()) {
                val errMsg = response.errors.joinToString("; ") { it.message ?: it.javaClass.simpleName }
                throw IllegalStateException("Offline route failed: $errMsg")
            }

            val best = response.best
            val pointList = best.points
            val routePoints = ArrayList<GeoPoint>(pointList.size())
            for (i in 0 until pointList.size()) {
                routePoints += GeoPoint(pointList.getLat(i), pointList.getLon(i))
            }

            val tr = TranslationMap().doImport().getWithFallBack(Locale.US)
            val maneuvers = best.instructions.mapIndexed { idx, inst ->
                val instLat = if (inst.points.size() > 0) inst.points.getLat(0) else destination.lat
                val instLon = if (inst.points.size() > 0) inst.points.getLon(0) else destination.lon
                RouteManeuver(
                    index = idx,
                    direction = mapSignToDirection(inst.sign),
                    instructionText = inst.getTurnDescription(tr).ifBlank { "Continue" },
                    streetName = inst.name.orEmpty().ifBlank { "Unnamed Road" },
                    distanceMeters = inst.distance,
                    durationMillis = inst.time,
                    location = GeoPoint(instLat, instLon)
                )
            }

            val elapsed = SystemClock.elapsedRealtime() - startMs
            ActiveRoute(
                destinationName = destinationName,
                destination = destination,
                profile = profile,
                points = routePoints,
                maneuvers = maneuvers,
                totalDistanceMeters = best.distance,
                totalDurationMillis = best.time,
                avoidanceZones = avoidanceZones,
                isDirectCompassFallback = false,
                calculationTimeMs = elapsed,
                regionId = graph.regionId
            )
        }
    }

    /**
     * Explicit direct-line compass bearing fallback when user chooses bearing guidance
     * outside installed regional routing graphs. Clearly marked `isDirectCompassFallback = true`.
     */
    fun buildDirectCompassBearing(
        origin: GeoPoint,
        destination: GeoPoint,
        destinationName: String,
        profile: RoutingProfile
    ): ActiveRoute {
        val distMeters = GeoMath.haversineMeters(origin.lat, origin.lon, destination.lat, destination.lon)
        val bearing = GeoMath.bearingDegrees(origin.lat, origin.lon, destination.lat, destination.lon)
        val cardinal = GeoMath.cardinalDirection(bearing)
        val estMillis = ((distMeters / 13.8) * 1000.0).toLong().coerceAtLeast(60_000L)

        return ActiveRoute(
            destinationName = destinationName,
            destination = destination,
            profile = profile,
            points = listOf(origin, destination),
            maneuvers = listOf(
                RouteManeuver(
                    index = 0,
                    direction = ManeuverDirection.STRAIGHT,
                    instructionText = "Head $cardinal (${bearing.toInt()}°) — direct compass bearing",
                    streetName = "No regional road graph installed",
                    distanceMeters = distMeters,
                    durationMillis = estMillis,
                    location = origin
                ),
                RouteManeuver(
                    index = 1,
                    direction = ManeuverDirection.ARRIVE,
                    instructionText = "Arrive at $destinationName",
                    streetName = destinationName,
                    distanceMeters = 0.0,
                    durationMillis = 0L,
                    location = destination
                )
            ),
            totalDistanceMeters = distMeters,
            totalDurationMillis = estMillis,
            isDirectCompassFallback = true,
            calculationTimeMs = 1L,
            regionId = "compass"
        )
    }

    fun isOffRoute(currentLocation: GeoPoint, activeRoute: ActiveRoute): Boolean {
        if (activeRoute.isDirectCompassFallback || activeRoute.points.size < 2) return false
        val (distMeters, _) = GeoMath.distanceToPolylineMeters(currentLocation, activeRoute.points)
        return distMeters > OFF_ROUTE_THRESHOLD_METERS
    }

    private fun resolveAvailableProfile(hopper: GraphHopper, requestedProfile: String): String {
        val available = hopper.profiles.map { it.name }
        return when {
            requestedProfile in available -> requestedProfile
            available.isNotEmpty() -> available.first()
            else -> "car"
        }
    }

    private fun buildDynamicCustomModel(
        profile: RoutingProfile,
        avoidanceZones: List<AvoidanceZone>
    ): CustomModel {
        val model = CustomModel()

        when (profile) {
            RoutingProfile.FASTEST -> {
                model.distanceInfluence = 40.0
            }
            RoutingProfile.SHORTEST -> {
                model.distanceInfluence = 110.0
            }
            RoutingProfile.AVOID_UNPAVED -> {
                model.distanceInfluence = 50.0
                model.addToPriority(
                    Statement.If(
                        "surface == UNPAVED || surface == GRAVEL || surface == DIRT || surface == SAND",
                        Statement.Op.MULTIPLY,
                        "0.12"
                    )
                )
                model.addToPriority(
                    Statement.If(
                        "smoothness == BAD || smoothness == VERY_BAD || smoothness == HORRIBLE",
                        Statement.Op.MULTIPLY,
                        "0.15"
                    )
                )
                model.addToPriority(
                    Statement.If(
                        "road_class == TRACK",
                        Statement.Op.MULTIPLY,
                        "0.2"
                    )
                )
            }
        }

        if (avoidanceZones.isNotEmpty()) {
            val featureCollection = JsonFeatureCollection()
            avoidanceZones.forEachIndexed { idx, zone ->
                if (zone.polygon.size >= 4) {
                    val areaName = "blocked_$idx"
                    val coords = zone.polygon.map { Coordinate(it.lon, it.lat) }.toTypedArray()
                    val closedCoords = if (coords.first() == coords.last()) {
                        coords
                    } else {
                        coords + coords.first()
                    }
                    val polygon = geometryFactory.createPolygon(closedCoords)
                    val feature = JsonFeature(areaName, "Feature", null, polygon, emptyMap())
                    featureCollection.features.add(feature)
                    model.addToPriority(
                        Statement.If("in_$areaName", Statement.Op.MULTIPLY, "0")
                    )
                }
            }
            model.areas = featureCollection
        }

        return model
    }

    private fun mapSignToDirection(sign: Int): ManeuverDirection = when (sign) {
        Instruction.TURN_SLIGHT_LEFT -> ManeuverDirection.SLIGHT_LEFT
        Instruction.TURN_LEFT -> ManeuverDirection.LEFT
        Instruction.TURN_SHARP_LEFT -> ManeuverDirection.SHARP_LEFT
        Instruction.TURN_SLIGHT_RIGHT -> ManeuverDirection.SLIGHT_RIGHT
        Instruction.TURN_RIGHT -> ManeuverDirection.RIGHT
        Instruction.TURN_SHARP_RIGHT -> ManeuverDirection.SHARP_RIGHT
        Instruction.U_TURN_LEFT, Instruction.U_TURN_RIGHT, Instruction.U_TURN_UNKNOWN -> ManeuverDirection.UTURN
        Instruction.USE_ROUNDABOUT, Instruction.LEAVE_ROUNDABOUT -> ManeuverDirection.ROUNDABOUT
        Instruction.FINISH -> ManeuverDirection.ARRIVE
        else -> ManeuverDirection.STRAIGHT
    }

    suspend fun closeAll() = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            loadedGraphs.values.forEach { runCatching { it.hopper.close() } }
            loadedGraphs.clear()
        }
    }
}

class NoRegionGraphException(message: String) : IllegalStateException(message)
