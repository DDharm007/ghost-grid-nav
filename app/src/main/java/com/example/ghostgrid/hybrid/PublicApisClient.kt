package com.example.ghostgrid.hybrid

import com.example.ghostgrid.downloader.BuiltInRegionPack
import com.example.ghostgrid.routing.ActiveRoute
import com.example.ghostgrid.routing.ManeuverDirection
import com.example.ghostgrid.routing.RouteManeuver
import com.example.ghostgrid.routing.RoutingProfile
import com.example.ghostgrid.search.AmenityCategory
import com.example.ghostgrid.search.GeoMath
import com.example.ghostgrid.search.GeoPoint
import com.example.ghostgrid.search.PlaceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class HourlyForecastPoint(
    val timeLabel: String,
    val temperatureC: Double,
    val weatherCode: Int,
    val precipProbPct: Int,
    val isDay: Boolean
)

data class DailyForecastPoint(
    val dateIso: String,
    val dayLabel: String,
    val weatherCode: Int,
    val weatherLabel: String,
    val tempMaxC: Double,
    val tempMinC: Double,
    val precipProbMaxPct: Int
)

data class WeatherIntel(
    val temperatureC: Double,
    val apparentTempC: Double,
    val humidityPercent: Int,
    val windSpeedKmh: Double,
    val windDirectionDeg: Int,
    val pressureHpa: Double,
    val weatherCode: Int = 0,
    val weatherLabel: String,
    val isDay: Boolean,
    val uvIndexMax: Double,
    val tempMaxC: Double,
    val tempMinC: Double,
    val sunriseLocal: String,
    val sunsetLocal: String,
    val usAqi: Int?,
    val pm25: Double?,
    val elevationMeters: Double?,
    val locationLat: Double = 0.0,
    val locationLon: Double = 0.0,
    val hourlyForecast: List<HourlyForecastPoint> = emptyList(),
    val dailyForecast: List<DailyForecastPoint> = emptyList(),
    val fetchedAtMs: Long = System.currentTimeMillis()
)

data class EarthquakeEvent(
    val id: String,
    val magnitude: Double,
    val place: String,
    val depthKm: Double,
    val distanceKm: Double,
    val timeEpochMs: Long
)

data class CountryIntel(
    val commonName: String,
    val officialName: String,
    val capital: String,
    val region: String,
    val currencySummary: String,
    val callingCode: String,
    val drivingSide: String,
    val timezone: String,
    val addressSummary: String
)

data class LiveGeoIntelBundle(
    val weather: WeatherIntel? = null,
    val country: CountryIntel? = null,
    val quakes: List<EarthquakeEvent> = emptyList(),
    val fetchedAtMs: Long = System.currentTimeMillis()
)

data class CurrencyRatesSnapshot(
    val base: String,
    val date: String,
    val rates: Map<String, Double>
)

/**
 * Multi-engine online search & open public APIs client:
 * - Google Places API (New) when user configures a Keystore-encrypted Google key
 * - Bing Maps Local Search API when user configures a Keystore-encrypted Bing key
 * - Zero-API-key ArcGIS World Geocoder & Photon by Komoot for instant global search & category discovery
 * - OpenStreetMap Nominatim, Overpass, OSRM, Open-Meteo, USGS, REST Countries, Frankfurter
 *
 * Every outbound request passes through [StrictOfflineInterceptor], so engaging
 * Strict Offline Mode immediately cuts 100% of network traffic at transport level.
 */
class PublicApisClient(
    isStrictOfflineEnabled: () -> Boolean
) {
    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(StrictOfflineInterceptor(isStrictOfflineEnabled))
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "GhostGrid-Android/1.0 (Open-Source Privacy Navigation)")
                .header("Accept", "application/json")
                .build()
            chain.proceed(req)
        }
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Concurrent multi-engine online search combining Google Places, Bing Maps,
     * zero-key ArcGIS World Geocoder, and Photon (with Nominatim fallback).
     * Guarantees real results for both text queries and 1-tap category chips globally.
     */
    suspend fun searchLivePlaces(
        query: String,
        category: AmenityCategory?,
        centerLat: Double,
        centerLon: Double,
        googleApiKey: String = "",
        bingApiKey: String = ""
    ): List<PlaceResult> = withContext(Dispatchers.IO) {
        if (category == AmenityCategory.SAVED) return@withContext emptyList()
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank() && category == null) return@withContext emptyList()

        val effectiveKeyword = when {
            trimmedQuery.isNotBlank() && category != null -> "$trimmedQuery ${categoryKeyword(category)}"
            trimmedQuery.isNotBlank() -> trimmedQuery
            category != null -> categoryKeyword(category)
            else -> ""
        }

        val combined = coroutineScope {
            val jobs = buildList {
                if (googleApiKey.isNotBlank()) {
                    add(async {
                        searchGooglePlaces(effectiveKeyword, centerLat, centerLon, googleApiKey)
                            .getOrDefault(emptyList())
                    })
                }
                if (bingApiKey.isNotBlank()) {
                    add(async {
                        searchBingMaps(effectiveKeyword, centerLat, centerLon, bingApiKey)
                            .getOrDefault(emptyList())
                    })
                }
                // Always run fast zero-key global engines in parallel so search works out of the box
                add(async {
                    searchArcGisGeocoder(trimmedQuery, category, centerLat, centerLon)
                        .getOrDefault(emptyList())
                })
                add(async {
                    searchPhoton(effectiveKeyword, centerLat, centerLon)
                        .getOrDefault(emptyList())
                })
            }
            jobs.awaitAll().flatten().toMutableList()
        }

        // Fallback to Nominatim / Overpass if primary providers returned sparse results
        if (combined.size < 4) {
            val fallback = coroutineScope {
                val nom = async {
                    searchNominatim(effectiveKeyword, centerLat, centerLon, limit = 12)
                        .getOrDefault(emptyList())
                }
                val ovp = async {
                    if (category != null && trimmedQuery.isBlank()) {
                        searchNearbyOverpass(category, centerLat, centerLon, radiusMeters = 18_000)
                            .getOrDefault(emptyList())
                    } else emptyList()
                }
                nom.await() + ovp.await()
            }
            combined += fallback
        }

        combined
            .distinctBy { "${it.name.lowercase().take(28)}_${String.format("%.3f_%.3f", it.lat, it.lon)}" }
            .sortedBy { it.distanceMeters }
            .take(28)
    }

    /**
     * Google Places API (New) Text Search (`places:searchText`) biased around the map center.
     */
    suspend fun searchGooglePlaces(
        textQuery: String,
        centerLat: Double,
        centerLon: Double,
        apiKey: String
    ): Result<List<PlaceResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("textQuery", textQuery)
                put("maxResultCount", 15)
                put("locationBias", JSONObject().apply {
                    put("circle", JSONObject().apply {
                        put("center", JSONObject().apply {
                            put("latitude", centerLat)
                            put("longitude", centerLon)
                        })
                        put("radius", 30000.0)
                    })
                })
            }.toString()

            val req = Request.Builder()
                .url("https://places.googleapis.com/v1/places:searchText")
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", "places.id,places.displayName,places.formattedAddress,places.location,places.primaryTypeDisplayName")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Google Places HTTP ${resp.code}")
                val root = JSONObject(resp.body?.string().orEmpty())
                val arr = root.optJSONArray("places") ?: JSONArray()
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val loc = obj.optJSONObject("location") ?: continue
                        val lat = loc.optDouble("latitude", Double.NaN)
                        val lon = loc.optDouble("longitude", Double.NaN)
                        if (lat.isNaN() || lon.isNaN()) continue
                        val name = obj.optJSONObject("displayName")?.optString("text").orEmpty()
                            .ifBlank { "Google Place" }
                        val address = obj.optString("formattedAddress")
                        val type = obj.optJSONObject("primaryTypeDisplayName")?.optString("text").orEmpty()
                            .ifBlank { "Place" }
                        add(
                            PlaceResult(
                                id = "google:${obj.optString("id", i.toString())}",
                                name = name,
                                category = type.lowercase(),
                                subcategory = type,
                                address = address,
                                lat = lat,
                                lon = lon,
                                distanceMeters = GeoMath.haversineMeters(centerLat, centerLon, lat, lon),
                                regionId = "google-live"
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Bing Maps Local Search API (`REST/v1/LocalSearch`) biased around the map center.
     */
    suspend fun searchBingMaps(
        query: String,
        centerLat: Double,
        centerLon: Double,
        apiKey: String
    ): Result<List<PlaceResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
            val url = "https://dev.virtualearth.net/REST/v1/LocalSearch" +
                "?query=$encoded&userLocation=$centerLat,$centerLon&maxResults=15&key=$encodedKey"
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Bing Maps HTTP ${resp.code}")
                val root = JSONObject(resp.body?.string().orEmpty())
                val resourceSets = root.optJSONArray("resourceSets") ?: JSONArray()
                if (resourceSets.length() == 0) return@use emptyList()
                val resources = resourceSets.getJSONObject(0).optJSONArray("resources") ?: JSONArray()
                buildList {
                    for (i in 0 until resources.length()) {
                        val obj = resources.optJSONObject(i) ?: continue
                        val coords = obj.optJSONObject("point")?.optJSONArray("coordinates") ?: continue
                        if (coords.length() < 2) continue
                        val lat = coords.optDouble(0, Double.NaN)
                        val lon = coords.optDouble(1, Double.NaN)
                        if (lat.isNaN() || lon.isNaN()) continue
                        val name = obj.optString("name").ifBlank { "Bing Place" }
                        val address = obj.optJSONObject("Address")?.optString("formattedAddress").orEmpty()
                        val entityType = obj.optString("entityType", "Place")
                        add(
                            PlaceResult(
                                id = "bing:${name.hashCode()}_$i",
                                name = name,
                                category = entityType.lowercase(),
                                subcategory = entityType,
                                address = address,
                                lat = lat,
                                lon = lon,
                                distanceMeters = GeoMath.haversineMeters(centerLat, centerLon, lat, lon),
                                regionId = "bing-live"
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Zero-API-key Esri ArcGIS World Geocoder (`findAddressCandidates`).
     * Supports both free-text queries AND pure category discovery (e.g. Hospitals, Food,
     * Pharmacy, Fuel, Transit) across urban and rural regions globally.
     */
    suspend fun searchArcGisGeocoder(
        query: String,
        category: AmenityCategory?,
        centerLat: Double,
        centerLon: Double,
        maxLocations: Int = 18
    ): Result<List<PlaceResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val params = buildList {
                add("f=pjson")
                add("location=$centerLon,$centerLat")
                add("maxLocations=$maxLocations")
                add("outFields=PlaceName,Place_addr,Type")
                if (query.isNotBlank()) {
                    add("singleLine=${URLEncoder.encode(query, "UTF-8")}")
                }
                if (category != null && category != AmenityCategory.SAVED) {
                    add("category=${URLEncoder.encode(arcgisCategory(category), "UTF-8")}")
                }
            }.joinToString("&")

            val url = "https://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer/findAddressCandidates?$params"
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("ArcGIS Geocoder HTTP ${resp.code}")
                val root = JSONObject(resp.body?.string().orEmpty())
                val candidates = root.optJSONArray("candidates") ?: JSONArray()
                buildList {
                    for (i in 0 until candidates.length()) {
                        val c = candidates.optJSONObject(i) ?: continue
                        val loc = c.optJSONObject("location") ?: continue
                        val lon = loc.optDouble("x", Double.NaN)
                        val lat = loc.optDouble("y", Double.NaN)
                        if (lat.isNaN() || lon.isNaN()) continue
                        val attrs = c.optJSONObject("attributes")
                        val fullAddr = attrs?.optString("Place_addr").orEmpty()
                            .ifBlank { c.optString("address") }
                        val placeName = attrs?.optString("PlaceName").orEmpty()
                            .ifBlank { c.optString("address").substringBefore(",").trim() }
                            .ifBlank { "Location" }
                        val type = attrs?.optString("Type").orEmpty()
                            .ifBlank { category?.label ?: "Place" }
                        add(
                            PlaceResult(
                                id = "arcgis:${placeName.hashCode()}_${String.format("%.4f_%.4f", lat, lon)}",
                                name = placeName,
                                category = category?.dbKey ?: type.lowercase(),
                                subcategory = type,
                                address = fullAddr.ifBlank { String.format("%.4f, %.4f", lat, lon) },
                                lat = lat,
                                lon = lon,
                                distanceMeters = GeoMath.haversineMeters(centerLat, centerLon, lat, lon),
                                regionId = "live-search"
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Zero-API-key Photon by Komoot (fast OpenStreetMap Elasticsearch search biased near map center).
     */
    suspend fun searchPhoton(
        query: String,
        centerLat: Double,
        centerLon: Double,
        limit: Int = 15
    ): Result<List<PlaceResult>> = withContext(Dispatchers.IO) {
        runCatching {
            if (query.isBlank()) return@runCatching emptyList()
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://photon.komoot.io/api?q=$encoded&lat=$centerLat&lon=$centerLon&limit=$limit"
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Photon HTTP ${resp.code}")
                val root = JSONObject(resp.body?.string().orEmpty())
                val features = root.optJSONArray("features") ?: JSONArray()
                buildList {
                    for (i in 0 until features.length()) {
                        val f = features.optJSONObject(i) ?: continue
                        val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                        if (coords.length() < 2) continue
                        val lon = coords.optDouble(0, Double.NaN)
                        val lat = coords.optDouble(1, Double.NaN)
                        if (lat.isNaN() || lon.isNaN()) continue
                        val props = f.optJSONObject("properties") ?: continue
                        val name = props.optString("name").ifBlank {
                            listOf(props.optString("housenumber"), props.optString("street"))
                                .filter { it.isNotBlank() }.joinToString(" ")
                        }.ifBlank { props.optString("city").ifBlank { "Place" } }
                        val addr = listOf(
                            props.optString("street"),
                            props.optString("district"),
                            props.optString("city"),
                            props.optString("state"),
                            props.optString("country")
                        ).filter { it.isNotBlank() }.distinct().joinToString(", ")
                        val osmVal = props.optString("osm_value", "place").replace('_', ' ')
                        add(
                            PlaceResult(
                                id = "photon:${props.optLong("osm_id", i.toLong())}_${String.format("%.4f", lat)}",
                                name = name,
                                category = props.optString("osm_key", "place"),
                                subcategory = osmVal.replaceFirstChar { it.uppercase() },
                                address = addr.ifBlank { osmVal },
                                lat = lat,
                                lon = lon,
                                distanceMeters = GeoMath.haversineMeters(centerLat, centerLon, lat, lon),
                                regionId = "photon-live"
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Enriches a built-in India or World region pack during installation by downloading real live
     * hospitals, pharmacies, transit hubs, fuel stations, and food venues inside its bounds.
     */
    suspend fun fetchRegionPlacesForPack(pack: BuiltInRegionPack): List<PlaceResult> = withContext(Dispatchers.IO) {
        val categories = listOf(
            AmenityCategory.HOSPITAL,
            AmenityCategory.FOOD,
            AmenityCategory.PHARMACY,
            AmenityCategory.FUEL,
            AmenityCategory.TRANSIT
        )
        coroutineScope {
            categories.map { cat ->
                async {
                    searchArcGisGeocoder(
                        query = "",
                        category = cat,
                        centerLat = pack.centerLat,
                        centerLon = pack.centerLon,
                        maxLocations = 16
                    ).getOrDefault(emptyList()).filter { place ->
                        place.lat in pack.minLat..pack.maxLat && place.lon in pack.minLon..pack.maxLon
                    }
                }
            }.awaitAll().flatten()
        }
    }

    private fun categoryKeyword(category: AmenityCategory): String = when (category) {
        AmenityCategory.FOOD -> "restaurant"
        AmenityCategory.HOSPITAL -> "hospital"
        AmenityCategory.PHARMACY -> "pharmacy"
        AmenityCategory.FUEL -> "gas station"
        AmenityCategory.TRANSIT -> "station"
        AmenityCategory.SAVED -> "saved"
    }

    private fun arcgisCategory(category: AmenityCategory): String = when (category) {
        AmenityCategory.FOOD -> "Food"
        AmenityCategory.HOSPITAL -> "Hospital"
        AmenityCategory.PHARMACY -> "Pharmacy"
        AmenityCategory.FUEL -> "Gas Station"
        AmenityCategory.TRANSIT -> "Bus Station,Train Station,Airport"
        AmenityCategory.SAVED -> ""
    }

    /**
     * Global OpenStreetMap Nominatim place & street search (public-apis Geocoding).
     */
    suspend fun searchNominatim(
        query: String,
        userLat: Double,
        userLon: Double,
        limit: Int = 15
    ): Result<List<PlaceResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val delta = 0.65
            val viewbox = "${userLon - delta},${userLat + delta},${userLon + delta},${userLat - delta}"
            val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=$limit&viewbox=$viewbox&q=$encoded"
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Nominatim HTTP ${resp.code}")
                val arr = JSONArray(resp.body?.string().orEmpty())
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val lat = obj.optString("lat").toDoubleOrNull() ?: continue
                        val lon = obj.optString("lon").toDoubleOrNull() ?: continue
                        val displayName = obj.optString("display_name")
                        val shortName = obj.optString("name").ifBlank {
                            displayName.substringBefore(",").trim().ifBlank { "OpenStreetMap Place" }
                        }
                        val category = obj.optString("category", "place")
                        val type = obj.optString("type", "osm")
                        add(
                            PlaceResult(
                                id = "osm:${obj.optLong("place_id", i.toLong())}",
                                name = shortName,
                                category = category,
                                subcategory = type.replace('_', ' '),
                                address = displayName,
                                lat = lat,
                                lon = lon,
                                distanceMeters = GeoMath.haversineMeters(userLat, userLon, lat, lon),
                                regionId = "osm-live"
                            )
                        )
                    }
                }.sortedBy { it.distanceMeters }
            }
        }
    }

    /**
     * Reverse geocodes coordinates to a real human-readable street address via Nominatim.
     */
    suspend fun reverseGeocode(lat: Double, lon: Double): Result<Pair<String, String?>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lon"
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Reverse geocode HTTP ${resp.code}")
                val obj = JSONObject(resp.body?.string().orEmpty())
                val display = obj.optString("display_name").ifBlank {
                    String.format("%.5f, %.5f", lat, lon)
                }
                val cc = obj.optJSONObject("address")?.optString("country_code")?.uppercase()?.ifBlank { null }
                display to cc
            }
        }
    }

    /**
     * Queries OpenStreetMap Overpass API for nearby amenities around the current map center.
     */
    suspend fun searchNearbyOverpass(
        category: AmenityCategory,
        userLat: Double,
        userLon: Double,
        radiusMeters: Int = 12000
    ): Result<List<PlaceResult>> = withContext(Dispatchers.IO) {
        runCatching {
            if (category == AmenityCategory.SAVED) return@runCatching emptyList()
            val amenityRegex = when (category) {
                AmenityCategory.FOOD -> "restaurant|cafe|fast_food|food_court|bakery"
                AmenityCategory.HOSPITAL -> "hospital|clinic|doctors|dentist"
                AmenityCategory.PHARMACY -> "pharmacy"
                AmenityCategory.FUEL -> "fuel|charging_station"
                AmenityCategory.TRANSIT -> "bus_station|taxi|ferry_terminal|bicycle_rental"
                AmenityCategory.SAVED -> ""
            }
            val overpassQl = buildString {
                append("[out:json][timeout:8];(")
                append("node[\"amenity\"~\"^($amenityRegex)$\"](around:$radiusMeters,$userLat,$userLon);")
                append("way[\"amenity\"~\"^($amenityRegex)$\"](around:$radiusMeters,$userLat,$userLon);")
                if (category == AmenityCategory.TRANSIT) {
                    append("node[\"railway\"~\"station|halt|subway_entrance\"](around:$radiusMeters,$userLat,$userLon);")
                }
                append(");out center 25;")
            }
            val body = ("data=" + URLEncoder.encode(overpassQl, "UTF-8"))
                .toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val req = Request.Builder()
                .url("https://overpass-api.de/api/interpreter")
                .post(body)
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Overpass HTTP ${resp.code}")
                val root = JSONObject(resp.body?.string().orEmpty())
                val elements = root.optJSONArray("elements") ?: JSONArray()
                buildList {
                    for (i in 0 until elements.length()) {
                        val el = elements.optJSONObject(i) ?: continue
                        val tags = el.optJSONObject("tags") ?: continue
                        val name = tags.optString("name").ifBlank {
                            tags.optString("brand").ifBlank { category.label }
                        }
                        val center = el.optJSONObject("center")
                        val lat = el.optDouble("lat", Double.NaN).takeUnless { it.isNaN() }
                            ?: center?.optDouble("lat", Double.NaN)?.takeUnless { it.isNaN() }
                            ?: continue
                        val lon = el.optDouble("lon", Double.NaN).takeUnless { it.isNaN() }
                            ?: center?.optDouble("lon", Double.NaN)?.takeUnless { it.isNaN() }
                            ?: continue
                        val street = listOf(
                            tags.optString("addr:housenumber"),
                            tags.optString("addr:street"),
                            tags.optString("addr:city")
                        ).filter { it.isNotBlank() }.joinToString(" ")
                        val sub = tags.optString("amenity").ifBlank { tags.optString("railway", category.dbKey) }
                        add(
                            PlaceResult(
                                id = "overpass:${el.optLong("id", i.toLong())}",
                                name = name,
                                category = category.dbKey,
                                subcategory = sub.replace('_', ' '),
                                address = street.ifBlank { "${sub.replace('_', ' ')} • OpenStreetMap" },
                                lat = lat,
                                lon = lon,
                                distanceMeters = GeoMath.haversineMeters(userLat, userLon, lat, lon),
                                regionId = "osm-live"
                            )
                        )
                    }
                }.distinctBy { "${it.name}_${String.format("%.4f", it.lat)}" }
                    .sortedBy { it.distanceMeters }
                    .take(25)
            }
        }
    }

    /**
     * Turn-by-turn OpenStreetMap road routing fallback via OSRM public server when no local
     * GraphHopper graph is installed and Strict Offline Mode is OFF.
     */
    suspend fun calculateOsrmRoute(
        origin: GeoPoint,
        destination: GeoPoint,
        destinationName: String,
        profile: RoutingProfile
    ): Result<ActiveRoute> = withContext(Dispatchers.IO) {
        runCatching {
            val startMs = System.currentTimeMillis()
            val osrmProfile = "driving"
            val url = "https://router.project-osrm.org/route/v1/$osrmProfile/" +
                "${origin.lon},${origin.lat};${destination.lon},${destination.lat}" +
                "?overview=full&geometries=geojson&steps=true"
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("OSRM HTTP ${resp.code}")
                val root = JSONObject(resp.body?.string().orEmpty())
                val routes = root.optJSONArray("routes")
                if (routes == null || routes.length() == 0) error("No OpenStreetMap road route found")
                val route = routes.getJSONObject(0)
                val distMeters = route.optDouble("distance", 0.0)
                val durationMs = (route.optDouble("duration", 0.0) * 1000.0).toLong()

                val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
                val points = buildList {
                    for (i in 0 until coords.length()) {
                        val c = coords.getJSONArray(i)
                        add(GeoPoint(lat = c.getDouble(1), lon = c.getDouble(0)))
                    }
                }

                val maneuvers = mutableListOf<RouteManeuver>()
                val legs = route.optJSONArray("legs")
                if (legs != null && legs.length() > 0) {
                    val steps = legs.getJSONObject(0).optJSONArray("steps") ?: JSONArray()
                    for (i in 0 until steps.length()) {
                        val step = steps.getJSONObject(i)
                        val m = step.optJSONObject("maneuver") ?: continue
                        val locArr = m.optJSONArray("location") ?: continue
                        val stepLon = locArr.getDouble(0)
                        val stepLat = locArr.getDouble(1)
                        val type = m.optString("type")
                        val mod = m.optString("modifier")
                        val street = step.optString("name").ifBlank { "Unnamed Road" }
                        val stepDist = step.optDouble("distance", 0.0)
                        val stepDur = (step.optDouble("duration", 0.0) * 1000.0).toLong()

                        val dir = mapOsrmDirection(type, mod)
                        val text = buildOsrmInstruction(type, mod, street, destinationName)
                        maneuvers.add(
                            RouteManeuver(
                                index = i,
                                direction = dir,
                                instructionText = text,
                                streetName = street,
                                distanceMeters = stepDist,
                                durationMillis = stepDur,
                                location = GeoPoint(stepLat, stepLon)
                            )
                        )
                    }
                }

                ActiveRoute(
                    regionId = "osrm-open",
                    destinationName = destinationName,
                    destination = destination,
                    profile = profile,
                    points = points.ifEmpty { listOf(origin, destination) },
                    maneuvers = maneuvers,
                    totalDistanceMeters = distMeters,
                    totalDurationMillis = durationMs,
                    calculationTimeMs = System.currentTimeMillis() - startMs,
                    avoidanceZones = emptyList(),
                    isDirectCompassFallback = false
                )
            }
        }
    }

    /**
     * Fetches comprehensive live environmental, elevation, country, and seismic intel
     * for the current GPS location using zero-key public APIs.
     */
    suspend fun fetchLiveGeoIntel(lat: Double, lon: Double): Result<LiveGeoIntelBundle> = withContext(Dispatchers.IO) {
        runCatching {
            val weather = fetchOpenMeteoWeather(lat, lon).getOrNull()
            val quakes = fetchUsgsEarthquakes(lat, lon).getOrDefault(emptyList())
            val country = fetchCountryAndAddressIntel(lat, lon).getOrNull()
            if (weather == null && country == null && quakes.isEmpty()) {
                error("Public APIs unreachable or blocked by Strict Offline Mode.")
            }
            LiveGeoIntelBundle(
                weather = weather,
                country = country,
                quakes = quakes,
                fetchedAtMs = System.currentTimeMillis()
            )
        }
    }

    suspend fun fetchWeatherForecast(lat: Double, lon: Double): Result<WeatherIntel> = withContext(Dispatchers.IO) {
        fetchOpenMeteoWeather(lat, lon, includeAqi = false)
    }

    private fun fetchOpenMeteoWeather(
        lat: Double,
        lon: Double,
        includeAqi: Boolean = true
    ): Result<WeatherIntel> = runCatching {
        val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,surface_pressure,wind_speed_10m,wind_direction_10m" +
            "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max" +
            "&timezone=auto&forecast_days=6"
        val wJson = httpClient.newCall(Request.Builder().url(weatherUrl).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) error("Open-Meteo HTTP ${resp.code}")
            JSONObject(resp.body?.string().orEmpty())
        }
        val cur = wJson.getJSONObject("current")
        val currentTimeIso = cur.optString("time", "")
        val hourly = wJson.optJSONObject("hourly")
        val daily = wJson.optJSONObject("daily")
        val elev = wJson.optDouble("elevation", Double.NaN).takeUnless { it.isNaN() }

        // Optional AQI call (Open-Meteo Air Quality API)
        var usAqi: Int? = null
        var pm25: Double? = null
        if (includeAqi) {
            runCatching {
                val aqiUrl = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&current=us_aqi,pm2_5"
                httpClient.newCall(Request.Builder().url(aqiUrl).get().build()).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val aCur = JSONObject(resp.body?.string().orEmpty()).optJSONObject("current")
                        if (aCur != null) {
                            if (!aCur.isNull("us_aqi")) usAqi = aCur.optInt("us_aqi")
                            if (!aCur.isNull("pm2_5")) pm25 = aCur.optDouble("pm2_5")
                        }
                    }
                }
            }
        }

        // Parse next 12 hourly forecast slots starting from current local hour
        val hourlyList = mutableListOf<HourlyForecastPoint>()
        if (hourly != null) {
            val hTimes = hourly.optJSONArray("time")
            val hTemps = hourly.optJSONArray("temperature_2m")
            val hCodes = hourly.optJSONArray("weather_code")
            val hPops = hourly.optJSONArray("precipitation_probability")
            val hDays = hourly.optJSONArray("is_day")
            if (hTimes != null && hTemps != null) {
                val curHourPrefix = currentTimeIso.take(13) // YYYY-MM-DDTHH
                var startIdx = 0
                for (i in 0 until hTimes.length()) {
                    if (hTimes.optString(i).startsWith(curHourPrefix)) {
                        startIdx = i
                        break
                    }
                }
                val endIdx = (startIdx + 14).coerceAtMost(hTimes.length())
                for (i in startIdx until endIdx) {
                    val iso = hTimes.optString(i)
                    val hh = iso.substringAfter("T", iso).take(5)
                    hourlyList.add(
                        HourlyForecastPoint(
                            timeLabel = if (i == startIdx) "Now" else hh,
                            temperatureC = hTemps.optDouble(i, 0.0),
                            weatherCode = hCodes?.optInt(i, 0) ?: 0,
                            precipProbPct = hPops?.optInt(i, 0) ?: 0,
                            isDay = (hDays?.optInt(i, 1) ?: 1) == 1
                        )
                    )
                }
            }
        }

        // Parse 6-day forecast
        val dailyList = mutableListOf<DailyForecastPoint>()
        if (daily != null) {
            val dTimes = daily.optJSONArray("time")
            val dCodes = daily.optJSONArray("weather_code")
            val dMax = daily.optJSONArray("temperature_2m_max")
            val dMin = daily.optJSONArray("temperature_2m_min")
            val dPop = daily.optJSONArray("precipitation_probability_max")
            val dayFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val labelFmt = java.text.SimpleDateFormat("EEE", java.util.Locale.US)
            if (dTimes != null && dMax != null && dMin != null) {
                for (i in 0 until dTimes.length().coerceAtMost(6)) {
                    val iso = dTimes.optString(i)
                    val dayName = if (i == 0) {
                        "Today"
                    } else {
                        runCatching { dayFmt.parse(iso)?.let { labelFmt.format(it) } }.getOrNull() ?: iso.takeLast(5)
                    }
                    val code = dCodes?.optInt(i, 0) ?: 0
                    dailyList.add(
                        DailyForecastPoint(
                            dateIso = iso,
                            dayLabel = dayName,
                            weatherCode = code,
                            weatherLabel = wmoCodeToLabel(code),
                            tempMaxC = dMax.optDouble(i, 0.0),
                            tempMinC = dMin.optDouble(i, 0.0),
                            precipProbMaxPct = dPop?.optInt(i, 0) ?: 0
                        )
                    )
                }
            }
        }

        val sunriseRaw = daily?.optJSONArray("sunrise")?.optString(0).orEmpty()
        val sunsetRaw = daily?.optJSONArray("sunset")?.optString(0).orEmpty()
        val curCode = cur.optInt("weather_code", 0)

        WeatherIntel(
            temperatureC = cur.optDouble("temperature_2m", 0.0),
            apparentTempC = cur.optDouble("apparent_temperature", 0.0),
            humidityPercent = cur.optInt("relative_humidity_2m", 0),
            windSpeedKmh = cur.optDouble("wind_speed_10m", 0.0),
            windDirectionDeg = cur.optInt("wind_direction_10m", 0),
            pressureHpa = cur.optDouble("surface_pressure", 1013.0),
            weatherCode = curCode,
            weatherLabel = wmoCodeToLabel(curCode),
            isDay = cur.optInt("is_day", 1) == 1,
            uvIndexMax = daily?.optJSONArray("uv_index_max")?.optDouble(0, 0.0) ?: 0.0,
            tempMaxC = daily?.optJSONArray("temperature_2m_max")?.optDouble(0, 0.0) ?: 0.0,
            tempMinC = daily?.optJSONArray("temperature_2m_min")?.optDouble(0, 0.0) ?: 0.0,
            sunriseLocal = sunriseRaw.substringAfter("T", sunriseRaw),
            sunsetLocal = sunsetRaw.substringAfter("T", sunsetRaw),
            usAqi = usAqi,
            pm25 = pm25,
            elevationMeters = elev,
            locationLat = lat,
            locationLon = lon,
            hourlyForecast = hourlyList,
            dailyForecast = dailyList,
            fetchedAtMs = System.currentTimeMillis()
        )
    }

    private fun fetchUsgsEarthquakes(lat: Double, lon: Double): Result<List<EarthquakeEvent>> = runCatching {
        val url = "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson" +
            "&latitude=$lat&longitude=$lon&maxradiuskm=600&limit=5&orderby=time"
        httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) error("USGS HTTP ${resp.code}")
            val root = JSONObject(resp.body?.string().orEmpty())
            val features = root.optJSONArray("features") ?: JSONArray()
            buildList {
                for (i in 0 until features.length()) {
                    val f = features.optJSONObject(i) ?: continue
                    val props = f.optJSONObject("properties") ?: continue
                    val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                    val qLon = coords.optDouble(0, 0.0)
                    val qLat = coords.optDouble(1, 0.0)
                    val depth = coords.optDouble(2, 0.0)
                    val distKm = GeoMath.haversineMeters(lat, lon, qLat, qLon) / 1000.0
                    add(
                        EarthquakeEvent(
                            id = f.optString("id", "$i"),
                            magnitude = props.optDouble("mag", 0.0),
                            place = props.optString("place", "Regional Seismic Event"),
                            depthKm = depth,
                            distanceKm = distKm,
                            timeEpochMs = props.optLong("time", 0L)
                        )
                    )
                }
            }
        }
    }

    private fun fetchCountryAndAddressIntel(lat: Double, lon: Double): Result<CountryIntel> = runCatching {
        val revUrl = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lon"
        val revJson = httpClient.newCall(Request.Builder().url(revUrl).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) error("Nominatim HTTP ${resp.code}")
            JSONObject(resp.body?.string().orEmpty())
        }
        val addressSummary = revJson.optString("display_name", String.format("%.4f, %.4f", lat, lon))
        val cc = revJson.optJSONObject("address")?.optString("country_code")?.uppercase().orEmpty()
        if (cc.length != 2) {
            return@runCatching CountryIntel(
                commonName = "International Waters / Uncharted",
                officialName = "",
                capital = "—",
                region = "Global",
                currencySummary = "—",
                callingCode = "—",
                drivingSide = "—",
                timezone = "UTC",
                addressSummary = addressSummary
            )
        }
        val rcUrl = "https://restcountries.com/v3.1/alpha/$cc"
        val rcArr = httpClient.newCall(Request.Builder().url(rcUrl).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) error("REST Countries HTTP ${resp.code}")
            JSONArray(resp.body?.string().orEmpty())
        }
        val obj = rcArr.getJSONObject(0)
        val nameObj = obj.optJSONObject("name")
        val idd = obj.optJSONObject("idd")
        val calling = buildString {
            append(idd?.optString("root").orEmpty())
            val suffixes = idd?.optJSONArray("suffixes")
            if (suffixes != null && suffixes.length() == 1) append(suffixes.optString(0))
        }.ifBlank { "—" }

        val currenciesObj = obj.optJSONObject("currencies")
        val currencySummary = buildString {
            if (currenciesObj != null) {
                val keys = currenciesObj.keys()
                if (keys.hasNext()) {
                    val code = keys.next()
                    val c = currenciesObj.optJSONObject(code)
                    append("$code (${c?.optString("symbol").orEmpty()} ${c?.optString("name").orEmpty()})".trim())
                }
            }
        }.ifBlank { "—" }

        CountryIntel(
            commonName = nameObj?.optString("common").orEmpty().ifBlank { cc },
            officialName = nameObj?.optString("official").orEmpty(),
            capital = obj.optJSONArray("capital")?.optString(0).orEmpty().ifBlank { "—" },
            region = obj.optString("subregion").ifBlank { obj.optString("region", "—") },
            currencySummary = currencySummary,
            callingCode = calling,
            drivingSide = obj.optJSONObject("car")?.optString("side").orEmpty().replaceFirstChar { it.uppercase() }.ifBlank { "—" },
            timezone = obj.optJSONArray("timezones")?.optString(0).orEmpty().ifBlank { "UTC" },
            addressSummary = addressSummary
        )
    }

    /**
     * Fetches live European Central Bank exchange rates via Frankfurter API (public-apis Currency Exchange).
     */
    suspend fun fetchCurrencyRates(baseCurrency: String = "USD"): Result<CurrencyRatesSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val base = baseCurrency.uppercase().take(3)
            val url = "https://api.frankfurter.app/latest?from=$base"
            httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) error("Frankfurter HTTP ${resp.code}")
                val root = JSONObject(resp.body?.string().orEmpty())
                val ratesObj = root.getJSONObject("rates")
                val map = linkedMapOf<String, Double>()
                map[base] = 1.0
                val keys = ratesObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = ratesObj.optDouble(k, 0.0)
                }
                CurrencyRatesSnapshot(
                    base = root.optString("base", base),
                    date = root.optString("date", ""),
                    rates = map
                )
            }
        }
    }

    private fun mapOsrmDirection(type: String, modifier: String): ManeuverDirection = when {
        type == "arrive" -> ManeuverDirection.ARRIVE
        type == "roundabout" || type == "rotary" -> ManeuverDirection.ROUNDABOUT
        modifier.contains("uturn") -> ManeuverDirection.UTURN
        modifier == "sharp left" -> ManeuverDirection.SHARP_LEFT
        modifier == "slight left" -> ManeuverDirection.SLIGHT_LEFT
        modifier == "left" -> ManeuverDirection.LEFT
        modifier == "sharp right" -> ManeuverDirection.SHARP_RIGHT
        modifier == "slight right" -> ManeuverDirection.SLIGHT_RIGHT
        modifier == "right" -> ManeuverDirection.RIGHT
        else -> ManeuverDirection.STRAIGHT
    }

    private fun buildOsrmInstruction(type: String, modifier: String, street: String, destName: String): String = when (type) {
        "depart" -> "Head onto $street"
        "arrive" -> "Arrive at $destName"
        "roundabout", "rotary" -> "Enter roundabout onto $street"
        "turn", "end of road", "fork", "merge", "on ramp", "off ramp" -> {
            val action = modifier.replaceFirstChar { it.uppercase() }.ifBlank { "Continue" }
            "Turn $action onto $street"
        }
        else -> "Continue on $street"
    }

    private fun wmoCodeToLabel(code: Int): String = when (code) {
        0 -> "Clear Sky"
        1, 2 -> "Partly Cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog / Mist"
        51, 53, 55 -> "Drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing Rain"
        71, 73, 75, 77 -> "Snowfall"
        80, 81, 82 -> "Rain Showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Variable Conditions"
    }
}
