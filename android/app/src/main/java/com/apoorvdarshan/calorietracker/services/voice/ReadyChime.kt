package com.apoorvdarshan.calorietracker.services.voice

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * The short "I'm listening" cue when a live voice session connects: two quick tones on the
 * call stream (the one the session uses, so the volume matches) and a light tick.
 */
internal object ReadyChime {
    fun play(context: Context) {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 220)
            Handler(Looper.getMainLooper()).postDelayed({ runCatching { tone.release() } }, 600)
        }
        runCatching {
            context.getSystemService(Vibrator::class.java)
                ?.vibrate(
                    if (android.os.Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    else VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
                )
        }
    }
}
