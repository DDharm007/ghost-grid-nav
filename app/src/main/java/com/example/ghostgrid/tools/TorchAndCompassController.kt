package com.example.ghostgrid.tools

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.example.ghostgrid.search.GeoMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class TorchMode(val label: String) {
    OFF("Off"),
    STEADY("Torch On"),
    SOS("SOS Morse"),
    CUSTOM_MORSE("Morse TX")
}

enum class CompassAccuracy(val label: String) {
    HIGH("High Accuracy"),
    MEDIUM("Medium"),
    LOW("Calibrate (Wave 8)"),
    UNRELIABLE("Interference"),
    GPS_FALLBACK("GPS Bearing")
}

data class CompassReading(
    val magneticAzimuthDeg: Float = 0f,
    val trueAzimuthDeg: Float = 0f,
    val declinationDeg: Float = 0f,
    val useTrueNorth: Boolean = true,
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val magneticFluxMicroTesla: Float = 0f,
    val accuracy: CompassAccuracy = CompassAccuracy.GPS_FALLBACK,
    val hasHardwareSensor: Boolean = false,
    val sensorSource: String = "Initializing"
) {
    val activeAzimuthDeg: Float
        get() = if (useTrueNorth) trueAzimuthDeg else magneticAzimuthDeg

    val cardinal: String
        get() = GeoMath.cardinalDirection(activeAzimuthDeg.toDouble())
}

/**
 * Aviation-grade offline sensor controller:
 * - Fused `TYPE_ROTATION_VECTOR` compass (with automatic `TYPE_ACCELEROMETER` + `TYPE_MAGNETIC_FIELD`
 *   fallback and GPS Course-Over-Ground fallback on devices without a magnetometer).
 * - Circular sin/cos vector smoothing to eliminate 359° ↔ 0° spin artifacts.
 * - Real True North declination calculation via `android.hardware.GeomagneticField`.
 * - Live 2-Axis Pitch & Roll inclinometer (bubble spirit level) and Magnetic Flux (µT) interference detector.
 * - Real hardware LED Torch + SOS Morse (`... --- ...`) + custom text Morse transmitter.
 */
class TorchAndCompassController(
    context: Context,
    private val scope: CoroutineScope
) : SensorEventListener {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasMagnetic = false

    // Circular vector smoothing state (prevents 359° -> 0° jump!)
    private var smoothedSin = 0.0
    private var smoothedCos = 1.0
    private var initializedHeading = false
    private var smoothedPitch = 0f
    private var smoothedRoll = 0f

    private var declinationDeg: Float = 0f
    private var useTrueNorth: Boolean = true
    private var currentFluxMicroTesla: Float = 0f
    private var currentAccuracy: CompassAccuracy = if (rotationVector != null || (accelerometer != null && magnetometer != null)) {
        CompassAccuracy.MEDIUM
    } else {
        CompassAccuracy.GPS_FALLBACK
    }

    private val _torchMode = MutableStateFlow(TorchMode.OFF)
    val torchMode: StateFlow<TorchMode> = _torchMode.asStateFlow()

    private val _torchAvailable = MutableStateFlow(findFlashCameraId() != null)
    val torchAvailable: StateFlow<Boolean> = _torchAvailable.asStateFlow()

    private val _compass = MutableStateFlow(
        CompassReading(
            hasHardwareSensor = rotationVector != null || (accelerometer != null && magnetometer != null),
            accuracy = currentAccuracy,
            sensorSource = when {
                rotationVector != null -> "Fused Rotation Vector"
                accelerometer != null && magnetometer != null -> "Accel + Magnetometer"
                else -> "GPS Heading Fallback"
            }
        )
    )
    val compass: StateFlow<CompassReading> = _compass.asStateFlow()

    private var strobeJob: Job? = null
    private var sensorsRegistered = false

    fun updateGeoLocationForDeclination(lat: Double, lon: Double, altitudeMeters: Float = 0f) {
        runCatching {
            val field = GeomagneticField(
                lat.toFloat(),
                lon.toFloat(),
                altitudeMeters,
                System.currentTimeMillis()
            )
            declinationDeg = field.declination
            val cur = _compass.value
            val trueAz = normalize360(cur.magneticAzimuthDeg + declinationDeg)
            _compass.value = cur.copy(
                declinationDeg = declinationDeg,
                trueAzimuthDeg = trueAz
            )
        }
    }

    fun updateGpsFallbackHeading(bearingDeg: Float, speedMps: Float) {
        if (_compass.value.hasHardwareSensor) return
        if (speedMps >= 0.6f || bearingDeg != 0f) {
            val norm = normalize360(bearingDeg)
            _compass.value = _compass.value.copy(
                magneticAzimuthDeg = norm,
                trueAzimuthDeg = norm,
                accuracy = CompassAccuracy.GPS_FALLBACK,
                sensorSource = "GPS Course Over Ground"
            )
        }
    }

    fun toggleTrueNorth() {
        useTrueNorth = !useTrueNorth
        _compass.value = _compass.value.copy(useTrueNorth = useTrueNorth)
    }

    private fun findFlashCameraId(): String? = runCatching {
        cameraManager.cameraIdList.firstOrNull { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            val lensFacing = chars.get(CameraCharacteristics.LENS_FACING)
            hasFlash && lensFacing == CameraCharacteristics.LENS_FACING_BACK
        } ?: cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    fun setTorchMode(mode: TorchMode) {
        strobeJob?.cancel()
        strobeJob = null
        val camId = findFlashCameraId()
        if (camId == null) {
            _torchAvailable.value = false
            _torchMode.value = TorchMode.OFF
            return
        }
        _torchAvailable.value = true
        _torchMode.value = mode

        when (mode) {
            TorchMode.OFF -> setHardwareTorch(camId, false)
            TorchMode.STEADY -> setHardwareTorch(camId, true)
            TorchMode.SOS -> transmitMorseViaTorch(camId, "SOS", repeat = true)
            TorchMode.CUSTOM_MORSE -> Unit
        }
    }

    fun transmitCustomMorse(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        strobeJob?.cancel()
        val camId = findFlashCameraId()
        if (camId == null) {
            _torchAvailable.value = false
            return
        }
        _torchMode.value = TorchMode.CUSTOM_MORSE
        transmitMorseViaTorch(camId, clean, repeat = false)
    }

    private fun transmitMorseViaTorch(cameraId: String, text: String, repeat: Boolean) {
        val morse = encodeToMorse(text)
        strobeJob = scope.launch(Dispatchers.Default) {
            val unitMs = 180L
            do {
                for (ch in morse) {
                    if (!isActive) break
                    when (ch) {
                        '.' -> {
                            setHardwareTorch(cameraId, true)
                            delay(unitMs)
                            setHardwareTorch(cameraId, false)
                            delay(unitMs)
                        }
                        '-' -> {
                            setHardwareTorch(cameraId, true)
                            delay(unitMs * 3)
                            setHardwareTorch(cameraId, false)
                            delay(unitMs)
                        }
                        ' ' -> delay(unitMs * 2)
                        '/' -> delay(unitMs * 4)
                    }
                }
                if (repeat) delay(unitMs * 6)
            } while (repeat && isActive)
            setHardwareTorch(cameraId, false)
            if (!repeat) _torchMode.value = TorchMode.OFF
        }
    }

    private fun setHardwareTorch(cameraId: String, enabled: Boolean) {
        runCatching { cameraManager.setTorchMode(cameraId, enabled) }
    }

    fun startCompass() {
        if (sensorsRegistered) return
        if (rotationVector != null) {
            sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_GAME)
            if (magnetometer != null) {
                sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
            }
            sensorsRegistered = true
        } else if (accelerometer != null && magnetometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME)
            sensorsRegistered = true
        }
    }

    fun stopCompass() {
        if (!sensorsRegistered) return
        sensorManager.unregisterListener(this)
        sensorsRegistered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val r = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(r, event.values)
                processRotationMatrix(r, "Fused Rotation Vector")
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                val mx = event.values[0]
                val my = event.values[1]
                val mz = event.values[2]
                currentFluxMicroTesla = sqrt(mx * mx + my * my + mz * mz)

                if (rotationVector == null) {
                    val alpha = 0.82f
                    geomagnetic[0] = alpha * geomagnetic[0] + (1f - alpha) * mx
                    geomagnetic[1] = alpha * geomagnetic[1] + (1f - alpha) * my
                    geomagnetic[2] = alpha * geomagnetic[2] + (1f - alpha) * mz
                    hasMagnetic = true
                    computeAccelMagMatrix()
                }
            }

            Sensor.TYPE_ACCELEROMETER -> {
                if (rotationVector == null) {
                    val alpha = 0.82f
                    gravity[0] = alpha * gravity[0] + (1f - alpha) * event.values[0]
                    gravity[1] = alpha * gravity[1] + (1f - alpha) * event.values[1]
                    gravity[2] = alpha * gravity[2] + (1f - alpha) * event.values[2]
                    hasGravity = true
                    computeAccelMagMatrix()
                }
            }
        }
    }

    private fun computeAccelMagMatrix() {
        if (!hasGravity || !hasMagnetic) return
        val r = FloatArray(9)
        val i = FloatArray(9)
        if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
            processRotationMatrix(r, "Accel + Magnetometer")
        }
    }

    private fun processRotationMatrix(rotationMatrix: FloatArray, sourceLabel: String) {
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)

        val rawPitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
        val rawRollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()

        // If phone is held upright (>55° pitch), remap Z-axis to screen-outward so compass stays accurate!
        val effectiveAzimuthRad = if (kotlin.math.abs(rawPitchDeg) > 55f) {
            val remapped = FloatArray(9)
            val remappedOrient = FloatArray(3)
            SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Z,
                remapped
            )
            SensorManager.getOrientation(remapped, remappedOrient)
            remappedOrient[0].toDouble()
        } else {
            orientation[0].toDouble()
        }

        // Circular exponential moving average — eliminates 359° <-> 0° snap artifacts
        val s = sin(effectiveAzimuthRad)
        val c = cos(effectiveAzimuthRad)
        if (!initializedHeading) {
            smoothedSin = s
            smoothedCos = c
            smoothedPitch = rawPitchDeg
            smoothedRoll = rawRollDeg
            initializedHeading = true
        } else {
            val alpha = 0.16
            smoothedSin += alpha * (s - smoothedSin)
            smoothedCos += alpha * (c - smoothedCos)
            smoothedPitch += 0.18f * (rawPitchDeg - smoothedPitch)
            smoothedRoll += 0.18f * (rawRollDeg - smoothedRoll)
        }

        val magAzimuth = normalize360(Math.toDegrees(atan2(smoothedSin, smoothedCos)).toFloat())
        val trueAzimuth = normalize360(magAzimuth + declinationDeg)

        _compass.value = CompassReading(
            magneticAzimuthDeg = magAzimuth,
            trueAzimuthDeg = trueAzimuth,
            declinationDeg = declinationDeg,
            useTrueNorth = useTrueNorth,
            pitchDeg = smoothedPitch,
            rollDeg = smoothedRoll,
            magneticFluxMicroTesla = currentFluxMicroTesla,
            accuracy = currentAccuracy,
            hasHardwareSensor = true,
            sensorSource = sourceLabel
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD || sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            currentAccuracy = when (accuracy) {
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> CompassAccuracy.HIGH
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> CompassAccuracy.MEDIUM
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> CompassAccuracy.LOW
                else -> CompassAccuracy.UNRELIABLE
            }
            _compass.value = _compass.value.copy(accuracy = currentAccuracy)
        }
    }

    fun shutdown() {
        setTorchMode(TorchMode.OFF)
        stopCompass()
    }

    companion object {
        private val MORSE_TABLE = mapOf(
            'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
            'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
            'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
            'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
            'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
            'Z' to "--..",
            '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
            '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----."
        )

        fun encodeToMorse(input: String): String = input.uppercase().mapNotNull { ch ->
            when {
                ch == ' ' -> "/"
                MORSE_TABLE.containsKey(ch) -> MORSE_TABLE[ch]
                else -> null
            }
        }.joinToString(" ")

        fun normalize360(deg: Float): Float = ((deg % 360f) + 360f) % 360f
    }
}
