package com.example.ghostgrid.routing

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 100% Offline Android TextToSpeech turn-by-turn voice guide.
 * Explicitly selects local on-device voice synthesis (never sends speech to cloud).
 */
class OfflineVoiceGuide(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    @Volatile
    private var isReady = false
    @Volatile
    private var enabled = true
    private var lastSpokenKey: String = ""

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts ?: return
            engine.language = Locale.US
            // Prefer an installed offline voice with zero network requirement
            val offlineVoice = engine.voices?.firstOrNull { voice ->
                voice.locale.language == Locale.US.language && !voice.isNetworkConnectionRequired
            }
            if (offlineVoice != null) {
                engine.voice = offlineVoice
            }
            engine.setSpeechRate(1.02f)
            isReady = true
        }
    }

    fun setEnabled(isEnabled: Boolean) {
        enabled = isEnabled
        if (!isEnabled) {
            tts?.stop()
        }
    }

    fun announceManeuver(maneuver: RouteManeuver, distanceMeters: Double) {
        if (!enabled || !isReady) return
        val bucket = when {
            distanceMeters <= 35.0 -> "now"
            distanceMeters <= 120.0 -> "100m"
            distanceMeters <= 320.0 -> "300m"
            else -> return
        }
        val key = "${maneuver.index}_$bucket"
        if (key == lastSpokenKey) return
        lastSpokenKey = key

        val prefix = when (bucket) {
            "now" -> ""
            "100m" -> "In 100 meters, "
            else -> "In 300 meters, "
        }
        speak("$prefix${maneuver.instructionText}")
    }

    fun announceAlert(message: String) {
        if (!enabled || !isReady) return
        lastSpokenKey = ""
        speak(message)
    }

    private fun speak(text: String) {
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS, "false")
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "gg_${System.currentTimeMillis()}")
    }

    fun shutdown() {
        isReady = false
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
