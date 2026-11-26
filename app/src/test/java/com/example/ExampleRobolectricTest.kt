package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ghostgrid.search.GeoMath
import com.example.ghostgrid.search.GeoPoint
import com.example.ghostgrid.tools.OfflineCalculatorEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app metadata and tagline match GhostGrid spec`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("GhostGrid", context.getString(R.string.app_name))
        assertEquals("Zero cloud, zero tracking, pure navigation.", context.getString(R.string.tagline))
    }

    @Test
    fun `offline calculator evaluates expressions accurately`() {
        assertEquals("42", OfflineCalculatorEngine.evaluate("(10 + 4) × 3"))
        assertEquals("12.5", OfflineCalculatorEngine.evaluate("100 ÷ 8"))
        assertEquals("Error", OfflineCalculatorEngine.evaluate("5 ÷ 0"))
    }

    @Test
    fun `geomath parses coordinates and computes road block polygon`() {
        val pt = GeoMath.parseCoordinateQuery("23.0225, 72.5714")
        assertNotNull(pt)
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
}
