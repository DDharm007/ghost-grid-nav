package com.example.ghostgrid.routing

import com.example.ghostgrid.search.GeoPoint

enum class RoutingProfile(
    val id: String,
    val label: String,
    val description: String
) {
    FASTEST(
        id = "car_fastest",
        label = "Fastest",
        description = "Optimizes speed across primary highways & arterials"
    ),
    AVOID_UNPAVED(
        id = "car_avoid_unpaved",
        label = "Avoid Unpaved",
        description = "Penalizes dirt, gravel, tracks & rough surface roads"
    ),
    SHORTEST(
        id = "car_shortest",
        label = "Shortest",
        description = "Minimizes total distance traveled"
    );

    companion object {
        fun fromId(id: String): RoutingProfile =
            entries.firstOrNull { it.id == id } ?: FASTEST
    }
}

enum class ManeuverDirection {
    STRAIGHT,
    SLIGHT_LEFT,
    LEFT,
    SHARP_LEFT,
    SLIGHT_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    UTURN,
    ROUNDABOUT,
    ARRIVE
}

data class RouteManeuver(
    val index: Int,
    val direction: ManeuverDirection,
    val instructionText: String,
    val streetName: String,
    val distanceMeters: Double,
    val durationMillis: Long,
    val location: GeoPoint
)

data class AvoidanceZone(
    val id: String,
    val polygon: List<GeoPoint>,
    val createdAt: Long = System.currentTimeMillis()
)

data class ActiveRoute(
    val destinationName: String,
    val destination: GeoPoint,
    val profile: RoutingProfile,
    val points: List<GeoPoint>,
    val maneuvers: List<RouteManeuver>,
    val totalDistanceMeters: Double,
    val totalDurationMillis: Long,
    val avoidanceZones: List<AvoidanceZone> = emptyList(),
    val isDirectCompassFallback: Boolean = false,
    val calculationTimeMs: Long = 0L,
    val regionId: String = ""
)

data class NavigationHudState(
    val isNavigating: Boolean = false,
    val activeRoute: ActiveRoute? = null,
    val currentManeuverIndex: Int = 0,
    val distanceToNextTurnMeters: Double = 0.0,
    val remainingDistanceMeters: Double = 0.0,
    val remainingDurationMillis: Long = 0L,
    val isRecalculating: Boolean = false,
    val lastAlertBanner: String? = null
)
