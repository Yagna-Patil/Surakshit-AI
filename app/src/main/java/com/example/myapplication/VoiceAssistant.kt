package com.example.myapplication

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceAssistant(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech = TextToSpeech(context, this)
    private var isReady = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
        }
    }

    fun speakWarning(text: String, langCode: String) {
        if (!isReady || text.isBlank()) return

        val targetLocale = when {
            langCode.startsWith("hi") -> Locale("hi", "IN")
            langCode.startsWith("gu") -> Locale("gu", "IN")
            langCode.startsWith("mr") -> Locale("mr", "IN")
            langCode.startsWith("ta") -> Locale("ta", "IN")
            langCode.startsWith("te") -> Locale("te", "IN")
            else -> Locale("en", "IN")
        }

        // Check availability of target voice engine
        val availability = tts.isLanguageAvailable(targetLocale)
        if (availability == TextToSpeech.LANG_AVAILABLE || availability == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
            tts.language = targetLocale
        } else {
            // Fallback to Hindi if target language voice data is missing on device
            tts.language = Locale("hi", "IN")
        }

        tts.setPitch(1.0f)
        tts.setSpeechRate(0.85f)

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "SurakshitVoiceTTS")
    }

    fun stop() {
        if (isReady && tts.isSpeaking) {
            tts.stop()
        }
    }

    fun shutdown() {
        tts.shutdown()
    }
}