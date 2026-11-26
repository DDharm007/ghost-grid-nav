package com.example.ghostgrid.tracking

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.ghostgrid.data.RecordedTrackEntity
import com.example.ghostgrid.data.TrackBreadcrumbEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Exports encrypted SQLCipher GPS tracks into standards-compliant `.gpx` (GPX 1.1)
 * and `.geojson` (`FeatureCollection`) files inside `cacheDir/exports/` for
 * zero-permission sharing via `FileProvider`.
 */
object TrackExporter {

    private fun isoUtc(timestampMs: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(timestampMs))
    }

    private fun escapeXml(input: String): String = input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun escapeJson(input: String): String = input
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")

    fun buildGpxXml(track: RecordedTrackEntity, points: List<TrackBreadcrumbEntity>): String {
        val sb = StringBuilder(points.size * 128 + 512)
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="GhostGrid Offline Navigation" xmlns="http://www.topografix.com/GPX/1/1">""")
        sb.appendLine("  <metadata>")
        sb.appendLine("    <name>${escapeXml(track.name)}</name>")
        sb.appendLine("    <time>${isoUtc(track.startedAt)}</time>")
        sb.appendLine("  </metadata>")
        sb.appendLine("  <trk>")
        sb.appendLine("    <name>${escapeXml(track.name)}</name>")
        sb.appendLine("    <trkseg>")
        for (pt in points) {
            sb.appendLine("""      <trkpt lat="${pt.lat}" lon="${pt.lon}">""")
            sb.appendLine("        <ele>${String.format(Locale.US, "%.1f", pt.altitudeMeters)}</ele>")
            sb.appendLine("        <time>${isoUtc(pt.timestamp)}</time>")
            sb.appendLine("      </trkpt>")
        }
        sb.appendLine("    </trkseg>")
        sb.appendLine("  </trk>")
        sb.appendLine("</gpx>")
        return sb.toString()
    }

    fun buildGeoJson(track: RecordedTrackEntity, points: List<TrackBreadcrumbEntity>): String {
        val coords = points.joinToString(",\n          ") { pt ->
            "[${pt.lon}, ${pt.lat}, ${String.format(Locale.US, "%.1f", pt.altitudeMeters)}]"
        }
        return """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": {
                    "name": "${escapeJson(track.name)}",
                    "startedAt": "${isoUtc(track.startedAt)}",
                    "distanceMeters": ${String.format(Locale.US, "%.1f", track.distanceMeters)},
                    "pointCount": ${points.size},
                    "generator": "GhostGrid"
                  },
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [
                      $coords
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
    }

    suspend fun exportAndCreateShareIntent(
        context: Context,
        track: RecordedTrackEntity,
        points: List<TrackBreadcrumbEntity>,
        format: ExportFormat
    ): Intent = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = track.name.lowercase().replace(Regex("[^a-z0-9_-]"), "_").take(32)
            .ifBlank { "ghostgrid_track_${track.id}" }
        val ext = if (format == ExportFormat.GPX) "gpx" else "geojson"
        val mime = if (format == ExportFormat.GPX) "application/gpx+xml" else "application/geo+json"

        val outFile = File(exportDir, "${safeName}_${track.id}.$ext")
        val content = if (format == ExportFormat.GPX) {
            buildGpxXml(track, points)
        } else {
            buildGeoJson(track, points)
        }
        outFile.writeText(content, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )

        Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "GhostGrid Track: ${track.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

enum class ExportFormat {
    GPX,
    GEOJSON
}
