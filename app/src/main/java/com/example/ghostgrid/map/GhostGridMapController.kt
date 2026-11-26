package com.example.ghostgrid.map

import android.content.Context
import com.example.ghostgrid.routing.AvoidanceZone
import com.example.ghostgrid.search.GeoPoint
import com.example.ghostgrid.search.PlaceResult
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import java.io.File

enum class GhostCameraMode(val label: String) {
    FREE("Explore"),
    TRACKING("Follow"),
    COMPASS("Compass"),
    NAVIGATION_3D("3D Drive")
}

enum class GhostMapStyle(
    val id: String,
    val label: String,
    val subtitle: String
) {
    STEALTH_DARK(
        id = "stealth_dark",
        label = "Dark",
        subtitle = "Minimal monochrome map"
    ),
    STANDARD_MAP(
        id = "standard_map",
        label = "Map",
        subtitle = "Global street map"
    ),
    SATELLITE(
        id = "satellite",
        label = "Satellite",
        subtitle = "Aerial imagery & labels"
    ),
    TERRAIN(
        id = "terrain",
        label = "Terrain",
        subtitle = "Shaded relief & topo"
    ),
    OPENFREEMAP_3D(
        id = "openfreemap_3d",
        label = "3D",
        subtitle = "OpenFreeMap 3D buildings"
    ),
    PURE_OFFLINE(
        id = "pure_offline",
        label = "Offline",
        subtitle = "Zero-network local vault"
    );

    companion object {
        fun fromId(id: String): GhostMapStyle = entries.firstOrNull { it.id == id } ?: STEALTH_DARK
    }
}

enum class CarColorPreset(
    val id: String,
    val label: String,
    val primaryHex: String,
    val highlightHex: String,
    val darkHex: String
) {
    PEARL("pearl", "Pearl White", "#F4F4F5", "#FFFFFF", "#A1A1AA"),
    SILVER("silver", "Titanium Grey", "#9CA3AF", "#E5E7EB", "#4B5563"),
    OBSIDIAN("obsidian", "Obsidian Matte", "#27272A", "#52525B", "#09090B");

    companion object {
        fun fromId(id: String): CarColorPreset = entries.firstOrNull { it.id == id } ?: PEARL
    }
}

/**
 * Orchestrates MapLibre Native rendering for GhostGrid:
 * - Zero-API-key global map styles: Minimal Dark Mono, Street Map, Hybrid Satellite Imagery,
 *   Topographic Terrain, and OpenFreeMap 3D Vector — with fail-safe fallback to Pure Offline.
 * - Seamlessly overlays local `world-base.pmtiles` and all installed regional `map.pmtiles`.
 * - Renders minimal monochrome 3D teardrop pins, high-contrast white/black routes, and a
 *   playful 3D Car model that morphs in automatically when moving or navigating.
 */
class GhostGridMapController(
    private val context: Context,
    private val mapAssetManager: MapAssetManager,
    private val offlineTileVault: OfflineTileVault
) {
    companion object {
        private const val SOURCE_WORLD_OFFLINE = "ghostgrid-world-offline-src"
        private const val SOURCE_WORLD_BASE = "ghostgrid-world-pmtiles"
        private const val SOURCE_OFFLINE_RASTER_BASE = "ghostgrid-offline-raster-base"
        private const val SOURCE_OFFLINE_RASTER_OVERLAY = "ghostgrid-offline-raster-overlay"
        private const val SOURCE_ROUTE = "ghostgrid-active-route-src"
        private const val SOURCE_BREADCRUMBS = "ghostgrid-breadcrumbs-src"
        private const val SOURCE_AVOIDANCE = "ghostgrid-avoidance-src"
        private const val SOURCE_MARKERS = "ghostgrid-markers-src"
        private const val SOURCE_USER_VEHICLE = "ghostgrid-user-vehicle-src"

        private const val LAYER_ROUTE_CASING = "ghostgrid-route-casing"
        private const val LAYER_ROUTE_CORE = "ghostgrid-route-core"
        private const val LAYER_BREADCRUMBS_GLOW = "ghostgrid-breadcrumbs-glow"
        private const val LAYER_BREADCRUMBS_CORE = "ghostgrid-breadcrumbs-core"
        private const val LAYER_AVOIDANCE_FILL = "ghostgrid-avoidance-fill"
        private const val LAYER_AVOIDANCE_LINE = "ghostgrid-avoidance-line"
        private const val LAYER_MARKERS_SYMBOL = "ghostgrid-markers-symbol"
        private const val LAYER_USER_VEHICLE = "ghostgrid-user-vehicle-layer"

        private const val IMG_BEACON = "gg-user-beacon"
        private const val IMG_CAR_PEARL = "gg-car-pearl"
        private const val IMG_CAR_SILVER = "gg-car-silver"
        private const val IMG_CAR_OBSIDIAN = "gg-car-obsidian"
        private const val IMG_PIN_DEFAULT = "gg-pin-default"
        private const val IMG_PIN_SELECTED = "gg-pin-selected"
        private const val IMG_PIN_SAVED = "gg-pin-saved"

        private const val MOVING_SPEED_THRESHOLD_MPS = 1.0f // ~3.6 km/h activates 3D car
    }

    private var map: MapLibreMap? = null
    private var activeStyle: Style? = null
    private var currentLoadedStyleKey: String = ""
    private var worldBaseFile: File? = null
    private val mountedRegionIds = mutableSetOf<String>()

    private var lastRoutePoints: List<GeoPoint> = emptyList()
    private var lastAvoidanceZones: List<AvoidanceZone> = emptyList()
    private var lastBreadcrumbs: List<GeoPoint> = emptyList()
    private var lastPlaces: List<PlaceResult> = emptyList()
    private var lastSelectedPlace: PlaceResult? = null
    private var lastLocation: LocationSnapshot = LocationSnapshot()
    private var lastIsNavigating: Boolean = false
    private var lastCarColor: CarColorPreset = CarColorPreset.PEARL
    private var lastAlwaysShowCar: Boolean = false
    private var lastRequestedStyle: GhostMapStyle = GhostMapStyle.STEALTH_DARK
    private var lastStrictOffline: Boolean = false

    fun attachMap(
        mapView: MapView,
        mapLibreMap: MapLibreMap,
        worldBase: File?,
        initialStyle: GhostMapStyle,
        strictOfflineMode: Boolean,
        onMapClick: (GeoPoint) -> Unit,
        onCameraIdle: (GeoPoint) -> Unit = {},
        onStyleReady: () -> Unit
    ) {
        this.map = mapLibreMap
        this.worldBaseFile = worldBase

        mapLibreMap.uiSettings.apply {
            isCompassEnabled = false
            isLogoEnabled = false
            isAttributionEnabled = false
            isTiltGesturesEnabled = true
            isRotateGesturesEnabled = true
        }

        mapLibreMap.addOnMapClickListener { latLng ->
            onMapClick(GeoPoint(latLng.latitude, latLng.longitude))
            false
        }

        mapLibreMap.addOnCameraIdleListener {
            val target = mapLibreMap.cameraPosition.target
            if (target != null) {
                onCameraIdle(GeoPoint(target.latitude, target.longitude))
            }
        }

        mapView.addOnDidFailLoadingMapListener {
            if (!lastStrictOffline) {
                loadMapStyle(lastRequestedStyle, strictOfflineMode = true, forceReload = true, onReady = {})
            }
        }

        loadMapStyle(initialStyle, strictOfflineMode, forceReload = true, onReady = onStyleReady)
    }

    private var styleReloadSeq: Int = 0

    fun loadMapStyle(
        requestedStyle: GhostMapStyle,
        strictOfflineMode: Boolean,
        forceReload: Boolean = false,
        onReady: () -> Unit = {}
    ) {
        val m = map ?: return
        lastRequestedStyle = requestedStyle
        lastStrictOffline = strictOfflineMode

        val effectiveOffline = strictOfflineMode || requestedStyle == GhostMapStyle.PURE_OFFLINE
        val styleKey = "${requestedStyle.id}_${if (effectiveOffline) "offline" else "online"}"
        if (!forceReload && currentLoadedStyleKey == styleKey && activeStyle != null) {
            onReady()
            return
        }
        currentLoadedStyleKey = styleKey
        styleReloadSeq++
        mountedRegionIds.clear()

        // Cache-busting query suffix ensures MapLibre refreshes upscaled offline fallback tiles
        // immediately when toggling Online <-> Offline or after downloading a new region pack.
        val qTag = "?m=${if (effectiveOffline) "off" else "on"}&v=$styleReloadSeq"
        val effectiveRasterStyle = when {
            requestedStyle == GhostMapStyle.OPENFREEMAP_3D && effectiveOffline -> GhostMapStyle.STEALTH_DARK
            requestedStyle == GhostMapStyle.PURE_OFFLINE -> GhostMapStyle.STEALTH_DARK
            else -> requestedStyle
        }

        val builder = if (!effectiveOffline && requestedStyle == GhostMapStyle.OPENFREEMAP_3D) {
            Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")
        } else {
            val baseTemplate = OfflineTileVault.baseUrlTemplateFor(effectiveRasterStyle)
                ?: OfflineTileVault.baseUrlTemplateFor(GhostMapStyle.STEALTH_DARK)!!
            val overlayTemplate = OfflineTileVault.overlayUrlTemplateFor(effectiveRasterStyle)
            val maxZ = if (effectiveRasterStyle == GhostMapStyle.STEALTH_DARK) 16 else 18
            Style.Builder().fromJson(
                buildLayeredRasterStyleJson(
                    bgHex = offlineBackgroundHex(effectiveRasterStyle),
                    baseTileUrl = "$baseTemplate$qTag",
                    baseMaxZoom = maxZ,
                    overlayTileUrl = overlayTemplate?.let { "$it$qTag" },
                    overlayMaxZoom = maxZ
                )
            )
        }

        m.setStyle(builder) { style ->
            activeStyle = style
            registerCustomBitmaps(style)
            if (requestedStyle == GhostMapStyle.PURE_OFFLINE) {
                mountProgrammaticOfflineWorld(style, requestedStyle)
            }
            if (effectiveOffline) {
                mountWorldBasePmtiles(style, worldBaseFile)
            }
            mountOverlaySourcesAndLayers(style)
            mountAllRegionalPmtiles(style)
            updateActiveRoute(lastRoutePoints, lastAvoidanceZones)
            updateLiveBreadcrumbs(lastBreadcrumbs)
            updatePlaceMarkers(lastPlaces, lastSelectedPlace)
            updateUserVehicleMarker(lastLocation, lastIsNavigating, lastCarColor, lastAlwaysShowCar)
            onReady()
        }
    }

    private fun offlineBackgroundHex(style: GhostMapStyle): String = when (style) {
        GhostMapStyle.STANDARD_MAP -> "#1C1E22"
        GhostMapStyle.SATELLITE -> "#060C14"
        GhostMapStyle.TERRAIN -> "#101612"
        GhostMapStyle.STEALTH_DARK, GhostMapStyle.OPENFREEMAP_3D, GhostMapStyle.PURE_OFFLINE -> "#09090B"
    }

    // Offline tiles are served transparently through OfflineTileVault.MapLibreTileInterceptor
    // registered with HttpRequestUtil.setOkHttpClient(...) in MainActivity.onCreate().

    private fun buildLayeredRasterStyleJson(
        bgHex: String,
        baseTileUrl: String,
        baseMaxZoom: Int,
        overlayTileUrl: String? = null,
        overlayMaxZoom: Int = 18
    ): String {
        val overlaySource = if (overlayTileUrl != null) {
            """,
                "raster-overlay": {
                  "type": "raster",
                  "tiles": ["$overlayTileUrl"],
                  "tileSize": 256,
                  "maxzoom": $overlayMaxZoom
                }
            """.trimIndent()
        } else ""

        val overlayLayer = if (overlayTileUrl != null) {
            """,
                {
                  "id": "raster-overlay-layer",
                  "type": "raster",
                  "source": "raster-overlay",
                  "minzoom": 0,
                  "maxzoom": 22
                }
            """.trimIndent()
        } else ""

        return """
            {
              "version": 8,
              "name": "GhostGrid Map",
              "sources": {
                "raster-base": {
                  "type": "raster",
                  "tiles": ["$baseTileUrl"],
                  "tileSize": 256,
                  "maxzoom": $baseMaxZoom
                }$overlaySource
              },
              "layers": [
                {
                  "id": "bg",
                  "type": "background",
                  "paint": { "background-color": "$bgHex" }
                },
                {
                  "id": "raster-base-layer",
                  "type": "raster",
                  "source": "raster-base",
                  "minzoom": 0,
                  "maxzoom": 22
                }$overlayLayer
              ]
            }
        """.trimIndent()
    }

    fun hotReloadRegionalPacks() {
        val style = activeStyle ?: return
        mountAllRegionalPmtiles(style)
    }

    fun updateUserVehicleMarker(
        snapshot: LocationSnapshot,
        isNavigating: Boolean,
        carColor: CarColorPreset,
        alwaysShowCar: Boolean
    ) {
        lastLocation = snapshot
        lastIsNavigating = isNavigating
        lastCarColor = carColor
        lastAlwaysShowCar = alwaysShowCar

        val style = activeStyle ?: return
        val src = style.getSourceAs<GeoJsonSource>(SOURCE_USER_VEHICLE) ?: return

        val show3dCar = alwaysShowCar || isNavigating || snapshot.speedMps >= MOVING_SPEED_THRESHOLD_MPS
        val iconKey = if (show3dCar) {
            when (carColor) {
                CarColorPreset.PEARL -> IMG_CAR_PEARL
                CarColorPreset.SILVER -> IMG_CAR_SILVER
                CarColorPreset.OBSIDIAN -> IMG_CAR_OBSIDIAN
            }
        } else {
            IMG_BEACON
        }

        val feature = Feature.fromGeometry(Point.fromLngLat(snapshot.point.lon, snapshot.point.lat)).apply {
            addStringProperty("icon", iconKey)
            addNumberProperty("bearing", snapshot.bearing.toDouble())
            addNumberProperty("scale", if (show3dCar) 0.56 else 0.50)
        }
        src.setGeoJson(feature)
    }

    fun applyCameraMode(mode: GhostCameraMode, center: GeoPoint, bearing: Float = 0f) {
        when (mode) {
            GhostCameraMode.FREE -> animateTo(center, zoom = 14.4, tilt = 0.0, bearing = 0.0)
            GhostCameraMode.TRACKING -> animateTo(center, zoom = 15.8, tilt = 16.0, bearing = 0.0)
            GhostCameraMode.COMPASS -> animateTo(center, zoom = 16.4, tilt = 34.0, bearing = bearing.toDouble())
            GhostCameraMode.NAVIGATION_3D -> animateTo(center, zoom = 17.3, tilt = 56.0, bearing = bearing.toDouble())
        }
    }

    fun animateTo(point: GeoPoint, zoom: Double = 15.0, tilt: Double = 0.0, bearing: Double = 0.0) {
        val m = map ?: return
        val position = CameraPosition.Builder()
            .target(LatLng(point.lat, point.lon))
            .zoom(zoom)
            .tilt(tilt)
            .bearing(bearing)
            .build()
        m.animateCamera(CameraUpdateFactory.newCameraPosition(position), 650)
    }

    fun updateActiveRoute(routePoints: List<GeoPoint>, avoidanceZones: List<AvoidanceZone>) {
        lastRoutePoints = routePoints
        lastAvoidanceZones = avoidanceZones
        val style = activeStyle ?: return

        val routeSource = style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE)
        if (routePoints.size >= 2) {
            val line = LineString.fromLngLats(routePoints.map { Point.fromLngLat(it.lon, it.lat) })
            routeSource?.setGeoJson(Feature.fromGeometry(line))
        } else {
            routeSource?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        }

        val avoidSource = style.getSourceAs<GeoJsonSource>(SOURCE_AVOIDANCE)
        if (avoidanceZones.isNotEmpty()) {
            val features = avoidanceZones.mapNotNull { zone ->
                if (zone.polygon.size < 4) null else {
                    val ring = zone.polygon.map { Point.fromLngLat(it.lon, it.lat) }
                    Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
                }
            }
            avoidSource?.setGeoJson(FeatureCollection.fromFeatures(features))
        } else {
            avoidSource?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        }
    }

    fun updateLiveBreadcrumbs(points: List<GeoPoint>) {
        lastBreadcrumbs = points
        val style = activeStyle ?: return
        val src = style.getSourceAs<GeoJsonSource>(SOURCE_BREADCRUMBS) ?: return
        if (points.size >= 2) {
            val line = LineString.fromLngLats(points.map { Point.fromLngLat(it.lon, it.lat) })
            src.setGeoJson(Feature.fromGeometry(line))
        } else {
            src.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        }
    }

    fun updatePlaceMarkers(places: List<PlaceResult>, selectedPlace: PlaceResult?) {
        lastPlaces = places
        lastSelectedPlace = selectedPlace
        val style = activeStyle ?: return
        val src = style.getSourceAs<GeoJsonSource>(SOURCE_MARKERS) ?: return

        val combined = buildList {
            addAll(places)
            if (selectedPlace != null && places.none { it.id == selectedPlace.id }) {
                add(selectedPlace)
            }
        }
        val features = combined.map { place ->
            val pinIcon = when {
                place.id == selectedPlace?.id -> IMG_PIN_SELECTED
                place.isSaved -> IMG_PIN_SAVED
                else -> IMG_PIN_DEFAULT
            }
            Feature.fromGeometry(Point.fromLngLat(place.lon, place.lat)).apply {
                addStringProperty("name", place.name)
                addStringProperty("pinIcon", pinIcon)
                addNumberProperty("pinScale", if (place.id == selectedPlace?.id) 0.56 else 0.42)
            }
        }
        src.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun registerCustomBitmaps(style: Style) {
        style.addImage(IMG_BEACON, MapBitmapFactory.createUserBeaconBitmap())
        style.addImage(
            IMG_CAR_PEARL,
            MapBitmapFactory.create3dCarBitmap(
                CarColorPreset.PEARL.primaryHex,
                CarColorPreset.PEARL.highlightHex,
                CarColorPreset.PEARL.darkHex
            )
        )
        style.addImage(
            IMG_CAR_SILVER,
            MapBitmapFactory.create3dCarBitmap(
                CarColorPreset.SILVER.primaryHex,
                CarColorPreset.SILVER.highlightHex,
                CarColorPreset.SILVER.darkHex
            )
        )
        style.addImage(
            IMG_CAR_OBSIDIAN,
            MapBitmapFactory.create3dCarBitmap(
                CarColorPreset.OBSIDIAN.primaryHex,
                CarColorPreset.OBSIDIAN.highlightHex,
                CarColorPreset.OBSIDIAN.darkHex
            )
        )
        // Minimal Monochrome Teardrop Pins
        style.addImage(
            IMG_PIN_DEFAULT,
            MapBitmapFactory.createTeardropPinBitmap("#3F3F46", "#18181B", "#FFFFFF", "#FFFFFF", "#18181B")
        )
        style.addImage(
            IMG_PIN_SELECTED,
            MapBitmapFactory.createTeardropPinBitmap("#FFFFFF", "#D4D4D8", "#09090B", "#09090B", "#FFFFFF")
        )
        style.addImage(
            IMG_PIN_SAVED,
            MapBitmapFactory.createTeardropPinBitmap("#E4E4E7", "#71717A", "#FFFFFF", "#18181B", "#FFFFFF")
        )
    }

    private fun mountProgrammaticOfflineWorld(style: Style, requestedStyle: GhostMapStyle) {
        val rawGeoJson = runCatching {
            context.assets.open("world_land.geojson").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return

        if (style.getSource(SOURCE_WORLD_OFFLINE) == null) {
            style.addSource(GeoJsonSource(SOURCE_WORLD_OFFLINE, rawGeoJson))
        }

        val (graticuleColor, landColor, coastColor) = when (requestedStyle) {
            GhostMapStyle.SATELLITE -> Triple("#0F2438", "#182920", "#4A7058")
            GhostMapStyle.TERRAIN -> Triple("#1E2822", "#262D24", "#7C8F74")
            GhostMapStyle.STANDARD_MAP -> Triple("#2A2D33", "#363940", "#D4D4D8")
            else -> Triple("#1F1F23", "#1C1C1F", "#A1A1AA")
        }

        style.addLayer(
            LineLayer("offline-world-graticule", SOURCE_WORLD_OFFLINE).apply {
                setFilter(Expression.eq(Expression.get("kind"), Expression.literal("graticule")))
                setProperties(
                    PropertyFactory.lineColor(graticuleColor),
                    PropertyFactory.lineWidth(0.9f),
                    PropertyFactory.lineOpacity(0.8f)
                )
            }
        )
        style.addLayer(
            FillLayer("offline-world-land", SOURCE_WORLD_OFFLINE).apply {
                setFilter(Expression.eq(Expression.get("kind"), Expression.literal("continent")))
                setProperties(
                    PropertyFactory.fillColor(landColor),
                    PropertyFactory.fillOpacity(0.98f)
                )
            }
        )
        style.addLayer(
            LineLayer("offline-world-coast", SOURCE_WORLD_OFFLINE).apply {
                setFilter(Expression.eq(Expression.get("kind"), Expression.literal("continent")))
                setProperties(
                    PropertyFactory.lineColor(coastColor),
                    PropertyFactory.lineWidth(1.5f),
                    PropertyFactory.lineOpacity(0.9f)
                )
            }
        )
        style.addLayer(
            CircleLayer("offline-world-hub-halo", SOURCE_WORLD_OFFLINE).apply {
                setFilter(Expression.eq(Expression.get("kind"), Expression.literal("hub")))
                setProperties(
                    PropertyFactory.circleRadius(8f),
                    PropertyFactory.circleColor("#FFFFFF"),
                    PropertyFactory.circleOpacity(0.22f),
                    PropertyFactory.circleBlur(0.6f)
                )
            }
        )
        style.addLayer(
            CircleLayer("offline-world-hub-dot", SOURCE_WORLD_OFFLINE).apply {
                setFilter(Expression.eq(Expression.get("kind"), Expression.literal("hub")))
                setProperties(
                    PropertyFactory.circleRadius(3.8f),
                    PropertyFactory.circleColor("#FFFFFF"),
                    PropertyFactory.circleStrokeColor("#08080A"),
                    PropertyFactory.circleStrokeWidth(1.2f)
                )
            }
        )
    }

    private fun mountWorldBasePmtiles(style: Style, file: File?) {
        if (file == null || !file.exists()) return
        if (style.getSource(SOURCE_WORLD_BASE) != null) return

        val pmtilesUri = "pmtiles://file://${file.absolutePath}"
        style.addSource(VectorSource(SOURCE_WORLD_BASE, pmtilesUri))

        style.addLayer(
            FillLayer("world-pm-water", SOURCE_WORLD_BASE).apply {
                setSourceLayer("water")
                setMaxZoom(7f)
                setProperties(PropertyFactory.fillColor("#050506"))
            }
        )
        style.addLayer(
            LineLayer("world-pm-boundaries", SOURCE_WORLD_BASE).apply {
                setSourceLayer("boundary")
                setMaxZoom(7f)
                setProperties(
                    PropertyFactory.lineColor("#71717A"),
                    PropertyFactory.lineWidth(1.2f),
                    PropertyFactory.lineOpacity(0.85f)
                )
            }
        )
    }

    private fun mountAllRegionalPmtiles(style: Style) {
        val installed = mapAssetManager.getInstalledRegionalPmtiles()
        val currentIds = installed.map { it.first }.toSet()

        val removed = mountedRegionIds - currentIds
        for (regionId in removed) {
            listOf("water", "landuse", "roads-minor", "roads-major", "buildings-3d").forEach { suffix ->
                runCatching { style.removeLayer("reg-$regionId-$suffix") }
            }
            runCatching { style.removeSource("reg-src-$regionId") }
            mountedRegionIds.remove(regionId)
        }

        for ((regionId, pmtilesFile) in installed) {
            if (regionId in mountedRegionIds) continue

            val sourceId = "reg-src-$regionId"
            val pmtilesUri = "pmtiles://file://${pmtilesFile.absolutePath}"
            style.addSource(VectorSource(sourceId, pmtilesUri))

            val anchorLayerId = LAYER_BREADCRUMBS_GLOW

            style.addLayerBelow(
                FillLayer("reg-$regionId-water", sourceId).apply {
                    setSourceLayer("water")
                    setMinZoom(6f)
                    setProperties(PropertyFactory.fillColor("#050506"))
                },
                anchorLayerId
            )

            style.addLayerBelow(
                FillLayer("reg-$regionId-landuse", sourceId).apply {
                    setSourceLayer("landuse")
                    setMinZoom(8f)
                    setProperties(
                        PropertyFactory.fillColor("#141416"),
                        PropertyFactory.fillOpacity(0.85f)
                    )
                },
                anchorLayerId
            )

            style.addLayerBelow(
                LineLayer("reg-$regionId-roads-minor", sourceId).apply {
                    setSourceLayer("transportation")
                    setMinZoom(11f)
                    setProperties(
                        PropertyFactory.lineColor("#3F3F46"),
                        PropertyFactory.lineWidth(
                            Expression.interpolate(
                                Expression.exponential(1.4f),
                                Expression.zoom(),
                                Expression.stop(11, 1.0f),
                                Expression.stop(16, 4.8f)
                            )
                        ),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                    )
                },
                anchorLayerId
            )

            style.addLayerBelow(
                LineLayer("reg-$regionId-roads-major", sourceId).apply {
                    setSourceLayer("transportation")
                    setMinZoom(6f)
                    setFilter(
                        Expression.match(
                            Expression.get("class"),
                            Expression.literal(false),
                            Expression.stop("motorway", true),
                            Expression.stop("trunk", true),
                            Expression.stop("primary", true),
                            Expression.stop("secondary", true)
                        )
                    )
                    setProperties(
                        PropertyFactory.lineColor("#D4D4D8"),
                        PropertyFactory.lineWidth(
                            Expression.interpolate(
                                Expression.exponential(1.4f),
                                Expression.zoom(),
                                Expression.stop(6, 1.0f),
                                Expression.stop(15, 6.0f)
                            )
                        ),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
                    )
                },
                anchorLayerId
            )

            style.addLayerBelow(
                FillExtrusionLayer("reg-$regionId-buildings-3d", sourceId).apply {
                    setSourceLayer("building")
                    setMinZoom(13.5f)
                    setProperties(
                        PropertyFactory.fillExtrusionColor("#27272A"),
                        PropertyFactory.fillExtrusionOpacity(0.9f),
                        PropertyFactory.fillExtrusionHeight(
                            Expression.coalesce(
                                Expression.get("render_height"),
                                Expression.get("height"),
                                Expression.literal(10f)
                            )
                        ),
                        PropertyFactory.fillExtrusionBase(
                            Expression.coalesce(
                                Expression.get("render_min_height"),
                                Expression.literal(0f)
                            )
                        )
                    )
                },
                anchorLayerId
            )

            mountedRegionIds.add(regionId)
        }
    }

    private fun mountOverlaySourcesAndLayers(style: Style) {
        // 1. Live Encrypted Recording Breadcrumb Polyline (Silver-Grey Dashed/Solid)
        style.addSource(GeoJsonSource(SOURCE_BREADCRUMBS))
        style.addLayer(
            LineLayer(LAYER_BREADCRUMBS_GLOW, SOURCE_BREADCRUMBS).apply {
                setProperties(
                    PropertyFactory.lineColor("#000000"),
                    PropertyFactory.lineWidth(8f),
                    PropertyFactory.lineOpacity(0.55f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            }
        )
        style.addLayer(
            LineLayer(LAYER_BREADCRUMBS_CORE, SOURCE_BREADCRUMBS).apply {
                setProperties(
                    PropertyFactory.lineColor("#D4D4D8"),
                    PropertyFactory.lineWidth(3.8f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            }
        )

        // 2. Road Blocked Ahead Avoidance Polygons (Restrained Alert Red)
        style.addSource(GeoJsonSource(SOURCE_AVOIDANCE))
        style.addLayer(
            FillLayer(LAYER_AVOIDANCE_FILL, SOURCE_AVOIDANCE).apply {
                setProperties(
                    PropertyFactory.fillColor("#E5484D"),
                    PropertyFactory.fillOpacity(0.28f)
                )
            }
        )
        style.addLayer(
            LineLayer(LAYER_AVOIDANCE_LINE, SOURCE_AVOIDANCE).apply {
                setProperties(
                    PropertyFactory.lineColor("#E5484D"),
                    PropertyFactory.lineWidth(2.4f)
                )
            }
        )

        // 3. Minimal High-Contrast Active Route Polyline (Black Casing + Pure White Core)
        style.addSource(GeoJsonSource(SOURCE_ROUTE))
        style.addLayer(
            LineLayer(LAYER_ROUTE_CASING, SOURCE_ROUTE).apply {
                setProperties(
                    PropertyFactory.lineColor("#000000"),
                    PropertyFactory.lineWidth(11.5f),
                    PropertyFactory.lineOpacity(0.85f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            }
        )
        style.addLayer(
            LineLayer(LAYER_ROUTE_CORE, SOURCE_ROUTE).apply {
                setProperties(
                    PropertyFactory.lineColor("#FFFFFF"),
                    PropertyFactory.lineWidth(5.8f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            }
        )

        // 4. Minimal Monochrome 3D Teardrop POI & Dropped Pin Markers
        style.addSource(GeoJsonSource(SOURCE_MARKERS))
        style.addLayer(
            SymbolLayer(LAYER_MARKERS_SYMBOL, SOURCE_MARKERS).apply {
                setProperties(
                    PropertyFactory.iconImage(Expression.get("pinIcon")),
                    PropertyFactory.iconSize(Expression.get("pinScale")),
                    PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true)
                )
            }
        )

        // 5. Custom Monochrome User Beacon / Playful 3D Car Model
        style.addSource(GeoJsonSource(SOURCE_USER_VEHICLE))
        style.addLayer(
            SymbolLayer(LAYER_USER_VEHICLE, SOURCE_USER_VEHICLE).apply {
                setProperties(
                    PropertyFactory.iconImage(Expression.get("icon")),
                    PropertyFactory.iconSize(Expression.get("scale")),
                    PropertyFactory.iconRotate(Expression.get("bearing")),
                    PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                    PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true)
                )
            }
        )
    }
}
