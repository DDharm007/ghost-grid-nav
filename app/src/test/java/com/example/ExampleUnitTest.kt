package com.example

import com.example.ghostgrid.data.RecordedTrackEntity
import com.example.ghostgrid.data.TrackBreadcrumbEntity
import com.example.ghostgrid.hybrid.StrictOfflineBlockedException
import com.example.ghostgrid.hybrid.StrictOfflineInterceptor
import com.example.ghostgrid.search.GeoMath
import com.example.ghostgrid.search.GeoPoint
import com.example.ghostgrid.tools.OfflineCalculatorEngine
import com.example.ghostgrid.tracking.TrackExporter
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun offlineCalculator_evaluatesArithmeticAndHandlesErrors() {
        assertEquals("42", OfflineCalculatorEngine.evaluate("(10 + 4) × 3"))
        assertEquals("12.5", OfflineCalculatorEngine.evaluate("100 ÷ 8"))
        assertEquals("0.25", OfflineCalculatorEngine.evaluate("25%"))
        assertEquals("Error", OfflineCalculatorEngine.evaluate("5 ÷ 0"))
    }

    @Test
    fun geoMath_parsesCoordinatesAndBuildsRoadBlockPolygon() {
        val pt = GeoMath.parseCoordinateQuery("23.0225, 72.5714")
        assertNotNull(pt)
        assertEquals(23.0225, pt!!.lat, 0.0001)
        assertEquals(72.5714, pt.lon, 0.0001)

        val poly = GeoMath.buildForwardBlockPolygon(
            origin = GeoPoint(23.0225, 72.5714),
            polyline = listOf(GeoPoint(23.0225, 72.5714), GeoPoint(23.0300, 72.5714)),
            lengthMeters = 200.0
        )
        assertEquals(5, poly.size)
        assertEquals(poly.first(), poly.last())

        val dist = GeoMath.haversineMeters(23.0225, 72.5714, 23.0325, 72.5714)
        assertTrue(dist in 1050.0..1150.0)
    }

    @Test
    fun strictOfflineInterceptor_blocksOutboundRequests() {
        val client = OkHttpClient.Builder()
            .addInterceptor(StrictOfflineInterceptor { true })
            .build()
        try {
            client.newCall(Request.Builder().url("https://example.com").build()).execute()
            fail("Expected StrictOfflineBlockedException")
        } catch (_: StrictOfflineBlockedException) {
            // Expected zero-leak firewall block
        }
    }

    @Test
    fun trackExporter_generatesValidGpxAndGeoJson() {
        val track = RecordedTrackEntity(id = 1, name = "Curfew Patrol", distanceMeters = 520.0, pointCount = 2)
        val pts = listOf(
            TrackBreadcrumbEntity(id = 1, trackId = 1, lat = 23.0225, lon = 72.5714, altitudeMeters = 53.0),
            TrackBreadcrumbEntity(id = 2, trackId = 1, lat = 23.0250, lon = 72.5720, altitudeMeters = 54.0)
        )
        val gpx = TrackExporter.buildGpxXml(track, pts)
        assertTrue(gpx.contains("<gpx version=\"1.1\""))
        assertTrue(gpx.contains("Curfew Patrol"))
        assertTrue(gpx.contains("lat=\"23.0225\""))

        val geoJson = TrackExporter.buildGeoJson(track, pts)
        assertTrue(geoJson.contains("\"type\": \"LineString\""))
        assertTrue(geoJson.contains("72.5714"))
    }
}
