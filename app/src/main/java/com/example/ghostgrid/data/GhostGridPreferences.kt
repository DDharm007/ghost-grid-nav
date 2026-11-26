package com.example.ghostgrid.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ghostgrid.security.KeystoreKeyManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.ghostGridDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ghostgrid_settings"
)

data class GhostGridSettings(
    val strictOfflineMode: Boolean = false,
    val mapStyleId: String = "stealth_dark",
    val carColorId: String = "pearl",
    val alwaysShow3dCar: Boolean = false,
    val routingProfileId: String = "car_fastest",
    val voiceGuidanceEnabled: Boolean = true,
    val manifestFeedUrl: String = "",
    val hasPlacesApiKey: Boolean = false,
    val hasBingApiKey: Boolean = false,
    val profileName: String = "",
    val profilePhotoPath: String = "",
    val userServerUrl: String = "",
    val lastProfileSyncedAt: Long = 0L,
    val medicalBloodType: String = "",
    val medicalAllergies: String = "",
    val selectedTimeZoneId: String = ""
)

class GhostGridPreferences(private val context: Context) {

    private val keyManager = KeystoreKeyManager(context.applicationContext)

    companion object {
        private val KEY_STRICT_OFFLINE = booleanPreferencesKey("strict_offline_mode")
        private val KEY_MAP_STYLE = stringPreferencesKey("map_style_id")
        private val KEY_CAR_COLOR = stringPreferencesKey("car_color_id")
        private val KEY_ALWAYS_3D_CAR = booleanPreferencesKey("always_show_3d_car")
        private val KEY_ROUTING_PROFILE = stringPreferencesKey("routing_profile_id")
        private val KEY_VOICE_GUIDANCE = booleanPreferencesKey("voice_guidance_enabled")
        private val KEY_MANIFEST_FEED_URL = stringPreferencesKey("manifest_feed_url")
        private val KEY_ENCRYPTED_PLACES_KEY = stringPreferencesKey("encrypted_places_api_key")
        private val KEY_ENCRYPTED_BING_KEY = stringPreferencesKey("encrypted_bing_api_key")
        private val KEY_PROFILE_NAME = stringPreferencesKey("profile_name")
        private val KEY_PROFILE_PHOTO_PATH = stringPreferencesKey("profile_photo_path")
        private val KEY_USER_SERVER_URL = stringPreferencesKey("user_server_url")
        private val KEY_LAST_PROFILE_SYNC = longPreferencesKey("last_profile_sync")
        private val KEY_BLOOD_TYPE = stringPreferencesKey("medical_blood_type")
        private val KEY_ALLERGIES = stringPreferencesKey("medical_allergies")
        private val KEY_SELECTED_TIMEZONE = stringPreferencesKey("selected_timezone_id")
    }

    val settingsFlow: Flow<GhostGridSettings> = context.ghostGridDataStore.data.map { prefs ->
        GhostGridSettings(
            strictOfflineMode = prefs[KEY_STRICT_OFFLINE] ?: false,
            mapStyleId = prefs[KEY_MAP_STYLE] ?: "stealth_dark",
            carColorId = prefs[KEY_CAR_COLOR] ?: "pearl",
            alwaysShow3dCar = prefs[KEY_ALWAYS_3D_CAR] ?: false,
            routingProfileId = prefs[KEY_ROUTING_PROFILE] ?: "car_fastest",
            voiceGuidanceEnabled = prefs[KEY_VOICE_GUIDANCE] ?: true,
            manifestFeedUrl = prefs[KEY_MANIFEST_FEED_URL] ?: "",
            hasPlacesApiKey = !prefs[KEY_ENCRYPTED_PLACES_KEY].isNullOrBlank(),
            hasBingApiKey = !prefs[KEY_ENCRYPTED_BING_KEY].isNullOrBlank(),
            profileName = prefs[KEY_PROFILE_NAME] ?: "",
            profilePhotoPath = prefs[KEY_PROFILE_PHOTO_PATH] ?: "",
            userServerUrl = prefs[KEY_USER_SERVER_URL] ?: "",
            lastProfileSyncedAt = prefs[KEY_LAST_PROFILE_SYNC] ?: 0L,
            medicalBloodType = prefs[KEY_BLOOD_TYPE] ?: "",
            medicalAllergies = prefs[KEY_ALLERGIES] ?: "",
            selectedTimeZoneId = prefs[KEY_SELECTED_TIMEZONE] ?: ""
        )
    }

    suspend fun setStrictOfflineMode(enabled: Boolean) {
        context.ghostGridDataStore.edit { it[KEY_STRICT_OFFLINE] = enabled }
    }

    suspend fun setMapStyleId(styleId: String) {
        context.ghostGridDataStore.edit { it[KEY_MAP_STYLE] = styleId }
    }

    suspend fun setCarColorId(colorId: String) {
        context.ghostGridDataStore.edit { it[KEY_CAR_COLOR] = colorId }
    }

    suspend fun setAlwaysShow3dCar(enabled: Boolean) {
        context.ghostGridDataStore.edit { it[KEY_ALWAYS_3D_CAR] = enabled }
    }

    suspend fun setRoutingProfileId(profileId: String) {
        context.ghostGridDataStore.edit { it[KEY_ROUTING_PROFILE] = profileId }
    }

    suspend fun setVoiceGuidanceEnabled(enabled: Boolean) {
        context.ghostGridDataStore.edit { it[KEY_VOICE_GUIDANCE] = enabled }
    }

    suspend fun setManifestFeedUrl(url: String) {
        context.ghostGridDataStore.edit { it[KEY_MANIFEST_FEED_URL] = url.trim() }
    }

    suspend fun setPlacesApiKey(rawApiKey: String) {
        val trimmed = rawApiKey.trim()
        val encrypted = if (trimmed.isEmpty()) "" else keyManager.encryptSecretString(trimmed)
        context.ghostGridDataStore.edit { it[KEY_ENCRYPTED_PLACES_KEY] = encrypted }
    }

    suspend fun getDecryptedPlacesApiKey(): String {
        val encrypted = context.ghostGridDataStore.data.first()[KEY_ENCRYPTED_PLACES_KEY].orEmpty()
        return keyManager.decryptSecretString(encrypted)
    }

    suspend fun setBingApiKey(rawApiKey: String) {
        val trimmed = rawApiKey.trim()
        val encrypted = if (trimmed.isEmpty()) "" else keyManager.encryptSecretString(trimmed)
        context.ghostGridDataStore.edit { it[KEY_ENCRYPTED_BING_KEY] = encrypted }
    }

    suspend fun getDecryptedBingApiKey(): String {
        val encrypted = context.ghostGridDataStore.data.first()[KEY_ENCRYPTED_BING_KEY].orEmpty()
        return keyManager.decryptSecretString(encrypted)
    }

    suspend fun setProfileName(name: String) {
        context.ghostGridDataStore.edit { it[KEY_PROFILE_NAME] = name.trim().take(60) }
    }

    suspend fun setProfilePhotoPath(path: String) {
        context.ghostGridDataStore.edit { it[KEY_PROFILE_PHOTO_PATH] = path }
    }

    suspend fun setUserServerUrl(url: String) {
        context.ghostGridDataStore.edit { it[KEY_USER_SERVER_URL] = url.trim() }
    }

    suspend fun markProfileSyncedNow() {
        context.ghostGridDataStore.edit { it[KEY_LAST_PROFILE_SYNC] = System.currentTimeMillis() }
    }

    suspend fun setMedicalInfo(bloodType: String, allergies: String) {
        context.ghostGridDataStore.edit {
            it[KEY_BLOOD_TYPE] = bloodType.trim().take(16)
            it[KEY_ALLERGIES] = allergies.trim().take(240)
        }
    }

    suspend fun setSelectedTimeZoneId(zoneId: String) {
        context.ghostGridDataStore.edit { it[KEY_SELECTED_TIMEZONE] = zoneId.trim() }
    }
}
