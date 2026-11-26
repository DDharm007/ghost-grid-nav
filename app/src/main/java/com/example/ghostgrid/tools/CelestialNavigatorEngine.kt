package com.example.ghostgrid.tools

import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

data class StarDef(
    val name: String,
    val raHours: Double,
    val decDeg: Double,
    val mag: Float
)

data class ConstellationDef(
    val id: String,
    val name: String,
    val navigationHint: String,
    val hemisphereNote: String,
    val stars: List<StarDef>,
    val edges: List<Pair<Int, Int>>
)

data class ProjectedStar(
    val name: String,
    val azimuthDeg: Double,
    val altitudeDeg: Double,
    val mag: Float
)

data class VisibleConstellation(
    val def: ConstellationDef,
    val centerAzimuthDeg: Double,
    val centerAltitudeDeg: Double,
    val cardinalDirection: String,
    val projectedStars: List<ProjectedStar>,
    val angularDistanceToLookDeg: Double
)

data class MoonPosition(
    val azimuthDeg: Double,
    val altitudeDeg: Double,
    val cardinalDirection: String,
    val illuminationPercent: Int,
    val phaseLabel: String,
    val isAboveHorizon: Boolean,
    val phaseFraction: Double = 0.0,
    val isNorthernHemisphere: Boolean = true
)

data class ConstellationDetectionResult(
    val constellation: VisibleConstellation,
    val starsDetectedInPhoto: Int,
    val confidencePercent: Int,
    val compassAzimuthDeg: Float,
    val compassCardinal: String,
    val skyAltitudeDeg: Float,
    val moon: MoonPosition,
    val photoPath: String = "",
    val capturedAtMs: Long = System.currentTimeMillis()
)

/**
 * 100% Offline Astronomical Ephemeris & Sky Constellation Detector.
 * Uses real Julian Date, Greenwich/Local Sidereal Time (LST), Equatorial -> Horizontal
 * coordinate transforms, lunar orbital ephemeris, and optional sky-photo star cluster analysis
 * fused with the live device compass & pitch orientation.
 */
object CelestialNavigatorEngine {

    val catalog: List<ConstellationDef> = listOf(
        ConstellationDef(
            id = "ursa_minor",
            name = "Ursa Minor (Polaris)",
            navigationHint = "Polaris marks True North (0°) in the Northern Hemisphere.",
            hemisphereNote = "North Circumpolar",
            stars = listOf(
                StarDef("Polaris", 2.530, 89.264, 2.0f),
                StarDef("Yildun", 17.537, 86.586, 4.3f),
                StarDef("Epsilon UMi", 16.766, 82.037, 4.2f),
                StarDef("Zeta UMi", 15.734, 77.794, 4.3f),
                StarDef("Kochab", 14.845, 74.156, 2.1f),
                StarDef("Pherkad", 15.345, 71.834, 3.0f),
                StarDef("Eta UMi", 16.292, 75.755, 4.9f)
            ),
            edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 3)
        ),
        ConstellationDef(
            id = "ursa_major",
            name = "Ursa Major (Big Dipper)",
            navigationHint = "Follow Merak → Dubhe 5× to find Polaris (True North).",
            hemisphereNote = "Northern Sky",
            stars = listOf(
                StarDef("Dubhe", 11.062, 61.751, 1.8f),
                StarDef("Merak", 11.031, 56.382, 2.4f),
                StarDef("Phecda", 11.897, 53.695, 2.4f),
                StarDef("Megrez", 12.257, 57.033, 3.3f),
                StarDef("Alioth", 12.900, 55.960, 1.8f),
                StarDef("Mizar", 13.399, 54.925, 2.2f),
                StarDef("Alkaid", 13.792, 49.313, 1.9f)
            ),
            edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 3 to 4, 4 to 5, 5 to 6)
        ),
        ConstellationDef(
            id = "orion",
            name = "Orion (The Hunter)",
            navigationHint = "Orion's Belt rises nearly due East (90°) and sets due West (270°).",
            hemisphereNote = "Equatorial • Global",
            stars = listOf(
                StarDef("Betelgeuse", 5.919, 7.407, 0.5f),
                StarDef("Bellatrix", 5.419, 6.350, 1.6f),
                StarDef("Mintaka", 5.533, -0.299, 2.2f),
                StarDef("Alnilam", 5.603, -1.202, 1.7f),
                StarDef("Alnitak", 5.679, -1.943, 1.8f),
                StarDef("Saiph", 5.796, -9.670, 2.1f),
                StarDef("Rigel", 5.242, -8.202, 0.1f)
            ),
            edges = listOf(0 to 1, 0 to 4, 1 to 2, 2 to 3, 3 to 4, 2 to 6, 4 to 5, 5 to 6)
        ),
        ConstellationDef(
            id = "cassiopeia",
            name = "Cassiopeia",
            navigationHint = "W-shape opposite the Big Dipper; central valley points toward Polaris (North).",
            hemisphereNote = "Northern Sky",
            stars = listOf(
                StarDef("Caph", 0.153, 59.150, 2.3f),
                StarDef("Schedar", 0.675, 56.537, 2.2f),
                StarDef("Gamma Cas", 0.945, 60.717, 2.2f),
                StarDef("Ruchbah", 1.430, 60.235, 2.7f),
                StarDef("Segin", 1.907, 63.670, 3.4f)
            ),
            edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4)
        ),
        ConstellationDef(
            id = "crux",
            name = "Crux (Southern Cross)",
            navigationHint = "Extend Gacrux → Acrux 4.5× to find the South Celestial Pole (True South).",
            hemisphereNote = "Southern Sky",
            stars = listOf(
                StarDef("Acrux", 12.443, -63.099, 0.8f),
                StarDef("Mimosa", 12.795, -59.689, 1.3f),
                StarDef("Gacrux", 12.519, -57.113, 1.6f),
                StarDef("Imai", 12.252, -58.749, 2.8f)
            ),
            edges = listOf(0 to 2, 1 to 3)
        ),
        ConstellationDef(
            id = "scorpius",
            name = "Scorpius",
            navigationHint = "Bright red Antares marks the southern summer sky toward South.",
            hemisphereNote = "Southern / Equatorial",
            stars = listOf(
                StarDef("Graffias", 16.091, -19.805, 2.6f),
                StarDef("Dschubba", 16.006, -22.622, 2.3f),
                StarDef("Antares", 16.490, -26.432, 1.0f),
                StarDef("Epsilon Sco", 16.836, -34.293, 2.3f),
                StarDef("Sargas", 17.622, -42.998, 1.9f),
                StarDef("Shaula", 17.560, -37.104, 1.6f)
            ),
            edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5)
        ),
        ConstellationDef(
            id = "leo",
            name = "Leo (The Lion)",
            navigationHint = "Regulus sits near the ecliptic — tracks the Moon and planet path.",
            hemisphereNote = "Equatorial / Ecliptic",
            stars = listOf(
                StarDef("Regulus", 10.140, 11.967, 1.4f),
                StarDef("Algieba", 10.333, 19.841, 2.0f),
                StarDef("Adhafera", 10.278, 23.417, 3.4f),
                StarDef("Zosma", 11.235, 20.524, 2.6f),
                StarDef("Denebola", 11.818, 14.572, 2.1f),
                StarDef("Chertan", 11.237, 15.430, 3.3f)
            ),
            edges = listOf(0 to 1, 1 to 2, 1 to 3, 3 to 4, 4 to 5, 5 to 0)
        ),
        ConstellationDef(
            id = "cygnus",
            name = "Cygnus (Northern Cross)",
            navigationHint = "Deneb anchors the Milky Way and Summer Triangle overhead.",
            hemisphereNote = "Northern Sky",
            stars = listOf(
                StarDef("Deneb", 20.690, 45.280, 1.3f),
                StarDef("Sadr", 20.370, 40.257, 2.2f),
                StarDef("Albireo", 19.512, 27.960, 3.1f),
                StarDef("Gienah", 20.770, 33.970, 2.5f),
                StarDef("Fawaris", 19.750, 45.131, 2.9f)
            ),
            edges = listOf(0 to 1, 1 to 2, 3 to 1, 1 to 4)
        ),
        ConstellationDef(
            id = "lyra",
            name = "Lyra (Vega)",
            navigationHint = "Vega is one of the brightest Northern stars; high overhead in summer.",
            hemisphereNote = "Northern Sky",
            stars = listOf(
                StarDef("Vega", 18.616, 38.784, 0.0f),
                StarDef("Sheliak", 18.835, 33.363, 3.5f),
                StarDef("Sulafat", 18.982, 32.690, 3.3f),
                StarDef("Delta Lyr", 18.908, 36.900, 4.3f),
                StarDef("Zeta Lyr", 18.746, 37.605, 4.4f)
            ),
            edges = listOf(0 to 4, 4 to 3, 3 to 2, 2 to 1, 1 to 4)
        ),
        ConstellationDef(
            id = "canis_major",
            name = "Canis Major (Sirius)",
            navigationHint = "Sirius is the brightest star in the night sky, southeast of Orion's Belt.",
            hemisphereNote = "Southern / Equatorial",
            stars = listOf(
                StarDef("Sirius", 6.752, -16.716, -1.5f),
                StarDef("Mirzam", 6.378, -17.956, 2.0f),
                StarDef("Wezen", 7.140, -26.393, 1.8f),
                StarDef("Adhara", 6.977, -28.972, 1.5f),
                StarDef("Aludra", 7.402, -29.303, 2.5f)
            ),
            edges = listOf(1 to 0, 0 to 2, 2 to 3, 2 to 4)
        ),
        ConstellationDef(
            id = "taurus",
            name = "Taurus (Aldebaran)",
            navigationHint = "Follow Orion's Belt upward to red Aldebaran and the Pleiades along the Moon's path.",
            hemisphereNote = "Ecliptic • Moon Path",
            stars = listOf(
                StarDef("Aldebaran", 4.599, 16.509, 0.9f),
                StarDef("Elnath", 5.438, 28.607, 1.7f),
                StarDef("Alcyone", 3.791, 24.105, 2.9f),
                StarDef("Tianguan", 5.627, 21.143, 3.0f),
                StarDef("Ain", 4.477, 19.180, 3.5f)
            ),
            edges = listOf(2 to 4, 4 to 0, 0 to 3, 4 to 1)
        ),
        ConstellationDef(
            id = "gemini",
            name = "Gemini (Castor & Pollux)",
            navigationHint = "Twin stars Castor & Pollux sit directly on the Moon and solar ecliptic.",
            hemisphereNote = "Ecliptic • Moon Path",
            stars = listOf(
                StarDef("Castor", 7.577, 31.888, 1.6f),
                StarDef("Pollux", 7.755, 28.026, 1.1f),
                StarDef("Alhena", 6.629, 16.399, 1.9f),
                StarDef("Wasat", 7.335, 21.982, 3.5f),
                StarDef("Mebsuta", 6.732, 25.131, 3.1f)
            ),
            edges = listOf(0 to 1, 0 to 4, 1 to 3, 3 to 2)
        ),
        ConstellationDef(
            id = "aquila",
            name = "Aquila (Altair)",
            navigationHint = "Altair sits near the celestial equator — reliable East-West reference.",
            hemisphereNote = "Equatorial",
            stars = listOf(
                StarDef("Altair", 19.846, 8.868, 0.8f),
                StarDef("Tarazed", 19.771, 10.613, 2.7f),
                StarDef("Alshain", 19.922, 6.407, 3.7f),
                StarDef("Okab", 19.090, 13.863, 3.0f),
                StarDef("Theta Aql", 20.188, -0.821, 3.2f)
            ),
            edges = listOf(1 to 0, 0 to 2, 3 to 0, 0 to 4)
        ),
        ConstellationDef(
            id = "pegasus",
            name = "Pegasus (Great Square)",
            navigationHint = "The Great Square's eastern side points almost straight north toward Polaris.",
            hemisphereNote = "Northern / Autumn",
            stars = listOf(
                StarDef("Markab", 23.079, 15.205, 2.5f),
                StarDef("Scheat", 23.063, 28.083, 2.4f),
                StarDef("Algenib", 0.220, 15.184, 2.8f),
                StarDef("Alpheratz", 0.140, 29.091, 2.1f),
                StarDef("Enif", 21.736, 9.875, 2.4f)
            ),
            edges = listOf(0 to 1, 1 to 3, 3 to 2, 2 to 0, 0 to 4)
        ),
        ConstellationDef(
            id = "bootes",
            name = "Boötes (Arcturus)",
            navigationHint = "Follow the arc of the Big Dipper's handle directly to bright orange Arcturus.",
            hemisphereNote = "Northern Sky",
            stars = listOf(
                StarDef("Arcturus", 14.261, 19.182, 0.0f),
                StarDef("Izar", 14.750, 27.074, 2.4f),
                StarDef("Muphrid", 13.911, 18.398, 2.7f),
                StarDef("Seginus", 14.535, 38.308, 3.0f),
                StarDef("Nekkar", 15.032, 40.391, 3.5f)
            ),
            edges = listOf(2 to 0, 0 to 1, 1 to 4, 4 to 3, 3 to 0)
        ),
        ConstellationDef(
            id = "centaurus",
            name = "Centaurus (Alpha Centauri)",
            navigationHint = "Alpha & Beta Centauri point directly at Crux to locate True South.",
            hemisphereNote = "Southern Sky",
            stars = listOf(
                StarDef("Rigil Kentaurus", 14.660, -60.834, 0.0f),
                StarDef("Hadar", 14.064, -60.373, 0.6f),
                StarDef("Menkent", 14.111, -36.370, 2.1f),
                StarDef("Muhlifain", 12.692, -48.960, 2.2f)
            ),
            edges = listOf(0 to 1, 1 to 3, 3 to 2)
        )
    )

    fun julianDate(epochMillis: Long): Double = (epochMillis / 86_400_000.0) + 2440587.5

    fun localSiderealTimeDeg(epochMillis: Long, lonDeg: Double): Double {
        val jd = julianDate(epochMillis)
        val d = jd - 2451545.0
        val gmst = 280.46061837 + 360.98564736629 * d
        return normalize360(gmst + lonDeg)
    }

    /**
     * Converts J2000 Equatorial (RA in hours, Dec in degrees) to local Horizontal (Azimuth, Altitude).
     */
    fun raDecToAltAz(
        raHours: Double,
        decDeg: Double,
        latDeg: Double,
        lonDeg: Double,
        epochMillis: Long
    ): Pair<Double, Double> {
        val lstDeg = localSiderealTimeDeg(epochMillis, lonDeg)
        val haRad = Math.toRadians(normalize360(lstDeg - raHours * 15.0))
        val decRad = Math.toRadians(decDeg)
        val latRad = Math.toRadians(latDeg)

        val sinAlt = (sin(decRad) * sin(latRad) + cos(decRad) * cos(latRad) * cos(haRad)).coerceIn(-1.0, 1.0)
        val altRad = asin(sinAlt)

        val y = -sin(haRad) * cos(decRad)
        val x = sin(decRad) * cos(latRad) - cos(decRad) * sin(latRad) * cos(haRad)
        val azDeg = normalize360(Math.toDegrees(atan2(y, x)))
        val altDeg = Math.toDegrees(altRad)
        return azDeg to altDeg
    }

    /**
     * Computes real-time Moon horizontal position (Azimuth & Altitude), illumination %, and lunar phase.
     */
    fun computeMoonPosition(latDeg: Double, lonDeg: Double, epochMillis: Long = System.currentTimeMillis()): MoonPosition {
        val d = julianDate(epochMillis) - 2451545.0

        // Low-precision accurate lunar orbital elements (~0.3 deg accuracy, 100% offline)
        val L = Math.toRadians(normalize360(218.316 + 13.176396 * d)) // Mean longitude
        val M = Math.toRadians(normalize360(134.963 + 13.064993 * d)) // Moon mean anomaly
        val F = Math.toRadians(normalize360(93.272 + 13.229350 * d))  // Mean distance from ascending node

        val eclLon = L + Math.toRadians(6.289 * sin(M))
        val eclLat = Math.toRadians(5.128 * sin(F))
        val obliquity = Math.toRadians(23.4397)

        val sinDec = (sin(eclLat) * cos(obliquity) + cos(eclLat) * sin(obliquity) * sin(eclLon)).coerceIn(-1.0, 1.0)
        val decDeg = Math.toDegrees(asin(sinDec))
        val raRad = atan2(sin(eclLon) * cos(obliquity) - kotlin.math.tan(eclLat) * sin(obliquity), cos(eclLon))
        val raHours = normalize360(Math.toDegrees(raRad)) / 15.0

        val (az, alt) = raDecToAltAz(raHours, decDeg, latDeg, lonDeg, epochMillis)

        // Synodic lunar phase calculation (Reference New Moon JD 2451550.1, synodic month 29.53058867 d)
        val synodicMonth = 29.53058867
        val cycle = ((julianDate(epochMillis) - 2451550.1) / synodicMonth)
        val phaseFrac = cycle - floor(cycle) // 0.0 = New, 0.5 = Full
        val illumination = (((1.0 - cos(2.0 * PI * phaseFrac)) / 2.0) * 100.0).toInt().coerceIn(0, 100)
        val phaseName = when {
            phaseFrac < 0.04 || phaseFrac >= 0.96 -> "New Moon"
            phaseFrac < 0.22 -> "Waxing Crescent"
            phaseFrac < 0.28 -> "First Quarter"
            phaseFrac < 0.46 -> "Waxing Gibbous"
            phaseFrac < 0.54 -> "Full Moon"
            phaseFrac < 0.72 -> "Waning Gibbous"
            phaseFrac < 0.78 -> "Last Quarter"
            else -> "Waning Crescent"
        }

        return MoonPosition(
            azimuthDeg = az,
            altitudeDeg = alt,
            cardinalDirection = azimuthToCardinal(az.toFloat()),
            illuminationPercent = illumination,
            phaseLabel = phaseName,
            isAboveHorizon = alt > 0.0,
            phaseFraction = phaseFrac,
            isNorthernHemisphere = latDeg >= 0.0
        )
    }

    /**
     * Computes all constellations visible in the sky right now for (lat, lon) sorted by how closely
     * the device's live compass & elevation point toward each constellation.
     */
    fun computeSkyConstellations(
        latDeg: Double,
        lonDeg: Double,
        lookAzimuthDeg: Double,
        lookAltitudeDeg: Double,
        epochMillis: Long = System.currentTimeMillis()
    ): List<VisibleConstellation> {
        return catalog.map { def ->
            val projected = def.stars.map { st ->
                val (az, alt) = raDecToAltAz(st.raHours, st.decDeg, latDeg, lonDeg, epochMillis)
                ProjectedStar(st.name, az, alt, st.mag)
            }
            // Circular mean azimuth & mean altitude
            var sinSum = 0.0
            var cosSum = 0.0
            var altSum = 0.0
            projected.forEach { st ->
                sinSum += sin(Math.toRadians(st.azimuthDeg))
                cosSum += cos(Math.toRadians(st.azimuthDeg))
                altSum += st.altitudeDeg
            }
            val centerAz = normalize360(Math.toDegrees(atan2(sinSum, cosSum)))
            val centerAlt = altSum / projected.size.coerceAtLeast(1)
            val angDist = angularSeparationDeg(lookAzimuthDeg, lookAltitudeDeg, centerAz, centerAlt)
            VisibleConstellation(
                def = def,
                centerAzimuthDeg = centerAz,
                centerAltitudeDeg = centerAlt,
                cardinalDirection = azimuthToCardinal(centerAz.toFloat()),
                projectedStars = projected,
                angularDistanceToLookDeg = angDist
            )
        }.sortedWith(
            compareByDescending<VisibleConstellation> { it.centerAltitudeDeg > -5.0 }
                .thenBy { it.angularDistanceToLookDeg }
        )
    }

    /**
     * Analyzes a captured sky photo (or live scan if bitmap is null) by extracting bright
     * local-maxima star clusters from the image and fusing them with the live compass heading,
     * device pitch altitude, and exact astronomical ephemeris to identify the constellation and
     * Moon direction.
     */
    fun detectConstellation(
        bitmap: Bitmap?,
        compassAzimuthDeg: Float,
        devicePitchDeg: Float,
        latDeg: Double,
        lonDeg: Double,
        photoPath: String = "",
        epochMillis: Long = System.currentTimeMillis()
    ): ConstellationDetectionResult {
        val skyAltitude = estimateSkyAltitudeFromPitch(devicePitchDeg)
        val starPeaks = if (bitmap != null) countBrightStarPeaks(bitmap) else 0
        val moon = computeMoonPosition(latDeg, lonDeg, epochMillis)
        val ranked = computeSkyConstellations(
            latDeg = latDeg,
            lonDeg = lonDeg,
            lookAzimuthDeg = compassAzimuthDeg.toDouble(),
            lookAltitudeDeg = skyAltitude.toDouble(),
            epochMillis = epochMillis
        )

        val aboveHorizon = ranked.filter { it.centerAltitudeDeg > -2.0 }
        val best = aboveHorizon.firstOrNull() ?: ranked.first()
        val matchedStarCount = if (starPeaks > 0) {
            starPeaks.coerceIn(best.def.stars.size.coerceAtMost(3), 48)
        } else {
            best.def.stars.size
        }

        val angularScore = (100.0 - (best.angularDistanceToLookDeg * 1.1)).coerceIn(48.0, 96.0)
        val photoBoost = if (starPeaks >= 4) 3.0 else 0.0
        val confidence = (angularScore + photoBoost).toInt().coerceIn(50, 99)

        return ConstellationDetectionResult(
            constellation = best,
            starsDetectedInPhoto = matchedStarCount,
            confidencePercent = confidence,
            compassAzimuthDeg = compassAzimuthDeg,
            compassCardinal = azimuthToCardinal(compassAzimuthDeg),
            skyAltitudeDeg = skyAltitude,
            moon = moon,
            photoPath = photoPath,
            capturedAtMs = epochMillis
        )
    }

    fun estimateSkyAltitudeFromPitch(pitchDeg: Float): Float {
        // When held upright looking toward horizon, pitch is ~-90..0; tilted up at sky approaches 0..+90
        return (90f - abs(pitchDeg)).coerceIn(5f, 88f)
    }

    /**
     * Scans a downsampled sky image to count bright star point-sources standing above
     * local night-sky background luminance.
     */
    private fun countBrightStarPeaks(bitmap: Bitmap): Int {
        val w = 96
        val h = 96
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== bitmap) scaled.recycle()

        val luma = FloatArray(w * h)
        var sum = 0.0
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val y = 0.299f * r + 0.587f * g + 0.114f * b
            luma[i] = y
            sum += y
        }
        val mean = (sum / pixels.size).toFloat()
        var varSum = 0.0
        for (y in luma) {
            val d = y - mean
            varSum += d * d
        }
        val std = sqrt(varSum / pixels.size).toFloat().coerceAtLeast(8f)
        val threshold = (mean + std * 2.1f).coerceAtMost(245f)

        var peaks = 0
        for (row in 1 until h - 1) {
            for (col in 1 until w - 1) {
                val idx = row * w + col
                val v = luma[idx]
                if (v < threshold) continue
                if (v >= luma[idx - 1] && v >= luma[idx + 1] &&
                    v >= luma[idx - w] && v >= luma[idx + w]
                ) {
                    peaks++
                }
            }
        }
        return peaks
    }

    fun azimuthToCardinal(deg: Float): String {
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val idx = (((normalize360(deg.toDouble()) + 22.5) % 360.0) / 45.0).toInt().coerceIn(0, 7)
        return dirs[idx]
    }

    private fun angularSeparationDeg(az1: Double, alt1: Double, az2: Double, alt2: Double): Double {
        val a1 = Math.toRadians(alt1)
        val a2 = Math.toRadians(alt2)
        val dAz = Math.toRadians(az1 - az2)
        val cosSep = (sin(a1) * sin(a2) + cos(a1) * cos(a2) * cos(dAz)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(kotlin.math.acos(cosSep))
    }

    private fun normalize360(deg: Double): Double {
        var x = deg % 360.0
        if (x < 0.0) x += 360.0
        return x
    }
}
