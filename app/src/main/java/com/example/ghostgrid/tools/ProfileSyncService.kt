package com.example.ghostgrid.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.ghostgrid.hybrid.StrictOfflineBlockedException
import com.example.ghostgrid.hybrid.StrictOfflineInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Manages the no-login local user identity (display name + profile photo).
 *
 * - Copies the picked photo from Android Photo Picker (`PickVisualMedia`) into
 *   app-private `context.noBackupFilesDir/profile/avatar.jpg` (zero storage permissions).
 * - Optionally syncs the name & profile photo to the user's own self-hosted server
 *   via HTTPS PUT when Strict Offline Mode is toggled OFF and a server endpoint URL is set.
 */
class ProfileSyncService(
    private val context: Context,
    private val isStrictOfflineEnabled: () -> Boolean
) {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(StrictOfflineInterceptor(isStrictOfflineEnabled))
        .connectTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val profileDir: File
        get() = File(context.noBackupFilesDir, "profile").apply { mkdirs() }

    private val avatarFile: File
        get() = File(profileDir, "avatar.jpg")

    suspend fun savePickedProfilePhoto(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Could not open selected image.")
            val decoded = stream.use { BitmapFactory.decodeStream(it) }
                ?: throw IllegalArgumentException("Unsupported image format.")

            val scaled = scaleDownToSquareMax(decoded, maxEdgePx = 512)
            val tmpFile = File(profileDir, "avatar.jpg.tmp")
            FileOutputStream(tmpFile).use { fos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 88, fos)
            }
            if (!tmpFile.renameTo(avatarFile)) {
                tmpFile.copyTo(avatarFile, overwrite = true)
                tmpFile.delete()
            }
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
            avatarFile.absolutePath
        }
    }

    suspend fun removeProfilePhoto() = withContext(Dispatchers.IO) {
        runCatching { avatarFile.delete() }
    }

    suspend fun syncProfileToUserServer(
        serverUrl: String,
        displayName: String,
        photoPath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (isStrictOfflineEnabled()) {
            return@withContext Result.failure(StrictOfflineBlockedException())
        }
        runCatching {
            val cleanUrl = serverUrl.trim()
            val parsed = URI(cleanUrl)
            require(parsed.scheme.equals("https", ignoreCase = true) && !parsed.host.isNullOrBlank()) {
                "Enter a valid HTTPS URL for your personal user server."
            }

            val photoBase64 = if (photoPath.isNotBlank() && File(photoPath).exists()) {
                val bytes = File(photoPath).readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else {
                ""
            }

            val safeName = displayName
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")

            val jsonBody = """
                {
                  "displayName": "$safeName",
                  "avatarMime": "image/jpeg",
                  "avatarBase64": "$photoBase64",
                  "updatedAt": ${System.currentTimeMillis()}
                }
            """.trimIndent()

            val request = Request.Builder()
                .url(cleanUrl)
                .header("Content-Type", "application/json")
                .put(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("User server rejected profile sync (HTTP ${resp.code}).")
                }
                "Profile synced to ${parsed.host}"
            }
        }
    }

    private fun scaleDownToSquareMax(src: Bitmap, maxEdgePx: Int): Bitmap {
        val maxDim = maxOf(src.width, src.height)
        if (maxDim <= maxEdgePx) return src
        val ratio = maxEdgePx.toFloat() / maxDim.toFloat()
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }
}
