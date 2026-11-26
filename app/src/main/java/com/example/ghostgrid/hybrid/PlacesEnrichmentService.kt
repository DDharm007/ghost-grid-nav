package com.example.ghostgrid.hybrid

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.ghostgrid.data.CachedPoiDao
import com.example.ghostgrid.data.CachedPoiEnrichmentEntity
import com.example.ghostgrid.data.GhostGridPreferences
import com.example.ghostgrid.search.PlaceResult
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class PoiEnrichment(
    val placeKey: String,
    val rating: Double?,
    val userRatingCount: Int?,
    val isOpenNow: Boolean?,
    val weekdayHours: List<String>,
    val topReviews: List<String>,
    val fromCache: Boolean,
    val fetchedAt: Long
)

@JsonClass(generateAdapter = true)
internal data class PlacesSearchResponse(
    val places: List<PlacesApiItem>? = null
)

@JsonClass(generateAdapter = true)
internal data class PlacesApiItem(
    val displayName: LocalizedText? = null,
    val rating: Double? = null,
    val userRatingCount: Int? = null,
    val currentOpeningHours: OpeningHoursDto? = null,
    val reviews: List<ReviewDto>? = null
)

@JsonClass(generateAdapter = true)
internal data class LocalizedText(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
internal data class OpeningHoursDto(
    val openNow: Boolean? = null,
    val weekdayDescriptions: List<String>? = null
)

@JsonClass(generateAdapter = true)
internal data class ReviewDto(
    val rating: Double? = null,
    val text: LocalizedText? = null,
    val authorAttribution: AuthorAttributionDto? = null
)

@JsonClass(generateAdapter = true)
internal data class AuthorAttributionDto(
    val displayName: String? = null
)

/**
 * Hybrid Online/Offline POI Enricher.
 *
 * - Always checks the SQLCipher-encrypted local cache first so previously enriched POIs
 *   render instantly even in Strict Offline Mode.
 * - When Strict Offline Mode is toggled OFF, connectivity is active, and the user has
 *   configured their Places API key in Settings, executes a single on-demand HTTPS POST
 *   to the official Google Places API (New) endpoint (`places:searchText`) with strict
 *   field masking and caches the result locally in SQLCipher Room.
 */
class PlacesEnrichmentService(
    private val context: Context,
    private val cachedPoiDao: CachedPoiDao,
    private val preferences: GhostGridPreferences,
    private val isStrictOfflineEnabled: () -> Boolean
) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val responseAdapter = moshi.adapter(PlacesSearchResponse::class.java)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(StrictOfflineInterceptor(isStrictOfflineEnabled))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val PLACES_SEARCH_URL = "https://places.googleapis.com/v1/places:searchText"
        private const val FIELD_MASK =
            "places.displayName,places.rating,places.userRatingCount,places.currentOpeningHours,places.reviews"
        private const val LIST_SEPARATOR = "\n|||\n"
    }

    suspend fun getCachedEnrichment(place: PlaceResult): PoiEnrichment? = withContext(Dispatchers.IO) {
        val entity = cachedPoiDao.getByKey(place.stableEnrichmentKey) ?: return@withContext null
        entity.toModel(fromCache = true)
    }

    suspend fun fetchLiveEnrichment(place: PlaceResult): Result<PoiEnrichment> = withContext(Dispatchers.IO) {
        if (isStrictOfflineEnabled()) {
            return@withContext Result.failure(StrictOfflineBlockedException())
        }
        if (!hasInternetConnection()) {
            return@withContext Result.failure(IllegalStateException("No active internet connection detected."))
        }
        val apiKey = preferences.getDecryptedPlacesApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Configure your Google Places API key in GhostGrid Settings first.")
            )
        }

        runCatching {
            val payload = buildRequestPayload(place)
            val request = Request.Builder()
                .url(PLACES_SEARCH_URL)
                .header("Content-Type", "application/json")
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", FIELD_MASK)
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Places lookup returned HTTP ${resp.code}.")
                }
                val body = resp.body?.string().orEmpty()
                val parsed = responseAdapter.fromJson(body)
                val first = parsed?.places?.firstOrNull()
                    ?: throw IllegalStateException("No matching live listing found for ${place.name}.")

                val hours = first.currentOpeningHours?.weekdayDescriptions.orEmpty()
                val reviews = first.reviews.orEmpty().take(3).mapNotNull { review ->
                    val text = review.text?.text?.trim().orEmpty()
                    if (text.isEmpty()) null else {
                        val author = review.authorAttribution?.displayName ?: "Reviewer"
                        val stars = review.rating?.let { String.format("%.1f★", it) } ?: ""
                        "$author ($stars): $text"
                    }
                }

                val entity = CachedPoiEnrichmentEntity(
                    placeKey = place.stableEnrichmentKey,
                    placeName = first.displayName?.text ?: place.name,
                    rating = first.rating,
                    userRatingCount = first.userRatingCount,
                    isOpenNow = first.currentOpeningHours?.openNow,
                    weekdayHours = hours.joinToString(LIST_SEPARATOR),
                    topReviews = reviews.joinToString(LIST_SEPARATOR),
                    fetchedAt = System.currentTimeMillis()
                )
                cachedPoiDao.upsert(entity)
                entity.toModel(fromCache = false)
            }
        }
    }

    private fun buildRequestPayload(place: PlaceResult): String {
        val safeName = place.name.replace("\\", "\\\\").replace("\"", "\\\"")
        return """
            {
              "textQuery": "$safeName",
              "maxResultCount": 1,
              "locationBias": {
                "circle": {
                  "center": {
                    "latitude": ${place.lat},
                    "longitude": ${place.lon}
                  },
                  "radius": 500.0
                }
              }
            }
        """.trimIndent()
    }

    private fun hasInternetConnection(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun CachedPoiEnrichmentEntity.toModel(fromCache: Boolean): PoiEnrichment {
        return PoiEnrichment(
            placeKey = placeKey,
            rating = rating,
            userRatingCount = userRatingCount,
            isOpenNow = isOpenNow,
            weekdayHours = if (weekdayHours.isBlank()) emptyList() else weekdayHours.split(LIST_SEPARATOR),
            topReviews = if (topReviews.isBlank()) emptyList() else topReviews.split(LIST_SEPARATOR),
            fromCache = fromCache,
            fetchedAt = fetchedAt
        )
    }
}
